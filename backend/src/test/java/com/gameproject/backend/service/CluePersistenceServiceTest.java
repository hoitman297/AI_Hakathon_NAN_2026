package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.InventoryItemType;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.domain.ShopItemCode;
import com.gameproject.backend.domain.ShopItemMaster;
import com.gameproject.backend.repository.ClueCardRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.ShopItemMasterRepository;

/** LlmProxyClient 의존성이 아예 없다는 것 자체가 "이 서비스는 LLM을 호출할 수 없다"는 보장이다. */
@ExtendWith(MockitoExtension.class)
class CluePersistenceServiceTest {

    @Mock
    private ClueCardRepository clueCardRepository;
    @Mock
    private ShopItemMasterRepository shopItemMasterRepository;
    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private SessionService sessionService;
    @Mock
    private NpcCaseAssignmentRepository caseAssignmentRepository;

    private CluePersistenceService service;

    private GameSession session;
    private ClueCard clue;
    private Npc culprit;
    private ShopItemMaster magnifier;

    @BeforeEach
    void setUp() {
        service = new CluePersistenceService(clueCardRepository, shopItemMasterRepository, sessionRepository,
                inventoryService, sessionService, caseAssignmentRepository);

        Account account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        culprit = Npc.builder().npcId(1L).name("나박수").appearanceDesc("검고 긴 머리카락").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(culprit)
                .currentDay(3).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
        clue = ClueCard.builder().clueId(10L).session(session).topic(ClueTopic.HAIR)
                .textAmbiguous("검고 긴 머리카락 한 올이 발견됐다.").acquired(true).build();
        magnifier = ShopItemMaster.builder().itemId(5L).itemCode(ShopItemCode.MAGNIFIER).build();

        // saveClarifiedText() 테스트는 prepareClarify()를 안 타므로 lenient로 둔다.
        org.mockito.Mockito.lenient().when(sessionService.findSession(100L)).thenReturn(session);
        org.mockito.Mockito.lenient().when(clueCardRepository.findById(10L)).thenReturn(Optional.of(clue));
    }

    @Test
    void prepareClarify_valid_removesMagnifierAndReturnsContextForLlmCall() {
        when(shopItemMasterRepository.findByItemCode(ShopItemCode.MAGNIFIER)).thenReturn(Optional.of(magnifier));
        NpcCaseAssignment assignment = NpcCaseAssignment.builder().session(session).npc(culprit).build();
        when(caseAssignmentRepository.findBySession(session)).thenReturn(Optional.of(assignment));

        ClarifyContext ctx = service.prepareClarify(100L, 10L);

        assertThat(ctx.topicName()).isEqualTo("HAIR");
        assertThat(ctx.appearanceDesc()).isEqualTo("검고 긴 머리카락");
        assertThat(ctx.previousText()).isEqualTo("검고 긴 머리카락 한 올이 발견됐다.");
        verify(inventoryService).removeItem(session, InventoryItemType.SHOP_ITEM, 5L, 1);
        assertThat(session.getLastMagnifierUseDay()).isEqualTo(3);
        verify(sessionRepository).save(session);
    }

    @Test
    void prepareClarify_notAcquired_throwsIllegalState() {
        clue.setAcquired(false);

        assertThatThrownBy(() -> service.prepareClarify(100L, 10L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prepareClarify_alreadyClarified_throwsIllegalState() {
        clue.setTextClarified("이미 명확화됨");

        assertThatThrownBy(() -> service.prepareClarify(100L, 10L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prepareClarify_magnifierAlreadyUsedToday_throwsIllegalState() {
        session.setLastMagnifierUseDay(3);

        assertThatThrownBy(() -> service.prepareClarify(100L, 10L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prepareClarify_clueFromDifferentSession_throwsIllegalArgument() {
        GameSession otherSession = GameSession.builder().sessionId(200L).currentDay(3)
                .status(SessionStatus.IN_PROGRESS).startedAt(LocalDateTime.now()).sneakersEquipped(false).build();
        clue.setSession(otherSession);

        assertThatThrownBy(() -> service.prepareClarify(100L, 10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveClarifiedText_setsTextAndReturnsResponse() {
        when(clueCardRepository.save(clue)).thenReturn(clue);

        var response = service.saveClarifiedText(clue, "앞머리가 한쪽 눈을 가릴 만큼 길다.");

        assertThat(clue.getTextClarified()).isEqualTo("앞머리가 한쪽 눈을 가릴 만큼 길다.");
        verify(clueCardRepository).save(clue);
        assertThat(response.text()).isEqualTo("앞머리가 한쪽 눈을 가릴 만큼 길다.");
        assertThat(response.clarified()).isTrue();
    }
}
