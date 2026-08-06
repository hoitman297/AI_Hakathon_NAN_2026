package com.gameproject.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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
}
