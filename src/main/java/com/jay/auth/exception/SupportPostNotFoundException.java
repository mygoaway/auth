package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class SupportPostNotFoundException extends BusinessException {

    public SupportPostNotFoundException() {
        super("게시글을 찾을 수 없습니다", "SUPPORT001", HttpStatus.NOT_FOUND);
    }
}
