package com.gameproject.llmproxy.dto;

/** backend -> llm-proxy: 정답 고발 성공 시 공개되는 범인 개별 엔딩 스토리 생성 요청. */
public record EndingContentRequest(
        Long npcId,
        String name,
        String role,
        Integer age,
        String personalityDesc,
        String speechStyle,
        String motiveText,
        String primaryType,
        String targetPoolDesc
) {
}
