package com.gameproject.backend.dto;

/** 내 밭 상태 조회용 (수확할 때 farmPlotId를 알아야 하는 프론트를 위해 신설). */
public record FarmPlotResponse(
        Long farmPlotId,
        String cropName,
        Integer plantedDay,
        Integer readyDay,
        Boolean harvested,
        Boolean readyToHarvest
) {
}
