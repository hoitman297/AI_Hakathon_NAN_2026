package com.gameproject.backend.dto.llm;

/** backend -> llm-proxy: 선물세트를 받은 NPC의 반응 대사 생성 요청. */
public record GiftReactionRequest(
        String name,
        String role,
        Integer age,
        String personalityDesc,
        String speechStyle,
        String sampleLine
) {
}
