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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.AccusationLog;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.repository.AccusationLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.RandomEventLogRepository;

@ExtendWith(MockitoExtension.class)
class AccusationPersistenceServiceTest {

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
    private GameSaveService gameSaveService;

    private AccusationPersistenceService service;

    private GameSession session;
    private Account account;
    private Npc culprit;
    private Npc other;

    @BeforeEach
    void setUp() {
        service = new AccusationPersistenceService(npcRepository, caseAssignmentRepository, accusationLogRepository,
                randomEventLogRepository, sessionRepository, sessionService, npcService, gameSaveService);

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
        // saveRandomEvent()/saveEndingStory() 테스트는 findSession()을 안 타므로 lenient로 둔다.
        org.mockito.Mockito.lenient().when(sessionService.findSession(100L)).thenReturn(session);
    }

    @Test
    void prepareAccusation_correctGuess_setsSuccessAndSyncsSave() {
        when(npcRepository.findById(1L)).thenReturn(Optional.of(culprit));

        AccusationOutcome outcome = service.prepareAccusation(100L, 1L);

        assertThat(outcome.correct()).isTrue();
        assertThat(outcome.sessionStatusName()).isEqualTo("SUCCESS");
        assertThat(outcome.needsEventDescription()).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SUCCESS);
        assertThat(session.getEndedAt()).isNotNull();
        verify(sessionRepository).save(session);
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_SUCCESS);
        verify(accusationLogRepository).save(any(AccusationLog.class));
        verify(randomEventLogRepository, never()).save(any());
    }

    @Test
    void prepareAccusation_wrongGuess_firstAccusationDay_needsVillageEventDescription() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY); // 7일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        AccusationOutcome outcome = service.prepareAccusation(100L, 2L);

        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.needsEventDescription()).isTrue();
        assertThat(outcome.eventTarget()).isEqualTo(EventTarget.VILLAGE);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(gameSaveService, never()).syncEndingState(any(), any());
        // 이벤트 설명 자체(LLM 응답 필요)는 이 단계에서 저장하지 않는다 — saveRandomEvent()가 별도로 한다.
        verify(randomEventLogRepository, never()).save(any());
    }

    @Test
    void prepareAccusation_wrongGuess_secondAccusationDay_needsPlayerEventDescription() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY + 1); // 8일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        AccusationOutcome outcome = service.prepareAccusation(100L, 2L);

        assertThat(outcome.needsEventDescription()).isTrue();
        assertThat(outcome.eventTarget()).isEqualTo(EventTarget.PLAYER);
    }

    @Test
    void prepareAccusation_wrongGuess_thirdAccusationDay_needsNoEventDescription() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY + 2); // 9일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        AccusationOutcome outcome = service.prepareAccusation(100L, 2L);

        assertThat(outcome.needsEventDescription()).isFalse();
    }

    @Test
    void prepareAccusation_wrongGuess_lastAccusationDay_setsBadEndingAndSyncsSave() {
        session.setCurrentDay(GameConstants.LAST_ACCUSATION_DAY); // 9일차
        when(npcRepository.findById(2L)).thenReturn(Optional.of(other));
        when(npcRepository.findAll()).thenReturn(List.of(culprit, other));

        AccusationOutcome outcome = service.prepareAccusation(100L, 2L);

        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.sessionStatusName()).isEqualTo("BAD_ENDING");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.BAD_ENDING);
        assertThat(session.getEndedAt()).isNotNull();
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_BAD_ENDING);
    }

    @Test
    void prepareAccusation_sessionAlreadyEnded_throwsIllegalState() {
        session.setStatus(SessionStatus.SUCCESS);

        assertThatThrownBy(() -> service.prepareAccusation(100L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prepareAccusation_dayBeforeAccusationWindow_throwsIllegalState() {
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY - 1);

        assertThatThrownBy(() -> service.prepareAccusation(100L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prepareAccusation_alreadyAttemptedToday_throwsIllegalStateWithoutSideEffects() {
        when(accusationLogRepository.existsBySessionAndDay(session, GameConstants.FIRST_ACCUSATION_DAY))
                .thenReturn(true);

        assertThatThrownBy(() -> service.prepareAccusation(100L, 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(accusationLogRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
        verify(gameSaveService, never()).syncEndingState(any(), any());
    }

    @Test
    void saveRandomEvent_savesWithGivenDescription() {
        service.saveRandomEvent(session, 7, EventTarget.VILLAGE, "마을 게시판 도발 쪽지", "게시판에 쪽지가 붙었다.");

        verify(randomEventLogRepository).save(org.mockito.ArgumentMatchers.argThat(log ->
                log.getSession() == session && log.getDay() == 7
                        && log.getTarget() == EventTarget.VILLAGE
                        && "마을 게시판 도발 쪽지".equals(log.getEventType())
                        && "게시판에 쪽지가 붙었다.".equals(log.getDescription())));
    }

    @Test
    void prepareEnding_inProgress_returnsFinishedWithoutTouchingAssignment() {
        EndingOutcome outcome = service.prepareEnding(100L);

        assertThat(outcome.finished()).isNotNull();
        assertThat(outcome.finished().status()).isEqualTo("IN_PROGRESS");
        verify(caseAssignmentRepository, never()).findBySession(any());
    }

    @Test
    void prepareEnding_badEnding_returnsFinishedStaticMessage() {
        session.setStatus(SessionStatus.BAD_ENDING);

        EndingOutcome outcome = service.prepareEnding(100L);

        assertThat(outcome.finished().status()).isEqualTo("BAD_ENDING");
    }

    @Test
    void prepareEnding_successWithCachedStory_returnsFinishedWithoutLlmNeeded() {
        session.setStatus(SessionStatus.SUCCESS);
        when(npcRepository.findById(1L)).thenReturn(Optional.of(culprit));
        com.gameproject.backend.domain.NpcCaseAssignment assignment =
                com.gameproject.backend.domain.NpcCaseAssignment.builder()
                        .session(session).npc(culprit).endingStoryText("이미 캐시된 이야기").build();
        when(caseAssignmentRepository.findBySession(session)).thenReturn(Optional.of(assignment));

        EndingOutcome outcome = service.prepareEnding(100L);

        assertThat(outcome.finished()).isNotNull();
        assertThat(outcome.finished().endingStory()).isEqualTo("이미 캐시된 이야기");
    }

    @Test
    void prepareEnding_successWithoutStory_returnsGenerationFields() {
        session.setStatus(SessionStatus.SUCCESS);
        when(npcRepository.findById(1L)).thenReturn(Optional.of(culprit));
        com.gameproject.backend.domain.NpcCaseAssignment assignment =
                com.gameproject.backend.domain.NpcCaseAssignment.builder()
                        .session(session).npc(culprit).motiveText("김치준과의 갈등")
                        .primaryType(com.gameproject.backend.domain.SabotageType.DAMAGE)
                        .targetPoolDesc("화분/진열대/간판")
                        .build();
        when(caseAssignmentRepository.findBySession(session)).thenReturn(Optional.of(assignment));

        EndingOutcome outcome = service.prepareEnding(100L);

        assertThat(outcome.finished()).isNull();
        assertThat(outcome.culpritNpcId()).isEqualTo(1L);
        assertThat(outcome.culpritName()).isEqualTo("나박수");
        assertThat(outcome.motiveText()).isEqualTo("김치준과의 갈등");
        assertThat(outcome.primaryType()).isEqualTo("DAMAGE");
        assertThat(outcome.targetPoolDesc()).isEqualTo("화분/진열대/간판");
    }

    @Test
    void saveEndingStory_persistsTextAndReturnsResponse() {
        com.gameproject.backend.domain.NpcCaseAssignment assignment =
                com.gameproject.backend.domain.NpcCaseAssignment.builder()
                        .session(session).npc(culprit).build();

        var response = service.saveEndingStory(assignment, 1L, "나박수", "그래, 내가 했다.");

        assertThat(assignment.getEndingStoryText()).isEqualTo("그래, 내가 했다.");
        verify(caseAssignmentRepository).save(assignment);
        assertThat(response.endingStory()).isEqualTo("그래, 내가 했다.");
        assertThat(response.culpritNpcId()).isEqualTo(1L);
    }
}
