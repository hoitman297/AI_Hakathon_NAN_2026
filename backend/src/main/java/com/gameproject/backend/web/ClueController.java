package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.ClueCardResponse;
import com.gameproject.backend.dto.UnacquiredClueResponse;
import com.gameproject.backend.service.ClueService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/clues")
@RequiredArgsConstructor
public class ClueController {

    private final ClueService clueService;

    @GetMapping
    public ResponseEntity<List<ClueCardResponse>> list(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(clueService.listAcquired(sessionId));
    }

    @GetMapping("/unacquired")
    public ResponseEntity<List<UnacquiredClueResponse>> unacquired(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(clueService.listUnacquired(sessionId));
    }

    @PostMapping("/{clueId}/acquire")
    public ResponseEntity<ClueCardResponse> acquire(@PathVariable("sessionId") Long sessionId, @PathVariable("clueId") Long clueId) {
        return ResponseEntity.ok(clueService.acquire(sessionId, clueId));
    }

    @PostMapping("/{clueId}/clarify")
    public ResponseEntity<ClueCardResponse> clarify(@PathVariable("sessionId") Long sessionId, @PathVariable("clueId") Long clueId) {
        return ResponseEntity.ok(clueService.clarify(sessionId, clueId));
    }
}
