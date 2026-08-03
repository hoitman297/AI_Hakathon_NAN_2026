package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.dto.InventorySlotResponse;
import com.gameproject.backend.dto.UseItemRequest;
import com.gameproject.backend.service.InventoryService;
import com.gameproject.backend.service.SessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<InventorySlotResponse>> list(@PathVariable Long sessionId) {
        GameSession session = sessionService.findSession(sessionId);
        return ResponseEntity.ok(inventoryService.list(session));
    }

    @PostMapping("/use")
    public ResponseEntity<String> use(@PathVariable Long sessionId, @RequestBody UseItemRequest request) {
        GameSession session = sessionService.findSession(sessionId);
        String result = inventoryService.useItem(session, request.slotIndex(), request.targetNpcId());
        return ResponseEntity.ok(result);
    }
}
