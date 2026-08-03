package com.gameproject.backend.dto.llm;

/** sender: "USER" 또는 "NPC" */
public record DialogueTurn(String sender, String message) {
}
