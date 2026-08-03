package com.gameproject.llmproxy.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.llmproxy.dto.DialogueChatRequest;
import com.gameproject.llmproxy.dto.DialogueChatResponse;
import com.gameproject.llmproxy.dto.PersonaGenerateRequest;
import com.gameproject.llmproxy.dto.PersonaGenerateResponse;
import com.gameproject.llmproxy.service.LlmService;

import lombok.RequiredArgsConstructor;

/** backend 전용 내부 API. 외부(프론트엔드)에서 직접 호출하지 않는다. */
@RestController
@RequestMapping("/internal/llm")
@RequiredArgsConstructor
public class InternalLlmController {

    private final LlmService llmService;

    @PostMapping("/persona/generate")
    public ResponseEntity<PersonaGenerateResponse> generatePersona(@RequestBody PersonaGenerateRequest request) {
        String personaJson = llmService.generatePersona(
                request.npcId(), request.name(), request.role(), request.age(),
                request.personalityDesc(), request.speechStyle(), request.sampleLine(), request.motiveText());
        return ResponseEntity.ok(new PersonaGenerateResponse(personaJson));
    }

    @PostMapping("/dialogue")
    public ResponseEntity<DialogueChatResponse> chat(@RequestBody DialogueChatRequest request) {
        String reply = llmService.chat(request);
        return ResponseEntity.ok(new DialogueChatResponse(reply));
    }
}
