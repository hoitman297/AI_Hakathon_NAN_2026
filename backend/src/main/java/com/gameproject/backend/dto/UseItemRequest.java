package com.gameproject.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** targetNpcId는 선물세트처럼 대상 NPC가 필요한 아이템에만 사용 (그 외는 null) */
public record UseItemRequest(
        @NotNull @Min(1) @Max(7) Integer slotIndex,
        Long targetNpcId
) {
}
