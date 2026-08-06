package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItem;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.ItemCategory;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

/** LlmProxyClient 의존성이 아예 없다는 것 자체가 "이 서비스는 LLM을 호출할 수 없다"는 보장이다. */
@ExtendWith(MockitoExtension.class)
class InventoryPersistenceServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private CropMasterRepository cropMasterRepository;
    @Mock
    private FruitMasterRepository fruitMasterRepository;
    @Mock
    private ShopItemMasterRepository shopItemMasterRepository;
    @Mock
    private NpcRepository npcRepository;
    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private StaminaService staminaService;
    @Mock
    private NpcService npcService;
    @Mock
    private SessionService sessionService;

    private InventoryPersistenceService service;

    private GameSession session;

    @BeforeEach
    void setUp() {
        service = new InventoryPersistenceService(inventoryItemRepository, cropMasterRepository, fruitMasterRepository,
                shopItemMasterRepository, npcRepository, sessionRepository, staminaService, npcService, sessionService);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        session = GameSession.builder()
                .sessionId(100L).account(account)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
        when(sessionService.findSession(100L)).thenReturn(session);
    }

    @Test
    void prepareUseItem_sneakers_equipsAndConsumesFromInventory() {
        InventoryItem sneakersSlot = InventoryItem.builder().session(session).slotIndex(1)
                .itemType(InventoryItemType.SHOP_ITEM).itemRefId(1L).quantity(1).build();
        ShopItemMaster sneakers = ShopItemMaster.builder()
                .itemId(1L).name("운동화").itemCode(ShopItemCode.SNEAKERS).category(ItemCategory.PERMANENT_EQUIPMENT)
                .price(50).build();
        when(inventoryItemRepository.findBySessionAndSlotIndex(session, 1)).thenReturn(Optional.of(sneakersSlot));
        when(shopItemMasterRepository.findById(1L)).thenReturn(Optional.of(sneakers));

        UseItemOutcome outcome = service.prepareUseItem(100L, 1, null);

        assertThat(outcome.finishedMessage()).contains("장착");
        assertThat(session.getSneakersEquipped()).isTrue();
        verify(inventoryItemRepository).delete(sneakersSlot); // quantity 1개라 소모 시 슬롯 자체가 삭제됨
    }

    @Test
    void prepareUseItem_magnifier_doesNotConsumeOrThrow_directsToClueScreenInstead() {
        InventoryItem magnifierSlot = InventoryItem.builder().session(session).slotIndex(2)
                .itemType(InventoryItemType.SHOP_ITEM).itemRefId(2L).quantity(1).build();
        ShopItemMaster magnifier = ShopItemMaster.builder()
                .itemId(2L).name("돋보기").itemCode(ShopItemCode.MAGNIFIER).category(ItemCategory.CONSUMABLE)
                .price(30).build();
        when(inventoryItemRepository.findBySessionAndSlotIndex(session, 2)).thenReturn(Optional.of(magnifierSlot));
        when(shopItemMasterRepository.findById(2L)).thenReturn(Optional.of(magnifier));

        UseItemOutcome outcome = service.prepareUseItem(100L, 2, null);

        assertThat(outcome.finishedMessage()).contains("단서 카드 화면");
        verify(inventoryItemRepository, never()).delete(any());
        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void prepareUseItem_giftSet_appliesAffinityAndConsumesButReturnsNoFinishedMessage() {
        InventoryItem giftSlot = InventoryItem.builder().session(session).slotIndex(3)
                .itemType(InventoryItemType.SHOP_ITEM).itemRefId(4L).quantity(1).build();
        ShopItemMaster giftSet = ShopItemMaster.builder()
                .itemId(4L).name("선물세트").itemCode(ShopItemCode.GIFT_SET).category(ItemCategory.CONSUMABLE)
                .price(40).build();
        Npc target = Npc.builder().npcId(2L).name("나주부").role("아내").age(32)
                .personalityDesc("상냥함").speechStyle("존댓말").sampleLine("어머").build();
        when(inventoryItemRepository.findBySessionAndSlotIndex(session, 3)).thenReturn(Optional.of(giftSlot));
        when(shopItemMasterRepository.findById(4L)).thenReturn(Optional.of(giftSet));
        when(npcRepository.findById(2L)).thenReturn(Optional.of(target));
        when(npcService.adjustAffinity(any(), any(), anyInt())).thenReturn(60);

        UseItemOutcome outcome = service.prepareUseItem(100L, 3, 2L);

        assertThat(outcome.finishedMessage()).isNull();
        assertThat(outcome.targetName()).isEqualTo("나주부");
        assertThat(outcome.newAffinityScore()).isEqualTo(60);
        verify(inventoryItemRepository).delete(giftSlot); // quantity 1개라 소모 시 슬롯 자체가 삭제됨
        verify(npcService).adjustAffinity(
                org.mockito.ArgumentMatchers.eq(session), org.mockito.ArgumentMatchers.eq(target), anyInt());
    }

    @Test
    void prepareUseItem_giftSetWithoutTargetNpc_throwsIllegalArgument() {
        InventoryItem giftSlot = InventoryItem.builder().session(session).slotIndex(3)
                .itemType(InventoryItemType.SHOP_ITEM).itemRefId(4L).quantity(1).build();
        ShopItemMaster giftSet = ShopItemMaster.builder()
                .itemId(4L).name("선물세트").itemCode(ShopItemCode.GIFT_SET).category(ItemCategory.CONSUMABLE)
                .price(40).build();
        when(inventoryItemRepository.findBySessionAndSlotIndex(session, 3)).thenReturn(Optional.of(giftSlot));
        when(shopItemMasterRepository.findById(4L)).thenReturn(Optional.of(giftSet));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.prepareUseItem(100L, 3, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
