package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.domain.DialogueLog;
import com.gameproject.backend.domain.DialogueSender;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcPersonaState;
import com.gameproject.backend.domain.NpcWitnessAwareness;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.domain.SessionStatus;
import com.gameproject.backend.dto.llm.DialogueTurn;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcPersonaStateRepository;
import com.gameproject.backend.repository.NpcRepository;
import com.gameproject.backend.repository.NpcWitnessAwarenessRepository;
import com.gameproject.backend.repository.RandomEventLogRepository;
import com.gameproject.backend.repository.SabotageEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * DialogueService.send()의 DB 읽기/쓰기 전용 조각들. LLM 호출(llm-proxy)이 최대 수십 초
 * 걸릴 수 있는데, 그 호출을 이 클래스의 트랜잭션 메서드 "안"에서 하면 그동안 DB 커넥션을
 * 붙들게 되어 동시 요청 몇 개만으로 커넥션 풀이 고갈된다. 그래서 LLM 호출은 항상 이 클래스
 * 바깥(DialogueService.send())에서 하고, 여기 메서드들은 짧게 열었다 바로 커밋한다.
 *
 * <p>별도 빈으로 분리한 이유: DialogueService.send()가 이 메서드들을 {@code this.method()}로
 * (같은 클래스 안에서) 호출하면 Spring의 트랜잭션 프록시를 거치지 않아(self-invocation)
 * {@code @Transactional}이 조용히 무시된다 — 그러면 GameSession.account/culprit 같은
 * 지연 로딩(LAZY) 필드에 트랜잭션 없이 접근하다 LazyInitializationException이 난다.
 * 별도 빈으로 두면 DialogueService → 이 빈 호출이 항상 프록시를 거치므로 안전하다.
 */
@Service
@RequiredArgsConstructor
class DialogueChatPersistenceService {

    private final NpcRepository npcRepository;
    private final NpcPersonaStateRepository personaStateRepository;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final DialogueLogRepository dialogueLogRepository;
    private final GameSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final StaminaService staminaService;
    private final NpcService npcService;
    private final LlmRateLimiter llmRateLimiter;
    private final SabotageEventRepository sabotageEventRepository;
    private final RandomEventLogRepository randomEventLogRepository;
    private final NpcWitnessAwarenessRepository witnessAwarenessRepository;

    /**
     * LLM 호출에 필요한 데이터를 모으는 짧은 트랜잭션. session.getAccount()/session.getCulprit()는
     * 지연 로딩이라 이 트랜잭션(영속성 컨텍스트)이 열려 있는 동안에만 접근할 수 있다 — 그래서
     * 필요한 값을 여기서 전부 꺼내 순수 데이터(DialogueChatContext)로 반환한다.
     */
    @Transactional
    DialogueChatContext prepareChatContext(Long sessionId, Long npcId) {
        GameSession session = sessionService.findSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 종료된 세션입니다.");
        }
        Npc npc = findNpc(npcId);

        // 대화는 체력 소모(하루 최대 ~12회)로만 자연스럽게 제한돼 있고 별도 횟수 제한이 없어서,
        // 프론트 버그/재시도 루프가 있으면 이 한도 안에서도 짧은 시간에 LLM 호출이 몰릴 수 있다.
        // 실제 상태 변경(체력 소모 등) 전에 먼저 검사해서, 막힐 요청은 아무 부작용 없이 막는다.
        llmRateLimiter.checkAllowed(session.getAccount().getAccountId());

