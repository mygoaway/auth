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
    }
}
