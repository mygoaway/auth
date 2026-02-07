package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class TwoFactorException extends BusinessException {

    private TwoFactorException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public static TwoFactorException notSetup() {
        return new TwoFactorException("2FA_NOT_SETUP", "2단계 인증이 설정되지 않았습니다", HttpStatus.BAD_REQUEST);
    }

    public static TwoFactorException notEnabled() {
        return new TwoFactorException("2FA_NOT_ENABLED", "2단계 인증이 활성화되지 않았습니다", HttpStatus.BAD_REQUEST);
    }

    public static TwoFactorException alreadyEnabled() {
        return new TwoFactorException("2FA_ALREADY_ENABLED", "2단계 인증이 이미 활성화되어 있습니다", HttpStatus.BAD_REQUEST);
    }

    public static TwoFactorException invalidCode() {
        return new TwoFactorException("2FA_INVALID_CODE", "잘못된 인증 코드입니다", HttpStatus.BAD_REQUEST);
    }

    public static TwoFactorException required() {
        return new TwoFactorException("2FA_REQUIRED", "2단계 인증이 필요합니다", HttpStatus.FORBIDDEN);
    }
}
