package com.gameproject.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.AccuseRequest;
import com.gameproject.backend.dto.AccuseResultResponse;
import com.gameproject.backend.dto.EndingResponse;
import com.gameproject.backend.service.AccusationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
public class AccusationController {

    private final AccusationService accusationService;

    @PostMapping("/accuse")
    public ResponseEntity<AccuseResultResponse> accuse(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody AccuseRequest request) {
        return ResponseEntity.ok(accusationService.accuse(sessionId, request.accusedNpcId()));
    }

    @GetMapping("/ending")
    public ResponseEntity<EndingResponse> ending(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(accusationService.getEnding(sessionId));
    }
}
