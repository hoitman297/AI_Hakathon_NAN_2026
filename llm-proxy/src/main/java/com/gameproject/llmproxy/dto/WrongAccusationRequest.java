package com.gameproject.llmproxy.dto;

/** backend -> llm-proxy: 오답 고발로 지목된(실제로는 무고한) NPC의 억울함 토로 연출 텍스트 생성 요청. */
public record WrongAccusationRequest(
        String name,
        String role,
        Integer age,
        String personalityDesc,
        String speechStyle,
        String sampleLine
) {
}
