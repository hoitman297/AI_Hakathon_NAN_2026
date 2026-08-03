package com.gameproject.backend.dto;

import java.time.LocalDateTime;

public record DialogueMessageResponse(
        String sender,
        String message,
        LocalDateTime createdAt
) {
}
