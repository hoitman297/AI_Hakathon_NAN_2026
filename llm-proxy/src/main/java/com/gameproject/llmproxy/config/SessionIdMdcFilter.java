package com.gameproject.llmproxy.config;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * backend가 요청마다 실어 보내는 X-Session-Id 헤더(SessionOwnershipInterceptor/
 * LlmProxyRestClientConfig 참고)를 읽어 이 서비스의 MDC에도 심는다. backend 로그와
 * llm-proxy 로그를 세션ID 하나로 이어서 grep할 수 있게 하기 위함 — 두 서비스가 별도
 * 프로세스라 MDC가 자동으로 넘어오지 않으므로 헤더로 직접 전달한다.
 */
@Component
public class SessionIdMdcFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Session-Id";
    private static final String MDC_KEY = "sessionId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String sessionId = request.getHeader(HEADER_NAME);
        if (sessionId != null) {
            MDC.put(MDC_KEY, sessionId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
