package com.gameproject.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 프론트가 실제로 캐릭터를 움직인 경과 시간(초)을 누적해서 보고한다. */
public record MoveRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("30.0") Double seconds
) {
}
