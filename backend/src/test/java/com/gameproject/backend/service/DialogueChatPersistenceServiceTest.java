package com.gameproject.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.gameproject.backend.domain.DialogueLog;
import com.gameproject.backend.domain.DialogueSender;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcCaseAssignment;
import com.gameproject.backend.domain.NpcPersonaState;
import com.gameproject.backend.domain.NpcWitnessAwareness;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcPersonaStateRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.NpcWitnessAwarenessRepository;
import com.gameproject.backend.repository.RandomEventLogRepository;
import com.gameproject.backend.repository.SabotageEventRepository;

/**
 * DialogueService.send()에서 이관된 DB 읽기/쓰기 로직 검증. LLM 호출은 이 클래스가 전혀
 * 하지 않는다는 게 이 리팩터링의 핵심이므로, LlmProxyClient는 의존성에서 제외되어 있다
 * (컴파일 타임에 이미 "이 서비스는 LLM을 호출할 수 없다"가 보장된다).
 */
@ExtendWith(MockitoExtension.class)
class DialogueChatPersistenceServiceTest {

    @Mock
    private NpcRepository npcRepository;
    @Mock
    private NpcPersonaStateRepository personaStateRepository;
    @Mock
    private NpcCaseAssignmentRepository caseAssignmentRepository;
    @Mock
    private DialogueLogRepository dialogueLogRepository;
    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private StaminaService staminaService;
    @Mock
    private NpcService npcService;
    @Mock
    private LlmRateLimiter llmRateLimiter;
    @Mock
    private SabotageEventRepository sabotageEventRepository;
    @Mock
    private RandomEventLogRepository randomEventLogRepository;
    @Mock
    private NpcWitnessAwarenessRepository witnessAwarenessRepository;

    private DialogueChatPersistenceService service;

    private Account account;
    private Npc npc;
    private Npc otherNpc;
    private GameSession session;

    @BeforeEach
    void setUp() {
        service = new DialogueChatPersistenceService(npcRepository, personaStateRepository, caseAssignmentRepository,
                dialogueLogRepository, sessionRepository, sessionService, staminaService, npcService,
                llmRateLimiter, sabotageEventRepository, randomEventLogRepository, witnessAwarenessRepository);

        account = Account.builder().accountId(1L).username("u").passwordHash("h").nickname("n")
                .createdAt(LocalDateTime.now()).build();
        npc = Npc.builder().npcId(1L).name("현수동").role("이장").build();
        otherNpc = Npc.builder().npcId(2L).name("나박수").role("수박밭 주인").build();
        session = GameSession.builder()
                .sessionId(100L).account(account).culprit(npc)
                .currentDay(2).status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .sneakersEquipped(false)
                .build();
    }

    /** prepareChatContext()를 실제로 호출하는 테스트에서만 필요한 공통 조회 스텁. */
    private void stubSessionAndNpcLookup() {
        when(sessionService.findSession(100L)).thenReturn(session);
        when(npcRepository.findById(1L)).thenReturn(Optional.of(npc));
    }

    private PlayerStat stat() {
        return PlayerStat.builder().session(session).day(2)
                .staminaCurrent(92.0).staminaMax(100).gold(0).fainted(false).build();
    }

    @Test
    void prepareChatContext_rateLimitExceeded_blocksBeforeStaminaConsumed() {
        stubSessionAndNpcLookup();
        doThrow(new LlmRateLimitExceededException("rate limit")).when(llmRateLimiter).checkAllowed(1L);

        assertThatThrownBy(() -> service.prepareChatContext(100L, 1L))
                .isInstanceOf(LlmRateLimitExceededException.class);

        verify(staminaService, never()).consume(any(), any(Double.class));
    }

