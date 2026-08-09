package com.gameproject.backend.service;

import java.util.List;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.domain.Npc;
import com.gameproject.backend.domain.PlayerStat;
import com.gameproject.backend.dto.llm.DialogueTurn;

/**
 * DialogueChatPersistenceService.prepareChatContext()가 모은, LLM 호출 구간
 * (트랜잭션 밖)에서 쓰는 순수 데이터. session/npc는 그 트랜잭션이 끝나면 detach되므로
 * 이후 필드 접근(지연 로딩 제외)만 안전하다.
 */
record DialogueChatContext(
        GameSession session,
        Npc npc,
        PlayerStat stat,
        String existingPersonaJson,
        String motiveTextForPersonaGen,
        List<DialogueTurn> history,
        boolean honestMode,
        int affinityScore,
        boolean restrictDetectiveTalk,
        String witnessContext,
        boolean witnessIsSecondhand,
        String recentVillageEventContext,
        /** 이 요청까지 포함해 오늘 이 NPC에게 보낸 USER 발화 수(1부터 시작). */
        int exchangesUsedToday) {
}
