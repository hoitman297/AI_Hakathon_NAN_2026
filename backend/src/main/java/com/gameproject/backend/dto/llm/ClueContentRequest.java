package com.gameproject.backend.dto.llm;

/** backend -> llm-proxy: NPC 외형/성격 묘사를 반영한 단서 카드 문구 생성 요청. */
public record ClueContentRequest(
        String topic,
        String npcAppearanceDesc,
        String npcPersonalityDesc,
        String location,
        String subTarget
) {
}
