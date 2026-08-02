package com.gameproject.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.AccountResponse;
import com.gameproject.backend.dto.AuthResponse;
import com.gameproject.backend.dto.LoginRequest;
import com.gameproject.backend.dto.SignupRequest;
import com.gameproject.backend.service.AuthException;
import com.gameproject.backend.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(extractToken(authorization));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(authService.me(extractToken(authorization)));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthException("Authorization 헤더가 없거나 형식이 올바르지 않습니다 (Bearer <token>).");
        }
        return authorization.substring("Bearer ".length());
    }
}
