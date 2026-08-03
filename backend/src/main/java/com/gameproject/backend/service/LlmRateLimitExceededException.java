package com.gameproject.backend.service;

/** 계정당 LLM 호출 빈도 제한을 초과했을 때. 429로 응답한다. */
public class LlmRateLimitExceededException extends RuntimeException {

    public LlmRateLimitExceededException(String message) {
        super(message);
    }
}
