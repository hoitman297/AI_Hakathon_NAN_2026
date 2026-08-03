package com.gameproject.llmproxy.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * backend에서만 호출해야 하는 /internal/** 엔드포인트를 공유 비밀키로 보호한다.
 * 이 키가 없으면 llm-proxy 포트에 접근 가능한 누구나 Anthropic API 호출을 대신 트리거할 수 있다.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String expectedApiKey;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/") || isAuthorized(request.getHeader(HEADER_NAME))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"missing or invalid " + HEADER_NAME + "\"}");
    }

    private boolean isAuthorized(String providedApiKey) {
        if (providedApiKey == null) {
            return false;
        }
        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedApiKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
