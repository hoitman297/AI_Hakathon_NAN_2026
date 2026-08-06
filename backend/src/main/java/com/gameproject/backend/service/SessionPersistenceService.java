package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SabotageType;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.repository.ClueCardRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.PlayerStatRepository;
import com.gameproject.backend.repository.SabotageEventRepository;
import com.gameproject.backend.service.CulpritProfileRegistry.CulpritProfile;
import com.gameproject.backend.service.CulpritProfileRegistry.Target;

import lombok.RequiredArgsConstructor;

/**
 * SessionService.advanceDay()의 DB 읽기/쓰기 전용 조각. 1~5일차 밤마다 사보타주 연출 문구와
 * 단서 카드 문구를 llm-proxy로 생성하는데(각각 최대 30초), 그 두 호출을 이 클래스 메서드
 * "안"에서 하면(=하나의 트랜잭션으로 감싸면) DB 커넥션을 그동안 붙들게 된다. 그래서 LLM 호출과
 * 무관한 읽기/쓰기는 트랜잭션 안에서 먼저 끝내고, LLM 응답이 있어야만 가능한 쓰기
 * (SabotageEvent.summaryText, ClueCard.textAmbiguous)만 별도의 짧은 트랜잭션으로 나중에 한다.
 *
 * <p>별도 빈으로 분리한 이유는 DialogueChatPersistenceService와 같다: SessionService가
 * 이 메서드들을 같은 클래스 안에서(self-invocation) 호출하면 {@code @Transactional}이
 * Spring 프록시를 거치지 않아 조용히 무시되기 때문이다. (SessionService 자체를 주입받으면
 * SessionService → 이 빈 순환 의존이 생기므로, findSession/toResponse에 해당하는 로직은
 * 여기서 필요한 만큼만 다시 구현한다 — SessionService.toResponse는 인스턴스 상태를 안 쓰는
 * package-private static 메서드라 순환 의존 없이 그대로 재사용한다.)
 */
@Service
@RequiredArgsConstructor
class SessionPersistenceService {

    private final GameSessionRepository sessionRepository;
    private final PlayerStatRepository playerStatRepository;
    private final NpcRepository npcRepository;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final SabotageEventRepository sabotageEventRepository;
    private final ClueCardRepository clueCardRepository;
    private final NpcLocationResolver locationResolver;
    private final CulpritProfileRegistry culpritProfileRegistry;
    private final GameSaveService gameSaveService;

    private final Random random = new Random();

    @Transactional
    AdvanceDayContext prepareAdvanceDay(Long sessionId) {
        GameSession session = findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        int day = session.getCurrentDay();
        PlayerStat today = currentStat(session);

        SabotagePrepContext sabotage = day <= GameConstants.SABOTAGE_NIGHTS ? prepareSabotage(session, day) : null;

        return new AdvanceDayContext(session, today, day, sabotage);
    }

    /**
     * prepareAdvanceDay()가 넘겨준 컨텍스트 + (필요했다면) LLM이 생성한 사보타주 연출/단서
     * 문구를 받아 나머지 쓰기(SabotageEvent/ClueCard 저장, 다음날 전환 또는 배드엔딩 처리)를
     * 한 트랜잭션으로 커밋한다. sabotageSummaryText/clueText는 sabotage가 null이면 안 쓴다.
     */
    @Transactional
    SessionResponse finalizeAdvanceDay(AdvanceDayContext ctx, String sabotageSummaryText, String clueText) {
        GameSession session = ctx.session();
        PlayerStat today = ctx.today();
        int day = ctx.day();

        if (ctx.sabotage() != null) {
            saveSabotageAndClue(session, day, ctx.sabotage(), sabotageSummaryText, clueText);
        }

        // 9일차(마지막 재도전일)까지 정답 고발을 못 했다면 더 이상 다음 날로 넘어가지 않고
        // 배드엔딩으로 종료한다. (정답 고발 시 이미 accuse()에서 SUCCESS로 끝나 여기까지 오지 않고,
        // 9일차 오답도 accuse()에서 이미 BAD_ENDING 처리되므로, 여기서 걸리는 건 "9일차에
        // 고발을 아예 하지 않고 넘기려는 경우"뿐이다.)
        if (day >= GameConstants.LAST_ACCUSATION_DAY) {
            session.setStatus(SessionStatus.BAD_ENDING);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);
            gameSaveService.syncEndingState(session.getAccount(), GameSaveService.ENDING_STATE_BAD_ENDING);
            return SessionService.toResponse(session, today);
        }

