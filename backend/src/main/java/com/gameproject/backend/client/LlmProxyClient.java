package com.gameproject.backend.client;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.gameproject.backend.dto.llm.ClueClarifyRequest;
import com.gameproject.backend.dto.llm.ClueClarifyResponse;
import com.gameproject.backend.dto.llm.ClueContentRequest;
import com.gameproject.backend.dto.llm.ClueContentResponse;
import com.gameproject.backend.dto.llm.DialogueChatRequest;
import com.gameproject.backend.dto.llm.DialogueChatResponse;
import com.gameproject.backend.dto.llm.DialogueTurn;
import com.gameproject.backend.dto.llm.EndingContentRequest;
import com.gameproject.backend.dto.llm.EndingContentResponse;
import com.gameproject.backend.dto.llm.EventContentRequest;
import com.gameproject.backend.dto.llm.EventContentResponse;
import com.gameproject.backend.dto.llm.PersonaGenerateRequest;
import com.gameproject.backend.dto.llm.PersonaGenerateResponse;

import lombok.RequiredArgsConstructor;

/**
 * llm-proxy 모듈(별도 Spring Boot 서버, 기본 포트 8081)의 내부 API를 호출하는 클라이언트.
 * 아키텍처 결정: 페르소나 생성 LLM과 대화용 LLM을 분리 — 이 클라이언트가 두 엔드포인트를 각각 호출한다.
 */
@Component
@RequiredArgsConstructor
public class LlmProxyClient {

    private final RestClient llmProxyRestClient;

    public String generatePersona(Long npcId, String name, String role, Integer age,
                                   String personalityDesc, String speechStyle, String sampleLine,
                                   String motiveText) {
        PersonaGenerateRequest request = new PersonaGenerateRequest(
                npcId, name, role, age, personalityDesc, speechStyle, sampleLine, motiveText);
        PersonaGenerateResponse response = llmProxyRestClient.post()
                .uri("/internal/llm/persona/generate")
                .body(request)
                .retrieve()
                .body(PersonaGenerateResponse.class);
        return response != null ? response.personaJson() : null;
    }

    public DialogueChatResponse chat(String personaJson, List<DialogueTurn> history, String userMessage, boolean honestMode) {
        DialogueChatRequest request = new DialogueChatRequest(personaJson, history, userMessage, honestMode);
        DialogueChatResponse response = llmProxyRestClient.post()
                .uri("/internal/llm/dialogue")
                .body(request)
                .retrieve()
                .body(DialogueChatResponse.class);
        return response != null ? response : new DialogueChatResponse(null, 0);
    }

    public String generateEventContent(String eventType, String target, int day, String accusedNpcName) {
        EventContentRequest request = new EventContentRequest(eventType, target, day, accusedNpcName);
        EventContentResponse response = llmProxyRestClient.post()
                .uri("/internal/llm/event")
                .body(request)
                .retrieve()
                .body(EventContentResponse.class);
        return response != null ? response.description() : null;
    }

    public String generateEndingStory(Long npcId, String name, String role, Integer age, String personalityDesc,
                                       String speechStyle, String motiveText, String primaryType, String targetPoolDesc) {
        EndingContentRequest request = new EndingContentRequest(
                npcId, name, role, age, personalityDesc, speechStyle, motiveText, primaryType, targetPoolDesc);
        EndingContentResponse response = llmProxyRestClient.post()
                .uri("/internal/llm/ending")
                .body(request)
                .retrieve()
                .body(EndingContentResponse.class);
        return response != null ? response.story() : null;
    }

    public String generateClueContent(String topic, String npcAppearanceDesc, String npcPersonalityDesc,
                                       String location, String subTarget) {
        ClueContentRequest request = new ClueContentRequest(topic, npcAppearanceDesc, npcPersonalityDesc, location, subTarget);
        ClueContentResponse response = llmProxyRestClient.post()
                .uri("/internal/llm/clue")
                .body(request)
                .retrieve()
                .body(ClueContentResponse.class);
        return response != null ? response.text() : null;
    }

    public String clarifyClueContent(String topic, String npcAppearanceDesc, String previousText) {
        ClueClarifyRequest request = new ClueClarifyRequest(topic, npcAppearanceDesc, previousText);
        ClueClarifyResponse response = llmProxyRestClient.post()
                .uri("/internal/llm/clue/clarify")
                .body(request)
                .retrieve()
                .body(ClueClarifyResponse.class);
        return response != null ? response.text() : null;
    }
}
