package com.gameproject.backend.service;

import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.CropMaster;
import com.gameproject.backend.domain.FruitMaster;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItem;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * InventoryService.useItem()의 DB 읽기/쓰기 전용 조각. 아이템 대부분(작물/과일/운동화/
 * 거짓말탐지기/돋보기 안내)은 LLM을 전혀 안 부르므로 이 클래스 안에서 처음부터 끝까지
 * 트랜잭션으로 처리해도 문제가 없다 — 선물세트(GIFT_SET)만 NPC 반응을 LLM으로 생성하는데,
 * 그 호출은 호감도 반영/아이템 소모가 전부 끝난 "뒤"에 일어나고 그 결과는 메시지 문구에만
 * 쓰일 뿐 DB에 저장되지 않는다. 그래서 선물세트 쓰기까지만 여기서 짧게 끝내고, LLM 호출은
 * InventoryService(트랜잭션 없음)가 이 서비스 바깥에서 한다 — 이유는
 * DialogueChatPersistenceService 클래스 주석 참고.
 */
@Service
@RequiredArgsConstructor
class InventoryPersistenceService {

    private final InventoryItemRepository inventoryItemRepository;
    private final CropMasterRepository cropMasterRepository;
    private final FruitMasterRepository fruitMasterRepository;
    private final ShopItemMasterRepository shopItemMasterRepository;
    private final NpcRepository npcRepository;
    private final GameSessionRepository sessionRepository;
    private final StaminaService staminaService;
    private final NpcService npcService;
    private final SessionService sessionService;

    private final Random random = new Random();

    @Transactional
    UseItemOutcome prepareUseItem(Long sessionId, Integer slotIndex, Long targetNpcId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        InventoryItem item = inventoryItemRepository.findBySessionAndSlotIndex(session, slotIndex)
                .orElseThrow(() -> new IllegalArgumentException("해당 슬롯에 아이템이 없습니다: " + slotIndex));

        return switch (item.getItemType()) {
            case CROP -> {
                CropMaster crop = cropMasterRepository.findById(item.getItemRefId())
                        .orElseThrow(() -> new IllegalStateException("작물 마스터 데이터를 찾을 수 없습니다."));
                consumeOne(item);
                staminaService.restore(session, crop.getRestoreHp());
                yield UseItemOutcome.finished(crop.getName() + " 섭취, 체력 " + crop.getRestoreHp() + " 회복");
            }
            case FRUIT -> {
                FruitMaster fruit = fruitMasterRepository.findById(item.getItemRefId())
                        .orElseThrow(() -> new IllegalStateException("과일 마스터 데이터를 찾을 수 없습니다."));
                consumeOne(item);
                staminaService.restore(session, fruit.getRestoreHp());
                yield UseItemOutcome.finished(fruit.getName() + " 섭취, 체력 " + fruit.getRestoreHp() + " 회복");
            }
            case SHOP_ITEM -> {
                ShopItemMaster shopItem = shopItemMasterRepository.findById(item.getItemRefId())
                        .orElseThrow(() -> new IllegalStateException("상점 아이템 마스터 데이터를 찾을 수 없습니다."));
                yield applyShopItemEffect(session, item, shopItem, targetNpcId);
            }
            default -> throw new IllegalStateException("알 수 없는 아이템 타입입니다.");
        };
    }

    private UseItemOutcome applyShopItemEffect(GameSession session, InventoryItem item, ShopItemMaster shopItem, Long targetNpcId) {
        return switch (shopItem.getItemCode()) {
            case SNEAKERS -> {
                session.setSneakersEquipped(true);
                sessionRepository.save(session);
                consumeOne(item); // 1회 구매/영구 장착 후 인벤토리에서는 사라짐
                yield UseItemOutcome.finished("운동화를 장착했습니다. 이동 체력 소모가 줄어듭니다.");
            }
            case LIE_DETECTOR -> {
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
                consumeOne(item);
                yield UseItemOutcome.finished("다음 대화 1턴 동안 정직 모드가 적용됩니다.");
            }
            case GIFT_SET -> {
                if (targetNpcId == null) {
                    throw new IllegalArgumentException("선물세트는 대상 NPC(targetNpcId)가 필요합니다.");
                }
                Npc target = npcRepository.findById(targetNpcId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + targetNpcId));
                int gain = GameConstants.AFFINITY_GIFT_MIN
                        + random.nextInt(GameConstants.AFFINITY_GIFT_MAX - GameConstants.AFFINITY_GIFT_MIN + 1);
                int newScore = npcService.adjustAffinity(session, target, gain);
                consumeOne(item);
                yield new UseItemOutcome(null, target.getName(), target.getRole(), target.getAge(),
                        target.getPersonalityDesc(), target.getSpeechStyle(), target.getSampleLine(),
                        gain, newScore);
            }
            case MAGNIFIER ->
                    // 돋보기는 어떤 단서를 명확화할지 지정해야 하므로 실제 소모/적용은
                    // POST /api/sessions/{id}/clues/{clueId}/clarify 에서 처리한다.
                    UseItemOutcome.finished("돋보기는 단서 카드 화면에서 '명확화' 기능으로 사용하세요.");
        };
    }

    private void consumeOne(InventoryItem item) {
        if (item.getQuantity() <= 1) {
            inventoryItemRepository.delete(item);
        } else {
            item.setQuantity(item.getQuantity() - 1);
            inventoryItemRepository.save(item);
        }
    }
}
