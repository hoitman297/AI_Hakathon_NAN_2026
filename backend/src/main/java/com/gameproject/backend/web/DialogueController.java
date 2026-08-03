package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.DialogueMessageResponse;
import com.gameproject.backend.dto.DialogueReplyResponse;
import com.gameproject.backend.dto.DialogueRequest;
import com.gameproject.backend.service.DialogueService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/npcs/{npcId}/dialogue")
@RequiredArgsConstructor
public class DialogueController {

    private final DialogueService dialogueService;

    @GetMapping
    public ResponseEntity<List<DialogueMessageResponse>> history(@PathVariable Long sessionId, @PathVariable Long npcId) {
        return ResponseEntity.ok(dialogueService.history(sessionId, npcId));
    }

    @PostMapping
    public ResponseEntity<DialogueReplyResponse> send(@PathVariable Long sessionId, @PathVariable Long npcId,
                                                        @Valid @RequestBody DialogueRequest request) {
        return ResponseEntity.ok(dialogueService.send(sessionId, npcId, request.message()));
    }
}
