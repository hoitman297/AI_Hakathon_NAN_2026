package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SabotageType;
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
    private ClueCardRepository clueCardRepository;
    @Mock
    private NpcLocationResolver locationResolver;
    @Mock
    private StaminaService staminaService;
    @Mock
    private GameSaveService gameSaveService;
    @Mock
    private LlmProxyClient llmProxyClient;

    /** 순수 정적 룩업이라 실제 인스턴스를 그대로 사용 — 목킹할 이유가 없음. */
    private final CulpritProfileRegistry culpritProfileRegistry = new CulpritProfileRegistry();

    private SessionService sessionService;

    private GameSession session;
    private Account account;
    private Npc culprit;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, npcRepository, caseAssignmentRepository,
                affinityRepository, playerStatRepository, sabotageEventRepository, clueCardRepository,
                culpritProfileRegistry, locationResolver, staminaService, gameSaveService, llmProxyClient);

        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        culprit = Npc.builder().npcId(1L).name("나박수").role("수박밭 주인").build();
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
    void advanceDay_normalTransition_incrementsDayAndResetsStamina() {
        PlayerStat today = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(40.0).staminaMax(100).gold(10).fainted(false).build();
        when(playerStatRepository.findBySessionAndDay(session, 2)).thenReturn(Optional.of(today));
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));
        stubNightSabotageCollaborators();

        SessionResponse response = sessionService.advanceDay(100L);

        assertThat(response.currentDay()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.staminaCurrent()).isEqualTo(GameConstants.DEFAULT_STAMINA_MAX);
        verify(sabotageEventRepository).save(any(SabotageEvent.class));
        verify(clueCardRepository).save(any(ClueCard.class));
        verify(gameSaveService, never()).syncEndingState(any(), any());
    }

    @Test
    void advanceDay_faintedToday_nextDayStartsAtFaintRestartStamina() {
        PlayerStat today = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(0.0).staminaMax(100).gold(5).fainted(true).build();
        when(playerStatRepository.findBySessionAndDay(session, 2)).thenReturn(Optional.of(today));
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));
        stubNightSabotageCollaborators();

        SessionResponse response = sessionService.advanceDay(100L);

        assertThat(response.staminaCurrent()).isEqualTo(GameConstants.FAINT_RESTART_STAMINA);
    }

    @Test
    void advanceDay_lastAccusationDayWithoutAccusation_setsBadEndingAndSyncsSave() {
        session.setCurrentDay(GameConstants.LAST_ACCUSATION_DAY); // 9일차, 사보타주 발생일(1~5) 아님
        PlayerStat today = PlayerStat.builder().session(session).day(9)
                .staminaCurrent(50.0).staminaMax(100).gold(0).fainted(false).build();
        when(playerStatRepository.findBySessionAndDay(session, 9)).thenReturn(Optional.of(today));

        SessionResponse response = sessionService.advanceDay(100L);

        assertThat(response.status()).isEqualTo(SessionStatus.BAD_ENDING.name());
        assertThat(session.getEndedAt()).isNotNull();
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_BAD_ENDING);
        verify(playerStatRepository, never()).save(any(PlayerStat.class));
        verify(sabotageEventRepository, never()).save(any());
    }

    @Test
    void advanceDay_alreadyEndedSession_throwsIllegalState() {
        session.setStatus(SessionStatus.BAD_ENDING);

        assertThatThrownBy(() -> sessionService.advanceDay(100L))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 1~5일차 밤 사보타주 생성 경로(generateNightSabotage)가 필요로 하는 협력자들을 스텁한다. */
    private void stubNightSabotageCollaborators() {
        NpcCaseAssignment assignment = NpcCaseAssignment.builder()
                .session(session).npc(culprit)
                .primaryType(SabotageType.DAMAGE).secondaryType(SabotageType.THEFT)
                .build();
        when(caseAssignmentRepository.findBySession(session)).thenReturn(Optional.of(assignment));
        when(sabotageEventRepository.findBySessionAndDay(eq(session), anyInt())).thenReturn(List.of());
        when(npcRepository.findAll()).thenReturn(List.of(culprit));
        when(llmProxyClient.generateClueContent(any(), any(), any(), any(), any())).thenReturn("단서 문구");
    }
}
