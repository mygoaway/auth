package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class PasskeyException extends BusinessException {

    private PasskeyException(String errorCode, String message, HttpStatus status) {
        super(message, errorCode, status);
    }

    public static PasskeyException notFound() {
        return new PasskeyException("PASSKEY_NOT_FOUND", "패스키를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }

    public static PasskeyException invalidChallenge() {
        return new PasskeyException("PASSKEY_INVALID_CHALLENGE", "유효하지 않은 챌린지입니다", HttpStatus.BAD_REQUEST);
    }

    public static PasskeyException verificationFailed() {
        return new PasskeyException("PASSKEY_VERIFICATION_FAILED", "패스키 인증에 실패했습니다", HttpStatus.BAD_REQUEST);
    }

    public static PasskeyException registrationFailed() {
        return new PasskeyException("PASSKEY_REGISTRATION_FAILED", "패스키 등록에 실패했습니다", HttpStatus.BAD_REQUEST);
    }

    public static PasskeyException alreadyRegistered() {
        return new PasskeyException("PASSKEY_ALREADY_REGISTERED", "이미 등록된 패스키입니다", HttpStatus.CONFLICT);
    }

    public static PasskeyException limitExceeded() {
        return new PasskeyException("PASSKEY_LIMIT_EXCEEDED", "패스키 등록 한도를 초과했습니다 (최대 10개)", HttpStatus.BAD_REQUEST);
    }

    public static PasskeyException userNotFound() {
        return new PasskeyException("PASSKEY_USER_NOT_FOUND", "사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
