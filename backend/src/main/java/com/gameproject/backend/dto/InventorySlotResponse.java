package com.gameproject.backend.dto;

public record InventorySlotResponse(
        Integer slotIndex,
        String itemType,
        Long itemRefId,
        String itemName,
        /** SHOP_ITEM일 때만 값이 있음(CROP/FRUIT는 null) — 표시 이름 문자열 매칭 대신 이걸로 아이템 종류를 판별. */
        String itemCode,
        Integer quantity
) {
}
