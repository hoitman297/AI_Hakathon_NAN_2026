package com.gameproject.backend.dto.llm;

/** llm-proxy → backend: 대사(reply)와 함께 LLM이 판단한 이번 턴 호감도 변화량(affinityDelta)을 받는다. */
public record DialogueChatResponse(String reply, Integer affinityDelta) {
}
