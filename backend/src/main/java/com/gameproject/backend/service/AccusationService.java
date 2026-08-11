package com.gameproject.backend.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.dto.AccuseResultResponse;
import com.gameproject.backend.dto.EndingResponse;

import lombok.RequiredArgsConstructor;

/**
 * 고발/엔딩 흐름 담당. DB 읽기/쓰기는 {@link AccusationPersistenceService}의 짧은
 * 트랜잭션들로 처리하고, 이 클래스는 그 사이에서 LLM(llm-proxy) 호출만 담당한다 —
 * LLM 호출이 느려질 때 DB 커넥션을 오래 붙들지 않기 위함. 자세한 이유는
 * {@link AccusationPersistenceService}의 클래스 주석 참고.
 */
@Service
@RequiredArgsConstructor
public class AccusationService {

    private final LlmProxyClient llmProxyClient;
    private final AccusationPersistenceService persistence;
    private final Executor llmParallelExecutor;

    public AccuseResultResponse accuse(Long sessionId, Long accusedNpcId) {
        AccusationOutcome outcome = persistence.prepareAccusation(sessionId, accusedNpcId);
        Npc accused = outcome.accused();

        if (outcome.correct()) {
            String message = "정답입니다! " + accused.getName() + "의 이야기가 공개됩니다.";
            return new AccuseResultResponse(true, message, outcome.sessionStatusName());
        }

        // 이벤트 연출(조건부)과 오답 반응은 서로 독립적인 LLM 호출이라 순차 대기 대신 동시에
        // 보낸다. 이벤트 연출 텍스트 생성 LLM에는 범인 정보를 전혀 넘기지 않으므로 응답이
        // 범인을 암시할 수 없다.
        CompletableFuture<String> descriptionFuture = outcome.needsEventDescription()
                ? CompletableFuture.supplyAsync(() -> llmProxyClient.generateEventContent(
                        outcome.eventType(), outcome.eventTarget().name(), outcome.day(), accused.getName()),
                        llmParallelExecutor)
                : null;
        CompletableFuture<String> reactionFuture = CompletableFuture.supplyAsync(() -> llmProxyClient.generateWrongAccusationReaction(
                accused.getName(), accused.getRole(), accused.getAge(),
                accused.getPersonalityDesc(), accused.getSpeechStyle(), accused.getSampleLine()),
                llmParallelExecutor);

        if (descriptionFuture != null) {
            persistence.saveRandomEvent(outcome.session(), outcome.day(), outcome.eventTarget(), outcome.eventType(), descriptionFuture.join());
        }

        String reaction = reactionFuture.join();
        String message = "오답입니다. " + (reaction != null ? reaction : accused.getName() + "이(가) 억울함을 토로합니다.");

        return new AccuseResultResponse(false, message, outcome.sessionStatusName());
    }

    public EndingResponse getEnding(Long sessionId) {
        EndingOutcome outcome = persistence.prepareEnding(sessionId);
        if (outcome.finished() != null) {
            return outcome.finished();
        }

        String story = llmProxyClient.generateEndingStory(
                outcome.culpritNpcId(), outcome.culpritName(), outcome.culpritRole(), outcome.culpritAge(),
                outcome.culpritPersonalityDesc(), outcome.culpritSpeechStyle(),
                outcome.motiveText(), outcome.primaryType(), outcome.targetPoolDesc());

        return persistence.saveEndingStory(outcome.assignment(), outcome.culpritNpcId(), outcome.culpritName(), story);
    }
}
