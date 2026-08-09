package com.gameproject.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FarmPlot;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.SessionStatus;
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

    /**
     * 씨앗 구매 — 농산물 상점에서 골드를 내고 씨앗을 사서 인벤토리(SEED)에 보관한다.
     * 실제로 밭에 심는 건 {@link #plant}에서 이 씨앗을 소모하며 별도로 한다(스타듀밸리 스타일:
     * 구매↔파종을 분리해서, 파종은 맵 위 실제 밭 타일 앞으로 걸어가서 해야 한다).
     */
    @Transactional
    public void buySeed(Long sessionId, Long cropId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        CropMaster crop = cropMasterRepository.findById(cropId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작물입니다: " + cropId));

        if (staminaService.currentStat(session).getGold() < crop.getSeedPrice()) {
            throw new IllegalStateException("골드가 부족합니다.");
        }

        staminaService.addGold(session, -crop.getSeedPrice());
        inventoryService.addItem(session, InventoryItemType.SEED, crop.getCropId(), 1);
    }

    /** 파종 — 인벤토리에 보관 중인 씨앗(SEED) 1개를 소모하고 스태미나를 써서 밭에 심는다. */
    @Transactional
    public void plant(Long sessionId, Long cropId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        CropMaster crop = cropMasterRepository.findById(cropId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작물입니다: " + cropId));

        inventoryService.removeItem(session, InventoryItemType.SEED, cropId, 1);
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
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
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
