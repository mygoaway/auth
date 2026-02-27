package com.jay.auth.service;

import com.jay.auth.domain.entity.EmailVerification;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.EmailVerificationRepository;
import com.jay.auth.service.metrics.AuthMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuthMetrics authMetrics;

    @Nested
    @DisplayName("인증 코드 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("인증 코드 발송이 성공해야 한다")
        void sendVerificationCodeSuccess() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.save(any(EmailVerification.class)))
                    .willAnswer(invocation -> {
                        EmailVerification v = invocation.getArgument(0);
                        setField(v, "tokenId", "token-123");
                        return v;
                    });

            // when
            String tokenId = emailVerificationService.sendVerificationCode(email, type);

            // then
            assertThat(tokenId).isEqualTo("token-123");
            verify(emailVerificationRepository).deleteByEmailAndType("enc_email_lower", type);
            verify(emailSender).sendVerificationCode(anyString(), anyString());
            verify(authMetrics).recordEmailVerificationSent("SIGNUP");
        }
    }

    @Nested
    @DisplayName("인증 코드 확인")
    class VerifyCode {

        @Test
        @DisplayName("인증 코드 확인이 성공해야 한다")
        void verifyCodeSuccess() {
            // given
            String email = "test@example.com";
            String code = "123456";
            VerificationType type = VerificationType.SIGNUP;

            EmailVerification verification = createVerification("token-123", code, type, false);

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(
                    "enc_email_lower", type)).willReturn(Optional.of(verification));
            given(emailVerificationRepository.save(any(EmailVerification.class))).willReturn(verification);

            // when
            String tokenId = emailVerificationService.verifyCode(email, code, type);

            // then
            assertThat(tokenId).isEqualTo("token-123");
            assertThat(verification.getIsVerified()).isTrue();
            verify(authMetrics).recordEmailVerificationSuccess("SIGNUP");
        }

        @Test
        @DisplayName("인증 요청이 없으면 실패해야 한다")
        void verifyCodeFailsWithNoRequest() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(
                    "enc_email_lower", type)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456", type))
                    .isInstanceOf(InvalidVerificationException.class);
            verify(authMetrics).recordEmailVerificationFailure("SIGNUP", "not_found");
        }

        @Test
        @DisplayName("만료된 인증 코드로 확인 시 실패해야 한다")
        void verifyCodeFailsWithExpiredCode() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;

            EmailVerification verification = createExpiredVerification("token-123", "123456", type);

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(
                    "enc_email_lower", type)).willReturn(Optional.of(verification));

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456", type))
                    .isInstanceOf(InvalidVerificationException.class);
            verify(authMetrics).recordEmailVerificationFailure("SIGNUP", "expired");
        }

        @Test
        @DisplayName("잘못된 인증 코드로 확인 시 실패해야 한다")
        void verifyCodeFailsWithWrongCode() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;

            EmailVerification verification = createVerification("token-123", "123456", type, false);

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(
                    "enc_email_lower", type)).willReturn(Optional.of(verification));

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "654321", type))
                    .isInstanceOf(InvalidVerificationException.class);
            verify(authMetrics).recordEmailVerificationFailure("SIGNUP", "mismatch");
        }
    }

    @Nested
    @DisplayName("인증 완료 여부 확인")
    class IsVerified {

        @Test
        @DisplayName("인증 완료된 경우 true를 반환해야 한다")
        void isVerifiedReturnsTrue() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;
            EmailVerification verification = createVerification("token-123", "123456", type, true);

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findVerifiedAndNotExpired(
                    anyString(), any(VerificationType.class), any(LocalDateTime.class)))
                    .willReturn(Optional.of(verification));

            // when
            boolean result = emailVerificationService.isVerified(email, type);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("인증 미완료된 경우 false를 반환해야 한다")
        void isVerifiedReturnsFalse() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findVerifiedAndNotExpired(
                    anyString(), any(VerificationType.class), any(LocalDateTime.class)))
                    .willReturn(Optional.empty());

            // when
            boolean result = emailVerificationService.isVerified(email, type);

            // then
            assertThat(result).isFalse();
        }
    }

    // Helper methods
    private EmailVerification createVerification(String tokenId, String code, VerificationType type, boolean verified) {
        EmailVerification verification = EmailVerification.builder()
                .emailLowerEnc("enc_email_lower")
                .verificationCode(code)
                .verificationType(type)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        setField(verification, "tokenId", tokenId);
        if (verified) {
            setField(verification, "isVerified", true);
            setField(verification, "verifiedAt", LocalDateTime.now());
        }
        return verification;
    }

    private EmailVerification createExpiredVerification(String tokenId, String code, VerificationType type) {
        EmailVerification verification = EmailVerification.builder()
                .emailLowerEnc("enc_email_lower")
                .verificationCode(code)
                .verificationType(type)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        setField(verification, "tokenId", tokenId);
        return verification;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
