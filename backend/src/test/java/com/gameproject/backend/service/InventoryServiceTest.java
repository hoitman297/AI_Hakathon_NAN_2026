package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.InventorySlotResponse;
import com.gameproject.backend.repository.CropMasterRepository;
import com.gameproject.backend.repository.FruitMasterRepository;
import com.gameproject.backend.repository.InventoryItemRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

/**
 * useItem()의 DB 읽기/쓰기는 InventoryPersistenceService(짧은 트랜잭션)로 옮겨졌으므로, 이
 * 테스트는 "LLM 호출(선물 반응 생성)은 항상 그 트랜잭션 바깥에서 일어난다"는 오케스트레이션만
 * 검증한다. InventoryPersistenceService 자체 로직(아이템별 효과, 소모 등)은
 * InventoryPersistenceServiceTest에서 검증한다.
 */
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
    private SessionService sessionService;
    @Mock
    private LlmProxyClient llmProxyClient;
    @Mock
    private InventoryPersistenceService persistence;

    private InventoryService inventoryService;

    private GameSession session;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryItemRepository, cropMasterRepository, fruitMasterRepository,
                shopItemMasterRepository, sessionService, llmProxyClient, persistence);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        session = GameSession.builder()
                .sessionId(100L).account(account)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
    }

    @Test
    void list_fetchesSessionThroughSessionServiceRatherThanReceivingDetachedEntity() {
        when(sessionService.findSession(100L)).thenReturn(session);
        when(inventoryItemRepository.findBySession(session)).thenReturn(List.of());

        List<InventorySlotResponse> result = inventoryService.list(100L);

        assertThat(result).isEmpty();
        verify(sessionService).findSession(100L);
    }

    @Test
    void useItem_nonGiftItem_returnsFinishedMessageWithoutAnyLlmCall() {
        when(persistence.prepareUseItem(100L, 1, null))
                .thenReturn(UseItemOutcome.finished("운동화를 장착했습니다. 이동 체력 소모가 줄어듭니다."));

        String result = inventoryService.useItem(100L, 1, null);

        assertThat(result).isEqualTo("운동화를 장착했습니다. 이동 체력 소모가 줄어듭니다.");
        verify(llmProxyClient, never())
                .generateGiftReaction(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void useItem_giftSet_callsLlmAfterPersistenceAndBuildsMessage() {
        UseItemOutcome outcome = new UseItemOutcome(null, "나주부", "아내", 32,
                "상냥함", "존댓말", "어머", 5, 60);
        when(persistence.prepareUseItem(100L, 3, 2L)).thenReturn(outcome);
        when(llmProxyClient.generateGiftReaction("나주부", "아내", 32, "상냥함", "존댓말", "어머"))
                .thenReturn("어머, 이런 것까지... 고마워요!");

        String result = inventoryService.useItem(100L, 3, 2L);

        assertThat(result).contains("어머, 이런 것까지... 고마워요!").contains("호감도 +5").contains("60");
    }

    @Test
    void useItem_giftSet_llmReturnsNull_fallsBackToDefaultReaction() {
        UseItemOutcome outcome = new UseItemOutcome(null, "나주부", "아내", 32,
                "상냥함", "존댓말", "어머", 5, 60);
        when(persistence.prepareUseItem(100L, 3, 2L)).thenReturn(outcome);
        when(llmProxyClient.generateGiftReaction("나주부", "아내", 32, "상냥함", "존댓말", "어머"))
                .thenReturn(null);

        String result = inventoryService.useItem(100L, 3, 2L);

        assertThat(result).contains("나주부이(가) 선물을 받고 반가워합니다.");
    }
}
