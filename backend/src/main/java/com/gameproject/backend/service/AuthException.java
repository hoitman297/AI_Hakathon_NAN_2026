package com.gameproject.backend.service;

/** 로그인 실패 / 토큰 무효 등 인증 관련 오류. 401로 응답한다. */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
