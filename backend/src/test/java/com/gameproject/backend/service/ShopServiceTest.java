package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopItemMasterRepository shopItemMasterRepository;
    @Mock
    private CropMasterRepository cropMasterRepository;
    @Mock
    private FruitMasterRepository fruitMasterRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private StaminaService staminaService;
    @Mock
    private InventoryService inventoryService;

    private ShopService shopService;

    private GameSession session;
    private ShopItemMaster sneakers;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(shopItemMasterRepository, cropMasterRepository, fruitMasterRepository,
                inventoryItemRepository, sessionService, staminaService, inventoryService);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        session = GameSession.builder()
                .sessionId(100L).account(account)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
        sneakers = ShopItemMaster.builder()
                .itemId(1L).name("운동화").itemCode(ShopItemCode.SNEAKERS).category(ItemCategory.PERMANENT_EQUIPMENT)
                .price(50).effectDesc("이동 중 체력 소모량 감소").usageLimit("1회 구매, 영구")
                .build();

        when(sessionService.findSession(100L)).thenReturn(session);
        when(shopItemMasterRepository.findById(1L)).thenReturn(Optional.of(sneakers));
    }

    @Test
    void purchase_alreadyEquipped_throwsWithoutDeductingGold() {
        session.setSneakersEquipped(true);

        assertThatThrownBy(() -> shopService.purchase(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 보유했거나 장착");

        verify(staminaService, never()).currentStat(any());
        verify(staminaService, never()).addGold(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(inventoryService, never()).addItem(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void purchase_alreadyOwnedButNotYetEquipped_throwsWithoutDeductingGold() {
        // 구매만 하고 아직 장착(useItem)은 안 한 상태 — sneakersEquipped 플래그만 보던 예전
        // 로직이면 이 상태에서 중복 구매로 골드가 또 빠져나갔다. 인벤토리 보유 여부까지 확인해야 막힌다.
        when(inventoryItemRepository.findBySessionAndItemTypeAndItemRefId(session, InventoryItemType.SHOP_ITEM, 1L))
                .thenReturn(Optional.of(InventoryItem.builder().session(session).slotIndex(1)
                        .itemType(InventoryItemType.SHOP_ITEM).itemRefId(1L).quantity(1).build()));

        assertThatThrownBy(() -> shopService.purchase(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 보유했거나 장착");

        verify(staminaService, never()).addGold(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(inventoryService, never()).addItem(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void purchase_notYetOwnedOrEquipped_deductsGoldAndAddsItem() {
        when(inventoryItemRepository.findBySessionAndItemTypeAndItemRefId(session, InventoryItemType.SHOP_ITEM, 1L))
                .thenReturn(Optional.empty());
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(100.0).staminaMax(100).gold(100).fainted(false).build();
        when(staminaService.currentStat(session)).thenReturn(stat);

        shopService.purchase(100L, 1L);

        verify(staminaService).addGold(session, -50);
        verify(inventoryService).addItem(session, InventoryItemType.SHOP_ITEM, 1L, 1);
    }

    @Test
    void purchase_insufficientGold_throws() {
        when(inventoryItemRepository.findBySessionAndItemTypeAndItemRefId(session, InventoryItemType.SHOP_ITEM, 1L))
                .thenReturn(Optional.empty());
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(100.0).staminaMax(100).gold(10).fainted(false).build();
        when(staminaService.currentStat(session)).thenReturn(stat);

        assertThatThrownBy(() -> shopService.purchase(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("골드가 부족");

        verify(inventoryService, never()).addItem(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
