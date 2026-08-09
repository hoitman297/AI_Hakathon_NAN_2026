package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.AccusationLog;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.EndingResponse;
import com.gameproject.backend.repository.AccusationLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.RandomEventLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * AccusationService의 DB 읽기/쓰기 전용 조각들. 이 클래스는 LLM(llm-proxy)을 절대 호출하지
 * 않는다 — 의존성에 LlmProxyClient가 없다는 것 자체가 그 보장이다. 고발/엔딩 조회는 각각
 * 최대 두 번(사보타주 이벤트 연출 + 오답 반응, 또는 엔딩 스토리) LLM을 부를 수 있는데, 그 호출을
 * 이 클래스 메서드 안에서 하면(=하나의 트랜잭션으로 감싸면) DB 커넥션을 그동안 붙들게 된다.
 * 그래서 LLM 호출과 무관한 쓰기는 전부 트랜잭션 안에서 먼저 끝내고, LLM 응답이 있어야만
 * 가능한 쓰기(사보타주 이벤트 설명, 엔딩 스토리 캐싱)만 별도의 짧은 트랜잭션으로 나중에 한다.
 *
 * <p>별도 빈으로 분리한 이유는 DialogueChatPersistenceService와 같다: AccusationService가
 * 이 메서드들을 같은 클래스 안에서(self-invocation) 호출하면 {@code @Transactional}이
 * Spring 프록시를 거치지 않아 조용히 무시되기 때문이다.
 */
@Service
@RequiredArgsConstructor
class AccusationPersistenceService {

    /** 기획서상 명확히 확인되는 "가까운 관계"는 나주부-전주인 부부뿐이라 이것만 정적으로 반영 */
    private static final Map<String, String> CLOSE_PARTNER = Map.of(
            "나주부", "전주인",
            "전주인", "나주부"
    );

    // "마을 공용시설 낙서·표식"은 프론트 UI(❗ 표시/알림)가 아직 없어 이번 라운드엔 후보에서
    // 뺐다 — 나중에 지원하게 되면 다시 추가.
    private static final List<String> VILLAGE_EVENTS = List.of(
            "마을 게시판 도발 쪽지", "특정 NPC 집 앞 정체불명 물건");
    private static final List<String> PLAYER_EVENTS = List.of("협박 편지", "밭 훼손");

    private final NpcRepository npcRepository;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final AccusationLogRepository accusationLogRepository;
    private final RandomEventLogRepository randomEventLogRepository;
    private final GameSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final NpcService npcService;
    private final GameSaveService gameSaveService;

    private final Random random = new Random();

    @Transactional
    AccusationOutcome prepareAccusation(Long sessionId, Long accusedNpcId) {
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

        if (correct) {
            session.setStatus(SessionStatus.SUCCESS);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            gameSaveService.syncEndingState(session.getAccount(), GameSaveService.ENDING_STATE_SUCCESS);
            return new AccusationOutcome(true, session.getStatus().name(), accused, false, null, null, day, session);
        }

        applyWrongAccusationPenalty(session, accused);

        boolean needsEventDescription = false;
        String eventType = null;
        EventTarget eventTarget = null;
        if (day == GameConstants.FIRST_ACCUSATION_DAY) {
            eventTarget = EventTarget.VILLAGE;
            eventType = VILLAGE_EVENTS.get(random.nextInt(VILLAGE_EVENTS.size()));
            needsEventDescription = true;
        } else if (day == GameConstants.FIRST_ACCUSATION_DAY + 1) {
            eventTarget = EventTarget.PLAYER;
            eventType = PLAYER_EVENTS.get(random.nextInt(PLAYER_EVENTS.size()));
            needsEventDescription = true;
        }

        if (day == GameConstants.LAST_ACCUSATION_DAY) {
            session.setStatus(SessionStatus.BAD_ENDING);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            gameSaveService.syncEndingState(session.getAccount(), GameSaveService.ENDING_STATE_BAD_ENDING);
        } else {
            sessionRepository.save(session);
        }

        return new AccusationOutcome(false, session.getStatus().name(), accused,
                needsEventDescription, eventType, eventTarget, day, session);
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

    @Transactional
    void saveRandomEvent(GameSession session, int day, EventTarget target, String eventType, String description) {
        randomEventLogRepository.save(RandomEventLog.builder()
                .session(session).day(day).target(target).eventType(eventType)
                .description(description).viewed(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    EndingOutcome prepareEnding(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            return EndingOutcome.finished(new EndingResponse("IN_PROGRESS", null, null, "아직 게임이 종료되지 않았습니다."));
        }
        if (session.getStatus() == SessionStatus.SUCCESS) {
            // session.getCulprit()는 지연 로딩 프록시라 트랜잭션 밖에서 필드를 읽으면
            // LazyInitializationException이 난다 — ID만 꺼내 npcRepository로 직접 다시
            // 조회해서(완전히 로드된 엔티티) 트랜잭션 경계 밖에서도 안전하게 넘긴다.
            Long culpritNpcId = session.getCulprit().getNpcId();
            Npc culprit = npcRepository.findById(culpritNpcId)
                    .orElseThrow(() -> new IllegalStateException("범인 NPC 정보가 없습니다: " + culpritNpcId));
            NpcCaseAssignment assignment = caseAssignmentRepository.findBySession(session)
                    .orElseThrow(() -> new IllegalStateException("범인 배정 정보가 없습니다."));

            if (assignment.getEndingStoryText() != null) {
                return EndingOutcome.finished(new EndingResponse(
                        "SUCCESS", culprit.getNpcId(), culprit.getName(), assignment.getEndingStoryText()));
            }

            return new EndingOutcome(null, culprit.getNpcId(), culprit.getName(), culprit.getRole(), culprit.getAge(),
                    culprit.getPersonalityDesc(), culprit.getSpeechStyle(),
                    assignment.getMotiveText(), assignment.getPrimaryType().name(), assignment.getTargetPoolDesc(),
                    assignment);
        }
        return EndingOutcome.finished(new EndingResponse("BAD_ENDING", null, null,
                GameConstants.LAST_ACCUSATION_DAY + "일차까지 범인을 찾지 못해 마을에서 쫓겨났습니다."));
    }

    /** 엔딩 스토리는 세션당 1회만 생성해 캐시한다 — 다시 조회할 때마다 LLM을 다시 부르지 않는다. */
    @Transactional
    EndingResponse saveEndingStory(NpcCaseAssignment assignment, Long culpritNpcId, String culpritName, String story) {
        assignment.setEndingStoryText(story);
        caseAssignmentRepository.save(assignment);
        return new EndingResponse("SUCCESS", culpritNpcId, culpritName, story);
    }
}
