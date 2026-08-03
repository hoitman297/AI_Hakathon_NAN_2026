package com.gameproject.backend.web;

import java.util.Map;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.gameproject.backend.domain.Account;
import com.gameproject.backend.domain.GameSession;
import com.gameproject.backend.service.AuthException;
import com.gameproject.backend.service.AuthService;
import com.gameproject.backend.service.ForbiddenException;
import com.gameproject.backend.service.SessionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * /api/sessions/** 하위 모든 요청에 적용. 로그인이 필수이고(게스트 플레이 없음),
 * 경로에 sessionId가 있으면 그 세션이 요청자 본인 계정 소유인지까지 검증한다.
 * (IDOR 방지 — 남의 세션 ID를 넣어 대화/골드/아이템/고발을 조작하는 것을 막음)
 */
@RequiredArgsConstructor
public class SessionOwnershipInterceptor implements HandlerInterceptor {

    public static final String ACCOUNT_ATTRIBUTE = "account";

    private final AuthService authService;
    private final SessionService sessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 브라우저 CORS preflight(OPTIONS) 요청에는 Authorization 헤더가 실려오지 않는다.
        // 여기서 막으면 Spring의 CORS 처리(addCorsMappings) 전에 401이 나서 CORS 자체가
        // 항상 실패하게 되므로, preflight는 인증 없이 통과시킨다.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Account account = authService.requireValidToken(extractToken(request));
        request.setAttribute(ACCOUNT_ATTRIBUTE, account);

        String sessionIdValue = pathVariable(request, "sessionId");
        if (sessionIdValue != null) {
            GameSession session = sessionService.findSession(Long.valueOf(sessionIdValue));
            if (session.getAccount() == null || !session.getAccount().getAccountId().equals(account.getAccountId())) {
                throw new ForbiddenException("본인 소유의 세션이 아닙니다.");
            }
        }
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthException("로그인이 필요합니다 (Authorization: Bearer <token>).");
        }
        return authorization.substring("Bearer ".length());
    }

    private String pathVariable(HttpServletRequest request, String name) {
        Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attr instanceof Map<?, ?> vars)) {
            return null;
        }
        Object value = vars.get(name);
        return value != null ? value.toString() : null;
    }
}
