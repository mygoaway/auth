package com.jay.auth.service;

import com.jay.auth.domain.entity.EmailVerification;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.EmailVerificationRepository;
import com.jay.auth.service.metrics.AuthMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLoginVerificationService 테스트")
class PostLoginVerificationServiceTest {

    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private EmailSender emailSender;
    @Mock private EncryptionService encryptionService;
    @Mock private TrustedDeviceService trustedDeviceService;
    @Mock private TotpService totpService;
    @Mock private AuthMetrics authMetrics;

    private PostLoginVerificationService service;

    @BeforeEach
    void setUp() {
        service = new PostLoginVerificationService(
                emailVerificationRepository, emailSender, encryptionService,
                trustedDeviceService, totpService, new SimpleMeterRegistry(), authMetrics);
    }

    @Nested
    @DisplayName("isVerificationRequired()")
    class IsVerificationRequired {

        @Test
        @DisplayName("2FA 활성화 계정은 재인증 불필요")
        void twoFactorEnabledSkipsVerification() {
            given(totpService.isTwoFactorRequired(1L)).willReturn(true);

            assertThat(service.isVerificationRequired(1L, "device-id")).isFalse();
        }

        @Test
        @DisplayName("신뢰 기기에서 로그인하면 재인증 불필요")
        void trustedDeviceSkipsVerification() {
            given(totpService.isTwoFactorRequired(1L)).willReturn(false);
            given(trustedDeviceService.isTrustedDevice(1L, "trusted-device")).willReturn(true);

            assertThat(service.isVerificationRequired(1L, "trusted-device")).isFalse();
        }

        @Test
        @DisplayName("새 기기 + 2FA 미설정이면 재인증 필요")
        void newDeviceRequiresVerification() {
            given(totpService.isTwoFactorRequired(1L)).willReturn(false);
            given(trustedDeviceService.isTrustedDevice(1L, "new-device")).willReturn(false);

            assertThat(service.isVerificationRequired(1L, "new-device")).isTrue();
        }

