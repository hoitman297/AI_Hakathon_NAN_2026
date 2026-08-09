package com.gameproject.backend.dto;

/** 밤 사보타주 다음날 아침 화면(NightTransitionScreen)에 노출되는 장소/연출 문구. */
public record NightSummaryResponse(String location, String summaryText, String sabotageType) {
}
