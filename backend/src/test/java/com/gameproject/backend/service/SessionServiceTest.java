package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageType;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.CreateSessionRequest;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.repository.AffinityRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.PlayerStatRepository;
import com.gameproject.backend.repository.SabotageEventRepository;

/**
 * advanceDay()의 DB 읽기/쓰기는 SessionPersistenceService(짧은 트랜잭션들)로 옮겨졌으므로,
 * 이 테스트는 "LLM 호출은 항상 그 트랜잭션 바깥에서 일어난다"는 오케스트레이션만 검증한다.
 * SessionPersistenceService 자체 로직(사보타주 생성, 다음날 전환, 배드엔딩 등)은
 * SessionPersistenceServiceTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private NpcRepository npcRepository;
    @Mock
    private NpcCaseAssignmentRepository caseAssignmentRepository;
    @Mock
    private AffinityRepository affinityRepository;
    @Mock
    private PlayerStatRepository playerStatRepository;
    @Mock
    private SabotageEventRepository sabotageEventRepository;
    @Mock
    private StaminaService staminaService;
    @Mock
    private GameSaveService gameSaveService;
    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private SessionPersistenceService persistence;

    /** 순수 정적 룩업이라 실제 인스턴스를 그대로 사용 — 목킹할 이유가 없음. */
    private final CulpritProfileRegistry culpritProfileRegistry = new CulpritProfileRegistry();

    private SessionService sessionService;

    private GameSession session;
    private Account account;
    private Npc culprit;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, npcRepository, caseAssignmentRepository,
                affinityRepository, playerStatRepository, sabotageEventRepository,
                culpritProfileRegistry, staminaService, gameSaveService, llmProxyClient, persistence);

        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        culprit = Npc.builder().npcId(1L).name("나박수").role("수박밭 주인")
                .appearanceDesc("검고 긴 머리카락").personalityDesc("다혈질").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(culprit)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();

        // createSession 계열 테스트는 findById를 안 쓰고, already-ended 테스트는 save()까지 도달하지
        // 않고 예외를 던지므로 둘 다 lenient 처리.
        lenient().when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createSession_success_setsUpNewGameAndResetsSaveEndingState() {
        when(npcRepository.findAll()).thenReturn(List.of(culprit));
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = sessionService.createSession(new CreateSessionRequest(null), account);

        assertThat(response.currentDay()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.staminaCurrent()).isEqualTo(GameConstants.DEFAULT_STAMINA_MAX);
        // 이전 판이 success/bad_ending으로 끝난 세이브가 남아있어도, 새 세션 생성 시 in_progress로 리셋한다.
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_IN_PROGRESS);

        ArgumentCaptor<NpcCaseAssignment> captor = ArgumentCaptor.forClass(NpcCaseAssignment.class);
        verify(caseAssignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getSecondaryType()).isNotEqualTo(captor.getValue().getPrimaryType());
    }

    @Test
    void createSession_noNpcSeeded_throwsIllegalState() {
        when(npcRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> sessionService.createSession(new CreateSessionRequest(null), account))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createSession_saveSlotsFull_throwsIllegalStateWithoutCreatingAnything() {
        when(sessionRepository.countByAccountAndStatusNot(account, SessionStatus.DELETED))
                .thenReturn((long) GameConstants.MAX_SAVES_PER_ACCOUNT);

        assertThatThrownBy(() -> sessionService.createSession(new CreateSessionRequest(null), account))
                .isInstanceOf(IllegalStateException.class);

        verify(npcRepository, never()).findAll();
        verify(sessionRepository, never()).save(any(GameSession.class));
    }

    @Test
    void createSession_belowSlotCap_proceedsNormally() {
        when(sessionRepository.countByAccountAndStatusNot(account, SessionStatus.DELETED))
                .thenReturn((long) (GameConstants.MAX_SAVES_PER_ACCOUNT - 1));
        when(npcRepository.findAll()).thenReturn(List.of(culprit));
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = sessionService.createSession(new CreateSessionRequest(null), account);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void listSessions_excludesDeletedAndMapsAllStatuses() {
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(77.0).staminaMax(100).gold(3).fainted(false).build();
        when(sessionRepository.findByAccountAndStatusNotOrderByStartedAtDesc(account, SessionStatus.DELETED))
                .thenReturn(List.of(session));
        when(playerStatRepository.findBySessionAndDay(session, 2)).thenReturn(Optional.of(stat));

        List<SessionResponse> result = sessionService.listSessions(account);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sessionId()).isEqualTo(100L);
    }

    @Test
    void deleteSession_setsStatusToDeleted() {
        sessionService.deleteSession(100L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.DELETED);
        verify(sessionRepository).save(session);
    }

    @Test
    void getCurrentSession_hasInProgressSession_returnsIt() {
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(77.0).staminaMax(100).gold(3).fainted(false).build();
        when(sessionRepository.findFirstByAccountAndStatusOrderByStartedAtDesc(account, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));
        when(playerStatRepository.findBySessionAndDay(session, 2)).thenReturn(Optional.of(stat));

        Optional<SessionResponse> result = sessionService.getCurrentSession(account);

        assertThat(result).isPresent();
        assertThat(result.get().sessionId()).isEqualTo(100L);
        assertThat(result.get().staminaCurrent()).isEqualTo(77.0);
    }

    @Test
    void getCurrentSession_noInProgressSession_returnsEmpty() {
        // "이어서하기"가 클라이언트 저장 상태 없이도 서버 기준으로 정확히 판단해야 하는
        // 핵심 케이스 — 진행 중인 세션이 없으면 (과거처럼 새 세션을 몰래 만들지 않고) 빈 값을 반환한다.
        when(sessionRepository.findFirstByAccountAndStatusOrderByStartedAtDesc(account, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        Optional<SessionResponse> result = sessionService.getCurrentSession(account);

        assertThat(result).isEmpty();
    }

    @Test
    void advanceDay_sabotageNight_callsLlmWithPreparedContextThenFinalizes() {
        PlayerStat today = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(40.0).staminaMax(100).gold(10).fainted(false).build();
        AdvanceDayContext ctx = new AdvanceDayContext(session, today, 2, new SabotagePrepContext(
                "수박밭", SabotageType.DAMAGE, "화분", false, null, "", ClueTopic.HAIR,
                "검고 긴 머리카락", "다혈질"));
        when(persistence.prepareAdvanceDay(100L)).thenReturn(ctx);
        when(llmProxyClient.generateSabotageSummary("수박밭", "DAMAGE", "화분", 2))
                .thenReturn("간밤에 수박밭에서 소란이 있었다.");
        when(llmProxyClient.generateClueContent("HAIR", "검고 긴 머리카락", "다혈질", "수박밭", "화분"))
                .thenReturn("검고 긴 머리카락 한 올이 발견됐다.");
        SessionResponse expected = new SessionResponse(100L, null, 1L, 3, "IN_PROGRESS",
                100.0, 100, 10, false, null, session.getStartedAt(), null);
        when(persistence.finalizeAdvanceDay(ctx, "간밤에 수박밭에서 소란이 있었다.", "검고 긴 머리카락 한 올이 발견됐다."))
                .thenReturn(expected);

        SessionResponse response = sessionService.advanceDay(100L);

        assertThat(response).isEqualTo(expected);
        verify(persistence).finalizeAdvanceDay(ctx, "간밤에 수박밭에서 소란이 있었다.", "검고 긴 머리카락 한 올이 발견됐다.");
    }

    @Test
    void advanceDay_noSabotageTonight_skipsLlmCallsEntirely() {
        PlayerStat today = PlayerStat.builder().session(session).day(9)
                .staminaCurrent(50.0).staminaMax(100).gold(0).fainted(false).build();
        AdvanceDayContext ctx = new AdvanceDayContext(session, today, 9, null);
        when(persistence.prepareAdvanceDay(100L)).thenReturn(ctx);
        SessionResponse expected = new SessionResponse(100L, null, 1L, 9, "BAD_ENDING",
                50.0, 100, 0, false, null, session.getStartedAt(), LocalDateTime.now());
        when(persistence.finalizeAdvanceDay(ctx, null, null)).thenReturn(expected);

        SessionResponse response = sessionService.advanceDay(100L);

        assertThat(response).isEqualTo(expected);
        verify(llmProxyClient, never()).generateSabotageSummary(any(), any(), any(), any(Integer.class));
        verify(llmProxyClient, never()).generateClueContent(any(), any(), any(), any(), any());
        verify(persistence).finalizeAdvanceDay(ctx, null, null);
    }
}
