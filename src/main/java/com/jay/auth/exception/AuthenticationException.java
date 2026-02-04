package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends BusinessException {

    private AuthenticationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED);
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다", "INVALID_CREDENTIALS");
    }

    public static AuthenticationException accountLocked() {
        return new AuthenticationException("계정이 잠겼습니다. 30분 후에 다시 시도해주세요", "ACCOUNT_LOCKED");
    }

    public static AuthenticationException accountNotActive() {
        return new AuthenticationException("활성화되지 않은 계정입니다", "ACCOUNT_NOT_ACTIVE");
    }

    public static AuthenticationException invalidToken() {
        return new AuthenticationException("유효하지 않은 토큰입니다", "INVALID_TOKEN");
    }

    public static AuthenticationException tokenExpired() {
        return new AuthenticationException("토큰이 만료되었습니다", "TOKEN_EXPIRED");
    }
}
