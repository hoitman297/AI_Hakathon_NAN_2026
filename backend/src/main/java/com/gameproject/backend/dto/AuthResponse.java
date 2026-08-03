package com.gameproject.backend.dto;

public record AuthResponse(
        Long accountId,
        String username,
        String nickname,
        String token
) {
}
