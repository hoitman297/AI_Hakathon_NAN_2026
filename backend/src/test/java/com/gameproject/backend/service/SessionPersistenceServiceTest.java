package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import com.gameproject.backend.domain.ClueCard;
import com.gameproject.backend.domain.ClueTopic;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.repository.ClueCardRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.PlayerStatRepository;
import com.gameproject.backend.repository.SabotageEventRepository;

/** LlmProxyClient 의존성이 아예 없다는 것 자체가 "이 서비스는 LLM을 호출할 수 없다"는 보장이다. */
@ExtendWith(MockitoExtension.class)
class SessionPersistenceServiceTest {

    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private PlayerStatRepository playerStatRepository;
    @Mock
    private NpcRepository npcRepository;
    @Mock
    private NpcCaseAssignmentRepository caseAssignmentRepository;
    @Mock
    private SabotageEventRepository sabotageEventRepository;
    @Mock
    private ClueCardRepository clueCardRepository;
    @Mock
    private NpcLocationResolver locationResolver;
    @Mock
    private GameSaveService gameSaveService;
    @Mock
    private WitnessGossipService witnessGossipService;

    /** 순수 정적 룩업이라 실제 인스턴스를 그대로 사용 — 목킹할 이유가 없음. */
    private final CulpritProfileRegistry culpritProfileRegistry = new CulpritProfileRegistry();

    private SessionPersistenceService service;

    private GameSession session;
    private Account account;
    private Npc culprit;

