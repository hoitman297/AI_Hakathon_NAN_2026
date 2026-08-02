package com.gameproject.llmproxy.dto;

import java.util.List;

public record DialogueChatRequest(
        String personaJson,
        List<DialogueTurn> history,
        String userMessage,
        boolean honestMode
) {
}
