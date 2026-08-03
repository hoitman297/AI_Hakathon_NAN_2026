package com.gameproject.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.gameproject.backend.service.AuthService;
import com.gameproject.backend.service.SessionService;
import com.gameproject.backend.web.SessionOwnershipInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthService authService;
    private final SessionService sessionService;

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionOwnershipInterceptor(authService, sessionService))
                .addPathPatterns("/api/sessions/**");
    }

    /**
     * 프론트(Vite, 기본 5173)가 브라우저에서 백엔드(8080) API를 호출할 수 있게 허용.
     * 이게 없으면 saveApi.ts 등 프론트의 모든 fetch가 CORS 에러로 막힌다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
