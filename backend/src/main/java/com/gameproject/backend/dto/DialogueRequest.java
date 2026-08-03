package com.gameproject.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DialogueRequest(@NotBlank @Size(max = 1000) String message) {
}