        List<DialogueLog> allLogs = dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc);

        // 기획서: NPC 한 명당 하루 최대 3회 대화. 예전엔 프론트 로컬 state(대화창을 새로 열 때마다
        // 0으로 초기화)로만 세서, 대화창을 닫았다 다시 열면 한도가 그냥 풀렸다 — 여기서 이 세션의
        // 오늘(currentDay)치 USER 발화 수를 DB 기준으로 직접 세서, 대화창을 몇 번을 열고 닫든
        // 하루 누적치가 유지되게 한다.
        long todayExchangeCount = allLogs.stream()
                .filter(log -> log.getSender() == DialogueSender.USER && log.getDay().equals(session.getCurrentDay()))
                .count();
        if (todayExchangeCount >= GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY) {
            throw new IllegalStateException(npc.getName() + "와(과)는 오늘 더 대화할 수 없습니다 (하루 최대 "
                    + GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY + "회).");
        }

        PlayerStat stat = staminaService.consume(session, GameConstants.DIALOGUE_STAMINA);

        String existingPersonaJson = personaStateRepository.findBySessionAndNpc(session, npc)
                .map(NpcPersonaState::getGeneratedPersonaJson)
                .orElse(null);
        String motiveTextForPersonaGen = null;
        if (existingPersonaJson == null && npc.getNpcId().equals(session.getCulprit().getNpcId())) {
            motiveTextForPersonaGen = caseAssignmentRepository.findBySession(session)
                    .map(a -> a.getMotiveText())
                    .orElse(null);
        }

        List<DialogueTurn> history = allLogs.stream()
                .map(log -> new DialogueTurn(log.getSender().name(), log.getMessage()))
                .toList();

        boolean honestMode = npc.getNpcId().equals(session.getHonestModeNpcId())
                && session.getCurrentDay().equals(session.getHonestModeDay());

        // 기획서 확정 스펙(70점 이상 우호적/단서 먼저 제공, 30~70 기본 대화만, 30 미만 회피·단답)을
        // 대화 LLM이 실제로 반영하도록 현재 호감도를 매 호출마다 같이 넘긴다.
        int affinityScore = npcService.getAffinityScore(session, npc);

        // 기획서 확정 스펙: 7일차(고발 가능 시작일)부터는 "간단한 대화만 가능(추리 대화 불가)".
        // 대화 주제 판별은 서버가 아니라 LLM이 시스템 프롬프트 지시로 직접 수행한다.
        boolean restrictDetectiveTalk = session.getCurrentDay() >= GameConstants.FIRST_ACCUSATION_DAY;

        // 기획서 "낮 동선과 밤 사보타주를 목격담으로 연결" — 이 NPC가 목격자로 배정된 밤이 있으면
        // "N일차 밤 장소" 형태로만 넘긴다(실제로 뭘 봤는지는 llm-proxy 프롬프트가 스스로 애매하게
        // 지어내게 한다 — 서버도 몰라야 범인을 특정할 정보가 새어나갈 일이 없다).
        SabotageEvent directWitnessEvent = sabotageEventRepository.findBySession(session).stream()
                .filter(event -> npc.getNpcId().equals(
                        event.getWitnessNpc() != null ? event.getWitnessNpc().getNpcId() : null))
                .max(Comparator.comparing(SabotageEvent::getDay))
                .orElse(null);

        // 직접 목격자가 아니면, WitnessGossipService가 밤마다 관계망(부부/마을 소식통)을 타고
        // 퍼뜨린 "전해 들은" 목격담이 있는지 확인한다 — 내용은 동일한 day/location 문자열을
        // 재사용하고, witnessIsSecondhand만 true로 넘겨서 llm-proxy가 "직접 봤다"고 잘못
        // 연기하지 않도록 구분한다.
        String witnessContext;
        boolean witnessIsSecondhand;
        if (directWitnessEvent != null) {
            witnessContext = directWitnessEvent.getDay() + "일차 밤 " + directWitnessEvent.getLocation();
            witnessIsSecondhand = false;
        } else {
            SabotageEvent heardEvent = witnessAwarenessRepository.findBySessionAndNpc(session, npc).stream()
                    .map(NpcWitnessAwareness::getSabotageEvent)
                    .max(Comparator.comparing(SabotageEvent::getDay))
                    .orElse(null);
            witnessContext = heardEvent != null ? heardEvent.getDay() + "일차 밤 " + heardEvent.getLocation() : null;
            witnessIsSecondhand = heardEvent != null;
        }

        // 최근 마을 전체 대상 랜덤 이벤트(7→8일차)만 대화 소재로 넘긴다 — 플레이어 개인 대상
        // 이벤트(8→9일차)는 NPC가 알 도리가 없는 사적인 사건이라 제외한다.
        String recentVillageEventContext = randomEventLogRepository.findBySession(session).stream()
                .filter(log -> log.getTarget() == EventTarget.VILLAGE)
                .max(Comparator.comparing(RandomEventLog::getDay))
                .map(RandomEventLog::getDescription)
                .orElse(null);

        return new DialogueChatContext(session, npc, stat, existingPersonaJson, motiveTextForPersonaGen, history,
                honestMode, affinityScore, restrictDetectiveTalk, witnessContext, witnessIsSecondhand,
                recentVillageEventContext, (int) todayExchangeCount + 1);
    }

    @Transactional
    void savePersona(GameSession session, Npc npc, String personaJson) {
        personaStateRepository.save(NpcPersonaState.builder()
                .session(session).npc(npc)
                .generatedPersonaJson(personaJson)
                .generatedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 대화 로그 저장 + 정직 모드 해제를 커밋한다. 호감도 반영({@link #applyAffinityDelta})과
     * 일부러 별도 트랜잭션으로 분리했다 — Affinity 엔티티는 낙관적 락(@Version)이 걸려 있어서
     * 선물 주기 등 다른 요청과 동시에 같은 NPC 호감도를 건드리면 충돌(409)이 날 수 있는데,
     * 예전엔 이 메서드 하나로 묶여 있어서 그 충돌이 나면 이미 LLM 호출까지 끝낸 대화 로그
     * 저장(USER+NPC 메시지)까지 통째로 롤백돼 대화 내용 자체가 사라지는 문제가 있었다.
     * 이제 대화 로그는 호감도 반영 성패와 무관하게 먼저 확정 커밋된다.
     */
    @Transactional
    void saveDialogueExchange(GameSession session, Npc npc, String userMessage, String reply, boolean honestMode) {
        dialogueLogRepository.save(DialogueLog.builder()
                .session(session).npc(npc).day(session.getCurrentDay())
                .sender(DialogueSender.USER).message(userMessage).createdAt(LocalDateTime.now())
                .build());
        dialogueLogRepository.save(DialogueLog.builder()
                .session(session).npc(npc).day(session.getCurrentDay())
                .sender(DialogueSender.NPC).message(reply).createdAt(LocalDateTime.now())
                .build());

        if (honestMode) {
            session.setHonestModeNpcId(null);
            session.setHonestModeDay(null);
            sessionRepository.save(session);
        }
    }

    /** saveDialogueExchange()가 이미 커밋된 뒤에 별도 트랜잭션으로 호감도만 반영한다. */
    @Transactional
    int applyAffinityDelta(GameSession session, Npc npc, int affinityDelta) {
        return npcService.adjustAffinity(session, npc, affinityDelta);
    }

    private Npc findNpc(Long npcId) {
        return npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + npcId));
    }
}
