package com.gameproject.backend.web;

import java.util.Map;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.gameproject.backend.service.AuthException;
import com.gameproject.backend.service.ForbiddenException;
import com.gameproject.backend.service.LlmRateLimitExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
     * llm-proxy가 연결/타임아웃 실패가 아니라 명시적인 HTTP 오류 상태로 응답한 경우
     * (예: INTERNAL_API_KEY 불일치로 인한 401, 잘못된 경로로 인한 404, llm-proxy 내부 5xx).
     * RestClientResponseException은 아래 RestClientException의 하위 타입이라 더 구체적인
     * 이 핸들러가 먼저 매칭된다.
     *
     * <p>이 핸들러가 없으면 이런 즉시-실패 케이스도 아래 handleUpstreamTimeout이 잡아서
     * "LLM 서버 응답이 지연되고 있습니다"로 뭉뚱그려지는데, 실제로는 "지연"이 아니라
     * 설정/배포 문제(예: 두 서비스의 API 키가 어긋남)라서 원인 파악이 크게 늦어진다 —
     * 응답 지연 진단 중 이 구분이 없어서 헛다리를 짚을 뻔한 적이 있어 분리했다.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleUpstreamHttpError(RestClientResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "LLM 서버 연동 중 오류가 발생했습니다 (상태 코드 "
                        + e.getStatusCode().value() + "). 잠시 후 다시 시도해주세요."));
    }

    /**
     * llm-proxy(대화/페르소나 생성) 호출이 타임아웃되거나 연결에 실패한 경우(응답 자체를
     * 못 받음). 연결 단계 실패는 ResourceAccessException으로 오지만, 응답 바디를 읽는 도중
     * 타임아웃이 나면(RestClient가 응답 헤더/스트림을 늦게 받는 경우) 상위 타입인
     * RestClientException으로만 온다 — 실제로 대화 중 read timeout이 이 경로로 발생해 500으로
     * 새는 걸 확인하고 상위 타입으로 넓혔다. HTTP 오류 상태로 응답이 오긴 온 경우는 위
     * handleUpstreamHttpError가 먼저 잡으므로 여기로는 안 온다.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleUpstreamTimeout(RestClientException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of("error", "LLM 서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."));
    }

    /**
     * SessionService.advanceDay()/AccusationService.accuse()가 서로 독립적인 llm-proxy
     * 호출 2개를 CompletableFuture로 병렬 실행하고 join()으로 기다리는데, join()은 원인
     * 예외를 항상 CompletionException으로 감싸서 던진다 — 그 안의 원인이 위
     * RestClientResponseException/RestClientException/IllegalStateException/
     * IllegalArgumentException이어도 타입이 안 맞아 해당 핸들러가 못 잡고 그대로 새서 일반
     * 500(스택트레이스 노출 위험)으로 떨어졌었다. 원인을 풀어서 같은 방식으로 재분류한다.
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletionException(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RestClientResponseException restClientResponseException) {
            return handleUpstreamHttpError(restClientResponseException);
        }
        if (cause instanceof RestClientException restClientException) {
            return handleUpstreamTimeout(restClientException);
        }
        if (cause instanceof IllegalStateException illegalStateException) {
            return handleConflict(illegalStateException);
        }
        if (cause instanceof IllegalArgumentException illegalArgumentException) {
            return handleBadRequest(illegalArgumentException);
        }
        log.error("병렬 LLM 호출 중 예기치 못한 예외", cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "처리 중 예기치 못한 오류가 발생했습니다."));
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
