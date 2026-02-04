package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationException extends BusinessException {

    public InvalidVerificationException(String message) {
        super(message, "AUTH002", HttpStatus.BAD_REQUEST);
    }

    public static InvalidVerificationException codeNotFound() {
        return new InvalidVerificationException("인증 요청을 찾을 수 없습니다");
    }

    public static InvalidVerificationException codeExpired() {
        return new InvalidVerificationException("인증 코드가 만료되었습니다");
    }

    public static InvalidVerificationException codeMismatch() {
        return new InvalidVerificationException("인증 코드가 일치하지 않습니다");
    }

    public static InvalidVerificationException notVerified() {
        return new InvalidVerificationException("이메일 인증이 완료되지 않았습니다");
    }
}
