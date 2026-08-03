package com.gameproject.backend.dto;

public record ClueCardResponse(
        Long clueId,
        String topic,
        String text,
        Boolean clarified
) {
}
