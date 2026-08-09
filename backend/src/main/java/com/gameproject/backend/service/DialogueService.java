package com.gameproject.backend.service;

import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameproject.backend.client.LlmProxyClient;
import com.gameproject.backend.domain.DialogueSender;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.dto.DialogueHistoryResponse;
import com.gameproject.backend.dto.DialogueMessageResponse;
import com.gameproject.backend.dto.DialogueReplyResponse;
import com.gameproject.backend.dto.llm.DialogueChatResponse;
import com.gameproject.backend.repository.DialogueLogRepository;
import com.gameproject.backend.repository.NpcRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대화 흐름 담당. 아키텍처 결정에 따라 페르소나는 (최초 1회) llm-proxy의 페르소나
 * 생성 LLM으로 만들어 DB에 저장해두고, 이후 대화마다 그 페르소나 + 대화 이력을
 * llm-proxy의 대화용 LLM에 넘겨 응답을 받는다.
 *
 * <p>{@code send()} 자체는 {@code @Transactional}이 아니다 — 이 메서드 안에서 llm-proxy를
 * 최대 두 번(페르소나 생성 1회 + 대화 응답) 호출하는데, 하나의 트랜잭션으로 감싸면 그
 * 호출들이 느려질 때마다(최대 수십 초) DB 커넥션/트랜잭션을 그만큼 붙들고 있게 되어 동시
 * 접속자가 몇 명만 몰려도 커넥션 풀이 고갈된다. DB 읽기/쓰기는 DialogueChatPersistenceService의
 * 짧은 트랜잭션들로 쪼개고, LLM 호출은 그 사이 트랜잭션 밖에서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class DialogueService {

    private final NpcRepository npcRepository;
    private final DialogueLogRepository dialogueLogRepository;
    private final SessionService sessionService;
    private final LlmProxyClient llmProxyClient;
    private final DialogueChatPersistenceService persistence;

    @Transactional(readOnly = true)
    public DialogueHistoryResponse history(Long sessionId, Long npcId) {
        GameSession session = sessionService.findSession(sessionId);
        Npc npc = findNpc(npcId);
        var logs = dialogueLogRepository.findBySessionAndNpcOrderByCreatedAtAsc(session, npc);
        List<DialogueMessageResponse> messages = logs.stream()
                .map(log -> new DialogueMessageResponse(log.getSender().name(), log.getMessage(), log.getCreatedAt()))
                .toList();
        long usedToday = logs.stream()
                .filter(log -> log.getSender() == DialogueSender.USER && log.getDay().equals(session.getCurrentDay()))
                .count();
        return new DialogueHistoryResponse(messages, (int) usedToday, GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY);
    }

    public DialogueReplyResponse send(Long sessionId, Long npcId, String userMessage) {
        DialogueChatContext ctx = persistence.prepareChatContext(sessionId, npcId);

        String personaJson = ctx.existingPersonaJson();
        if (personaJson == null) {
            personaJson = llmProxyClient.generatePersona(
                    ctx.npc().getNpcId(), ctx.npc().getName(), ctx.npc().getRole(), ctx.npc().getAge(),
                    ctx.npc().getPersonalityDesc(), ctx.npc().getSpeechStyle(), ctx.npc().getSampleLine(),
                    ctx.motiveTextForPersonaGen());
            persistence.savePersona(ctx.session(), ctx.npc(), personaJson);
        }

        DialogueChatResponse llmResult = llmProxyClient.chat(personaJson, ctx.history(), userMessage, ctx.honestMode(),
                ctx.affinityScore(), ctx.restrictDetectiveTalk(), ctx.witnessContext(), ctx.witnessIsSecondhand(),
                ctx.recentVillageEventContext());
        String reply = llmResult.reply();

        // 대화 로그는 호감도 반영과 별개 트랜잭션으로 먼저 확정 커밋한다 — 아래 호감도 반영이
        // 실패해도(동시에 선물 등으로 같은 NPC 호감도가 바뀌어 낙관적 락 충돌이 나는 경우 등)
        // 이미 LLM까지 호출해서 만든 대화 내용 자체는 사라지지 않는다.
        persistence.saveDialogueExchange(ctx.session(), ctx.npc(), userMessage, reply, ctx.honestMode());

        // 호감도 증감은 더 이상 고정 랜덤이 아니라, NPC 성격에 비추어 이번 발화가 어땠는지 LLM이
        // 직접 판단한 값(llm-proxy에서 -5~+5로 clamp됨)을 그대로 반영한다.
        int affinityDelta = llmResult.affinityDelta() != null ? llmResult.affinityDelta() : 0;
        int newAffinity;
        try {
            newAffinity = persistence.applyAffinityDelta(ctx.session(), ctx.npc(), affinityDelta);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 대화 자체는 이미 저장됐으니 이 정도 동시성 충돌로 전체 요청을 실패시키지 않는다 —
            // 이번 턴의 호감도 변화만 반영이 안 된 채로(대화 시작 시점 값) 넘어간다.
            newAffinity = ctx.affinityScore();
        }

        return new DialogueReplyResponse(reply, newAffinity, ctx.stat().getStaminaCurrent(),
                ctx.exchangesUsedToday(), GameConstants.MAX_DIALOGUE_EXCHANGES_PER_NPC_PER_DAY);
    }

    private Npc findNpc(Long npcId) {
        return npcRepository.findById(npcId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 NPC입니다: " + npcId));
    }
}
