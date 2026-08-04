package com.gameproject.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.DialogueLog;
import com.gameproject.backend.domain.DialogueSender;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.NpcPersonaState;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.dto.DialogueMessageResponse;
import com.gameproject.backend.dto.DialogueReplyResponse;
import com.gameproject.backend.dto.llm.DialogueChatResponse;
import com.gameproject.backend.dto.llm.DialogueTurn;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.GameSessionRepository;
import com.gameproject.backend.repository.NpcCaseAssignmentRepository;
import com.gameproject.backend.repository.NpcPersonaStateRepository;
import com.gameproject.backend.repository.NpcRepository;

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

        // 7일차부터는 기획서상 "간단한 대화만 가능(추리 대화 불가)" 이지만, 대화 내용 자체의
        // 주제를 서버가 판별하기는 어려워 현재는 별도 제한을 걸지 않았다 (기획 확정 필요 항목).
        PlayerStat stat = staminaService.consume(session, GameConstants.DIALOGUE_STAMINA);

        String personaJson = getOrGeneratePersona(session, npc);
        List<DialogueTurn> history = dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc).stream()
                .map(log -> new DialogueTurn(log.getSender().name(), log.getMessage()))
                .toList();

        boolean honestMode = npc.getNpcId().equals(session.getHonestModeNpcId())
                && session.getCurrentDay().equals(session.getHonestModeDay());

        DialogueChatResponse llmResult = llmProxyClient.chat(personaJson, history, userMessage, honestMode);
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
