package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.Affinity;
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SabotageType;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.CreateSessionRequest;
import com.gameproject.backend.dto.NightSummaryResponse;
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
    private final StaminaService staminaService;
    private final GameSaveService gameSaveService;
    private final LlmProxyClient llmProxyClient;

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
        SabotageType secondaryType = culpritProfileRegistry.pickSecondaryType(random, profile.primaryType());
        caseAssignmentRepository.save(NpcCaseAssignment.builder()
                .session(session)
                .npc(culprit)
                .primaryType(profile.primaryType())
                .secondaryType(secondaryType)
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
                .staminaCurrent((double) GameConstants.DEFAULT_STAMINA_MAX)
                .staminaMax(GameConstants.DEFAULT_STAMINA_MAX)
                .gold(0)
                .fainted(false)
                .build());

        // 이전 판이 끝난 채로(success/bad_ending) 남아있는 세이브가 있다면, 새 판을 시작하는
        // 시점에 ending_state를 in_progress로 되돌린다 — game_save는 "현재 진행 중인 판"의
        // 상태를 반영해야 한다는 원칙(TODO.md 7번)을 세션 시작 시점까지 일관되게 적용.
        gameSaveService.syncEndingState(account, GameSaveService.ENDING_STATE_IN_PROGRESS);

        return toResponse(session, stat);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId) {
        GameSession session = findSession(sessionId);
        PlayerStat stat = currentStat(session);
        return toResponse(session, stat);
    }

    /** NightTransitionScreen용 — 지정한 일차 밤에 사보타주가 있었다면 장소/연출 문구를 반환. */
    @Transactional(readOnly = true)
    public Optional<NightSummaryResponse> getNightSummary(Long sessionId, int day) {
        GameSession session = findSession(sessionId);
        return sabotageEventRepository.findBySessionAndDay(session, day).stream()
                .findFirst()
                .map(event -> new NightSummaryResponse(event.getLocation(), event.getSummaryText()));
    }

    /**
     * "이어서하기"용 — 이 계정의 진행 중인(IN_PROGRESS) 세션을 찾는다. 프론트가 세션 ID를
     * localStorage에만 들고 있다 보니, 그 값이 사라지면(다른 기기/브라우저 저장소 초기화 등)
     * 실제로는 서버에 진행 중인 게임이 있어도 "이어서하기"가 새 게임을 만들어버리는 문제가
     * 있었다 — 이 API로 클라이언트 상태와 무관하게 서버가 진실 공급원이 되게 한다.
     * 같은 계정에 IN_PROGRESS 세션이 여러 개 있을 수 있는 상황(과거 버그로 인한 잔재 등)을
     * 대비해 가장 최근에 시작한 세션을 반환한다.
     */
    @Transactional(readOnly = true)
    public Optional<SessionResponse> getCurrentSession(Account account) {
        return sessionRepository.findFirstByAccountAndStatusOrderByStartedAtDesc(account, SessionStatus.IN_PROGRESS)
                .map(session -> toResponse(session, currentStat(session)));
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
            gameSaveService.syncEndingState(session.getAccount(), GameSaveService.ENDING_STATE_BAD_ENDING);
            return toResponse(session, today);
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

        return toResponse(session, next);
    }

    /**
     * 이동으로 실제 경과한 시간(초)만큼 체력을 소모한다 (기획서 체력 세부 수치, ✅ 확정:
     * 초당 0.15 소모, 정지 시 없음, 운동화 착용 시 초당 0.12로 20% 감소). 프론트가 캐릭터가
     * 실제로 움직인 시간을 누적해서 이 API로 보고하는 방식을 전제로 한다.
     */
    @Transactional
    public SessionResponse move(Long sessionId, double movedSeconds) {
        GameSession session = findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        double ratePerSecond = Boolean.TRUE.equals(session.getSneakersEquipped())
                ? GameConstants.MOVE_STAMINA_PER_SECOND_WITH_SNEAKERS
                : GameConstants.MOVE_STAMINA_PER_SECOND;
        PlayerStat stat = staminaService.consume(session, ratePerSecond * movedSeconds);
        return toResponse(session, stat);
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
        SabotageType tonightType = pickTonightType(assignment, random);

        // 다음날 아침 화면(NightTransitionScreen)에 노출되는 연출 문구 — 범인 정보는 안 들어간다.
        String summaryText = llmProxyClient.generateSabotageSummary(
                tonight.location(), tonightType.name(), tonight.subTarget(), day);

        SabotageEvent event = sabotageEventRepository.save(SabotageEvent.builder()
                .session(session)
                .day(day)
                .location(tonight.location())
                .type(tonightType)
                .subTarget(tonight.subTarget())
                .selfDecoy(selfDecoy)
                .witnessNpc(witness)
                .summaryText(summaryText)
                .createdAt(LocalDateTime.now())
                .build());

        // 단서 카드 5장 총량에 맞춰 1~5일차 밤마다 주제를 하나씩 순환 배정
        ClueTopic topic = ClueTopic.values()[(day - 1) % ClueTopic.values().length];
        String witnessHint = witness != null
                ? " 그 시각 근처에 " + witness.getName() + "이(가) 있었다는 이야기도 있다."
                : "";
        Npc culprit = assignment.getNpc();
        String clueText = llmProxyClient.generateClueContent(
                topic.name(), culprit.getAppearanceDesc(), culprit.getPersonalityDesc(),
                tonight.location(), tonight.subTarget()) + witnessHint;
        clueCardRepository.save(ClueCard.builder()
                .session(session)
                .sabotageEvent(event)
                .topic(topic)
                .textAmbiguous(clueText)
                .textClarified(null)
                .acquired(false)
                .acquiredAt(null)
                .build());
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

    public GameSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));
    }

    PlayerStat currentStat(GameSession session) {
        return playerStatRepository.findBySessionAndDay(session, session.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("현재 일차의 플레이어 상태가 없습니다."));
    }

    private SessionResponse toResponse(GameSession session, PlayerStat stat) {
        // honestModeNpcId는 실제로 소모되기 전까지 남아있을 수 있어(예: 구매만 하고 그날 안 씀),
        // DialogueService와 같은 기준(당일 여부)으로 걸러야 "어제 산 정직모드가 오늘도 활성"으로
        // 잘못 표시되지 않는다.
        Long activeHonestModeNpcId = session.getCurrentDay().equals(session.getHonestModeDay())
                ? session.getHonestModeNpcId()
                : null;
        return new SessionResponse(
                session.getSessionId(),
                session.getPlayerId(),
                session.getAccount() != null ? session.getAccount().getAccountId() : null,
                session.getCurrentDay(),
                session.getStatus().name(),
                stat.getStaminaCurrent(),
                stat.getStaminaMax(),
                stat.getGold(),
                session.getSneakersEquipped(),
                activeHonestModeNpcId,
                session.getStartedAt(),
                session.getEndedAt()
        );
    }
}
