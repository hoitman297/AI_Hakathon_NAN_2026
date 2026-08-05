package com.gameproject.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FarmPlot;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.dto.CropSummaryResponse;
import com.gameproject.backend.dto.FarmPlotResponse;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FarmPlotRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmPlotRepository farmPlotRepository;
    private final CropMasterRepository cropMasterRepository;
    private final SessionService sessionService;
    private final StaminaService staminaService;
    private final InventoryService inventoryService;

    /** 파종 가능한 작물 목록 — 씨앗 가격/성장일수 등 심기 전에 프론트가 보여줘야 하는 정보. */
    @Transactional(readOnly = true)
    public List<CropSummaryResponse> listCrops() {
        return cropMasterRepository.findAll().stream()
                .map(crop -> new CropSummaryResponse(
                        crop.getCropId(), crop.getName(), crop.getSeedPrice(), crop.getGrowDays(),
                        crop.getPlantOrHarvestStamina(), crop.getSellPrice(), crop.getRestoreHp()))
                .toList();
    }

    /** 이 세션의 밭 상태 — 수확하려면 프론트가 어떤 farmPlotId가 다 자랐는지 알아야 한다. */
    @Transactional(readOnly = true)
    public List<FarmPlotResponse> listPlots(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return farmPlotRepository.findBySession(session).stream()
                .map(plot -> new FarmPlotResponse(
                        plot.getId(), plot.getCrop().getName(), plot.getPlantedDay(), plot.getReadyDay(),
                        plot.getHarvested(),
                        !Boolean.TRUE.equals(plot.getHarvested()) && plot.getReadyDay() <= session.getCurrentDay()))
                .toList();
    }

    @Transactional
    public void plant(Long sessionId, Long cropId) {
        GameSession session = sessionService.findSession(sessionId);
        CropMaster crop = cropMasterRepository.findById(cropId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작물입니다: " + cropId));

        staminaService.consume(session, crop.getPlantOrHarvestStamina());

        farmPlotRepository.save(FarmPlot.builder()
                .session(session)
                .crop(crop)
                .plantedDay(session.getCurrentDay())
                .readyDay(session.getCurrentDay() + crop.getGrowDays())
                .harvested(false)
                .build());
    }

    @Transactional
    public void harvest(Long sessionId, Long farmPlotId) {
        GameSession session = sessionService.findSession(sessionId);
        FarmPlot plot = farmPlotRepository.findById(farmPlotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 밭입니다: " + farmPlotId));

        if (!plot.getSession().getSessionId().equals(session.getSessionId())) {
            throw new IllegalArgumentException("이 세션의 밭이 아닙니다.");
        }
        if (Boolean.TRUE.equals(plot.getHarvested())) {
            throw new IllegalStateException("이미 수확한 밭입니다.");
        }
        if (plot.getReadyDay() > session.getCurrentDay()) {
            throw new IllegalStateException("아직 다 자라지 않았습니다 (수확 가능일: " + plot.getReadyDay() + "일차).");
        }

        CropMaster crop = plot.getCrop();
        staminaService.consume(session, crop.getPlantOrHarvestStamina());

        plot.setHarvested(true);
        farmPlotRepository.save(plot);

        inventoryService.addItem(session, InventoryItemType.CROP, crop.getCropId(), crop.getYieldQty());
    }
}