        int nextDay = day + 1;
        session.setCurrentDay(nextDay);
        sessionRepository.save(session);

        double newStamina = Boolean.TRUE.equals(today.getFainted())
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

        return SessionService.toResponse(session, next);
    }

    private void saveSabotageAndClue(GameSession session, int day, SabotagePrepContext sabotage,
                                      String sabotageSummaryText, String clueText) {
        SabotageEvent event = sabotageEventRepository.save(SabotageEvent.builder()
                .session(session)
                .day(day)
                .location(sabotage.location())
                .type(sabotage.type())
                .subTarget(sabotage.subTarget())
                .selfDecoy(sabotage.selfDecoy())
                .witnessNpc(sabotage.witness())
                .summaryText(sabotageSummaryText)
                .createdAt(LocalDateTime.now())
                .build());

        clueCardRepository.save(ClueCard.builder()
                .session(session)
                .sabotageEvent(event)
                .topic(sabotage.topic())
                .textAmbiguous(clueText + sabotage.witnessHint())
                .textClarified(null)
                .acquired(false)
                .acquiredAt(null)
                .build());
    }

    private SabotagePrepContext prepareSabotage(GameSession session, int day) {
        NpcCaseAssignment assignment = caseAssignmentRepository.findBySession(session)
                .orElseThrow(() -> new IllegalStateException("범인 배정 정보가 없습니다."));
        // culprit은 assignment.getNpc()(지연 로딩)를 여기서(트랜잭션이 열려 있는 동안) 초기화해서
        // 필요한 필드를 미리 문자열로 꺼내둔다 — 이후 트랜잭션 밖(LLM 호출 구간)에서도 안전하다.
        Npc culprit = assignment.getNpc();
        CulpritProfile profile = culpritProfileRegistry.get(culprit.getName());

        Target previousTarget = sabotageEventRepository.findBySessionAndDay(session, day - 1).stream()
                .findFirst()
                .map(prev -> new Target(prev.getLocation(), prev.getSubTarget()))
                .orElse(null);
        Target tonight = profile.pickTonight(random, previousTarget);

        boolean selfDecoy = random.nextInt(100) < 10; // 낮은 확률(10%)로 자작극
        Npc witness = findWitness(session, culprit, tonight.location());
        SabotageType tonightType = pickTonightType(assignment, random);

        // 단서 카드 5장 총량에 맞춰 1~5일차 밤마다 주제를 하나씩 순환 배정
        ClueTopic topic = ClueTopic.values()[(day - 1) % ClueTopic.values().length];
        String witnessHint = witness != null
                ? " 그 시각 근처에 " + witness.getName() + "이(가) 있었다는 이야기도 있다."
                : "";

        return new SabotagePrepContext(tonight.location(), tonightType, tonight.subTarget(), selfDecoy, witness,
                witnessHint, topic, culprit.getAppearanceDesc(), culprit.getPersonalityDesc());
    }

    /**
     * 유형만으로 범인이 특정되지 않도록, 주 유형을 기본으로 하되 보조 유형이 배정돼 있으면
     * 낮은 확률(기본 20%)로 그날 밤은 보조 유형을 대신 쓴다.
     */
    private SabotageType pickTonightType(NpcCaseAssignment assignment, Random random) {
        if (assignment.getSecondaryType() == null) {
            return assignment.getPrimaryType();
        }
        boolean useSecondary = random.nextInt(100) < GameConstants.SECONDARY_SABOTAGE_TYPE_CHANCE_PERCENT;
        return useSecondary ? assignment.getSecondaryType() : assignment.getPrimaryType();
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

    private GameSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));
    }

    private PlayerStat currentStat(GameSession session) {
        return playerStatRepository.findBySessionAndDay(session, session.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("현재 일차의 플레이어 상태가 없습니다."));
    }
}
