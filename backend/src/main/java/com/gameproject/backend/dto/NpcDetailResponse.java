package com.gameproject.backend.dto;

public record NpcDetailResponse(
        Long npcId,
        String name,
        String role,
        Integer age,
        String personalityDesc,
        String speechStyle,
        String sampleLine,
        Integer affinityScore,
        String currentLocation
) {
}
