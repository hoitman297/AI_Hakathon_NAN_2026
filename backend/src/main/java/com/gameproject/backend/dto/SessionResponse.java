package com.gameproject.backend.dto;

import java.time.LocalDateTime;

public record SessionResponse(
        Long sessionId,
        String playerId,
        Long accountId,
        Integer currentDay,
        String status,
        Double staminaCurrent,
        Integer staminaMax,
        Integer gold,
        Boolean sneakersEquipped,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
