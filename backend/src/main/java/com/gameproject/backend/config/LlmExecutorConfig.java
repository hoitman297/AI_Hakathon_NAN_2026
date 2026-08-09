package com.gameproject.backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서로 독립적인 llm-proxy 호출(예: 사보타주 요약+단서 카드, 오답 이벤트 연출+반응)을
 * 순차 대기 대신 동시에 보내기 위한 실행기. 각 호출이 블로킹 HTTP(RestClient)라 스레드가
 * 응답을 기다리는 동안 묶이는데, 이런 I/O 대기 작업엔 가상 스레드가 딱 맞고(동시 요청이
 * 늘어도 플랫폼 스레드를 추가로 잡아먹지 않음), 세션당 최대 2개 정도만 동시 사용하는
 * 규모라 별도 풀 크기 튜닝도 불필요하다.
 */
@Configuration
public class LlmExecutorConfig {

    @Bean
    public Executor llmParallelExecutor() {
        return new MdcPropagatingExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
