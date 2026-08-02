package com.gameproject.backend.dto.llm;

import java.util.List;

/** backend → llm-proxy: 대화용 LLM 호출 요청 */
public record DialogueChatRequest(
        String personaJson,
        List<DialogueTurn> history,
        String userMessage,
        boolean honestMode
) {
}
