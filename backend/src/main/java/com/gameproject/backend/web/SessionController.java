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
import com.gameproject.backend.dto.MoveRequest;
import com.gameproject.backend.dto.NightSummaryResponse;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.service.SessionService;

import jakarta.validation.Valid;
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

    /**
     * "이어서하기"가 클라이언트에 저장된 세션 ID 없이도 동작하도록, 이 계정의 진행 중인
     * 세션을 서버 기준으로 조회한다. 없으면 204(본문 없음) — 프론트는 이걸 "이어서할 게임 없음"
     * 신호로 사용한다.
     */
    @GetMapping("/current")
    public ResponseEntity<SessionResponse> getCurrent(
            @RequestAttribute(SessionOwnershipInterceptor.ACCOUNT_ATTRIBUTE) Account account) {
        return sessionService.getCurrentSession(account)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/day/advance")
    public ResponseEntity<SessionResponse> advanceDay(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(sessionService.advanceDay(sessionId));
    }

    /**
     * 이동으로 경과한 시간(초)만큼 체력 소모 (초당 0.15, 운동화 장착 시 초당 0.12로 20% 할인).
     * 프론트가 캐릭터가 실제로 움직인 시간을 누적해서 주기적으로 호출한다.
     */
    @PostMapping("/{sessionId}/move")
    public ResponseEntity<SessionResponse> move(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody MoveRequest request) {
        return ResponseEntity.ok(sessionService.move(sessionId, request.seconds()));
    }

    /** NightTransitionScreen용 — 지정한 일차 밤에 사보타주가 없었으면(6일차 이후 등) 204. */
    @GetMapping("/{sessionId}/sabotage/{day}")
    public ResponseEntity<NightSummaryResponse> nightSummary(@PathVariable("sessionId") Long sessionId, @PathVariable("day") int day) {
        return sessionService.getNightSummary(sessionId, day)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
