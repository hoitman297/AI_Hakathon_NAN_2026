package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.DialogueReplyResponse;
import com.gameproject.backend.dto.llm.DialogueChatResponse;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.NpcRepository;

/**
 * send()의 DB 읽기/쓰기는 DialogueChatPersistenceService(짧은 트랜잭션들)로 옮겨졌으므로,
 * 이 테스트는 그 오케스트레이션만 검증한다 — "LLM 호출은 항상 DialogueChatPersistenceService의
 * 트랜잭션 바깥에서 일어난다"는 이 리팩터링의 핵심 불변식이 지켜지는지가 초점.
 * DialogueChatPersistenceService 자체 로직(레이트리밋 순서, 지연 로딩 필드 접근, 목격담/정직모드
 * 계산 등)은 DialogueChatPersistenceServiceTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DialogueServiceTest {

    @Mock
    private NpcRepository npcRepository;
    @Mock
    private DialogueLogRepository dialogueLogRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private DialogueChatPersistenceService persistence;

    private DialogueService dialogueService;

    private GameSession session;
    private Npc npc;

    @BeforeEach
    void setUp() {
        dialogueService = new DialogueService(npcRepository, dialogueLogRepository, sessionService,
                llmProxyClient, persistence);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        npc = Npc.builder().npcId(1L).name("현수동").role("이장").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(npc)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
    }

    private DialogueChatContext contextWithExistingPersona(boolean restrictDetectiveTalk) {
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(92.0).staminaMax(100).gold(0).fainted(false).build();
        return new DialogueChatContext(session, npc, stat, "{}", null, List.of(),
                false, 50, restrictDetectiveTalk, null, false, null);
    }

    @Test
    void send_rateLimitExceeded_blocksBeforeAnyLlmCall() {
        when(persistence.prepareChatContext(100L, 1L))
                .thenThrow(new LlmRateLimitExceededException("rate limit"));

        assertThatThrownBy(() -> dialogueService.send(100L, 1L, "안녕하세요"))
                .isInstanceOf(LlmRateLimitExceededException.class);

        verify(llmProxyClient, never()).chat(any(), any(), any(), anyBoolean(), anyInt(), anyBoolean(), any(), anyBoolean(), any());
        verify(llmProxyClient, never()).generatePersona(any(), any(), any(), any(), any(), any(), any(), any());
        verify(persistence, never()).saveDialogueExchange(any(), any(), any(), any(), anyBoolean());
        verify(persistence, never()).applyAffinityDelta(any(), any(), anyInt());
    }

    @Test
    void send_existingPersona_skipsPersonaGenerationAndCallsChat() {
        when(persistence.prepareChatContext(100L, 1L)).thenReturn(contextWithExistingPersona(false));
        when(llmProxyClient.chat("{}", List.of(), "안녕하세요", false, 50, false, null, false, null))
                .thenReturn(new DialogueChatResponse("반갑습니다.", 3));
        when(persistence.applyAffinityDelta(session, npc, 3)).thenReturn(55);

        DialogueReplyResponse response = dialogueService.send(100L, 1L, "안녕하세요");

        verify(llmProxyClient, never()).generatePersona(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(response.npcReply()).isEqualTo("반갑습니다.");
        assertThat(response.affinityScore()).isEqualTo(55);
        assertThat(response.staminaCurrent()).isEqualTo(92);
    }

    @Test
    void send_missingPersona_generatesAndSavesPersonaBeforeChat() {
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(92.0).staminaMax(100).gold(0).fainted(false).build();
        DialogueChatContext ctx = new DialogueChatContext(session, npc, stat, null, "동기", List.of(),
                false, 50, false, null, false, null);
        when(persistence.prepareChatContext(100L, 1L)).thenReturn(ctx);
        when(llmProxyClient.generatePersona(1L, "현수동", "이장", null, null, null, null, "동기"))
                .thenReturn("{\"generated\":true}");
        when(llmProxyClient.chat("{\"generated\":true}", List.of(), "안녕하세요", false, 50, false, null, false, null))
                .thenReturn(new DialogueChatResponse("반갑습니다.", 0));
        when(persistence.applyAffinityDelta(session, npc, 0)).thenReturn(50);

        dialogueService.send(100L, 1L, "안녕하세요");

        verify(llmProxyClient).generatePersona(1L, "현수동", "이장", null, null, null, null, "동기");
        verify(persistence).savePersona(session, npc, "{\"generated\":true}");
        verify(llmProxyClient).chat("{\"generated\":true}", List.of(), "안녕하세요", false, 50, false, null, false, null);
    }

    @Test
    void send_day7OrLater_passesRestrictDetectiveTalkTrueToLlm() {
        when(persistence.prepareChatContext(100L, 1L)).thenReturn(contextWithExistingPersona(true));
        when(llmProxyClient.chat("{}", List.of(), "범인이 누구예요?", false, 50, true, null, false, null))
                .thenReturn(new DialogueChatResponse("글쎄, 그건 나도 모르겠구먼.", 0));
        when(persistence.applyAffinityDelta(any(), any(), anyInt())).thenReturn(50);

        dialogueService.send(100L, 1L, "범인이 누구예요?");

        verify(llmProxyClient).chat("{}", List.of(), "범인이 누구예요?", false, 50, true, null, false, null);
    }

    @Test
    void send_affinityDeltaNull_treatedAsZero() {
        when(persistence.prepareChatContext(100L, 1L)).thenReturn(contextWithExistingPersona(false));
        when(llmProxyClient.chat("{}", List.of(), "...", false, 50, false, null, false, null))
                .thenReturn(new DialogueChatResponse("...", null));
        when(persistence.applyAffinityDelta(session, npc, 0)).thenReturn(50);

        dialogueService.send(100L, 1L, "...");

        verify(persistence).applyAffinityDelta(session, npc, 0);
    }

    @Test
    void send_affinityUpdateOptimisticLockConflict_stillReturnsSuccessfullyWithPreExistingAffinity() {
        when(persistence.prepareChatContext(100L, 1L)).thenReturn(contextWithExistingPersona(false));
        when(llmProxyClient.chat("{}", List.of(), "안녕하세요", false, 50, false, null, false, null))
                .thenReturn(new DialogueChatResponse("반갑습니다.", 3));
        when(persistence.applyAffinityDelta(session, npc, 3))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        "affinity", (Object) null));

        DialogueReplyResponse response = dialogueService.send(100L, 1L, "안녕하세요");

        // 호감도 반영이 동시성 충돌로 실패해도 대화 저장은 이미 끝난 뒤라 요청 자체는 성공해야 하고,
        // 호감도는 대화 시작 시점 값(반영 실패)으로 대체된다.
        verify(persistence).saveDialogueExchange(session, npc, "안녕하세요", "반갑습니다.", false);
        assertThat(response.npcReply()).isEqualTo("반갑습니다.");
        assertThat(response.affinityScore()).isEqualTo(50);
    }
}
