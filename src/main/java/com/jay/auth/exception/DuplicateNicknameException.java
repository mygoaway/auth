package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class DuplicateNicknameException extends BusinessException {

    public DuplicateNicknameException() {
        super("이미 사용 중인 닉네임입니다", "DUPLICATE_NICKNAME", HttpStatus.CONFLICT);
    }
}
