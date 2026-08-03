package com.gameproject.backend.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.dto.CreateSessionRequest;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.service.SessionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * 로그인이 필수다 (게스트 플레이 없음). Authorization 검증과 계정 조회는
     * SessionOwnershipInterceptor가 먼저 처리해서 "account" 요청 속성으로 넘겨준다.
     */
    @PostMapping
    public ResponseEntity<SessionResponse> create(
            @RequestBody(required = false) CreateSessionRequest request,
            @RequestAttribute(SessionOwnershipInterceptor.ACCOUNT_ATTRIBUTE) Account account) {
        CreateSessionRequest body = request != null ? request : new CreateSessionRequest(null);
        return ResponseEntity.ok(sessionService.createSession(body, account));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/day/advance")
    public ResponseEntity<SessionResponse> advanceDay(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.advanceDay(sessionId));
    }

    /**
     * 이동 1회에 대한 체력 소모(운동화 장착 시 5→4 할인 적용). 프론트가 "이동 1회"로
     * 판단하는 시점(예: 타일 1칸, 일정 거리)마다 호출한다.
     */
    @PostMapping("/{sessionId}/move")
    public ResponseEntity<SessionResponse> move(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.move(sessionId));
    }
}
