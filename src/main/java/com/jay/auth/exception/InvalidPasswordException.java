package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordException extends BusinessException {

    public InvalidPasswordException() {
        super("비밀번호는 8자 이상, 영문, 숫자, 특수문자를 포함해야 합니다", "AUTH003", HttpStatus.BAD_REQUEST);
    }
}
