package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.InventorySlotResponse;
import com.gameproject.backend.dto.UseItemRequest;
import com.gameproject.backend.service.InventoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<List<InventorySlotResponse>> list(@PathVariable Long sessionId) {
        return ResponseEntity.ok(inventoryService.list(sessionId));
    }

    @PostMapping("/use")
    public ResponseEntity<String> use(@PathVariable Long sessionId, @Valid @RequestBody UseItemRequest request) {
        String result = inventoryService.useItem(sessionId, request.slotIndex(), request.targetNpcId());
        return ResponseEntity.ok(result);
    }
}
