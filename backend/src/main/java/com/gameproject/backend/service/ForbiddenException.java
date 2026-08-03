package com.gameproject.backend.service;

/** 인증은 됐지만 해당 리소스에 대한 권한이 없는 경우 (예: 남의 세션에 접근). 403으로 응답한다. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
