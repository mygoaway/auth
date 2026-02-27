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
import static org.mockito.Mockito.never;
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

    @Nested
    @DisplayName("tokenId로 인증 완료 여부 확인")
    class IsVerifiedByTokenId {

        @Test
        @DisplayName("정상적으로 검증된 tokenId는 true를 반환해야 한다")
        void isVerifiedByTokenIdReturnsTrue() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;
            EmailVerification verification = createVerification("token-123", "123456", type, true);

            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByTokenId("token-123"))
                    .willReturn(Optional.of(verification));

            // when
            boolean result = emailVerificationService.isVerifiedByTokenId("token-123", email, type);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("tokenId가 없으면 false를 반환해야 한다")
        void isVerifiedByTokenIdReturnsFalseWhenTokenNotFound() {
            // given
            given(encryptionService.encryptForSearch("test@example.com")).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByTokenId("unknown-token"))
                    .willReturn(Optional.empty());

            // when
            boolean result = emailVerificationService.isVerifiedByTokenId(
                    "unknown-token", "test@example.com", VerificationType.SIGNUP);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이메일이 다르면 false를 반환해야 한다")
        void isVerifiedByTokenIdReturnsFalseWhenEmailMismatch() {
            // given
            VerificationType type = VerificationType.SIGNUP;
            EmailVerification verification = createVerification("token-123", "123456", type, true);
            // verification의 emailLowerEnc = "enc_email_lower"

            given(encryptionService.encryptForSearch("other@example.com")).willReturn("enc_other_lower");
            given(emailVerificationRepository.findByTokenId("token-123"))
                    .willReturn(Optional.of(verification));

            // when - emailLowerEnc("enc_email_lower") != encryptForSearch("other") = "enc_other_lower"
            boolean result = emailVerificationService.isVerifiedByTokenId(
                    "token-123", "other@example.com", type);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("인증 타입이 다르면 false를 반환해야 한다")
        void isVerifiedByTokenIdReturnsFalseWhenTypeMismatch() {
            // given
            EmailVerification verification = createVerification("token-123", "123456", VerificationType.SIGNUP, true);

            given(encryptionService.encryptForSearch("test@example.com")).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByTokenId("token-123"))
                    .willReturn(Optional.of(verification));

            // when - verification type is SIGNUP, but requesting PASSWORD_RESET
            boolean result = emailVerificationService.isVerifiedByTokenId(
                    "token-123", "test@example.com", VerificationType.PASSWORD_RESET);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("인증 미완료 상태이면 false를 반환해야 한다")
        void isVerifiedByTokenIdReturnsFalseWhenNotVerified() {
            // given
            VerificationType type = VerificationType.SIGNUP;
            EmailVerification verification = createVerification("token-123", "123456", type, false);

            given(encryptionService.encryptForSearch("test@example.com")).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByTokenId("token-123"))
                    .willReturn(Optional.of(verification));

            // when
            boolean result = emailVerificationService.isVerifiedByTokenId(
                    "token-123", "test@example.com", type);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("만료된 경우 false를 반환해야 한다")
        void isVerifiedByTokenIdReturnsFalseWhenExpired() {
            // given
            VerificationType type = VerificationType.SIGNUP;
            EmailVerification verification = createExpiredVerification("token-123", "123456", type);
            setField(verification, "isVerified", true);

            given(encryptionService.encryptForSearch("test@example.com")).willReturn("enc_email_lower");
            given(emailVerificationRepository.findByTokenId("token-123"))
                    .willReturn(Optional.of(verification));

            // when
            boolean result = emailVerificationService.isVerifiedByTokenId(
                    "token-123", "test@example.com", type);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("인증 레코드 삭제")
    class DeleteVerification {

        @Test
        @DisplayName("이메일과 타입으로 인증 레코드가 삭제되어야 한다")
        void deleteVerificationByEmailAndType() {
            // given
            String email = "test@example.com";
            VerificationType type = VerificationType.SIGNUP;
            given(encryptionService.encryptForSearch(email)).willReturn("enc_email_lower");

            // when
            emailVerificationService.deleteVerification(email, type);

            // then
            verify(emailVerificationRepository).deleteByEmailAndType("enc_email_lower", type);
        }

        @Test
        @DisplayName("tokenId로 인증 레코드가 삭제되어야 한다 (존재하는 경우)")
        void deleteVerificationByTokenIdWhenExists() {
            // given
            EmailVerification verification = createVerification("token-123", "123456", VerificationType.SIGNUP, true);
            given(emailVerificationRepository.findByTokenId("token-123"))
                    .willReturn(Optional.of(verification));

            // when
            emailVerificationService.deleteVerificationByTokenId("token-123");

            // then
            verify(emailVerificationRepository).delete(verification);
        }

        @Test
        @DisplayName("tokenId에 해당하는 레코드가 없으면 삭제를 호출하지 않아야 한다")
        void deleteVerificationByTokenIdWhenNotExists() {
            // given
            given(emailVerificationRepository.findByTokenId("unknown-token"))
                    .willReturn(Optional.empty());

            // when
            emailVerificationService.deleteVerificationByTokenId("unknown-token");

            // then
            verify(emailVerificationRepository, never()).delete(any(EmailVerification.class));
        }
    }

    @Nested
    @DisplayName("만료 시간 조회")
    class GetExpirationMinutes {

        @Test
        @DisplayName("만료 시간이 10분이어야 한다")
        void expirationMinutesIs10() {
            assertThat(emailVerificationService.getExpirationMinutes()).isEqualTo(10);
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
