package com.jay.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserTwoFactor;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.TwoFactorSetupResponse;
import com.jay.auth.dto.response.TwoFactorStatusResponse;
import com.jay.auth.exception.TwoFactorException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserTwoFactorRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.secret.SecretGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TotpServiceTest {

    @InjectMocks
    private TotpService totpService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTwoFactorRepository userTwoFactorRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("2FA 설정 (setupTwoFactor)")
    class SetupTwoFactor {

        @Test
        @DisplayName("정상적으로 2FA를 설정하면 QR 코드를 반환해야 한다")
        void setupTwoFactorSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.empty());
            given(encryptionService.encrypt(anyString())).willReturn("encrypted_secret");
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@email.com");
            given(userTwoFactorRepository.save(any(UserTwoFactor.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            TwoFactorSetupResponse response = totpService.setupTwoFactor(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getSecret()).isNotNull();
            assertThat(response.getQrCodeDataUrl()).startsWith("data:image/png;base64,");
            verify(userTwoFactorRepository).save(any(UserTwoFactor.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 경우 실패해야 한다")
        void setupTwoFactorFailsUserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> totpService.setupTwoFactor(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("이미 설정된 2FA가 있어도 새 시크릿으로 재설정할 수 있어야 한다")
        void setupTwoFactorOverwritesExisting() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor existing = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(existing, "secretEnc", "old_secret");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(existing));
            given(encryptionService.encrypt(anyString())).willReturn("new_encrypted_secret");
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@email.com");
            given(userTwoFactorRepository.save(any(UserTwoFactor.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            TwoFactorSetupResponse response = totpService.setupTwoFactor(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getSecret()).isNotNull();
            verify(userTwoFactorRepository).save(any(UserTwoFactor.class));
        }
    }

    @Nested
    @DisplayName("2FA 활성화 (enableTwoFactor)")
    class EnableTwoFactor {

        @Test
        @DisplayName("올바른 코드로 2FA를 활성화하면 백업 코드를 반환해야 한다")
        void enableTwoFactorSuccess() throws Exception {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", false);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");

            // Mock the codeVerifier inside TotpService via reflection
            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "123456")).willReturn(true);

            given(objectMapper.writeValueAsString(any(List.class))).willReturn("[\"12345678\"]");
            given(encryptionService.encrypt("[\"12345678\"]")).willReturn("encrypted_backup");

            // when
            List<String> backupCodes = totpService.enableTwoFactor(userId, "123456");

            // then
            assertThat(backupCodes).isNotNull();
            assertThat(backupCodes).hasSize(8);
        }

        @Test
        @DisplayName("잘못된 코드로 활성화 시 실패해야 한다")
        void enableTwoFactorFailsWithInvalidCode() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", false);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");

            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "000000")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> totpService.enableTwoFactor(userId, "000000"))
                    .isInstanceOf(TwoFactorException.class);
        }

        @Test
        @DisplayName("2FA 미설정 상태에서 활성화 시 실패해야 한다")
        void enableTwoFactorFailsWhenNotSetup() {
            // given
            Long userId = 1L;
            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> totpService.enableTwoFactor(userId, "123456"))
                    .isInstanceOf(TwoFactorException.class);
        }

        @Test
        @DisplayName("이미 활성화된 경우 실패해야 한다")
        void enableTwoFactorFailsWhenAlreadyEnabled() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "enabled", true);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));

            // when & then
            assertThatThrownBy(() -> totpService.enableTwoFactor(userId, "123456"))
                    .isInstanceOf(TwoFactorException.class);
        }
    }

    @Nested
    @DisplayName("2FA 비활성화 (disableTwoFactor)")
    class DisableTwoFactor {

        @Test
        @DisplayName("올바른 코드로 2FA를 비활성화해야 한다")
        void disableTwoFactorSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", true);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");

            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "123456")).willReturn(true);

            // when
            totpService.disableTwoFactor(userId, "123456");

            // then
            assertThat(twoFactor.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("잘못된 코드로 비활성화 시 실패해야 한다")
        void disableTwoFactorFailsWithInvalidCode() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", true);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");

            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "000000")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> totpService.disableTwoFactor(userId, "000000"))
                    .isInstanceOf(TwoFactorException.class);
        }
    }

    @Nested
    @DisplayName("코드 검증 (verifyCode)")
    class VerifyCode {

        @Test
        @DisplayName("정상 TOTP 코드로 검증이 성공해야 한다")
        void verifyCodeWithValidTotpCode() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", true);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");

            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "123456")).willReturn(true);

            // when
            boolean result = totpService.verifyCode(userId, "123456");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("백업 코드로 검증이 성공해야 한다")
        void verifyCodeWithBackupCode() throws Exception {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "id", 1L);
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", true);
            setField(twoFactor, "backupCodesEnc", "encrypted_backup");

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");
            given(encryptionService.decrypt("encrypted_backup")).willReturn("[\"12345678\",\"87654321\"]");

            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "12345678")).willReturn(false);

            List<String> backupCodes = new java.util.ArrayList<>(List.of("12345678", "87654321"));
            given(objectMapper.readValue(eq("[\"12345678\",\"87654321\"]"), any(TypeReference.class)))
                    .willReturn(backupCodes);
            given(objectMapper.writeValueAsString(any(List.class))).willReturn("[\"87654321\"]");
            given(encryptionService.encrypt("[\"87654321\"]")).willReturn("new_encrypted_backup");

            // when
            boolean result = totpService.verifyCode(userId, "12345678");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("2FA가 비활성화된 경우 항상 true를 반환해야 한다")
        void verifyCodeWhenTwoFactorNotEnabled() {
            // given
            Long userId = 1L;
            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.empty());

            // when
            boolean result = totpService.verifyCode(userId, "123456");

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("2FA 상태 조회 (getTwoFactorStatus)")
    class GetTwoFactorStatus {

        @Test
        @DisplayName("활성화 상태를 올바르게 반환해야 한다")
        void getTwoFactorStatusEnabled() throws Exception {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "enabled", true);
            setField(twoFactor, "backupCodesEnc", "encrypted_backup");
            setField(twoFactor, "lastUsedAt", LocalDateTime.now());

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_backup")).willReturn("[\"code1\",\"code2\",\"code3\"]");
            given(objectMapper.readValue(eq("[\"code1\",\"code2\",\"code3\"]"), any(TypeReference.class)))
                    .willReturn(List.of("code1", "code2", "code3"));

            // when
            TwoFactorStatusResponse response = totpService.getTwoFactorStatus(userId);

            // then
            assertThat(response.isEnabled()).isTrue();
            assertThat(response.getRemainingBackupCodes()).isEqualTo(3);
            assertThat(response.getLastUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("비활성화 상태를 올바르게 반환해야 한다")
        void getTwoFactorStatusDisabled() {
            // given
            Long userId = 1L;
            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.empty());

            // when
            TwoFactorStatusResponse response = totpService.getTwoFactorStatus(userId);

            // then
            assertThat(response.isEnabled()).isFalse();
            assertThat(response.getRemainingBackupCodes()).isEqualTo(0);
            assertThat(response.getLastUsedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("백업 코드 재생성 (regenerateBackupCodes)")
    class RegenerateBackupCodes {

        @Test
        @DisplayName("올바른 코드로 백업 코드를 재생성해야 한다")
        void regenerateBackupCodesSuccess() throws Exception {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            UserTwoFactor twoFactor = UserTwoFactor.builder()
                    .user(user)
                    .build();
            setField(twoFactor, "secretEnc", "encrypted_secret");
            setField(twoFactor, "enabled", true);

            given(userTwoFactorRepository.findByUserId(userId)).willReturn(Optional.of(twoFactor));
            given(encryptionService.decrypt("encrypted_secret")).willReturn("plain_secret");

            CodeVerifier mockCodeVerifier = org.mockito.Mockito.mock(CodeVerifier.class);
            setField(totpService, "codeVerifier", mockCodeVerifier);
            given(mockCodeVerifier.isValidCode("plain_secret", "123456")).willReturn(true);

            given(objectMapper.writeValueAsString(any(List.class))).willReturn("[\"newcode1\"]");
            given(encryptionService.encrypt("[\"newcode1\"]")).willReturn("new_encrypted_backup");

            // when
            List<String> newBackupCodes = totpService.regenerateBackupCodes(userId, "123456");

            // then
            assertThat(newBackupCodes).isNotNull();
            assertThat(newBackupCodes).hasSize(8);
        }
    }

    @Nested
    @DisplayName("2FA 필요 여부 확인 (isTwoFactorRequired)")
    class IsTwoFactorRequired {

        @Test
        @DisplayName("2FA가 활성화된 경우 true를 반환해야 한다")
        void isTwoFactorRequiredTrue() {
            // given
            Long userId = 1L;
            given(userTwoFactorRepository.existsByUserIdAndEnabled(userId, true)).willReturn(true);

            // when
            boolean result = totpService.isTwoFactorRequired(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("2FA가 비활성화된 경우 false를 반환해야 한다")
        void isTwoFactorRequiredFalse() {
            // given
            Long userId = 1L;
            given(userTwoFactorRepository.existsByUserIdAndEnabled(userId, true)).willReturn(false);

            // when
            boolean result = totpService.isTwoFactorRequired(userId);

            // then
            assertThat(result).isFalse();
        }
    }

    // Helper methods
    private User createUser(Long id, String emailEnc) {
        User user = User.builder()
                .emailEnc(emailEnc)
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", id);
        setField(user, "userUuid", "uuid-" + id);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
