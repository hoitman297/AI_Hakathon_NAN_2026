package com.gameproject.llmproxy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class InternalApiKeyFilterTest {

    private static final String API_KEY = "test-secret-key";
    private static final String HEADER_NAME = "X-Internal-Api-Key";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private InternalApiKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter(API_KEY);
    }

    @Test
    void nonInternalPath_passesThroughRegardlessOfHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void internalPath_correctKey_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/llm/dialogue");
        when(request.getHeader(HEADER_NAME)).thenReturn(API_KEY);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void internalPath_missingKey_blocksWith401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/llm/dialogue");
        when(request.getHeader(HEADER_NAME)).thenReturn(null);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(body.toString()).contains("missing or invalid");
    }

    @Test
    void internalPath_wrongKey_blocksWith401() throws Exception {
        when(request.getRequestURI()).thenReturn("/internal/llm/persona/generate");
        when(request.getHeader(HEADER_NAME)).thenReturn("wrong-key");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void internalPath_keyDifferentLength_blocksWith401() throws Exception {
        // MessageDigest.isEqual은 길이가 다르면 즉시 false를 반환하는데, 그 경로도 정상 동작하는지 확인.
        when(request.getRequestURI()).thenReturn("/internal/llm/event");
        when(request.getHeader(HEADER_NAME)).thenReturn("short");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