    @BeforeEach
    void setUp() {
        service = new SessionPersistenceService(sessionRepository, playerStatRepository, npcRepository,
                caseAssignmentRepository, sabotageEventRepository, clueCardRepository, locationResolver,
                culpritProfileRegistry, gameSaveService, witnessGossipService);

        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        culprit = Npc.builder().npcId(1L).name("나박수").role("수박밭 주인")
                .appearanceDesc("검고 긴 머리카락").personalityDesc("다혈질").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(culprit)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();

        // finalizeAdvanceDay 계열 테스트는 이미 만들어둔 AdvanceDayContext를 직접 넘기므로
        // findById를 안 타고, prepareAdvanceDay 계열 중 일부(alreadyEnded 등)는 save()까지
        // 도달하지 않으므로 둘 다 lenient 처리.
        lenient().when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.save(any(GameSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void prepareAdvanceDay_alreadyEndedSession_throwsIllegalState() {
        session.setStatus(SessionStatus.BAD_ENDING);

        assertThatThrownBy(() -> service.prepareAdvanceDay(100L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prepareAdvanceDay_sabotageNight_preparesDeterministicSabotageFields() {
        PlayerStat today = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(40.0).staminaMax(100).gold(10).fainted(false).build();
        when(playerStatRepository.findBySessionAndDay(session, 2)).thenReturn(Optional.of(today));
        NpcCaseAssignment assignment = NpcCaseAssignment.builder().session(session).npc(culprit).build();
        when(caseAssignmentRepository.findBySession(session)).thenReturn(Optional.of(assignment));
        when(sabotageEventRepository.findBySessionAndDay(session, 1)).thenReturn(List.of());
        // 범인 본인 말고 다른 NPC가 없으니 목격자는 항상 없음(null) — 랜덤으로 뽑히는 장소와
        // 무관하게 결정적으로 테스트할 수 있다.
        when(npcRepository.findAll()).thenReturn(List.of(culprit));

        AdvanceDayContext ctx = service.prepareAdvanceDay(100L);

        assertThat(ctx.day()).isEqualTo(2);
        assertThat(ctx.sabotage()).isNotNull();
        assertThat(ctx.sabotage().witness()).isNull();
        assertThat(ctx.sabotage().witnessHint()).isEmpty();
        // 단서 주제는 (day-1) % 5로 결정적 — 2일차는 인덱스 1 = BELONGING.
        assertThat(ctx.sabotage().topic()).isEqualTo(ClueTopic.BELONGING);
        assertThat(ctx.sabotage().culpritAppearanceDesc()).isEqualTo("검고 긴 머리카락");
        assertThat(ctx.sabotage().culpritPersonalityDesc()).isEqualTo("다혈질");
    }

    @Test
    void prepareAdvanceDay_afterSabotageNights_sabotageIsNullAndSkipsLookup() {
        session.setCurrentDay(GameConstants.SABOTAGE_NIGHTS + 1);
        PlayerStat today = PlayerStat.builder().session(session).day(GameConstants.SABOTAGE_NIGHTS + 1)
                .staminaCurrent(40.0).staminaMax(100).gold(10).fainted(false).build();
        when(playerStatRepository.findBySessionAndDay(session, GameConstants.SABOTAGE_NIGHTS + 1))
                .thenReturn(Optional.of(today));

        AdvanceDayContext ctx = service.prepareAdvanceDay(100L);

        assertThat(ctx.sabotage()).isNull();
        verify(caseAssignmentRepository, never()).findBySession(any());
    }

    @Test
    void finalizeAdvanceDay_withSabotage_savesEventAndClueWithLlmTextThenAdvancesDay() {
        PlayerStat today = PlayerStat.builder().session(session).day(2)
                .staminaCurrent(40.0).staminaMax(100).gold(10).fainted(false).build();
        AdvanceDayContext ctx = new AdvanceDayContext(session, today, 2, new SabotagePrepContext(
                "수박밭", com.gameproject.backend.domain.SabotageType.DAMAGE, "화분", false, null, "",
                ClueTopic.HAIR, "검고 긴 머리카락", "다혈질"));
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.finalizeAdvanceDay(ctx, "간밤에 수박밭에서 소란이 있었다.", "검고 긴 머리카락 한 올이 발견됐다.");

        assertThat(response.currentDay()).isEqualTo(3);
        verify(sabotageEventRepository).save(org.mockito.ArgumentMatchers.argThat((SabotageEvent event) ->
                "간밤에 수박밭에서 소란이 있었다.".equals(event.getSummaryText()) && event.getLocation().equals("수박밭")));
        verify(clueCardRepository).save(org.mockito.ArgumentMatchers.argThat((ClueCard clue) ->
                "검고 긴 머리카락 한 올이 발견됐다.".equals(clue.getTextAmbiguous()) && clue.getTopic() == ClueTopic.HAIR));
    }

    @Test
    void finalizeAdvanceDay_withoutSabotage_neverTouchesSabotageOrClueTables() {
        PlayerStat today = PlayerStat.builder().session(session).day(6)
                .staminaCurrent(40.0).staminaMax(100).gold(10).fainted(false).build();
        AdvanceDayContext ctx = new AdvanceDayContext(session, today, 6, null);
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));

        service.finalizeAdvanceDay(ctx, null, null);

        verify(sabotageEventRepository, never()).save(any());
        verify(clueCardRepository, never()).save(any());
    }

    @Test
    void finalizeAdvanceDay_faintedToday_nextDayStartsAtFaintRestartStamina() {
        PlayerStat today = PlayerStat.builder().session(session).day(6)
                .staminaCurrent(0.0).staminaMax(100).gold(5).fainted(true).build();
        AdvanceDayContext ctx = new AdvanceDayContext(session, today, 6, null);
        when(playerStatRepository.save(any(PlayerStat.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.finalizeAdvanceDay(ctx, null, null);

        assertThat(response.staminaCurrent()).isEqualTo(GameConstants.FAINT_RESTART_STAMINA);
    }

    @Test
    void finalizeAdvanceDay_lastAccusationDay_setsBadEndingAndSyncsSaveWithoutNextDayStat() {
        session.setCurrentDay(GameConstants.LAST_ACCUSATION_DAY);
        PlayerStat today = PlayerStat.builder().session(session).day(GameConstants.LAST_ACCUSATION_DAY)
                .staminaCurrent(50.0).staminaMax(100).gold(0).fainted(false).build();
        AdvanceDayContext ctx = new AdvanceDayContext(session, today, GameConstants.LAST_ACCUSATION_DAY, null);

        var response = service.finalizeAdvanceDay(ctx, null, null);

        assertThat(response.status()).isEqualTo(SessionStatus.BAD_ENDING.name());
        assertThat(session.getEndedAt()).isNotNull();
        verify(gameSaveService).syncEndingState(account, GameSaveService.ENDING_STATE_BAD_ENDING);
        verify(playerStatRepository, never()).save(any(PlayerStat.class));
    }
}
