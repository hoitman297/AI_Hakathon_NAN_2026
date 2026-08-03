package com.gameproject.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmProxyRestClientConfig {

    /**
     * DialogueService/AccusationService가 @Transactional 안에서 이 클라이언트로
     * llm-proxy(실제 LLM API)를 호출한다. 타임아웃이 없으면 LLM 응답이 느려질 때 DB
     * 트랜잭션·커넥션이 그만큼 물려 있게 되므로, 응답을 무한정 기다리지 않도록 상한을 둔다.
     * (실측치: 페르소나 생성 약 23초, 엔딩 스토리 생성 약 20초, 랜덤 이벤트 연출 약 20~24초 —
     * 기존 20초로는 정상 응답도 종종 504로 끊겼다. 여유를 두고 45초로 상향.)
     */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
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
                .build();
    }
}
