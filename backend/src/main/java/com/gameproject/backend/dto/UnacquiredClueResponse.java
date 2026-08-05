package com.gameproject.backend.dto;

/**
 * 아직 습득하지 않은 단서 카드의 발생 장소 조회용 — 지도 위 어디로 가야 하는지
 * 프론트가 알 방법이 없어서 신설. 범인/사건 세부 내용은 노출하지 않는다(위치/일차/주제만).
 */
public record UnacquiredClueResponse(
        Long clueId,
        String location,
        Integer day,
        String topic
) {
}
