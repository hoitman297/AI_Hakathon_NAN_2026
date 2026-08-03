package com.gameproject.llmproxy.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.llmproxy.dto.ClueClarifyRequest;
import com.gameproject.llmproxy.dto.ClueClarifyResponse;
import com.gameproject.llmproxy.dto.ClueContentRequest;
import com.gameproject.llmproxy.dto.ClueContentResponse;
import com.gameproject.llmproxy.dto.DialogueChatRequest;
import com.gameproject.llmproxy.dto.DialogueChatResponse;
import com.gameproject.llmproxy.dto.EndingContentRequest;
import com.gameproject.llmproxy.dto.EndingContentResponse;
import com.gameproject.llmproxy.dto.EventContentRequest;
import com.gameproject.llmproxy.dto.EventContentResponse;
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

    @PostMapping("/event")
    public ResponseEntity<EventContentResponse> event(@RequestBody EventContentRequest request) {
        String description = llmService.generateEventContent(
                request.eventType(), request.target(), request.day(), request.accusedNpcName());
        return ResponseEntity.ok(new EventContentResponse(description));
    }

    @PostMapping("/ending")
    public ResponseEntity<EndingContentResponse> ending(@RequestBody EndingContentRequest request) {
        String story = llmService.generateEndingStory(
                request.name(), request.role(), request.age(), request.personalityDesc(),
                request.speechStyle(), request.motiveText(), request.primaryType(), request.targetPoolDesc());
        return ResponseEntity.ok(new EndingContentResponse(story));
    }

    @PostMapping("/clue")
    public ResponseEntity<ClueContentResponse> clue(@RequestBody ClueContentRequest request) {
        String text = llmService.generateClueContent(request.topic(), request.npcAppearanceDesc(),
                request.npcPersonalityDesc(), request.location(), request.subTarget());
        return ResponseEntity.ok(new ClueContentResponse(text));
    }

    @PostMapping("/clue/clarify")
    public ResponseEntity<ClueClarifyResponse> clueClarify(@RequestBody ClueClarifyRequest request) {
        String text = llmService.clarifyClueContent(request.topic(), request.npcAppearanceDesc(), request.previousText());
        return ResponseEntity.ok(new ClueClarifyResponse(text));
    }
}
