package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotNull;

public record PlantRequest(@NotNull Long cropId) {
}
