package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다", "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
