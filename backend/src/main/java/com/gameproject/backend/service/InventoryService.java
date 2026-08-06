package com.gameproject.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItem;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.dto.InventorySlotResponse;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * useItem()의 선물세트(GIFT_SET) 분기만 LLM(NPC 반응 생성)을 호출한다. 그 DB 읽기/쓰기는
 * {@link InventoryPersistenceService}의 트랜잭션으로 처리하고, 이 클래스는 그 뒤에서
 * LLM 호출만 담당한다 — 이유는 DialogueChatPersistenceService 클래스 주석 참고.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final CropMasterRepository cropMasterRepository;
    private final FruitMasterRepository fruitMasterRepository;
    private final ShopItemMasterRepository shopItemMasterRepository;
    private final SessionService sessionService;
    private final LlmProxyClient llmProxyClient;
    private final InventoryPersistenceService persistence;

    /**
     * 세션 ID만 받아 이 메서드의 트랜잭션 안에서 직접 조회한다 — 컨트롤러에서 미리 조회한
     * 엔티티를 넘겨받으면(과거엔 그랬음) 그 엔티티가 이미 트랜잭션이 닫힌 detached 상태라
     * 지연 로딩 필드를 건드리는 순간 LazyInitializationException이 날 수 있었다.
     */
    @Transactional(readOnly = true)
    public List<InventorySlotResponse> list(Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return inventoryItemRepository.findBySession(session).stream()
                .map(item -> new InventorySlotResponse(
                        item.getSlotIndex(),
                        item.getItemType().name(),
                        item.getItemRefId(),
                        resolveName(item.getItemType(), item.getItemRefId()),
                        resolveItemCode(item.getItemType(), item.getItemRefId()),
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
    public String useItem(Long sessionId, Integer slotIndex, Long targetNpcId) {
        UseItemOutcome outcome = persistence.prepareUseItem(sessionId, slotIndex, targetNpcId);
        if (outcome.finishedMessage() != null) {
            return outcome.finishedMessage();
        }

        String reaction = llmProxyClient.generateGiftReaction(outcome.targetName(), outcome.targetRole(),
                outcome.targetAge(), outcome.targetPersonalityDesc(), outcome.targetSpeechStyle(), outcome.targetSampleLine());
        if (reaction == null) {
            reaction = outcome.targetName() + "이(가) 선물을 받고 반가워합니다.";
        }
        return reaction + " (호감도 +" + outcome.gain() + ", 현재 " + outcome.newAffinityScore() + ")";
    }

    private String resolveName(InventoryItemType type, Long refId) {
        return switch (type) {
            case CROP -> cropMasterRepository.findById(refId).map(CropMaster::getName).orElse("알 수 없는 작물");
            case FRUIT -> fruitMasterRepository.findById(refId).map(FruitMaster::getName).orElse("알 수 없는 과일");
            case SHOP_ITEM -> shopItemMasterRepository.findById(refId).map(ShopItemMaster::getName).orElse("알 수 없는 아이템");
        };
    }

    private String resolveItemCode(InventoryItemType type, Long refId) {
        if (type != InventoryItemType.SHOP_ITEM) {
            return null;
        }
        return shopItemMasterRepository.findById(refId).map(item -> item.getItemCode().name()).orElse(null);
    }
}
