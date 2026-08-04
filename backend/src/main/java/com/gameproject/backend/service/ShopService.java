package com.gameproject.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.ItemCategory;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.dto.SellRequest;
import com.gameproject.backend.dto.ShopItemResponse;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopItemMasterRepository shopItemMasterRepository;
    private final CropMasterRepository cropMasterRepository;
    private final FruitMasterRepository fruitMasterRepository;
    private final SessionService sessionService;
    private final StaminaService staminaService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public List<ShopItemResponse> listItems() {
        return shopItemMasterRepository.findAll().stream()
                .map(item -> new ShopItemResponse(
                        item.getItemId(), item.getName(), item.getCategory().name(),
                        item.getPrice(), item.getEffectDesc(), item.getUsageLimit()))
                .toList();
    }

    @Transactional
    public void purchase(Long sessionId, Long itemId) {
        GameSession session = sessionService.findSession(sessionId);
        ShopItemMaster item = shopItemMasterRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이템입니다: " + itemId));

        // "1회 구매, 영구" 아이템(운동화)은 이미 장착했으면 다시 살 수 없다.
        if (item.getCategory() == ItemCategory.PERMANENT_EQUIPMENT && Boolean.TRUE.equals(session.getSneakersEquipped())) {
            throw new IllegalStateException("이미 영구 장착한 아이템입니다: " + item.getName());
        }

        PlayerStat stat = staminaService.currentStat(session);
        if (stat.getGold() < item.getPrice()) {
            throw new IllegalStateException("골드가 부족합니다.");
        }

        staminaService.addGold(session, -item.getPrice());
        inventoryService.addItem(session, InventoryItemType.SHOP_ITEM, item.getItemId(), 1);
    }

    @Transactional
    public void sell(Long sessionId, SellRequest request) {
        GameSession session = sessionService.findSession(sessionId);
        int quantity = request.quantity() == null ? 1 : request.quantity();

        int unitPrice;
        InventoryItemType type;
        if ("CROP".equalsIgnoreCase(request.itemType())) {
            type = InventoryItemType.CROP;
            CropMaster crop = cropMasterRepository.findById(request.itemRefId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 작물입니다."));
            unitPrice = crop.getSellPrice();
        } else if ("FRUIT".equalsIgnoreCase(request.itemType())) {
            type = InventoryItemType.FRUIT;
            FruitMaster fruit = fruitMasterRepository.findById(request.itemRefId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 과일입니다."));
            unitPrice = fruit.getSellPrice();
        } else {
            throw new IllegalArgumentException("판매 가능한 itemType은 CROP 또는 FRUIT입니다.");
        }

        inventoryService.removeItem(session, type, request.itemRefId(), quantity);
        staminaService.addGold(session, unitPrice * quantity);
    }
}
