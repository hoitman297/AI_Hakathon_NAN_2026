package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotNull;

public record AccuseRequest(@NotNull Long accusedNpcId) {
}
