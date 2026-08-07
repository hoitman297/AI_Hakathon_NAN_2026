package com.gameproject.backend.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.gameproject.backend.web.SessionOwnershipInterceptor;

@Configuration
public class LlmProxyRestClientConfig {

    /** llm-proxy 쪽 SessionIdMdcFilter가 이 헤더명으로 읽어 자기 MDC에도 심는다. */
    private static final String SESSION_ID_HEADER = "X-Session-Id";

    /**
     * DialogueService/AccusationService가 @Transactional 안에서 이 클라이언트로
     * llm-proxy(실제 LLM API)를 호출한다. 타임아웃이 없으면 LLM 응답이 느려질 때 DB
     * 트랜잭션·커넥션이 그만큼 물려 있게 되므로, 응답을 무한정 기다리지 않도록 상한을 둔다.
     * (실측치: 페르소나 생성 약 23초, 엔딩 스토리 생성 약 20초, 랜덤 이벤트 연출 약 20~24초 —
     * 기존 20초로는 정상 응답도 종종 504로 끊겼다. 여유를 두고 45초로 상향.)
     *
     * connect timeout은 로컬(같은 머신, 즉시 연결)만 염두에 두고 3초로 짧게 잡혀 있었는데,
     * Render 같은 무료/저사양 호스팅에서는 llm-proxy가 일정 시간 요청이 없으면 슬립되고
     * 다음 요청에서 깨어나는 데(cold start) 수십 초가 걸릴 수 있다 — 이 구간엔 TCP 연결 자체가
     * 안 되니 3초 만에 연결 타임아웃으로 끊겨 "LLM 서버 응답이 지연되고 있습니다" 오류가
     * 뜬다(로컬에서는 재현 안 됨). read timeout과 동일하게 45초로 맞춰 cold start를 버티게 한다.
     */
    private static final int CONNECT_TIMEOUT_MS = 45_000;
    private static final int READ_TIMEOUT_MS = 45_000;

    @Bean
    public RestClient llmProxyRestClient(@Value("${app.llm-proxy.base-url}") String baseUrl,
                                          @Value("${app.llm-proxy.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", apiKey)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String sessionId = MDC.get(SessionOwnershipInterceptor.SESSION_ID_MDC_KEY);
                    if (sessionId != null) {
                        request.getHeaders().add(SESSION_ID_HEADER, sessionId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
