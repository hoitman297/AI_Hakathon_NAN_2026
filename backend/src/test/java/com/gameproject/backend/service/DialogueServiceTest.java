package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
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

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcPersonaState;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.DialogueReplyResponse;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcPersonaStateRepository;
import com.gameproject.backend.repository.NpcRepository;

/** LlmRateLimiter 연동에 초점을 맞춘 테스트 — DialogueService 전체 커버리지는 아님. */
@ExtendWith(MockitoExtension.class)
class DialogueServiceTest {

    @Mock
    private NpcRepository npcRepository;
    @Mock
    private NpcPersonaStateRepository personaStateRepository;
    @Mock
    private NpcCaseAssignmentRepository caseAssignmentRepository;
    @Mock
    private DialogueLogRepository dialogueLogRepository;
    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private StaminaService staminaService;
    @Mock
    private NpcService npcService;
    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private LlmRateLimiter llmRateLimiter;

    private DialogueService dialogueService;

    private GameSession session;
    private Account account;
    private Npc npc;

    @BeforeEach
    void setUp() {
        dialogueService = new DialogueService(npcRepository, personaStateRepository, caseAssignmentRepository,
                dialogueLogRepository, sessionRepository, sessionService, staminaService, npcService,
                llmProxyClient, llmRateLimiter);

        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        npc = Npc.builder().npcId(1L).name("현수동").role("이장").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(npc)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();

        when(sessionService.findSession(100L)).thenReturn(session);
        when(npcRepository.findById(1L)).thenReturn(Optional.of(npc));
    }

    @Test
    void send_rateLimitExceeded_blocksBeforeAnyStateChange() {
        doThrow(new LlmRateLimitExceededException("rate limit"))
                .when(llmRateLimiter).checkAllowed(1L);

        assertThatThrownBy(() -> dialogueService.send(100L, 1L, "안녕하세요"))
                .isInstanceOf(LlmRateLimitExceededException.class);

        verify(staminaService, never()).consume(any(), anyInt());
        verify(llmProxyClient, never()).chat(any(), any(), any(), anyBoolean());
        verify(dialogueLogRepository, never()).save(any());
    }

    @Test
    void send_success_checksRateLimiterForSessionAccountBeforeCallingLlm() {
        NpcPersonaState existingPersona = NpcPersonaState.builder()
                .session(session).npc(npc).generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build();
        when(personaStateRepository.findBySessionAndNpc(session, npc)).thenReturn(Optional.of(existingPersona));
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(92).staminaMax(100).gold(0).fainted(false).build();
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat);
        when(llmProxyClient.chat("{}", List.of(), "안녕하세요", false)).thenReturn("반갑습니다.");
        when(npcService.adjustAffinity(any(), any(), anyInt())).thenReturn(55);

        DialogueReplyResponse response = dialogueService.send(100L, 1L, "안녕하세요");

        verify(llmRateLimiter).checkAllowed(1L);
        assertThat(response.npcReply()).isEqualTo("반갑습니다.");
        assertThat(response.staminaCurrent()).isEqualTo(92);
    }
}
