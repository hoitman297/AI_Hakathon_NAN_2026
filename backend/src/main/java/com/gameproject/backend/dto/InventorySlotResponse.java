package com.gameproject.backend.dto;

public record InventorySlotResponse(
        Integer slotIndex,
        String itemType,
        Long itemRefId,
        String itemName,
        Integer quantity
) {
}
