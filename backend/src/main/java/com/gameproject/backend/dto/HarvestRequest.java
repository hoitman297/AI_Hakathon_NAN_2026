package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotNull;

public record HarvestRequest(@NotNull Long farmPlotId) {
}
