package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException() {
        super("이미 가입된 이메일입니다", "AUTH001", HttpStatus.CONFLICT);
    }

    public DuplicateEmailException(String email) {
        super("이미 가입된 이메일입니다: " + email, "AUTH001", HttpStatus.CONFLICT);
    }
}
