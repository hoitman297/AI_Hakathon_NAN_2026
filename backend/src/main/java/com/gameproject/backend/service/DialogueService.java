package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.DialogueLog;
import com.gameproject.backend.domain.DialogueSender;
import com.gameproject.backend.domain.EventTarget;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcPersonaState;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.domain.RandomEventLog;
import com.gameproject.backend.domain.SabotageEvent;
import com.gameproject.backend.dto.DialogueMessageResponse;
import com.gameproject.backend.dto.DialogueReplyResponse;
import com.gameproject.backend.dto.llm.DialogueChatResponse;
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
 * 대화 흐름 담당. 아키텍처 결정에 따라 페르소나는 (최초 1회) llm-proxy의 페르소나
 * 생성 LLM으로 만들어 DB에 저장해두고, 이후 대화마다 그 페르소나 + 대화 이력을
 * llm-proxy의 대화용 LLM에 넘겨 응답을 받는다.
 */
@Service
@RequiredArgsConstructor
public class DialogueService {

    private final NpcRepository npcRepository;
    private final NpcPersonaStateRepository personaStateRepository;
    private final NpcCaseAssignmentRepository caseAssignmentRepository;
    private final DialogueLogRepository dialogueLogRepository;
    private final GameSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final StaminaService staminaService;
    private final NpcService npcService;
    private final LlmProxyClient llmProxyClient;
    private final LlmRateLimiter llmRateLimiter;
    private final SabotageEventRepository sabotageEventRepository;
    private final RandomEventLogRepository randomEventLogRepository;

    @Transactional(readOnly = true)
    public List<DialogueMessageResponse> history(Long sessionId, Long npcId) {
        GameSession session = sessionService.findSession(sessionId);
        Npc npc = findNpc(npcId);
        return dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc).stream()
                .map(log -> new DialogueMessageResponse(log.getSender().name(), log.getMessage(), log.getCreatedAt()))
                .toList();
    }

    @Transactional
    public DialogueReplyResponse send(Long sessionId, Long npcId, String userMessage) {
        GameSession session = sessionService.findSession(sessionId);
        Npc npc = findNpc(npcId);

        // 대화는 체력 소모(하루 최대 ~12회)로만 자연스럽게 제한돼 있고 별도 횟수 제한이 없어서,
        // 프론트 버그/재시도 루프가 있으면 이 한도 안에서도 짧은 시간에 LLM 호출이 몰릴 수 있다.
        // 실제 상태 변경(체력 소모 등) 전에 먼저 검사해서, 막힐 요청은 아무 부작용 없이 막는다.
        llmRateLimiter.checkAllowed(session.getAccount().getAccountId());

        PlayerStat stat = staminaService.consume(session, GameConstants.DIALOGUE_STAMINA);

        String personaJson = getOrGeneratePersona(session, npc);
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

        DialogueChatResponse llmResult = llmProxyClient.chat(personaJson, history, userMessage, honestMode,
                affinityScore, restrictDetectiveTalk, witnessContext, recentVillageEventContext);
        String reply = llmResult.reply();

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

        // 호감도 증감은 더 이상 고정 랜덤이 아니라, NPC 성격에 비추어 이번 발화가 어땠는지 LLM이
        // 직접 판단한 값(llm-proxy에서 -5~+5로 clamp됨)을 그대로 반영한다.
        int affinityDelta = llmResult.affinityDelta() != null ? llmResult.affinityDelta() : 0;
        int newAffinity = npcService.adjustAffinity(session, npc, affinityDelta);

        return new DialogueReplyResponse(reply, newAffinity, stat.getStaminaCurrent());
    }

    private String getOrGeneratePersona(GameSession session, Npc npc) {
        return personaStateRepository.findBySessionAndNpc(session, npc)
                .map(NpcPersonaState::getGeneratedPersonaJson)
                .orElseGet(() -> {
                    String motiveText = null;
                    if (npc.getNpcId().equals(session.getCulprit().getNpcId())) {
                        motiveText = caseAssignmentRepository.findBySession(session)
                                .map(a -> a.getMotiveText())
                                .orElse(null);
                    }
                    String personaJson = llmProxyClient.generatePersona(
                            npc.getNpcId(), npc.getName(), npc.getRole(), npc.getAge(),
                            npc.getPersonalityDesc(), npc.getSpeechStyle(), npc.getSampleLine(), motiveText);

                    personaStateRepository.save(NpcPersonaState.builder()
                            .session(session).npc(npc)
                            .generatedPersonaJson(personaJson)
                            .generatedAt(LocalDateTime.now())
                            .build());
                    return personaJson;
                });
    }

    private Npc findNpc(Long npcId) {
        return npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + npcId));
    }
}
