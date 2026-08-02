package com.gameproject.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.dto.CreateSessionRequest;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.service.AuthService;
import com.gameproject.backend.service.SessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AuthService authService;

    /**
     * Authorization 헤더가 있으면 로그인 계정으로 세션을 소유시키고, 없으면
     * (프론트 로그인 연동 전이거나 게스트 플레이) body의 playerId만으로 기존처럼 동작한다.
     */
    @PostMapping
    public ResponseEntity<SessionResponse> create(
            @RequestBody(required = false) CreateSessionRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CreateSessionRequest body = request != null ? request : new CreateSessionRequest(null);
        Account account = resolveAccount(authorization);
        return ResponseEntity.ok(sessionService.createSession(body, account));
    }

    private Account resolveAccount(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authService.requireValidToken(authorization.substring("Bearer ".length()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/day/advance")
    public ResponseEntity<SessionResponse> advanceDay(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.advanceDay(sessionId));
    }
}
