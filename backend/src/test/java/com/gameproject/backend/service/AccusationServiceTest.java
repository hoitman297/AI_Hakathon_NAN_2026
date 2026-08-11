package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.AccuseResultResponse;
import com.gameproject.backend.dto.EndingResponse;

/**
 * DB 읽기/쓰기는 AccusationPersistenceService(짧은 트랜잭션들)로 옮겨졌으므로, 이 테스트는
 * "LLM 호출은 항상 그 트랜잭션 바깥에서 일어난다"는 오케스트레이션만 검증한다.
 * AccusationPersistenceService 자체 로직(세션 상태 전이, 호감도 페널티, 엔딩 캐싱 등)은
 * AccusationPersistenceServiceTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AccusationServiceTest {

    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private AccusationPersistenceService persistence;

    private AccusationService accusationService;

    private GameSession session;
    private Npc culprit;
    private Npc other;

    @BeforeEach
    void setUp() {
        accusationService = new AccusationService(llmProxyClient, persistence, Runnable::run);

        culprit = Npc.builder().npcId(1L).name("나박수").role("수박밭 주인").age(32)
                .personalityDesc("다혈질").speechStyle("사투리").sampleLine("아이고").build();
        other = Npc.builder().npcId(2L).name("현수동").role("이장").age(70)
                .personalityDesc("고집 셈").speechStyle("반말").sampleLine("쯧").build();
        session = GameSession.builder()
                .sessionId(100L).culprit(culprit)
                .currentDay(GameConstants.FIRST_ACCUSATION_DAY)
                .status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
    }

    @Test
    void accuse_correct_returnsMessageWithoutAnyLlmCall() {
        AccusationOutcome outcome = new AccusationOutcome(true, "SUCCESS", culprit, false, null, null,
                GameConstants.FIRST_ACCUSATION_DAY, session);
        when(persistence.prepareAccusation(100L, 1L)).thenReturn(outcome);

        AccuseResultResponse response = accusationService.accuse(100L, 1L);

        assertThat(response.correct()).isTrue();
        assertThat(response.sessionStatus()).isEqualTo("SUCCESS");
        assertThat(response.message()).contains("나박수");
        verify(llmProxyClient, never()).generateEventContent(any(), any(), any(Integer.class), any());
        verify(llmProxyClient, never())
                .generateWrongAccusationReaction(any(), any(), any(), any(), any(), any());
        verify(persistence, never()).saveRandomEvent(any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void accuse_wrongWithEventDescriptionNeeded_generatesEventBeforeReactionAndSavesIt() {
        AccusationOutcome outcome = new AccusationOutcome(false, "IN_PROGRESS", other, true,
                "마을 게시판 도발 쪽지", EventTarget.VILLAGE, GameConstants.FIRST_ACCUSATION_DAY, session);
        when(persistence.prepareAccusation(100L, 2L)).thenReturn(outcome);
        when(llmProxyClient.generateEventContent("마을 게시판 도발 쪽지", "VILLAGE", GameConstants.FIRST_ACCUSATION_DAY, "현수동"))
                .thenReturn("게시판에 쪽지가 붙었다.");
        when(llmProxyClient.generateWrongAccusationReaction("현수동", "이장", 70, "고집 셈", "반말", "쯧"))
                .thenReturn("억울합니다!");

        AccuseResultResponse response = accusationService.accuse(100L, 2L);

        verify(persistence).saveRandomEvent(session, GameConstants.FIRST_ACCUSATION_DAY, EventTarget.VILLAGE,
                "마을 게시판 도발 쪽지", "게시판에 쪽지가 붙었다.");
        assertThat(response.correct()).isFalse();
        assertThat(response.message()).contains("억울합니다!");
    }

    @Test
    void accuse_wrongWithoutEventDescriptionNeeded_onlyCallsReaction() {
        AccusationOutcome outcome = new AccusationOutcome(false, "IN_PROGRESS", other, false, null, null,
                GameConstants.FIRST_ACCUSATION_DAY + 2, session);
        when(persistence.prepareAccusation(100L, 2L)).thenReturn(outcome);
        when(llmProxyClient.generateWrongAccusationReaction("현수동", "이장", 70, "고집 셈", "반말", "쯧"))
                .thenReturn("억울합니다!");

        accusationService.accuse(100L, 2L);

        verify(llmProxyClient, never()).generateEventContent(any(), any(), any(Integer.class), any());
        verify(persistence, never()).saveRandomEvent(any(), any(Integer.class), any(), any(), any());
    }

    @Test
    void accuse_wrongReactionNull_fallsBackToDefaultMessage() {
        AccusationOutcome outcome = new AccusationOutcome(false, "BAD_ENDING", other, false, null, null,
                GameConstants.LAST_ACCUSATION_DAY, session);
        when(persistence.prepareAccusation(100L, 2L)).thenReturn(outcome);
        when(llmProxyClient.generateWrongAccusationReaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        AccuseResultResponse response = accusationService.accuse(100L, 2L);

        assertThat(response.message()).contains("현수동이(가) 억울함을 토로합니다.");
        assertThat(response.sessionStatus()).isEqualTo("BAD_ENDING");
    }

    @Test
    void getEnding_alreadyFinished_returnsDirectlyWithoutLlmCall() {
        EndingResponse cached = new EndingResponse("SUCCESS", 1L, "나박수", "이미 캐시된 이야기");
        when(persistence.prepareEnding(100L)).thenReturn(EndingOutcome.finished(cached));

        EndingResponse response = accusationService.getEnding(100L);

        assertThat(response).isEqualTo(cached);
        verify(llmProxyClient, never())
                .generateEndingStory(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(persistence, never()).saveEndingStory(any(), any(), any(), any());
    }

    @Test
    void getEnding_needsGeneration_callsLlmThenSaves() {
        com.gameproject.backend.domain.NpcCaseAssignment assignment =
                com.gameproject.backend.domain.NpcCaseAssignment.builder()
                        .session(session).npc(culprit).motiveText("김치준과의 갈등")
                        .primaryType(com.gameproject.backend.domain.SabotageType.DAMAGE)
                        .targetPoolDesc("화분/진열대/간판")
                        .build();
        EndingOutcome outcome = new EndingOutcome(null, 1L, "나박수", "수박밭 주인", 32,
                "다혈질", "사투리", "김치준과의 갈등", "DAMAGE", "화분/진열대/간판", assignment);
        when(persistence.prepareEnding(100L)).thenReturn(outcome);
        when(llmProxyClient.generateEndingStory(1L, "나박수", "수박밭 주인", 32, "다혈질", "사투리",
                "김치준과의 갈등", "DAMAGE", "화분/진열대/간판"))
                .thenReturn("그래, 내가 했다.");
        EndingResponse saved = new EndingResponse("SUCCESS", 1L, "나박수", "그래, 내가 했다.");
        when(persistence.saveEndingStory(assignment, 1L, "나박수", "그래, 내가 했다.")).thenReturn(saved);

        EndingResponse response = accusationService.getEnding(100L);

        assertThat(response).isEqualTo(saved);
    }
}
