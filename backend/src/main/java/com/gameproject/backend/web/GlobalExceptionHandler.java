package com.gameproject.backend.web;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import com.gameproject.backend.service.AuthException;
import com.gameproject.backend.service.ForbiddenException;
import com.gameproject.backend.service.LlmRateLimitExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    /** 동시 요청으로 유니크 제약(예: 회원가입 아이디 중복)이 DB 레벨에서 걸린 경우. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "요청이 기존 데이터와 충돌합니다."));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    /** 로그인은 됐지만 본인 소유가 아닌 세션에 접근한 경우 (IDOR 방지). */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    /**
     * llm-proxy(대화/페르소나 생성) 호출이 타임아웃되거나 연결/응답 처리에 실패한 경우.
     * 연결 단계 실패는 ResourceAccessException으로 오지만, 응답 바디를 읽는 도중 타임아웃이 나면
     * (RestClient가 응답 헤더/스트림을 늦게 받는 경우) 상위 타입인 RestClientException으로만 온다 —
     * 실제로 대화 중 read timeout이 이 경로로 발생해 500으로 새는 걸 확인하고 상위 타입으로 넓혔다.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleUpstreamTimeout(RestClientException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of("error", "LLM 서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."));
    }

    /** 같은 세션 상태(체력/골드/인벤토리 등)를 동시에 수정하려다 낙관적 락(@Version)에 걸린 경우. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "다른 요청과 동시에 처리되어 충돌했습니다. 다시 시도해주세요."));
    }

    /** 계정당 LLM 호출 빈도 제한(LlmRateLimiter) 초과 — 버그/재시도 루프로부터 비용을 보호. */
    @ExceptionHandler(LlmRateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleLlmRateLimit(LlmRateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
    }
}
