package com.gameproject.llmproxy.dto;

public record PersonaGenerateRequest(
        Long npcId,
        String name,
        String role,
        Integer age,
        String personalityDesc,
        String speechStyle,
        String sampleLine,
        String motiveText
) {
}
