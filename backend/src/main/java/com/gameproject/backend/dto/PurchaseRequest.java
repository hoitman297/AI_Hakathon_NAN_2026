package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotNull;

public record PurchaseRequest(@NotNull Long itemId) {
}
