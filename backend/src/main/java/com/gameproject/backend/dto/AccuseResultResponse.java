package com.gameproject.backend.dto;

public record AccuseResultResponse(
        Boolean correct,
        String message,
        String sessionStatus
) {
}
