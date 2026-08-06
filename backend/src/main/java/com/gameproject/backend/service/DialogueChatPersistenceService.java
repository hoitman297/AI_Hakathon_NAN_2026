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
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.dto.llm.DialogueTurn;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcPersonaStateRepository;
import com.gameproject.backend.repository.NpcRepository;
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

    /**
     * LLM 호출에 필요한 데이터를 모으는 짧은 트랜잭션. session.getAccount()/session.getCulprit()는
     * 지연 로딩이라 이 트랜잭션(영속성 컨텍스트)이 열려 있는 동안에만 접근할 수 있다 — 그래서
     * 필요한 값을 여기서 전부 꺼내 순수 데이터(DialogueChatContext)로 반환한다.
     */
    @Transactional
    DialogueChatContext prepareChatContext(Long sessionId, Long npcId) {
        GameSession session = sessionService.findSession(sessionId);
        Npc npc = findNpc(npcId);

        // 대화는 체력 소모(하루 최대 ~12회)로만 자연스럽게 제한돼 있고 별도 횟수 제한이 없어서,
        // 프론트 버그/재시도 루프가 있으면 이 한도 안에서도 짧은 시간에 LLM 호출이 몰릴 수 있다.
        // 실제 상태 변경(체력 소모 등) 전에 먼저 검사해서, 막힐 요청은 아무 부작용 없이 막는다.
        llmRateLimiter.checkAllowed(session.getAccount().getAccountId());

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

        List<DialogueTurn> history = dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc).stream()
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
        String witnessContext = sabotageEventRepository.findBySession(session).stream()
                .filter(event -> npc.getNpcId().equals(
                        event.getWitnessNpc() != null ? event.getWitnessNpc().getNpcId() : null))
                .max(Comparator.comparing(SabotageEvent::getDay))
                .map(event -> event.getDay() + "일차 밤 " + event.getLocation())
                .orElse(null);

        // 최근 마을 전체 대상 랜덤 이벤트(7→8일차)만 대화 소재로 넘긴다 — 플레이어 개인 대상
        // 이벤트(8→9일차)는 NPC가 알 도리가 없는 사적인 사건이라 제외한다.
        String recentVillageEventContext = randomEventLogRepository.findBySession(session).stream()
                .filter(log -> log.getTarget() == EventTarget.VILLAGE)
                .max(Comparator.comparing(RandomEventLog::getDay))
                .map(RandomEventLog::getDescription)
                .orElse(null);

        return new DialogueChatContext(session, npc, stat, existingPersonaJson, motiveTextForPersonaGen, history,
                honestMode, affinityScore, restrictDetectiveTalk, witnessContext, recentVillageEventContext);
    }

    @Transactional
    void savePersona(GameSession session, Npc npc, String personaJson) {
        personaStateRepository.save(NpcPersonaState.builder()
                .session(session).npc(npc)
                .generatedPersonaJson(personaJson)
                .generatedAt(LocalDateTime.now())
                .build());
    }

    /** 대화 로그 저장 + 정직 모드 해제 + 호감도 반영을 한 트랜잭션으로 묶어 커밋한다. */
    @Transactional
    int finalizeChat(GameSession session, Npc npc, String userMessage, String reply, boolean honestMode, int affinityDelta) {
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

        return npcService.adjustAffinity(session, npc, affinityDelta);
    }

    private Npc findNpc(Long npcId) {
        return npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + npcId));
    }
}
