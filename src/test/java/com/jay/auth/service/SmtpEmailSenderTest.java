package com.jay.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @InjectMocks
    private SmtpEmailSender smtpEmailSender;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @Nested
    @DisplayName("인증 코드 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("인증 코드 이메일이 정상 발송되어야 한다")
        void sendVerificationCodeSuccess() {
            // given
            given(templateEngine.process(eq("email/verification-code"), any(Context.class)))
                    .willReturn("<html>인증코드</html>");
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // when & then
            assertThatCode(() -> smtpEmailSender.sendVerificationCode("test@example.com", "123456"))
                    .doesNotThrowAnyException();
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("MimeMessage 구성 중 MessagingException 발생 시 예외가 전파되지 않아야 한다")
        void sendVerificationCodeWithMessagingException() throws MessagingException {
            // given - MimeMessage that causes MessagingException when setContent(Multipart) is called
            MimeMessage badMimeMessage = mock(MimeMessage.class);
            doThrow(new MessagingException("Failed to set content"))
                    .when(badMimeMessage).setContent(any(jakarta.mail.Multipart.class));
            given(templateEngine.process(eq("email/verification-code"), any(Context.class)))
                    .willReturn("<html>인증코드</html>");
            given(mailSender.createMimeMessage()).willReturn(badMimeMessage);

            // when & then - should not throw because sendHtmlEmail catches MessagingException
            assertThatCode(() -> smtpEmailSender.sendVerificationCode("test@example.com", "123456"))
                    .doesNotThrowAnyException();
            verify(mailSender, never()).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("새 기기 로그인 알림")
    class SendNewDeviceLoginAlert {

        @Test
        @DisplayName("새 기기 로그인 알림이 정상 발송되어야 한다")
        void sendNewDeviceLoginAlertSuccess() {
            // given
            given(templateEngine.process(eq("email/security-alert"), any(Context.class)))
                    .willReturn("<html>알림</html>");
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // when & then
            assertThatCode(() -> smtpEmailSender.sendNewDeviceLoginAlert(
                    "test@example.com", "Chrome on Windows", "127.0.0.1", "Seoul, KR", "2025-01-01 12:00:00"
            )).doesNotThrowAnyException();
            verify(mailSender).send(mimeMessage);
        }

        @Test
        @DisplayName("위치가 null이어도 정상 발송되어야 한다")
        void sendNewDeviceLoginAlertWithNullLocation() {
            // given
            given(templateEngine.process(eq("email/security-alert"), any(Context.class)))
                    .willReturn("<html>알림</html>");
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // when & then
            assertThatCode(() -> smtpEmailSender.sendNewDeviceLoginAlert(
                    "test@example.com", "Chrome on Windows", "127.0.0.1", null, "2025-01-01 12:00:00"
            )).doesNotThrowAnyException();
            verify(mailSender).send(mimeMessage);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 알림")
    class SendPasswordChangedAlert {

        @Test
        @DisplayName("비밀번호 변경 알림이 정상 발송되어야 한다")
        void sendPasswordChangedAlertSuccess() {
            // given
            given(templateEngine.process(eq("email/security-alert"), any(Context.class)))
                    .willReturn("<html>알림</html>");
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // when & then
            assertThatCode(() -> smtpEmailSender.sendPasswordChangedAlert("test@example.com", "2025-01-01 12:00:00"))
                    .doesNotThrowAnyException();
            verify(mailSender).send(mimeMessage);
        }
    }

    @Nested
    @DisplayName("계정 연동 알림")
    class SendAccountLinkedAlert {

        @Test
        @DisplayName("계정 연동 알림이 정상 발송되어야 한다")
        void sendAccountLinkedAlertSuccess() {
            // given
            given(templateEngine.process(eq("email/security-alert"), any(Context.class)))
                    .willReturn("<html>알림</html>");
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // when & then
            assertThatCode(() -> smtpEmailSender.sendAccountLinkedAlert("test@example.com", "Google", "2025-01-01 12:00:00"))
                    .doesNotThrowAnyException();
            verify(mailSender).send(mimeMessage);
        }
    }

    @Nested
    @DisplayName("계정 연동 해제 알림")
    class SendAccountUnlinkedAlert {

        @Test
        @DisplayName("계정 연동 해제 알림이 정상 발송되어야 한다")
        void sendAccountUnlinkedAlertSuccess() {
            // given
            given(templateEngine.process(eq("email/security-alert"), any(Context.class)))
                    .willReturn("<html>알림</html>");
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // when & then
            assertThatCode(() -> smtpEmailSender.sendAccountUnlinkedAlert("test@example.com", "Google", "2025-01-01 12:00:00"))
                    .doesNotThrowAnyException();
            verify(mailSender).send(mimeMessage);
        }
    }
}
