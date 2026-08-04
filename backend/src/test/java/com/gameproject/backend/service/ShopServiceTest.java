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
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.ItemCategory;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
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
                sessionService, staminaService, inventoryService);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        session = GameSession.builder()
                .sessionId(100L).account(account)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
        sneakers = ShopItemMaster.builder()
                .itemId(1L).name("운동화").category(ItemCategory.PERMANENT_EQUIPMENT)
                .price(50).effectDesc("이동 시 체력 소모량 감소").usageLimit("1회 구매, 영구")
                .build();

        when(sessionService.findSession(100L)).thenReturn(session);
        when(shopItemMasterRepository.findById(1L)).thenReturn(Optional.of(sneakers));
    }

    @Test
    void purchase_sneakersAlreadyEquipped_throwsWithoutDeductingGold() {
        session.setSneakersEquipped(true);

        assertThatThrownBy(() -> shopService.purchase(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 영구 장착");

        verify(staminaService, never()).currentStat(any());
        verify(staminaService, never()).addGold(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(inventoryService, never()).addItem(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void purchase_sneakersNotYetEquipped_deductsGoldAndAddsItem() {
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(100.0).staminaMax(100).gold(100).fainted(false).build();
        when(staminaService.currentStat(session)).thenReturn(stat);

        shopService.purchase(100L, 1L);

        verify(staminaService).addGold(session, -50);
        verify(inventoryService).addItem(session, InventoryItemType.SHOP_ITEM, 1L, 1);
    }

    @Test
    void purchase_insufficientGold_throws() {
        PlayerStat stat = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(100.0).staminaMax(100).gold(10).fainted(false).build();
        when(staminaService.currentStat(session)).thenReturn(stat);

        assertThatThrownBy(() -> shopService.purchase(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("골드가 부족");

        verify(inventoryService, never()).addItem(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
