package com.gameproject.backend.dto;

public record ForageResponse(
        Long fruitId,
        String fruitName,
        Integer staminaCurrent
) {
}
