package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.gameproject.backend.domain.AccusationLog;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.AccuseResultResponse;
import com.gameproject.backend.repository.AccusationLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.RandomEventLogRepository;

@ExtendWith(MockitoExtension.class)
class AccusationServiceTest {

    @Mock
    private NpcRepository npcRepository;
    @Mock
    private NpcCaseAssignmentRepository caseAssignmentRepository;
    @Mock
    private AccusationLogRepository accusationLogRepository;
    @Mock
    private RandomEventLogRepository randomEventLogRepository;
    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private NpcService npcService;
    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private GameSaveService gameSaveService;

    private AccusationService accusationService;

    private GameSession session;
    private Account account;
    private Npc culprit;
    private Npc other;

    @BeforeEach
    void setUp() {
        accusationService = new AccusationService(npcRepository, caseAssignmentRepository, accusationLogRepository,
                randomEventLogRepository, sessionRepository, sessionService, npcService, llmProxyClient, gameSaveService);

        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        culprit = Npc.builder().npcId(1L).name("나박수").role("수박밭 주인").build();
        other = Npc.builder().npcId(2L).name("현수동").role("이장").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(culprit)
                .currentDay(GameConstants.FIRST_ACCUSATION_DAY)
                .status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
        when(sessionService.findSession(100L)).thenReturn(session);
    }

    @Test
    void accuse_correctGuess_setsSuccessAndSyncsSave() {
        when(npcRepository.findById(1L)).thenReturn(Optional.of(culprit));

        AccuseResultResponse response = accusationService.accuse(100L, 1L);

        assertThat(response.correct()).isTrue();
        assertThat(response.sessionStatus()).isEqualTo("SUCCESS");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SUCCESS);
        assertThat(session.getEndedAt()).isNotNull();
        verify(sessionRepository).save(session);
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_SUCCESS);
        verify(accusationLogRepository).save(any(AccusationLog.class));
        verify(randomEventLogRepository, never()).save(any());
    }

    @Test
    void accuse_wrongGuess_firstAccusationDay_logsVillageEventAndStaysInProgress() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY); // 7일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        AccuseResultResponse response = accusationService.accuse(100L, 2L);

        assertThat(response.correct()).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(gameSaveService, never()).syncEndingState(any(), any());

        ArgumentCaptor<RandomEventLog> captor = ArgumentCaptor.forClass(RandomEventLog.class);
        verify(randomEventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTarget()).isEqualTo(EventTarget.VILLAGE);
    }

    @Test
    void accuse_wrongGuess_secondAccusationDay_logsPlayerEvent() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY + 1); // 8일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        accusationService.accuse(100L, 2L);

        ArgumentCaptor<RandomEventLog> captor = ArgumentCaptor.forClass(RandomEventLog.class);
        verify(randomEventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTarget()).isEqualTo(EventTarget.PLAYER);
    }

    @Test
    void accuse_wrongGuess_lastAccusationDay_setsBadEndingAndSyncsSave() {
        session.setCurrentDay(GameConstants.LAST_ACCUSATION_DAY); // 9일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        AccuseResultResponse response = accusationService.accuse(100L, 2L);

        assertThat(response.correct()).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.BAD_ENDING);
        assertThat(session.getEndedAt()).isNotNull();
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_BAD_ENDING);
    }

    @Test
    void accuse_sessionAlreadyEnded_throwsIllegalState() {
        session.setStatus(SessionStatus.SUCCESS);

        assertThatThrownBy(() -> accusationService.accuse(100L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accuse_dayBeforeAccusationWindow_throwsIllegalState() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY - 1);

        assertThatThrownBy(() -> accusationService.accuse(100L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accuse_alreadyAttemptedToday_throwsIllegalStateWithoutSideEffects() {
        // 이 제한이 없으면 같은 날 오답을 반복해서 매번 랜덤 이벤트 LLM 호출을 공짜로
        // 재실행시킬 수 있었다(비용 누수) — 하루 1회로 막혔는지, 그리고 그 경우 로그 저장/LLM
        // 호출 등 어떤 부작용도 없는지까지 확인한다.
        when(accusationLogRepository.existsBySessionAndDay(session, GameConstants.FIRST_ACCUSATION_DAY))
                .thenReturn(true);

        assertThatThrownBy(() -> accusationService.accuse(100L, 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(accusationLogRepository, never()).save(any());
        verify(randomEventLogRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
        verify(gameSaveService, never()).syncEndingState(any(), any());
    }
}
