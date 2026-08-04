package com.gameproject.backend.dto;

public record DialogueReplyResponse(
        String npcReply,
        Integer affinityScore,
        Double staminaCurrent
) {
}
