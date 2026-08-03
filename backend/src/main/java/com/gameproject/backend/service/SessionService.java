package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.Affinity;
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.CreateSessionRequest;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.repository.AffinityRepository;
import com.gameproject.backend.repository.ClueCardRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.PlayerStatRepository;
import com.gameproject.backend.repository.SabotageEventRepository;
import com.gameproject.backend.service.CulpritProfileRegistry.CulpritProfile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final GameSessionRepository sessionRepository;
    private final NpcRepository npcRepository;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final AffinityRepository affinityRepository;
    private final PlayerStatRepository playerStatRepository;
    private final SabotageEventRepository sabotageEventRepository;
    private final ClueCardRepository clueCardRepository;
    private final CulpritProfileRegistry culpritProfileRegistry;

    private final Random random = new Random();

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, Account account) {
        List<Npc> npcs = npcRepository.findAll();
        if (npcs.isEmpty()) {
            throw new IllegalStateException("NPC 마스터 데이터가 없습니다. 서버 기동 시 시딩이 안 된 것 같습니다.");
        }
        Npc culprit = npcs.get(random.nextInt(npcs.size()));

        GameSession session = sessionRepository.save(GameSession.builder()
                .playerId(request.playerId())
                .account(account)
                .culprit(culprit)
                .currentDay(1)
                .status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build());

        CulpritProfile profile = culpritProfileRegistry.get(culprit.getName());
        caseAssignmentRepository.save(NpcCaseAssignment.builder()
                .session(session)
                .npc(culprit)
                .primaryType(profile.primaryType())
                .secondaryType(null) // 기획서 미확정
                .motiveText(profile.motiveText())
                .targetPoolDesc(profile.targetPoolDesc())
                .build());

        for (Npc npc : npcs) {
            affinityRepository.save(Affinity.builder()
                    .session(session)
                    .npc(npc)
                    .score(GameConstants.AFFINITY_START)
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        PlayerStat stat = playerStatRepository.save(PlayerStat.builder()
                .session(session)
                .day(1)
                .staminaCurrent(GameConstants.DEFAULT_STAMINA_MAX)
                .staminaMax(GameConstants.DEFAULT_STAMINA_MAX)
                .gold(0)
                .fainted(false)
                .build());

        return toResponse(session, stat);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId) {
        GameSession session = findSession(sessionId);
        PlayerStat stat = currentStat(session);
        return toResponse(session, stat);
    }

    @Transactional
    public SessionResponse advanceDay(Long sessionId) {
        GameSession session = findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        int day = session.getCurrentDay();
        PlayerStat today = currentStat(session);

        if (day <= GameConstants.SABOTAGE_NIGHTS) {
            generateNightSabotage(session, day);
        }

        int nextDay = day + 1;
        session.setCurrentDay(nextDay);
        sessionRepository.save(session);

        int newStamina = Boolean.TRUE.equals(today.getFainted())
                ? GameConstants.FAINT_RESTART_STAMINA
                : GameConstants.DEFAULT_STAMINA_MAX;

        PlayerStat next = playerStatRepository.save(PlayerStat.builder()
                .session(session)
                .day(nextDay)
                .staminaCurrent(newStamina)
                .staminaMax(GameConstants.DEFAULT_STAMINA_MAX)
                .gold(today.getGold())
                .fainted(false)
                .build());

        return toResponse(session, next);
    }

    private void generateNightSabotage(GameSession session, int day) {
        NpcCaseAssignment assignment = caseAssignmentRepository.findBySession(session)
                .orElseThrow(() -> new IllegalStateException("범인 배정 정보가 없습니다."));

        boolean selfDecoy = random.nextInt(100) < 10; // 낮은 확률(10%)로 자작극

        SabotageEvent event = sabotageEventRepository.save(SabotageEvent.builder()
                .session(session)
                .day(day)
                .location(assignment.getTargetPoolDesc())
                .type(assignment.getPrimaryType())
                .subTarget(null)
                .selfDecoy(selfDecoy)
                .witnessNpc(null)
                .createdAt(LocalDateTime.now())
                .build());

        // 단서 카드 5장 총량에 맞춰 1~5일차 밤마다 주제를 하나씩 순환 배정 (실제 문구는 추후 작가 콘텐츠로 교체 필요)
        ClueTopic topic = ClueTopic.values()[(day - 1) % ClueTopic.values().length];
        clueCardRepository.save(ClueCard.builder()
                .session(session)
                .sabotageEvent(event)
                .topic(topic)
                .textAmbiguous(topic.name() + " 관련 단서 — 그날 밤 " + assignment.getTargetPoolDesc() + " 부근에서 발견됨. 정확한 정황은 알 수 없다.")
                .textClarified(null)
                .acquired(false)
                .acquiredAt(null)
                .build());
    }

    public GameSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));
    }

    PlayerStat currentStat(GameSession session) {
        return playerStatRepository.findBySessionAndDay(session, session.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("현재 일차의 플레이어 상태가 없습니다."));
    }

    private SessionResponse toResponse(GameSession session, PlayerStat stat) {
        return new SessionResponse(
                session.getSessionId(),
                session.getPlayerId(),
                session.getAccount() != null ? session.getAccount().getAccountId() : null,
                session.getCurrentDay(),
                session.getStatus().name(),
                stat.getStaminaCurrent(),
                stat.getStaminaMax(),
                stat.getGold(),
                session.getStartedAt(),
                session.getEndedAt()
        );
    }
}
