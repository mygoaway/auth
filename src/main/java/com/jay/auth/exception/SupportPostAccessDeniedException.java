package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class SupportPostAccessDeniedException extends BusinessException {

    public SupportPostAccessDeniedException() {
        super("해당 게시글에 접근할 수 없습니다", "SUPPORT002", HttpStatus.FORBIDDEN);
    }
}
