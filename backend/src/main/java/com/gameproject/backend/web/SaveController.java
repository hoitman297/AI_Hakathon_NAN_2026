package com.gameproject.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.dto.SaveRequest;
import com.gameproject.backend.dto.SaveResponse;
import com.gameproject.backend.service.AuthException;
import com.gameproject.backend.service.AuthService;
import com.gameproject.backend.service.GameSaveService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 계정별 게임 세이브 스냅샷 저장/조회. /api/sessions/** 하위가 아니라서
 * SessionOwnershipInterceptor 적용 대상이 아니므로, AuthController와 동일한 방식으로
 * 여기서 직접 Bearer 토큰을 검증한다.
 */
@RestController
@RequestMapping("/api/save")
@RequiredArgsConstructor
public class SaveController {

    private final GameSaveService gameSaveService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<SaveResponse> save(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SaveRequest request) {
        Account account = resolveAccount(authorization);
        return ResponseEntity.ok(gameSaveService.save(account, request));
    }

    @GetMapping
    public ResponseEntity<SaveResponse> load(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Account account = resolveAccount(authorization);
        return ResponseEntity.ok(gameSaveService.load(account));
    }

    private Account resolveAccount(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthException("로그인이 필요합니다 (Authorization: Bearer <token>).");
        }
        return authService.requireValidToken(authorization.substring("Bearer ".length()));
    }
}
