package com.gameproject.backend.dto;

/** 파종 가능한 작물 목록 조회용 (심을 때 cropId를 알아야 하는 프론트를 위해 신설). */
public record CropSummaryResponse(
        Long cropId,
        String name,
        Integer seedPrice,
        Integer growDays,
        Integer plantOrHarvestStamina,
        Integer sellPrice,
        Integer restoreHp
) {
}
