package com.gameproject.llmproxy.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

@Configuration
public class AnthropicClientConfig {

    /**
     * SDK 기본 요청 타임아웃은 10분이다 — backend(LlmProxyRestClientConfig)가 이 서비스를
     * 최대 45초까지만 기다리므로, 그보다 길게 잡아두면 Anthropic API 호출이 느려질 때
     * LlmService의 catch(RuntimeException) 폴백이 발동하기도 전에 backend가 먼저 504로
     * 포기해버린다("LLM 서버 응답이 지연되고 있습니다" 오류로 노출됨). backend 예산보다
     * 여유 있게 짧게 잡아, 느릴 땐 여기서 먼저 실패해 LlmService의 캐릭터 답변 폴백 문구가
     * 정상 200 응답으로 나가도록 한다.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(35);

    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(REQUEST_TIMEOUT)
                .build();
    }
}
