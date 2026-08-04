package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
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
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.dto.InventorySlotResponse;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

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

    private InventoryService inventoryService;

    private GameSession session;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryItemRepository, cropMasterRepository, fruitMasterRepository,
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
    void list_fetchesSessionThroughSessionServiceRatherThanReceivingDetachedEntity() {
        when(inventoryItemRepository.findBySession(session)).thenReturn(List.of());

        List<InventorySlotResponse> result = inventoryService.list(100L);

        assertThat(result).isEmpty();
        verify(sessionService).findSession(100L);
    }

    @Test
    void useItem_sneakers_equipsAndConsumesFromInventory() {
        InventoryItem sneakersSlot = InventoryItem.builder().session(session).slotIndex(1)
                .itemType(InventoryItemType.SHOP_ITEM).itemRefId(1L).quantity(1).build();
        ShopItemMaster sneakers = ShopItemMaster.builder()
                .itemId(1L).name("운동화").itemCode(ShopItemCode.SNEAKERS).category(ItemCategory.PERMANENT_EQUIPMENT)
                .price(50).build();
        when(inventoryItemRepository.findBySessionAndSlotIndex(session, 1)).thenReturn(Optional.of(sneakersSlot));
        when(shopItemMasterRepository.findById(1L)).thenReturn(Optional.of(sneakers));

        String result = inventoryService.useItem(100L, 1, null);

        assertThat(result).contains("장착");
        assertThat(session.getSneakersEquipped()).isTrue();
        verify(inventoryItemRepository).delete(sneakersSlot); // quantity 1개라 소모 시 슬롯 자체가 삭제됨
    }

    @Test
    void useItem_magnifier_doesNotConsumeOrThrow_directsToClueScreenInstead() {
        // 돋보기는 어떤 단서를 명확화할지 지정해야 해서, 실제 소모는
        // ClueService.clarify()에서 처리한다 — 여기서는 안내 메시지만 반환하고 아무것도 소모하지 않는다.
        InventoryItem magnifierSlot = InventoryItem.builder().session(session).slotIndex(2)
                .itemType(InventoryItemType.SHOP_ITEM).itemRefId(2L).quantity(1).build();
        ShopItemMaster magnifier = ShopItemMaster.builder()
                .itemId(2L).name("돋보기").itemCode(ShopItemCode.MAGNIFIER).category(ItemCategory.CONSUMABLE)
                .price(30).build();
        when(inventoryItemRepository.findBySessionAndSlotIndex(session, 2)).thenReturn(Optional.of(magnifierSlot));
        when(shopItemMasterRepository.findById(2L)).thenReturn(Optional.of(magnifier));

        String result = inventoryService.useItem(100L, 2, null);

        assertThat(result).contains("단서 카드 화면");
        verify(inventoryItemRepository, org.mockito.Mockito.never()).delete(any());
        verify(inventoryItemRepository, org.mockito.Mockito.never()).save(any());
    }
}
