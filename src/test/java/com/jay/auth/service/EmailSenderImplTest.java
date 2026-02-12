package com.jay.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class EmailSenderImplTest {

    private final EmailSenderImpl emailSender = new EmailSenderImpl();

    @Nested
    @DisplayName("인증 코드 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("인증 코드 이메일 발송이 예외 없이 수행되어야 한다")
        void sendVerificationCodeSuccess() {
            assertThatCode(() -> emailSender.sendVerificationCode("test@example.com", "123456"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("새 기기 로그인 알림")
    class SendNewDeviceLoginAlert {

        @Test
        @DisplayName("새 기기 로그인 알림이 예외 없이 수행되어야 한다")
        void sendNewDeviceLoginAlertSuccess() {
            assertThatCode(() -> emailSender.sendNewDeviceLoginAlert(
                    "test@example.com", "Chrome on Windows", "127.0.0.1", "Seoul, KR", "2025-01-01 12:00:00"
            )).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("위치가 null이어도 예외 없이 수행되어야 한다")
        void sendNewDeviceLoginAlertWithNullLocation() {
            assertThatCode(() -> emailSender.sendNewDeviceLoginAlert(
                    "test@example.com", "Chrome on Windows", "127.0.0.1", null, "2025-01-01 12:00:00"
            )).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 알림")
    class SendPasswordChangedAlert {

        @Test
        @DisplayName("비밀번호 변경 알림이 예외 없이 수행되어야 한다")
        void sendPasswordChangedAlertSuccess() {
            assertThatCode(() -> emailSender.sendPasswordChangedAlert("test@example.com", "2025-01-01 12:00:00"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("계정 연동 알림")
    class SendAccountLinkedAlert {

        @Test
        @DisplayName("계정 연동 알림이 예외 없이 수행되어야 한다")
        void sendAccountLinkedAlertSuccess() {
            assertThatCode(() -> emailSender.sendAccountLinkedAlert("test@example.com", "Google", "2025-01-01 12:00:00"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("계정 연동 해제 알림")
    class SendAccountUnlinkedAlert {

        @Test
        @DisplayName("계정 연동 해제 알림이 예외 없이 수행되어야 한다")
        void sendAccountUnlinkedAlertSuccess() {
            assertThatCode(() -> emailSender.sendAccountUnlinkedAlert("test@example.com", "Google", "2025-01-01 12:00:00"))
                    .doesNotThrowAnyException();
        }
    }
}
