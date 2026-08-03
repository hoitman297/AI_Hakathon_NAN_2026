package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.NpcDetailResponse;
import com.gameproject.backend.dto.NpcSummaryResponse;
import com.gameproject.backend.service.NpcService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/npcs")
@RequiredArgsConstructor
public class NpcController {

    private final NpcService npcService;

    @GetMapping
    public ResponseEntity<List<NpcSummaryResponse>> listToday(@PathVariable Long sessionId) {
        return ResponseEntity.ok(npcService.listToday(sessionId));
    }

    @GetMapping("/{npcId}")
    public ResponseEntity<NpcDetailResponse> detail(@PathVariable Long sessionId, @PathVariable Long npcId) {
        return ResponseEntity.ok(npcService.getDetail(sessionId, npcId));
    }
}
