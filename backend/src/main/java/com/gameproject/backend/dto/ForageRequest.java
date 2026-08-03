package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotNull;

public record ForageRequest(@NotNull Long fruitId) {
}
