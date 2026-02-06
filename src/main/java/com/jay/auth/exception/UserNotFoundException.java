package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public UserNotFoundException(String message) {
        super(message, "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public static UserNotFoundException recoveryEmailNotFound() {
        return new UserNotFoundException("해당 복구 이메일로 등록된 사용자가 없습니다");
    }
}
