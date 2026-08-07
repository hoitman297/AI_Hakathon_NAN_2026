package com.gameproject.backend.web;

import java.util.Map;

import org.slf4j.MDC;
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
 *
 * <p>여기서 sessionId를 이미 경로 변수로 뽑고 있으므로, 같은 자리에서 MDC에도 심어둔다 —
 * 이 요청 처리 중 찍히는 모든 로그(그리고 llm-proxy 호출까지, {@code LlmProxyRestClientConfig}
 * 참고)에 세션ID가 같이 남아서, 특정 유저가 겪은 문제를 로그에서 세션 단위로 grep할 수 있다.
 */
@RequiredArgsConstructor
public class SessionOwnershipInterceptor implements HandlerInterceptor {

    public static final String ACCOUNT_ATTRIBUTE = "account";

    /** LlmProxyRestClientConfig가 이 키로 MDC를 읽어 X-Session-Id 헤더로 그대로 넘긴다. */
    public static final String SESSION_ID_MDC_KEY = "sessionId";

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

        // afterCompletion에서 항상 지워주지만(스레드 재사용 시 다음 요청으로 새는 것 방지),
        // 그 보장은 preHandle이 예외 없이 true를 반환했을 때만 유효하다 — 그래서 이 메서드 안에서
        // 더 이상 예외가 날 수 없는 마지막 지점(return 직전)에 심는다.
        if (sessionIdValue != null) {
            MDC.put(SESSION_ID_MDC_KEY, sessionIdValue);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                 Exception ex) {
        MDC.remove(SESSION_ID_MDC_KEY);
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
