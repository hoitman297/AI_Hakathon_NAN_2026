package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.RandomEventResponse;
import com.gameproject.backend.service.RandomEventService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/events")
@RequiredArgsConstructor
public class RandomEventController {

    private final RandomEventService randomEventService;

    @GetMapping("/unviewed")
    public ResponseEntity<List<RandomEventResponse>> unviewed(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(randomEventService.listUnviewed(sessionId));
    }

    @PostMapping("/{eventId}/view")
    public ResponseEntity<RandomEventResponse> view(@PathVariable("sessionId") Long sessionId, @PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(randomEventService.markViewed(sessionId, eventId));
    }
}
