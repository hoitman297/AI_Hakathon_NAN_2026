package com.gameproject.backend.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 세이브 데이터 통짜 JSON 구조. LONGTEXT로 저장되며 Jackson으로 직렬화/역직렬화된다. */
public record SaveRequest(
        @NotNull Integer day,
        @NotBlank String phase,
        @NotNull Integer playerHp,
        Map<String, Integer> inventory,
        Map<String, Integer> affection,
        List<String> cluesCollected,
        String culpritId,
        List<SabotageScheduleEntry> sabotageSchedule,
        Integer accusationAttempts,
        Map<String, List<String>> chatHistory
) {

    public record SabotageScheduleEntry(Integer day, String location, String variant) {
    }
}
