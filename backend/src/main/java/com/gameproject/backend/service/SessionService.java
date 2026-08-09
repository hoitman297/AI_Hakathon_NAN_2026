package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.Affinity;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageType;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.CreateSessionRequest;
import com.gameproject.backend.dto.NightSummaryResponse;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.repository.AffinityRepository;
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
    private final CulpritProfileRegistry culpritProfileRegistry;
    private final StaminaService staminaService;
    private final GameSaveService gameSaveService;
    private final LlmProxyClient llmProxyClient;
    private final SessionPersistenceService persistence;
    private final Executor llmParallelExecutor;

    private final Random random = new Random();

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, Account account) {
        long activeSaveCount = sessionRepository.countByAccountAndStatusNot(account, SessionStatus.DELETED);
        if (activeSaveCount >= GameConstants.MAX_SAVES_PER_ACCOUNT) {
            throw new IllegalStateException(
                    "세이브 슬롯이 가득 찼습니다 (최대 " + GameConstants.MAX_SAVES_PER_ACCOUNT + "개). 세이브를 삭제한 후 다시 시도해주세요.");
        }

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
                .gold(GameConstants.STARTING_GOLD)
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
                .map(event -> new NightSummaryResponse(event.getLocation(), event.getSummaryText(), toSceneType(event.getType())));
    }

    /** 맵 위 시설(양계장/수박밭 등) 파손 표시용 — 지금까지 발생한 사보타주 장소 이름만 중복 없이. */
    @Transactional(readOnly = true)
    public List<String> getSabotageLocations(Long sessionId) {
        GameSession session = findSession(sessionId);
        return sabotageEventRepository.findBySession(session).stream()
                .map(com.gameproject.backend.domain.SabotageEvent::getLocation)
                .distinct()
                .toList();
    }

    private String toSceneType(com.gameproject.backend.domain.SabotageType type) {
        if (type == null) return "theft";
        return switch (type) {
            case THEFT -> "theft";
            case DAMAGE -> "vandalism";
            case DISRUPTION -> "sabotage";
        };
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

    /**
     * "이어서하기" 슬롯 선택 화면용 — 이 계정의 세이브(삭제되지 않은 세션) 전체를 최신순으로
     * 반환한다. 진행중/성공/배드엔딩 상태를 그대로 노출해서, 프론트가 진행중인 건 이어서
     * 플레이하고 끝난 건 엔딩을 다시 보여주는 식으로 구분해 처리할 수 있게 한다.
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(Account account) {
        return sessionRepository.findByAccountAndStatusNotOrderByStartedAtDesc(account, SessionStatus.DELETED).stream()
                .map(session -> toResponse(session, currentStat(session)))
                .toList();
    }

    /**
     * 세이브 슬롯 삭제. 연관된 대화 로그/단서/호감도 등을 물리적으로 지우려면 여러 테이블을
     * 순서대로 지워야 해서 위험도가 높다 — 대신 상태만 DELETED로 바꿔 목록/슬롯 수 계산에서
     * 제외한다(소프트 삭제). 세션 소유권 검증은 SessionOwnershipInterceptor가 이미 처리한다.
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        GameSession session = findSession(sessionId);
        session.setStatus(SessionStatus.DELETED);
        sessionRepository.save(session);
    }

    /**
     * 낮 -&gt; 밤(사보타주 발생, 1~5일차) -&gt; 다음날. DB 읽기/쓰기는
     * {@link SessionPersistenceService}의 짧은 트랜잭션들로 처리하고, 이 메서드는 그 사이에서
     * LLM(llm-proxy) 호출만 담당한다 — 예전엔 이 메서드 전체가 하나의 트랜잭션이라 밤마다
     * LLM 호출(사보타주 연출 + 단서 문구, 최대 60초) 동안 DB 커넥션을 계속 붙들고 있었다.
     */
    public SessionResponse advanceDay(Long sessionId) {
        AdvanceDayContext ctx = persistence.prepareAdvanceDay(sessionId);

        String summaryText = null;
        String clueText = null;
        if (ctx.sabotage() != null) {
            SabotagePrepContext sabotage = ctx.sabotage();
            // 서로 독립적인 두 호출(연출 문구 vs 단서 문구)이라 순차 대기 대신 동시에 보낸다 —
            // 둘 다 최대 45초까지 걸릴 수 있어(LlmProxyRestClientConfig), 순차 실행이면 최악의
            // 경우 두 배로 늘어난다. 다음날 아침 화면(NightTransitionScreen)에 노출되는 연출
            // 문구 — 범인 정보는 안 들어간다.
            CompletableFuture<String> summaryFuture = CompletableFuture.supplyAsync(
                    () -> llmProxyClient.generateSabotageSummary(
                            sabotage.location(), sabotage.type().name(), sabotage.subTarget(), ctx.day()),
                    llmParallelExecutor);
            CompletableFuture<String> clueFuture = CompletableFuture.supplyAsync(
                    () -> llmProxyClient.generateClueContent(
                            sabotage.topic().name(), sabotage.culpritAppearanceDesc(), sabotage.culpritPersonalityDesc(),
                            sabotage.location(), sabotage.subTarget()),
                    llmParallelExecutor);
            summaryText = summaryFuture.join();
            clueText = clueFuture.join();
        }

        return persistence.finalizeAdvanceDay(ctx, summaryText, clueText);
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

    public GameSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다: " + sessionId));
    }

    PlayerStat currentStat(GameSession session) {
        return playerStatRepository.findBySessionAndDay(session, session.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("현재 일차의 플레이어 상태가 없습니다."));
    }

    /** package-private static — SessionPersistenceService도 순환 의존 없이 그대로 재사용한다. */
    static SessionResponse toResponse(GameSession session, PlayerStat stat) {
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
