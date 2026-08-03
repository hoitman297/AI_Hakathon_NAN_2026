package com.gameproject.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmProxyRestClientConfig {

    /**
     * DialogueService가 @Transactional 안에서 이 클라이언트로 llm-proxy(실제 LLM API)를
     * 호출한다. 타임아웃이 없으면 LLM 응답이 느려질 때 DB 트랜잭션·커넥션이 그만큼
     * 물려 있게 되므로, 응답을 무한정 기다리지 않도록 상한을 둔다.
     */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    @Bean
    public RestClient llmProxyRestClient(@Value("${app.llm-proxy.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
