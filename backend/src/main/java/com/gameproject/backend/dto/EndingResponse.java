package com.gameproject.backend.dto;

public record EndingResponse(
        String status,
        Long culpritNpcId,
        String culpritName,
        String endingStory
) {
}