        @Test
        @DisplayName("deviceId가 null이면 재인증 필요")
        void nullDeviceIdRequiresVerification() {
            given(totpService.isTwoFactorRequired(1L)).willReturn(false);

            assertThat(service.isVerificationRequired(1L, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("sendVerificationCode()")
    class SendVerificationCode {

        @Test
        @DisplayName("인증 코드를 발송하고 tokenId를 반환한다")
        void sendsCodeAndReturnsTokenId() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            ReflectionTestUtils.setField(verification, "tokenId", "test-token-id");

            given(emailVerificationRepository.save(any())).willReturn(verification);

            String tokenId = service.sendVerificationCode("user@example.com");

            assertThat(tokenId).isEqualTo("test-token-id");
            then(emailSender).should().sendPostLoginVerificationCode(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyCode()")
    class VerifyCode {

        @Test
        @DisplayName("유효한 코드 인증 시 tokenId를 반환한다")
        void verifyValidCode() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();
            ReflectionTestUtils.setField(verification, "tokenId", "return-token-id");

            given(emailVerificationRepository
                    .findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse("enc-email", VerificationType.POST_LOGIN_VERIFICATION))
                    .willReturn(Optional.of(verification));
            given(emailVerificationRepository.save(any())).willReturn(verification);

            String tokenId = service.verifyCode("user@example.com", "123456");

            assertThat(tokenId).isEqualTo("return-token-id");
        }

        @Test
        @DisplayName("인증 레코드가 없으면 InvalidVerificationException")
        void noRecordThrows() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");
            given(emailVerificationRepository
                    .findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(anyString(), any()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyCode("user@example.com", "000000"))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("코드 불일치 시 InvalidVerificationException")
        void codeMismatchThrows() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("999999")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            given(emailVerificationRepository
                    .findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse("enc-email", VerificationType.POST_LOGIN_VERIFICATION))
                    .willReturn(Optional.of(verification));

            assertThatThrownBy(() -> service.verifyCode("user@example.com", "123456"))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("만료된 코드 — InvalidVerificationException(codeExpired)")
        void expiredCodeThrows() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().minusMinutes(1)) // 이미 만료
                    .build();

            given(emailVerificationRepository
                    .findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse("enc-email", VerificationType.POST_LOGIN_VERIFICATION))
                    .willReturn(Optional.of(verification));

            assertThatThrownBy(() -> service.verifyCode("user@example.com", "123456"))
                    .isInstanceOf(InvalidVerificationException.class);
        }
    }

    @Nested
    @DisplayName("isVerifiedByTokenId()")
    class IsVerifiedByTokenId {

        @Test
        @DisplayName("검증 완료 레코드 — true 반환")
        void verifiedRecordReturnsTrue() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();
            ReflectionTestUtils.setField(verification, "tokenId", "valid-token");
            ReflectionTestUtils.setField(verification, "isVerified", true);

            given(emailVerificationRepository.findByTokenId("valid-token"))
                    .willReturn(Optional.of(verification));

            assertThat(service.isVerifiedByTokenId("valid-token", "user@example.com")).isTrue();
        }

        @Test
        @DisplayName("레코드 없음 — false 반환")
        void noRecordReturnsFalse() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");
            given(emailVerificationRepository.findByTokenId("missing-token"))
                    .willReturn(Optional.empty());

            assertThat(service.isVerifiedByTokenId("missing-token", "user@example.com")).isFalse();
        }

        @Test
        @DisplayName("isVerified=false — false 반환")
        void notVerifiedReturnsFalse() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();
            ReflectionTestUtils.setField(verification, "tokenId", "token-not-verified");
            // isVerified는 기본값 false

            given(emailVerificationRepository.findByTokenId("token-not-verified"))
                    .willReturn(Optional.of(verification));

            assertThat(service.isVerifiedByTokenId("token-not-verified", "user@example.com")).isFalse();
        }

        @Test
        @DisplayName("만료 레코드 — false 반환")
        void expiredRecordReturnsFalse() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().minusMinutes(1)) // 만료됨
                    .build();
            ReflectionTestUtils.setField(verification, "tokenId", "expired-token");
            ReflectionTestUtils.setField(verification, "isVerified", true);

            given(emailVerificationRepository.findByTokenId("expired-token"))
                    .willReturn(Optional.of(verification));

            assertThat(service.isVerifiedByTokenId("expired-token", "user@example.com")).isFalse();
        }

        @Test
        @DisplayName("email 불일치 — false 반환")
        void emailMismatchReturnsFalse() {
            given(encryptionService.encryptForSearch(anyString())).willReturn("enc-other-email");

            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email") // 다른 이메일
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();
            ReflectionTestUtils.setField(verification, "tokenId", "token-email-mismatch");
            ReflectionTestUtils.setField(verification, "isVerified", true);

            given(emailVerificationRepository.findByTokenId("token-email-mismatch"))
                    .willReturn(Optional.of(verification));

            assertThat(service.isVerifiedByTokenId("token-email-mismatch", "other@example.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteVerificationByTokenId()")
    class DeleteVerificationByTokenId {

        @Test
        @DisplayName("레코드 있음 — emailVerificationRepository.delete() 호출")
        void deletesWhenFound() {
            EmailVerification verification = EmailVerification.builder()
                    .emailLowerEnc("enc-email")
                    .verificationCode("123456")
                    .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            given(emailVerificationRepository.findByTokenId("token-to-delete"))
                    .willReturn(Optional.of(verification));

            service.deleteVerificationByTokenId("token-to-delete");

            then(emailVerificationRepository).should().delete(verification);
        }

        @Test
        @DisplayName("레코드 없음 — delete 미호출")
        void noDeleteWhenNotFound() {
            given(emailVerificationRepository.findByTokenId("missing-token"))
                    .willReturn(Optional.empty());

            service.deleteVerificationByTokenId("missing-token");

            then(emailVerificationRepository).should(org.mockito.Mockito.never()).delete(any());
        }
    }
}
