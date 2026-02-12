package com.jay.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class LogSmsSenderTest {

    private final LogSmsSender logSmsSender = new LogSmsSender();

    @Nested
    @DisplayName("인증 코드 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("인증 코드 SMS 발송이 예외 없이 수행되어야 한다")
        void sendVerificationCodeSuccess() {
            assertThatCode(() -> logSmsSender.sendVerificationCode("010-1234-5678", "123456"))
                    .doesNotThrowAnyException();
        }
    }
}
