package com.gameproject.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * spring-boot-starter-webmvc가 이 프로젝트에서 Jackson 자동 설정(ObjectMapper 빈)을
 * 끌고 오지 않아서(llm-proxy에서 실제 테스트로 확인된 것과 동일한 문제) GameSaveService의
 * 생성자 주입이 실패할 수 있다. 명시적으로 빈을 등록해서 해결.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
