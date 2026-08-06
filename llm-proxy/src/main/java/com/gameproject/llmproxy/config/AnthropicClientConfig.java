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
     *
     * 주의: 이 timeout은 "재시도 제외" 단위 시간이다 — SDK가 타임아웃(IOException 계열)을
     * 기본적으로 재시도 대상으로 보고 maxRetries(기본 2, 즉 최대 3회 시도)만큼 되풀이하면
     * 35초를 잡아도 백오프까지 합쳐 실질적으로 90초 이상 걸릴 수 있어 결국 backend가 먼저
     * 포기한다(실제로 이 재시도 때문에 timeout만 줄인 1차 수정으로는 504가 재현됐다).
     * 실시간 대화는 어차피 실패 시 폴백 대사로 즉시 대체되므로 재시도 이득이 없다 —
     * 재시도를 꺼서 한 번의 시도 시간만으로 예산이 확정되게 한다.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 0;

    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(REQUEST_TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }
}
