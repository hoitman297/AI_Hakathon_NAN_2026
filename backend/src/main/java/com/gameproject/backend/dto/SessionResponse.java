package com.gameproject.backend.dto;

import java.time.LocalDateTime;

public record SessionResponse(
        Long sessionId,
        String playerId,
        Long accountId,
        Integer currentDay,
        String status,
        Integer staminaCurrent,
        Integer staminaMax,
        Integer gold,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
