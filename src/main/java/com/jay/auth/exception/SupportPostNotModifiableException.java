package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class SupportPostNotModifiableException extends BusinessException {

    public SupportPostNotModifiableException() {
        super("대기중 상태의 게시글만 수정할 수 있습니다", "SUPPORT003", HttpStatus.BAD_REQUEST);
    }
}
