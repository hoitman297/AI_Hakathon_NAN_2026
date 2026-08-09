package com.gameproject.backend.dto;

public record DialogueReplyResponse(
        String npcReply,
        Integer affinityScore,
        Double staminaCurrent,
        /** 오늘 이 NPC에게 이 발화까지 포함해 몇 번째 대화인지(1부터). maxExchangesPerDay에 도달하면
         *  프론트가 대화창을 다시 열어도 더 말을 걸 수 없음을 바로 알 수 있다. */
        Integer exchangesUsedToday,
        Integer maxExchangesPerDay
) {
}
