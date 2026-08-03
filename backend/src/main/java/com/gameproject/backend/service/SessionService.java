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
import com.gameproject.backend.service.CulpritProfileRegistry.Target;

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
    private final NpcLocationResolver locationResolver;

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
                .targetPoolDesc(profile.describePool())
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

        // 9일차(마지막 재도전일)까지 정답 고발을 못 했다면 더 이상 다음 날로 넘어가지 않고
        // 배드엔딩으로 종료한다. (정답 고발 시 이미 accuse()에서 SUCCESS로 끝나 여기까지 오지 않고,
        // 9일차 오답도 accuse()에서 이미 BAD_ENDING 처리되므로, 여기서 걸리는 건 "9일차에
        // 고발을 아예 하지 않고 넘기려는 경우"뿐이다.)
        if (day >= GameConstants.LAST_ACCUSATION_DAY) {
            session.setStatus(SessionStatus.BAD_ENDING);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            return toResponse(session, today);
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
        CulpritProfile profile = culpritProfileRegistry.get(assignment.getNpc().getName());

        Target previousTarget = sabotageEventRepository.findBySessionAndDay(session, day - 1).stream()
                .findFirst()
                .map(prev -> new Target(prev.getLocation(), prev.getSubTarget()))
                .orElse(null);
        Target tonight = profile.pickTonight(random, previousTarget);

        boolean selfDecoy = random.nextInt(100) < 10; // 낮은 확률(10%)로 자작극
        Npc witness = findWitness(session, assignment.getNpc(), tonight.location());

        SabotageEvent event = sabotageEventRepository.save(SabotageEvent.builder()
                .session(session)
                .day(day)
                .location(tonight.location())
                .type(assignment.getPrimaryType())
                .subTarget(tonight.subTarget())
                .selfDecoy(selfDecoy)
                .witnessNpc(witness)
                .createdAt(LocalDateTime.now())
                .build());

        // 단서 카드 5장 총량에 맞춰 1~5일차 밤마다 주제를 하나씩 순환 배정 (실제 문구는 추후 작가 콘텐츠로 교체 필요)
        ClueTopic topic = ClueTopic.values()[(day - 1) % ClueTopic.values().length];
        String witnessHint = witness != null
                ? " 그 시각 근처에 " + witness.getName() + "이(가) 있었다는 이야기도 있다."
                : "";
        clueCardRepository.save(ClueCard.builder()
                .session(session)
                .sabotageEvent(event)
                .topic(topic)
                .textAmbiguous(topic.name() + " 관련 단서 — 그날 밤 " + tonight.location() + "의 "
                        + tonight.subTarget() + " 부근에서 발견됨." + witnessHint)
                .textClarified(null)
                .acquired(false)
                .acquiredAt(null)
                .build());
    }

    /** 사보타주 발생 장소에 그 시간 실제로 있었을 법한(낮 동선상 그 장소인) 범인 외 NPC를 찾는다. 없으면 null. */
    private Npc findWitness(GameSession session, Npc culprit, String sabotageLocation) {
        List<Npc> candidates = npcRepository.findAll().stream()
                .filter(npc -> !npc.getNpcId().equals(culprit.getNpcId()))
                .filter(npc -> sabotageLocation.equals(locationResolver.resolveToday(session, npc)))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
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
