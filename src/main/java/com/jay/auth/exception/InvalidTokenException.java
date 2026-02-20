package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(message, "INVALID_TOKEN", HttpStatus.UNAUTHORIZED);
    }
}
