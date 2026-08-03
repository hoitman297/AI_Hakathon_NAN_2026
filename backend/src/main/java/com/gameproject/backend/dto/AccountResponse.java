package com.gameproject.backend.dto;

public record AccountResponse(
        Long accountId,
        String username,
        String nickname
) {
}
