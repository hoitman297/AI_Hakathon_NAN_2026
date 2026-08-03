package com.gameproject.backend.dto;

import java.time.LocalDateTime;

public record SaveResponse(
        SaveRequest saveData,
        String endingState,
        LocalDateTime updatedAt
) {
}
