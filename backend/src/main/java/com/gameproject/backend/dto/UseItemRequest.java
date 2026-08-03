package com.gameproject.backend.dto;

/** targetNpcId는 선물세트처럼 대상 NPC가 필요한 아이템에만 사용 (그 외는 null) */
public record UseItemRequest(Integer slotIndex, Long targetNpcId) {
}
