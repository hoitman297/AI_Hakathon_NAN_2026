package com.gameproject.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItem;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.dto.InventorySlotResponse;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final CropMasterRepository cropMasterRepository;
    private final FruitMasterRepository fruitMasterRepository;
    private final ShopItemMasterRepository shopItemMasterRepository;
    private final NpcRepository npcRepository;
    private final GameSessionRepository sessionRepository;
    private final StaminaService staminaService;
    private final NpcService npcService;

    private final Random random = new Random();

    @Transactional(readOnly = true)
    public List<InventorySlotResponse> list(GameSession session) {
        return inventoryItemRepository.findBySession(session).stream()
                .map(item -> new InventorySlotResponse(
                        item.getSlotIndex(),
                        item.getItemType().name(),
                        item.getItemRefId(),
                        resolveName(item.getItemType(), item.getItemRefId()),
                        item.getQuantity()))
                .toList();
    }

    /** 같은 아이템이 이미 있으면 수량만 증가, 없으면 빈 슬롯(1~7)에 새로 넣음. 슬롯이 꽉 차면 예외. */
    @Transactional
    public void addItem(GameSession session, InventoryItemType type, Long refId, int quantity) {
        Optional<InventoryItem> existing = inventoryItemRepository.findBySessionAndItemTypeAndItemRefId(session, type, refId);
        if (existing.isPresent()) {
            InventoryItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
            inventoryItemRepository.save(item);
            return;
        }

        List<InventoryItem> current = inventoryItemRepository.findBySession(session);
        int usedSlots = current.size();
        if (usedSlots >= GameConstants.INVENTORY_SLOT_COUNT) {
            throw new IllegalStateException("인벤토리가 가득 찼습니다 (7칸).");
        }
        java.util.Set<Integer> usedIndexes = current.stream()
                .map(InventoryItem::getSlotIndex)
                .collect(java.util.stream.Collectors.toSet());
        int nextSlot = 1;
        while (usedIndexes.contains(nextSlot)) {
            nextSlot++;
        }

        inventoryItemRepository.save(InventoryItem.builder()
                .session(session)
                .slotIndex(nextSlot)
                .itemType(type)
                .itemRefId(refId)
                .quantity(quantity)
                .build());
    }

    @Transactional
    public void removeItem(GameSession session, InventoryItemType type, Long refId, int quantity) {
        InventoryItem item = inventoryItemRepository.findBySessionAndItemTypeAndItemRefId(session, type, refId)
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 아이템입니다."));
        if (item.getQuantity() < quantity) {
            throw new IllegalArgumentException("보유 수량이 부족합니다.");
        }
        if (item.getQuantity() == quantity) {
            inventoryItemRepository.delete(item);
        } else {
            item.setQuantity(item.getQuantity() - quantity);
            inventoryItemRepository.save(item);
        }
    }

    /**
     * 슬롯의 아이템을 사용한다.
     * - CROP/FRUIT: 1개 소모하고 섭취회복만큼 체력 회복
     * - SHOP_ITEM(운동화/거짓말탐지기/돋보기/선물세트): 아이템별 효과 적용
     */
    @Transactional
    public String useItem(GameSession session, Integer slotIndex, Long targetNpcId) {
        InventoryItem item = inventoryItemRepository.findBySessionAndSlotIndex(session, slotIndex)
                .orElseThrow(() -> new IllegalArgumentException("해당 슬롯에 아이템이 없습니다: " + slotIndex));

        switch (item.getItemType()) {
            case CROP -> {
                CropMaster crop = cropMasterRepository.findById(item.getItemRefId())
                        .orElseThrow(() -> new IllegalStateException("작물 마스터 데이터를 찾을 수 없습니다."));
                consumeOne(session, item);
                restoreStamina(session, crop.getRestoreHp());
                return crop.getName() + " 섭취, 체력 " + crop.getRestoreHp() + " 회복";
            }
            case FRUIT -> {
                FruitMaster fruit = fruitMasterRepository.findById(item.getItemRefId())
                        .orElseThrow(() -> new IllegalStateException("과일 마스터 데이터를 찾을 수 없습니다."));
                consumeOne(session, item);
                restoreStamina(session, fruit.getRestoreHp());
                return fruit.getName() + " 섭취, 체력 " + fruit.getRestoreHp() + " 회복";
            }
            case SHOP_ITEM -> {
                ShopItemMaster shopItem = shopItemMasterRepository.findById(item.getItemRefId())
                        .orElseThrow(() -> new IllegalStateException("상점 아이템 마스터 데이터를 찾을 수 없습니다."));
                return applyShopItemEffect(session, item, shopItem, targetNpcId);
            }
            default -> throw new IllegalStateException("알 수 없는 아이템 타입입니다.");
        }
    }

    private String applyShopItemEffect(GameSession session, InventoryItem item, ShopItemMaster shopItem, Long targetNpcId) {
        String result = switch (shopItem.getName()) {
            case "운동화" -> {
                session.setSneakersEquipped(true);
                sessionRepository.save(session);
                consumeOne(session, item); // 1회 구매/영구 장착 후 인벤토리에서는 사라짐
                yield "운동화를 장착했습니다. 이동 체력 소모가 줄어듭니다.";
            }
            case "거짓말탐지기" -> {
                if (targetNpcId == null) {
                    throw new IllegalArgumentException("거짓말탐지기는 대상 NPC(targetNpcId)가 필요합니다.");
                }
                if (session.getLastLieDetectorUseDay() != null
                        && session.getLastLieDetectorUseDay().equals(session.getCurrentDay())) {
                    throw new IllegalStateException("거짓말탐지기는 하루에 한 번만 사용할 수 있습니다.");
                }
                session.setHonestModeNpcId(targetNpcId);
                session.setHonestModeDay(session.getCurrentDay());
                session.setLastLieDetectorUseDay(session.getCurrentDay());
                sessionRepository.save(session);
                consumeOne(session, item);
                yield "다음 대화 1턴 동안 정직 모드가 적용됩니다.";
            }
            case "선물세트" -> {
                if (targetNpcId == null) {
                    throw new IllegalArgumentException("선물세트는 대상 NPC(targetNpcId)가 필요합니다.");
                }
                Npc target = npcRepository.findById(targetNpcId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + targetNpcId));
                int gain = GameConstants.AFFINITY_GIFT_MIN
                        + random.nextInt(GameConstants.AFFINITY_GIFT_MAX - GameConstants.AFFINITY_GIFT_MIN + 1);
                int newScore = npcService.adjustAffinity(session, target, gain);
                consumeOne(session, item);
                yield target.getName() + "의 호감도가 " + gain + " 상승했습니다 (현재 " + newScore + ")";
            }
            case "돋보기" ->
                    // 돋보기는 어떤 단서를 명확화할지 지정해야 하므로 실제 소모/적용은
                    // POST /api/sessions/{id}/clues/{clueId}/clarify 에서 처리한다.
                    "돋보기는 단서 카드 화면에서 '명확화' 기능으로 사용하세요.";
            default -> throw new IllegalStateException("정의되지 않은 상점 아이템입니다: " + shopItem.getName());
        };
        return result;
    }

    private void consumeOne(GameSession session, InventoryItem item) {
        if (item.getQuantity() <= 1) {
            inventoryItemRepository.delete(item);
        } else {
            item.setQuantity(item.getQuantity() - 1);
            inventoryItemRepository.save(item);
        }
    }

    private void restoreStamina(GameSession session, int amount) {
        staminaService.restore(session, amount);
    }

    private String resolveName(InventoryItemType type, Long refId) {
        return switch (type) {
            case CROP -> cropMasterRepository.findById(refId).map(CropMaster::getName).orElse("알 수 없는 작물");
            case FRUIT -> fruitMasterRepository.findById(refId).map(FruitMaster::getName).orElse("알 수 없는 과일");
            case SHOP_ITEM -> shopItemMasterRepository.findById(refId).map(ShopItemMaster::getName).orElse("알 수 없는 아이템");
        };
    }
}
