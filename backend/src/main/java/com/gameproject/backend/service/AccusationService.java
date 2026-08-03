package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.AccusationLog;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.AccuseResultResponse;
import com.gameproject.backend.dto.EndingResponse;
import com.gameproject.backend.repository.AccusationLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.RandomEventLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccusationService {

    /** 기획서상 명확히 확인되는 "가까운 관계"는 나주부-전주인 부부뿐이라 이것만 정적으로 반영 */
    private static final Map<String, String> CLOSE_PARTNER = Map.of(
            "나주부", "전주인",
            "전주인", "나주부"
    );

    private static final List<String> VILLAGE_EVENTS = List.of(
            "마을 게시판 도발 쪽지", "특정 NPC 집 앞 정체불명 물건", "마을 공용시설 낙서·표식");
    private static final List<String> PLAYER_EVENTS = List.of("협박 편지", "밭 훼손");

    private final NpcRepository npcRepository;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final AccusationLogRepository accusationLogRepository;
    private final RandomEventLogRepository randomEventLogRepository;
    private final GameSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final NpcService npcService;
    private final LlmProxyClient llmProxyClient;
    private final GameSaveService gameSaveService;

    private final Random random = new Random();

    @Transactional
    public AccuseResultResponse accuse(Long sessionId, Long accusedNpcId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        int day = session.getCurrentDay();
        if (day < GameConstants.FIRST_ACCUSATION_DAY || day > GameConstants.LAST_ACCUSATION_DAY) {
            throw new IllegalStateException("고발은 " + GameConstants.FIRST_ACCUSATION_DAY + "~"
                    + GameConstants.LAST_ACCUSATION_DAY + "일차에만 가능합니다. (현재 " + day + "일차)");
        }
        // 기획상 "7→8→9일차 순차 재도전"은 하루 1회 시도를 전제로 한다. 이 제한이 없으면 같은 날
        // 오답을 무한히 반복해서 매번 랜덤 이벤트 LLM 호출(day 7/8)을 공짜로 재실행시킬 수 있었다.
        if (accusationLogRepository.existsBySessionAndDay(session, day)) {
            throw new IllegalStateException("오늘은 이미 고발을 시도했습니다. 다음날 다시 시도해주세요.");
        }

        Npc accused = npcRepository.findById(accusedNpcId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + accusedNpcId));
        boolean correct = accused.getNpcId().equals(session.getCulprit().getNpcId());

        accusationLogRepository.save(AccusationLog.builder()
                .session(session).day(day).accusedNpc(accused).correct(correct)
                .resolvedAt(LocalDateTime.now())
                .build());

        String message;
        if (correct) {
            session.setStatus(SessionStatus.SUCCESS);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            gameSaveService.syncEndingState(session.getAccount(), GameSaveService.ENDING_STATE_SUCCESS);
            message = "정답입니다! " + accused.getName() + "의 이야기가 공개됩니다.";
        } else {
            applyWrongAccusationPenalty(session, accused);

            if (day == GameConstants.FIRST_ACCUSATION_DAY) {
                logRandomEvent(session, day, EventTarget.VILLAGE, VILLAGE_EVENTS, accused.getName());
            } else if (day == GameConstants.FIRST_ACCUSATION_DAY + 1) {
                logRandomEvent(session, day, EventTarget.PLAYER, PLAYER_EVENTS, accused.getName());
            }

            if (day == GameConstants.LAST_ACCUSATION_DAY) {
                session.setStatus(SessionStatus.BAD_ENDING);
                session.setEndedAt(LocalDateTime.now());
                sessionRepository.save(session);
                gameSaveService.syncEndingState(session.getAccount(), GameSaveService.ENDING_STATE_BAD_ENDING);
            } else {
                sessionRepository.save(session);
            }
            message = "오답입니다. " + accused.getName() + "가 억울함을 토로합니다.";
        }

        return new AccuseResultResponse(correct, message, session.getStatus().name());
    }

    private void applyWrongAccusationPenalty(GameSession session, Npc accused) {
        npcService.adjustAffinity(session, accused, GameConstants.AFFINITY_WRONG_ACCUSED_PENALTY);

        String closePartnerName = CLOSE_PARTNER.get(accused.getName());
        for (Npc npc : npcRepository.findAll()) {
            if (npc.getNpcId().equals(accused.getNpcId())) {
                continue;
            }
            int delta;
            if (closePartnerName != null && closePartnerName.equals(npc.getName())) {
                int min = GameConstants.AFFINITY_WRONG_CLOSE_PENALTY_MAGNITUDE_MIN;
                int max = GameConstants.AFFINITY_WRONG_CLOSE_PENALTY_MAGNITUDE_MAX;
                delta = -(min + random.nextInt(max - min + 1));
            } else {
                delta = GameConstants.AFFINITY_WRONG_UNRELATED_PENALTY;
            }
            npcService.adjustAffinity(session, npc, delta);
        }
    }

    /** 이벤트 연출 텍스트 생성 LLM에는 범인 정보를 전혀 넘기지 않으므로 응답이 범인을 암시할 수 없다. */
    private void logRandomEvent(GameSession session, int day, EventTarget target, List<String> options,
                                 String accusedNpcName) {
        String eventType = options.get(random.nextInt(options.size()));
        String description = llmProxyClient.generateEventContent(eventType, target.name(), day, accusedNpcName);
        randomEventLogRepository.save(RandomEventLog.builder()
                .session(session).day(day).target(target).eventType(eventType)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public EndingResponse getEnding(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            return new EndingResponse("IN_PROGRESS", null, null, "아직 게임이 종료되지 않았습니다.");
        }
        if (session.getStatus() == SessionStatus.SUCCESS) {
            Npc culprit = session.getCulprit();
            NpcCaseAssignment assignment = caseAssignmentRepository.findBySession(session)
                    .orElseThrow(() -> new IllegalStateException("범인 배정 정보가 없습니다."));
            String story = getOrGenerateEndingStory(culprit, assignment);
            return new EndingResponse("SUCCESS", culprit.getNpcId(), culprit.getName(), story);
        }
        return new EndingResponse("BAD_ENDING", null, null,
                GameConstants.LAST_ACCUSATION_DAY + "일차까지 범인을 찾지 못해 마을에서 쫓겨났습니다.");
    }

    /** 엔딩 스토리는 세션당 1회만 생성해 캐시한다 — 다시 조회할 때마다 LLM을 다시 부르지 않는다. */
    private String getOrGenerateEndingStory(Npc culprit, NpcCaseAssignment assignment) {
        if (assignment.getEndingStoryText() != null) {
            return assignment.getEndingStoryText();
        }
        String story = llmProxyClient.generateEndingStory(
                culprit.getNpcId(), culprit.getName(), culprit.getRole(), culprit.getAge(),
                culprit.getPersonalityDesc(), culprit.getSpeechStyle(),
                assignment.getMotiveText(), assignment.getPrimaryType().name(), assignment.getTargetPoolDesc());
        assignment.setEndingStoryText(story);
        caseAssignmentRepository.save(assignment);
        return story;
    }
}
