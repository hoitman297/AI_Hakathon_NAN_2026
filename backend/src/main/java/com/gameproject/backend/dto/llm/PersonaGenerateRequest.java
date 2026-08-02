package com.gameproject.backend.dto.llm;

/** backend → llm-proxy: 페르소나 생성 LLM 호출 요청 */
public record PersonaGenerateRequest(
        Long npcId,
        String name,
        String role,
        Integer age,
        String personalityDesc,
        String speechStyle,
        String sampleLine,
        String motiveText // 이 NPC가 이번 판의 범인일 경우에만 채워짐 (아니면 null)
) {
}
