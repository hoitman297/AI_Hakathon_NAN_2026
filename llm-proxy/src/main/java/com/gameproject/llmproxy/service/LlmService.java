package com.gameproject.llmproxy.service;

import org.springframework.stereotype.Service;

import com.gameproject.llmproxy.dto.DialogueChatRequest;

/**
 * 실제 LLM API 연동 전 단계의 목(mock) 구현.
 *
 * TODO: 실제 LLM 제공자/모델이 정해지면 이 클래스를 실제 API 호출로 교체할 것.
 * 지금은 backend↔llm-proxy 간 API 계약(페르소나 생성 LLM / 대화용 LLM 분리)이
 * 정상 동작하는지 end-to-end로 검증할 수 있도록 그럴듯한 텍스트를 돌려준다.
 */
@Service
public class LlmService {

    public String generatePersona(Long npcId, String name, String role, Integer age,
                                   String personalityDesc, String speechStyle, String sampleLine,
                                   String motiveText) {
        // TODO: 실제로는 여기서 페르소나 생성 LLM에 프롬프트를 보내고, 상세한 배경/말투/
        // 알리바이 등을 담은 JSON을 받아와야 한다.
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"npcId\":").append(npcId).append(',');
        sb.append("\"name\":\"").append(name).append("\",");
        sb.append("\"role\":\"").append(role).append("\",");
        sb.append("\"age\":").append(age).append(',');
        sb.append("\"personality\":\"").append(escape(personalityDesc)).append("\",");
        sb.append("\"speechStyle\":\"").append(escape(speechStyle)).append("\",");
        sb.append("\"sampleLine\":\"").append(escape(sampleLine)).append("\",");
        sb.append("\"isCulprit\":").append(motiveText != null).append(',');
        sb.append("\"motive\":\"").append(escape(motiveText)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public String chat(DialogueChatRequest request) {
        // TODO: 실제로는 personaJson + history + userMessage를 대화용 LLM에 보내서
        // 캐릭터에 맞는 응답을 받아와야 한다. honestMode면 "정직 모드" 시스템 프롬프트를 추가.
        String prefix = request.honestMode() ? "(정직 모드) " : "";
        return prefix + "[mock 응답] \"" + request.userMessage() + "\"에 대한 대답입니다. "
                + "(실제 LLM 연동 전 임시 응답 — llm-proxy/LlmService 교체 필요)";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"").replace("\n", " ");
    }
}
