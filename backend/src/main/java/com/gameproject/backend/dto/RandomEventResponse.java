package com.gameproject.backend.dto;

/**
 * 아직 확인하지 않은(viewed=false) 7~8/8~9일차 랜덤 이벤트 조회용 — UnacquiredClueResponse와
 * 같은 이유로 신설: 프론트가 어떤 이벤트가 발생했는지 알 방법이 없었다. 범인 정보는 전혀
 * 포함하지 않는다(target/eventType/description만 노출).
 */
public record RandomEventResponse(
        Long eventId,
        Integer day,
        String target,
        String eventType,
        String description,
        Boolean viewed
) {
}
