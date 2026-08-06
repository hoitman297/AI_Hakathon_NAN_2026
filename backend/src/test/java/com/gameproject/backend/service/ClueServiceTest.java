package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.dto.ClueCardResponse;
import com.gameproject.backend.repository.ClueCardRepository;

/**
 * clarify()의 DB 읽기/쓰기는 CluePersistenceService(짧은 트랜잭션)로 옮겨졌으므로, 이 테스트는
 * "LLM 호출은 항상 그 트랜잭션 바깥에서 일어난다"는 오케스트레이션만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ClueServiceTest {

    @Mock
    private ClueCardRepository clueCardRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private CluePersistenceService persistence;

    private ClueService clueService;

    @BeforeEach
    void setUp() {
        clueService = new ClueService(clueCardRepository, sessionService, llmProxyClient, persistence);
    }

    @Test
    void clarify_callsLlmWithPreparedContextThenSavesResult() {
        ClueCard clue = ClueCard.builder().clueId(10L).topic(ClueTopic.HAIR)
                .textAmbiguous("검고 긴 머리카락 한 올이 발견됐다.").acquired(true).build();
        ClarifyContext ctx = new ClarifyContext(clue, "HAIR", "검고 긴 머리카락", "검고 긴 머리카락 한 올이 발견됐다.");
        when(persistence.prepareClarify(100L, 10L)).thenReturn(ctx);
        when(llmProxyClient.clarifyClueContent("HAIR", "검고 긴 머리카락", "검고 긴 머리카락 한 올이 발견됐다."))
                .thenReturn("앞머리가 한쪽 눈을 가릴 만큼 길다.");
        ClueCardResponse saved = new ClueCardResponse(10L, "HAIR", "앞머리가 한쪽 눈을 가릴 만큼 길다.", true);
        when(persistence.saveClarifiedText(clue, "앞머리가 한쪽 눈을 가릴 만큼 길다.")).thenReturn(saved);

        ClueCardResponse response = clueService.clarify(100L, 10L);

        assertThat(response).isEqualTo(saved);
        verify(persistence).saveClarifiedText(clue, "앞머리가 한쪽 눈을 가릴 만큼 길다.");
    }
}
