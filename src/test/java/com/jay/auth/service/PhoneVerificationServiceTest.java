package com.jay.auth.service;

import com.jay.auth.domain.entity.PhoneVerification;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.PhoneVerificationRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    @InjectMocks
    private PhoneVerificationService phoneVerificationService;

    @Mock
    private PhoneVerificationRepository phoneVerificationRepository;

    @Mock
    private EncryptionService encryptionService;

    @Nested
    @DisplayName("인증 코드 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("인증 코드 발송이 성공해야 한다")
        void sendVerificationCodeSuccess() {
            // given
            String phone = "010-1234-5678";
            given(encryptionService.encryptPhone(phone)).willReturn("enc_phone");
            given(encryptionService.encryptForSearch(phone)).willReturn("enc_phone_lower");
            given(phoneVerificationRepository.save(any(PhoneVerification.class)))
                    .willAnswer(invocation -> {
                        PhoneVerification v = invocation.getArgument(0);
                        setField(v, "tokenId", "token-123");
                        return v;
                    });

            // when
            String tokenId = phoneVerificationService.sendVerificationCode(phone);

            // then
            assertThat(tokenId).isEqualTo("token-123");
            verify(phoneVerificationRepository).deleteByPhoneLowerEnc("enc_phone_lower");
            verify(phoneVerificationRepository).save(any(PhoneVerification.class));
        }
    }

    @Nested
    @DisplayName("인증 코드 확인")
    class VerifyCode {

        @Test
        @DisplayName("인증 코드 확인이 성공해야 한다")
        void verifyCodeSuccess() {
            // given
            String phone = "010-1234-5678";
            String code = "123456";

            PhoneVerification verification = createVerification("token-123", code, false);

            given(encryptionService.encryptForSearch(phone)).willReturn("enc_phone_lower");
            given(phoneVerificationRepository.findByPhoneLowerEncAndIsVerifiedFalse("enc_phone_lower"))
                    .willReturn(Optional.of(verification));
            given(phoneVerificationRepository.save(any(PhoneVerification.class))).willReturn(verification);

            // when
            String tokenId = phoneVerificationService.verifyCode(phone, code);

            // then
            assertThat(tokenId).isEqualTo("token-123");
            assertThat(verification.getIsVerified()).isTrue();
            verify(phoneVerificationRepository).save(verification);
        }

        @Test
        @DisplayName("인증 요청이 없으면 실패해야 한다")
        void verifyCodeFailsWithNoRequest() {
            // given
            String phone = "010-1234-5678";
            String code = "123456";

            given(encryptionService.encryptForSearch(phone)).willReturn("enc_phone_lower");
            given(phoneVerificationRepository.findByPhoneLowerEncAndIsVerifiedFalse("enc_phone_lower"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> phoneVerificationService.verifyCode(phone, code))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("만료된 인증 코드로 확인 시 실패해야 한다")
        void verifyCodeFailsWithExpiredCode() {
            // given
            String phone = "010-1234-5678";
            String code = "123456";

            PhoneVerification verification = createExpiredVerification("token-123", code);

            given(encryptionService.encryptForSearch(phone)).willReturn("enc_phone_lower");
            given(phoneVerificationRepository.findByPhoneLowerEncAndIsVerifiedFalse("enc_phone_lower"))
                    .willReturn(Optional.of(verification));

            // when & then
            assertThatThrownBy(() -> phoneVerificationService.verifyCode(phone, code))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("잘못된 인증 코드로 확인 시 실패해야 한다")
        void verifyCodeFailsWithWrongCode() {
            // given
            String phone = "010-1234-5678";
            String correctCode = "123456";
            String wrongCode = "654321";

            PhoneVerification verification = createVerification("token-123", correctCode, false);

            given(encryptionService.encryptForSearch(phone)).willReturn("enc_phone_lower");
            given(phoneVerificationRepository.findByPhoneLowerEncAndIsVerifiedFalse("enc_phone_lower"))
                    .willReturn(Optional.of(verification));

            // when & then
            assertThatThrownBy(() -> phoneVerificationService.verifyCode(phone, wrongCode))
                    .isInstanceOf(InvalidVerificationException.class);
        }
    }

    @Nested
    @DisplayName("tokenId 유효성 검증")
    class IsValidTokenId {

        @Test
        @DisplayName("유효한 tokenId는 true를 반환해야 한다")
        void validTokenIdReturnsTrue() {
            // given
            String tokenId = "token-123";
            PhoneVerification verification = createVerification(tokenId, "123456", true);

            given(phoneVerificationRepository.findByTokenIdAndVerifiedTrue(eq(tokenId), any(LocalDateTime.class)))
                    .willReturn(Optional.of(verification));

            // when
            boolean result = phoneVerificationService.isValidTokenId(tokenId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효하지 않은 tokenId는 false를 반환해야 한다")
        void invalidTokenIdReturnsFalse() {
            // given
            String tokenId = "invalid-token";

            given(phoneVerificationRepository.findByTokenIdAndVerifiedTrue(eq(tokenId), any(LocalDateTime.class)))
                    .willReturn(Optional.empty());

            // when
            boolean result = phoneVerificationService.isValidTokenId(tokenId);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("인증 레코드 삭제")
    class DeleteVerificationByTokenId {

        @Test
        @DisplayName("tokenId로 인증 레코드 삭제가 성공해야 한다")
        void deleteVerificationByTokenIdSuccess() {
            // given
            String tokenId = "token-123";
            PhoneVerification verification = createVerification(tokenId, "123456", true);

            given(phoneVerificationRepository.findByTokenId(tokenId))
                    .willReturn(Optional.of(verification));

            // when
            phoneVerificationService.deleteVerificationByTokenId(tokenId);

            // then
            verify(phoneVerificationRepository).delete(verification);
        }
    }

    // Helper methods
    private PhoneVerification createVerification(String tokenId, String code, boolean verified) {
        PhoneVerification verification = PhoneVerification.builder()
                .phoneEnc("enc_phone")
                .phoneLowerEnc("enc_phone_lower")
                .verificationCode(code)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
        setField(verification, "tokenId", tokenId);
        if (verified) {
            setField(verification, "isVerified", true);
            setField(verification, "verifiedAt", LocalDateTime.now());
        }
        return verification;
    }

    private PhoneVerification createExpiredVerification(String tokenId, String code) {
        PhoneVerification verification = PhoneVerification.builder()
                .phoneEnc("enc_phone")
                .phoneLowerEnc("enc_phone_lower")
                .verificationCode(code)
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
