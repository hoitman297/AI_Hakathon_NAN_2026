package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** itemType: "CROP" 또는 "FRUIT". quantity는 생략 시 서비스에서 1로 처리. */
public record SellRequest(
        @NotBlank String itemType,
        @NotNull Long itemRefId,
        @Positive Integer quantity
) {
}
