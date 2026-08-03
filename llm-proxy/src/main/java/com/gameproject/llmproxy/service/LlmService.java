package com.gameproject.llmproxy.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameproject.llmproxy.dto.DialogueChatRequest;
import com.gameproject.llmproxy.dto.DialogueTurn;
import com.gameproject.llmproxy.dto.GeneratedPersona;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실제 LLM(Claude) 연동. 페르소나 생성 LLM과 대화용 LLM 호출을 각각 담당한다.
 * 페르소나 생성은 구조화 출력(JSON schema)으로 받아 대화 LLM이 그대로 재료로 쓸 수 있게 하고,
 * 대화 응답은 실시간성이 중요해 thinking을 꺼서 지연을 줄인다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.model}")
    private String model;

    public String generatePersona(Long npcId, String name, String role, Integer age,
                                   String personalityDesc, String speechStyle, String sampleLine,
                                   String motiveText) {
        boolean isCulprit = motiveText != null;
        String systemPrompt = """
                당신은 마을 사보타주 추리 게임의 NPC 페르소나를 설계하는 작가입니다.
                주어진 NPC 기본 정보를 바탕으로, 대화용 LLM이 이 캐릭터를 실시간으로 연기할 때
                참고할 수 있는 구체적인 배경 서사와 예시 대사를 만들어주세요.
                범인으로 배정된 NPC라면(동기가 주어짐) 그 동기를 배경 서사와 자연스럽게 엮어
                구체화하되, 너무 노골적으로 죄를 암시하지는 마세요.
                """;
        String userPrompt = """
                NPC ID: %d
                이름: %s
                역할: %s
                나이: %s
                성격 요약: %s
                말투 특징: %s
                예시 대사: %s
                범인 여부: %s
                동기(초안): %s
                """.formatted(npcId, name, role, age, personalityDesc, speechStyle, sampleLine,
                isCulprit, motiveText == null ? "(범인 아님)" : motiveText);

        try {
            StructuredMessageCreateParams<GeneratedPersona> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2000L)
                    .system(systemPrompt)
                    .outputConfig(GeneratedPersona.class)
                    .addUserMessage(userPrompt)
                    .build();

            GeneratedPersona persona = anthropicClient.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(typed -> typed.text())
                    .orElseThrow(() -> new IllegalStateException("LLM 응답에 persona 텍스트 블록이 없습니다"));

            return objectMapper.writeValueAsString(persona);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("생성된 페르소나 직렬화 실패", e);
        } catch (RuntimeException e) {
            log.error("페르소나 생성 LLM 호출 실패 (npcId={}), 대체 페르소나로 진행", npcId, e);
            return fallbackPersonaJson(npcId, name, role, age, personalityDesc, speechStyle, sampleLine,
                    motiveText, isCulprit);
        }
    }

    public String chat(DialogueChatRequest request) {
        GeneratedPersona persona = parsePersona(request.personaJson());

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("당신은 '%s'(%s, %s세) 역할을 연기합니다.\n".formatted(
                persona.name(), persona.role(), persona.age()));
        systemPrompt.append("성격: %s\n".formatted(persona.personality()));
        systemPrompt.append("말투: %s\n".formatted(persona.speechStyle()));
        systemPrompt.append("배경: %s\n".formatted(persona.backstory()));
        systemPrompt.append("이 말투와 배경에 맞춰 1~3문장으로 짧게 대답하세요. 캐릭터에서 벗어나지 마세요.\n");
        if (request.honestMode()) {
            systemPrompt.append("지금은 '정직 모드'입니다: 알리바이 관련 질문에 평소보다 더 구체적으로 답하되, ")
                    .append("본인이 범인인지 여부는 절대 직접 밝히지 마세요.\n");
        }

        List<MessageParam> messages = new ArrayList<>();
        for (DialogueTurn turn : request.history()) {
            MessageParam.Role role = "NPC".equalsIgnoreCase(turn.sender())
                    ? MessageParam.Role.ASSISTANT
                    : MessageParam.Role.USER;
            messages.add(MessageParam.builder().role(role).content(turn.message()).build());
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(request.userMessage()).build());

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(1024L)
                    .system(systemPrompt.toString())
                    .thinking(ThinkingConfigDisabled.builder().build())
                    .messages(messages)
                    .build();

            Message response = anthropicClient.messages().create(params);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(text -> text.text())
                    .orElse("...");
        } catch (RuntimeException e) {
            log.error("대화 LLM 호출 실패", e);
            return "(연결이 불안정한지 대답을 잇지 못했습니다. 잠시 후 다시 말을 걸어주세요.)";
        }
    }

    private GeneratedPersona parsePersona(String personaJson) {
        try {
            return objectMapper.readValue(personaJson, GeneratedPersona.class);
        } catch (JsonProcessingException e) {
            log.warn("personaJson 파싱 실패, 원본 문자열을 배경으로 대체", e);
            return new GeneratedPersona(null, "알 수 없는 NPC", "주민", null,
                    "알 수 없음", "평범한 존댓말", personaJson, null, false, List.of());
        }
    }

    private String fallbackPersonaJson(Long npcId, String name, String role, Integer age,
                                        String personalityDesc, String speechStyle, String sampleLine,
                                        String motiveText, boolean isCulprit) {
        GeneratedPersona fallback = new GeneratedPersona(
                npcId, name, role, age, personalityDesc, speechStyle,
                sampleLine, motiveText, isCulprit, List.of(sampleLine));
        try {
            return objectMapper.writeValueAsString(fallback);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("대체 페르소나 직렬화마저 실패", e);
        }
    }
}
