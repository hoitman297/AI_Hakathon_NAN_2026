package com.gameproject.llmproxy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class SessionIdMdcFilterTest {

    private static final String HEADER_NAME = "X-Session-Id";
    private static final String MDC_KEY = "sessionId";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private final SessionIdMdcFilter filter = new SessionIdMdcFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove(MDC_KEY);
    }

    @Test
    void headerPresent_setsMdcDuringChainAndClearsAfterward() throws Exception {
        when(request.getHeader(HEADER_NAME)).thenReturn("session-42");
        doAnswer(inv -> {
            assertThat(MDC.get(MDC_KEY)).isEqualTo("session-42");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void headerMissing_leavesMdcUnsetAndStillProceeds() throws Exception {
        when(request.getHeader(HEADER_NAME)).thenReturn(null);
        doAnswer(inv -> {
            assertThat(MDC.get(MDC_KEY)).isNull();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void chainThrows_stillClearsMdcAndPropagatesException() throws Exception {
        when(request.getHeader(HEADER_NAME)).thenReturn("session-99");
        doThrow(new RuntimeException("downstream failure")).when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("downstream failure");

        assertThat(MDC.get(MDC_KEY)).isNull();
    }
}
