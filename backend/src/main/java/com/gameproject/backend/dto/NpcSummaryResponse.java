package com.gameproject.backend.dto;

public record NpcSummaryResponse(
        Long npcId,
        String name,
        String role,
        String currentLocation
) {
}
