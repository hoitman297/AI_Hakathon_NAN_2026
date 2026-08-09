package com.gameproject.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * llm-proxy 호출 실패를 "진짜 타임아웃/연결 실패"와 "HTTP 오류 상태로 응답은 옴"으로
 * 구분하는 핸들러 분기 검증. 이 구분이 없으면 INTERNAL_API_KEY 불일치(401) 같은 즉시-실패
 * 설정 오류도 "응답 지연" 문구로 뭉뚱그려져 원인 파악이 늦어진다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleUpstreamHttpError_httpStatusError_returnsBadGatewayWithStatusCodeInMessage() {
        HttpClientErrorException e = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);

        ResponseEntity<Map<String, String>> response = handler.handleUpstreamHttpError(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().get("error")).contains("401");
        assertThat(response.getBody().get("error")).doesNotContain("지연");
    }

    @Test
    void handleUpstreamTimeout_connectionFailure_returnsGatewayTimeoutWithDelayMessage() {
        ResourceAccessException e = new ResourceAccessException("Connection timed out");

        ResponseEntity<Map<String, String>> response = handler.handleUpstreamTimeout(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().get("error")).contains("지연");
    }

    /**
     * SessionService.advanceDay()/AccusationService.accuse()의 병렬 LLM 호출(CompletableFuture.join())이
     * 원인 예외를 CompletionException으로 감싸는 것을 재현 — 감싸지 않은 원본 타입 핸들러들과
     * 동일한 결과가 나와야 한다(이걸 안 풀면 일반 500으로 새는 회귀가 있었음).
     */
    @Test
    void handleCompletionException_wrappingHttpStatusError_returnsBadGatewayWithStatusCodeInMessage() {
        HttpClientErrorException cause = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        CompletionException e = new CompletionException(cause);

        ResponseEntity<Map<String, String>> response = handler.handleCompletionException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().get("error")).contains("401");
    }

    @Test
    void handleCompletionException_wrappingConnectionFailure_returnsGatewayTimeoutWithDelayMessage() {
        CompletionException e = new CompletionException(new ResourceAccessException("Connection timed out"));

        ResponseEntity<Map<String, String>> response = handler.handleCompletionException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().get("error")).contains("지연");
    }

    @Test
    void handleCompletionException_wrappingIllegalStateException_returnsConflictWithOriginalMessage() {
        CompletionException e = new CompletionException(new IllegalStateException("이미 종료된 세션입니다."));

        ResponseEntity<Map<String, String>> response = handler.handleCompletionException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error")).isEqualTo("이미 종료된 세션입니다.");
    }

    @Test
    void handleCompletionException_wrappingUnknownCause_returnsGenericInternalServerError() {
        CompletionException e = new CompletionException(new NullPointerException("boom"));

        ResponseEntity<Map<String, String>> response = handler.handleCompletionException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("error")).doesNotContain("boom");
    }
}
