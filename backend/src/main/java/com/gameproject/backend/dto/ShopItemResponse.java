package com.gameproject.backend.dto;

public record ShopItemResponse(
        Long itemId,
        String name,
        String category,
        Integer price,
        String effectDesc,
        String usageLimit
) {
}
