package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.PurchaseRequest;
import com.gameproject.backend.dto.SellRequest;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.dto.ShopItemResponse;
import com.gameproject.backend.service.SessionService;
import com.gameproject.backend.service.ShopService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final SessionService sessionService;

    @GetMapping("/items")
    public ResponseEntity<List<ShopItemResponse>> items(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(shopService.listItems());
    }

    @PostMapping("/purchase")
    public ResponseEntity<SessionResponse> purchase(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody PurchaseRequest request) {
        shopService.purchase(sessionId, request.itemId());
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping("/sell")
    public ResponseEntity<SessionResponse> sell(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody SellRequest request) {
        shopService.sell(sessionId, request);
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }
}