    /** session.currentDay(2) 기준 today용 USER 로그 n개를 만든다. */
    private List<DialogueLog> userLogsOnDay(int day, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> DialogueLog.builder().session(session).npc(npc).day(day)
                        .sender(DialogueSender.USER).message("q" + i).createdAt(LocalDateTime.now()).build())
                .toList();
    }

    @Test
    void prepareChatContext_dailyLimitReachedToday_throwsBeforeStaminaConsumed() {
        stubSessionAndNpcLookup();
        // currentDay(2)에 이미 MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY(3)번 말을 건 상태.
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc))
                .thenReturn(userLogsOnDay(2, GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY));

        assertThatThrownBy(() -> service.prepareChatContext(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하루 최대");

        verify(staminaService, never()).consume(any(), any(Double.class));
    }

    @Test
    void prepareChatContext_dailyLimitReachedOnPreviousDay_doesNotBlockToday() {
        // 어제(day=1)는 대화창을 여러 번 열고 닫으며 한도(3회)를 다 썼지만, 오늘(currentDay=2)은
        // 아직 한 번도 말을 안 걸었다 — "대화창을 새로 열면 초기화되던" 예전 버그와 반대로,
        // 이번엔 "날짜가 실제로 바뀌면 정상적으로 리셋"되는지를 검증한다.
        stubSessionAndNpcLookup();
        when(personaStateRepository.findBySessionAndNpc(session, npc))
                .thenReturn(Optional.of(NpcPersonaState.builder().session(session).npc(npc)
                        .generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build()));
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc))
                .thenReturn(userLogsOnDay(1, GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY));
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of());
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());
        when(witnessAwarenessRepository.findBySessionAndNpc(session, npc)).thenReturn(List.of());

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.exchangesUsedToday()).isEqualTo(1);
    }

    @Test
    void prepareChatContext_existingPersona_returnsItAndSkipsMotiveLookup() {
        stubSessionAndNpcLookup();
        NpcPersonaState existing = NpcPersonaState.builder()
                .session(session).npc(npc).generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build();
        when(personaStateRepository.findBySessionAndNpc(session, npc)).thenReturn(Optional.of(existing));
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of());
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());
        when(witnessAwarenessRepository.findBySessionAndNpc(session, npc)).thenReturn(List.of());

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.existingPersonaJson()).isEqualTo("{}");
        assertThat(ctx.motiveTextForPersonaGen()).isNull();
        verify(caseAssignmentRepository, never()).findBySession(any());
    }

    @Test
    void prepareChatContext_missingPersonaAndNpcIsCulprit_looksUpMotiveText() {
        stubSessionAndNpcLookup();
        when(personaStateRepository.findBySessionAndNpc(session, npc)).thenReturn(Optional.empty());
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of());
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());
        when(witnessAwarenessRepository.findBySessionAndNpc(session, npc)).thenReturn(List.of());
        NpcCaseAssignment assignment = NpcCaseAssignment.builder().session(session).npc(npc)
                .motiveText("김치준과의 갈등").build();
        when(caseAssignmentRepository.findBySession(session)).thenReturn(Optional.of(assignment));

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.existingPersonaJson()).isNull();
        assertThat(ctx.motiveTextForPersonaGen()).isEqualTo("김치준과의 갈등");
    }

    @Test
    void prepareChatContext_missingPersonaAndNpcNotCulprit_skipsMotiveLookup() {
        when(sessionService.findSession(100L)).thenReturn(session);
        when(npcRepository.findById(2L)).thenReturn(Optional.of(otherNpc));
        when(personaStateRepository.findBySessionAndNpc(session, otherNpc)).thenReturn(Optional.empty());
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, otherNpc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, otherNpc)).thenReturn(50);
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of());
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());
        when(witnessAwarenessRepository.findBySessionAndNpc(session, otherNpc)).thenReturn(List.of());

        DialogueChatContext ctx = service.prepareChatContext(100L, 2L);

        assertThat(ctx.motiveTextForPersonaGen()).isNull();
        verify(caseAssignmentRepository, never()).findBySession(any());
    }

    @Test
    void prepareChatContext_day7OrLater_setsRestrictDetectiveTalkTrue() {
        stubSessionAndNpcLookup();
        session.setCurrentDay(GameConstants.FIRST_ACCUSATION_DAY);
        when(personaStateRepository.findBySessionAndNpc(session, npc))
                .thenReturn(Optional.of(NpcPersonaState.builder().session(session).npc(npc)
                        .generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build()));
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of());
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());
        when(witnessAwarenessRepository.findBySessionAndNpc(session, npc)).thenReturn(List.of());

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.restrictDetectiveTalk()).isTrue();
    }

    @Test
    void prepareChatContext_witnessContext_picksLatestNightAtThisNpcAndFormatsText() {
        stubSessionAndNpcLookup();
        when(personaStateRepository.findBySessionAndNpc(session, npc))
                .thenReturn(Optional.of(NpcPersonaState.builder().session(session).npc(npc)
                        .generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build()));
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        SabotageEvent older = SabotageEvent.builder().session(session).day(1).location("수박밭")
                .witnessNpc(npc).createdAt(LocalDateTime.now()).build();
        SabotageEvent newer = SabotageEvent.builder().session(session).day(3).location("농기구 창고")
                .witnessNpc(npc).createdAt(LocalDateTime.now()).build();
        SabotageEvent notWitnessedByThisNpc = SabotageEvent.builder().session(session).day(4).location("우물")
                .witnessNpc(otherNpc).createdAt(LocalDateTime.now()).build();
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of(older, newer, notWitnessedByThisNpc));
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.witnessContext()).isEqualTo("3일차 밤 농기구 창고");
        assertThat(ctx.witnessIsSecondhand()).isFalse();
        verify(witnessAwarenessRepository, never()).findBySessionAndNpc(any(), any());
    }

    @Test
    void prepareChatContext_secondhandWitness_usedOnlyWhenNoDirectWitnessAndFlaggedSecondhand() {
        stubSessionAndNpcLookup();
        when(personaStateRepository.findBySessionAndNpc(session, npc))
                .thenReturn(Optional.of(NpcPersonaState.builder().session(session).npc(npc)
                        .generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build()));
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        // npc 본인은 직접 목격자가 아니고(otherNpc가 목격자), WitnessGossipService가 관계망을 통해
        // 전파한 결과로 이 사건을 "전해 들어서" 안다.
        SabotageEvent heard = SabotageEvent.builder().session(session).day(2).location("양계장")
                .witnessNpc(otherNpc).createdAt(LocalDateTime.now()).build();
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of(heard));
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of());
        NpcWitnessAwareness awareness = NpcWitnessAwareness.builder()
                .session(session).sabotageEvent(heard).npc(npc).learnedDay(3)
                .createdAt(LocalDateTime.now()).build();
        when(witnessAwarenessRepository.findBySessionAndNpc(session, npc)).thenReturn(List.of(awareness));

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.witnessContext()).isEqualTo("2일차 밤 양계장");
        assertThat(ctx.witnessIsSecondhand()).isTrue();
    }

    @Test
    void prepareChatContext_recentVillageEventContext_filtersToVillageTargetAndLatestDay() {
        stubSessionAndNpcLookup();
        when(personaStateRepository.findBySessionAndNpc(session, npc))
                .thenReturn(Optional.of(NpcPersonaState.builder().session(session).npc(npc)
                        .generatedPersonaJson("{}").generatedAt(LocalDateTime.now()).build()));
        when(staminaService.consume(session, GameConstants.DIALOGUE_STAMINA)).thenReturn(stat());
        when(dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc)).thenReturn(List.of());
        when(npcService.getAffinityScore(session, npc)).thenReturn(50);
        when(sabotageEventRepository.findBySession(session)).thenReturn(List.of());
        when(witnessAwarenessRepository.findBySessionAndNpc(session, npc)).thenReturn(List.of());
        RandomEventLog playerEvent = RandomEventLog.builder().session(session).day(8).target(EventTarget.PLAYER)
                .eventType("X").description("플레이어 전용 사건").createdAt(LocalDateTime.now()).build();
        RandomEventLog villageEvent = RandomEventLog.builder().session(session).day(7).target(EventTarget.VILLAGE)
                .eventType("Y").description("게시판에 도발적인 쪽지가 붙었다.").createdAt(LocalDateTime.now()).build();
        when(randomEventLogRepository.findBySession(session)).thenReturn(List.of(playerEvent, villageEvent));

        DialogueChatContext ctx = service.prepareChatContext(100L, 1L);

        assertThat(ctx.recentVillageEventContext()).isEqualTo("게시판에 도발적인 쪽지가 붙었다.");
    }

    @Test
    void saveDialogueExchange_savesTwoLogs() {
        service.saveDialogueExchange(session, npc, "안녕하세요", "반갑습니다.", false);

        verify(dialogueLogRepository, times(2)).save(any(DialogueLog.class));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void saveDialogueExchange_honestMode_clearsFlagsAndSavesSession() {
        session.setHonestModeNpcId(1L);
        session.setHonestModeDay(2);

        service.saveDialogueExchange(session, npc, "알리바이가 뭐예요?", "그날은 집에 있었소.", true);

        assertThat(session.getHonestModeNpcId()).isNull();
        assertThat(session.getHonestModeDay()).isNull();
        verify(sessionRepository).save(session);
    }

    @Test
    void applyAffinityDelta_delegatesToNpcService() {
        when(npcService.adjustAffinity(session, npc, 3)).thenReturn(55);

        int result = service.applyAffinityDelta(session, npc, 3);

        assertThat(result).isEqualTo(55);
    }

    @Test
    void savePersona_savesWithSessionNpcAndJson() {
        service.savePersona(session, npc, "{\"generated\":true}");

        verify(personaStateRepository).save(argThat((NpcPersonaState state) ->
                state.getSession() == session
                        && state.getNpc() == npc
                        && "{\"generated\":true}".equals(state.getGeneratedPersonaJson())));
    }
}
