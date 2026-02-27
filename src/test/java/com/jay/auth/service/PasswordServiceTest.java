package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.ChangePasswordRequest;
import com.jay.auth.dto.request.ResetPasswordRequest;
import com.jay.auth.dto.response.RecoveryAccountsResponse;
import com.jay.auth.exception.AuthenticationException;
import com.jay.auth.exception.InvalidPasswordException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.exception.PasswordPolicyException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @InjectMocks
    private PasswordService passwordService;

    @Mock
    private UserSignInInfoRepository userSignInInfoRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordUtil passwordUtil;
    @Mock
    private SecurityNotificationService securityNotificationService;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("비밀번호 변경이 성공해야 한다")
        void changePasswordSuccess() {
            // given
            User user = createUser(1L, "uuid-1234");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("OldPass@1234", "hashed_old")).willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(passwordPolicyService.isSameAsCurrentPassword("NewPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "NewPass@1234")).willReturn(false);
            given(passwordUtil.encode("NewPass@1234")).willReturn("hashed_new");

            ChangePasswordRequest request = createChangePasswordRequest("OldPass@1234", "NewPass@1234");

            // when
            passwordService.changePassword(1L, request);

            // then
            // verify password was updated (through entity method)
            verify(passwordUtil).encode("NewPass@1234");
            // verify password history was saved
            verify(passwordPolicyService).savePasswordHistory(user, "hashed_old");
            // verify all sessions were logged out
            verify(tokenService).logoutAll(1L, null);
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 실패해야 한다")
        void changePasswordFailsWithWrongCurrentPassword() {
            // given
            User user = createUser(1L, "uuid-1234");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("WrongPass@1", "hashed_old")).willReturn(false);

            ChangePasswordRequest request = createChangePasswordRequest("WrongPass@1", "NewPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.changePassword(1L, request))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        @DisplayName("새 비밀번호가 정책 미충족이면 실패해야 한다")
        void changePasswordFailsWithInvalidNewPassword() {
            // given
            User user = createUser(1L, "uuid-1234");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("OldPass@1234", "hashed_old")).willReturn(true);
            given(passwordUtil.isValidPassword("weak")).willReturn(false);

            ChangePasswordRequest request = createChangePasswordRequest("OldPass@1234", "weak");

            // when & then
            assertThatThrownBy(() -> passwordService.changePassword(1L, request))
                    .isInstanceOf(InvalidPasswordException.class);
        }

        @Test
        @DisplayName("사용자가 없으면 실패해야 한다")
        void changePasswordFailsWithUserNotFound() {
            // given
            given(userSignInInfoRepository.findByUserId(999L)).willReturn(Optional.empty());

            ChangePasswordRequest request = createChangePasswordRequest("OldPass@1234", "NewPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.changePassword(999L, request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 실패해야 한다")
        void changePasswordFailsWhenSameAsCurrentPassword() {
            // given
            User user = createUser(1L, "uuid-1234");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("OldPass@1234", "hashed_old")).willReturn(true);
            given(passwordUtil.isValidPassword("OldPass@1234")).willReturn(true);
            given(passwordPolicyService.isSameAsCurrentPassword("OldPass@1234", "hashed_old")).willReturn(true);

            ChangePasswordRequest request = createChangePasswordRequest("OldPass@1234", "OldPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.changePassword(1L, request))
                    .isInstanceOf(PasswordPolicyException.class);
        }

        @Test
        @DisplayName("이전 비밀번호 재사용 시 실패해야 한다")
        void changePasswordFailsWhenPasswordReused() {
            // given
            User user = createUser(1L, "uuid-1234");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("OldPass@1234", "hashed_old")).willReturn(true);
            given(passwordUtil.isValidPassword("PrevPass@1234")).willReturn(true);
            given(passwordPolicyService.isSameAsCurrentPassword("PrevPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "PrevPass@1234")).willReturn(true);

            ChangePasswordRequest request = createChangePasswordRequest("OldPass@1234", "PrevPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.changePassword(1L, request))
                    .isInstanceOf(PasswordPolicyException.class);
        }

        @Test
        @DisplayName("비밀번호 변경 성공 시 보안 알림이 발송되어야 한다")
        void changePasswordSuccessNotifiesSecurity() {
            // given
            User user = createUser(1L, "uuid-1234");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("OldPass@1234", "hashed_old")).willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(passwordPolicyService.isSameAsCurrentPassword("NewPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "NewPass@1234")).willReturn(false);
            given(passwordUtil.encode("NewPass@1234")).willReturn("hashed_new");

            ChangePasswordRequest request = createChangePasswordRequest("OldPass@1234", "NewPass@1234");

            // when
            passwordService.changePassword(1L, request);

            // then
            verify(securityNotificationService).notifyPasswordChanged(1L);
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class ResetPassword {

        @Test
        @DisplayName("복구 이메일로 비밀번호 재설정이 성공해야 한다")
        void resetPasswordSuccess() {
            // given
            User user = createUser(1L, "uuid-1234");
            setField(user, "recoveryEmailLowerEnc", "enc_recovery_email_lower");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("login@email.com")).willReturn("enc_login_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_login_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_email_lower");
            given(passwordPolicyService.isSameAsCurrentPassword("NewPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "NewPass@1234")).willReturn(false);
            given(passwordUtil.encode("NewPass@1234")).willReturn("hashed_new");
            given(cacheManager.getCache("securityDashboard")).willReturn(cache);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "NewPass@1234");

            // when
            passwordService.resetPassword(request);

            // then
            verify(passwordPolicyService).savePasswordHistory(user, "hashed_old");
            verify(emailVerificationService).deleteVerificationByTokenId("token-123");
            verify(tokenService).logoutAll(1L, null);
        }

        @Test
        @DisplayName("복구 이메일 인증이 안 된 경우 실패해야 한다")
        void resetPasswordFailsWithoutVerification() {
            // given
            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(false);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "NewPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.resetPassword(request))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("로그인 이메일로 등록된 사용자가 없으면 실패해야 한다")
        void resetPasswordFailsWithUserNotFound() {
            // given
            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("unknown@email.com")).willReturn("enc_unknown_email");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_unknown_email"))
                    .willReturn(Optional.empty());

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "unknown@email.com", "NewPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.resetPassword(request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("새 비밀번호가 정책 미충족이면 실패해야 한다")
        void resetPasswordFailsWithInvalidPassword() {
            // given
            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("weak")).willReturn(false);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "weak");

            // when & then
            assertThatThrownBy(() -> passwordService.resetPassword(request))
                    .isInstanceOf(InvalidPasswordException.class);
        }

        @Test
        @DisplayName("복구 이메일이 계정의 복구 이메일과 불일치하면 실패해야 한다")
        void resetPasswordFailsWithRecoveryEmailMismatch() {
            // given
            User user = createUser(1L, "uuid-1234");
            setField(user, "recoveryEmailLowerEnc", "enc_other_recovery_email");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("login@email.com")).willReturn("enc_login_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_login_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_email_lower");
            // enc_recovery_email_lower != enc_other_recovery_email → mismatch

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "NewPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.resetPassword(request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 실패해야 한다")
        void resetPasswordFailsWhenSameAsCurrentPassword() {
            // given
            User user = createUser(1L, "uuid-1234");
            setField(user, "recoveryEmailLowerEnc", "enc_recovery_email_lower");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("OldPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("login@email.com")).willReturn("enc_login_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_login_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_email_lower");
            given(passwordPolicyService.isSameAsCurrentPassword("OldPass@1234", "hashed_old")).willReturn(true);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "OldPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.resetPassword(request))
                    .isInstanceOf(PasswordPolicyException.class);
        }

        @Test
        @DisplayName("이전 비밀번호 재사용 시 실패해야 한다")
        void resetPasswordFailsWhenPasswordReused() {
            // given
            User user = createUser(1L, "uuid-1234");
            setField(user, "recoveryEmailLowerEnc", "enc_recovery_email_lower");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("PrevPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("login@email.com")).willReturn("enc_login_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_login_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_email_lower");
            given(passwordPolicyService.isSameAsCurrentPassword("PrevPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "PrevPass@1234")).willReturn(true);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "PrevPass@1234");

            // when & then
            assertThatThrownBy(() -> passwordService.resetPassword(request))
                    .isInstanceOf(PasswordPolicyException.class);
        }

        @Test
        @DisplayName("잠금된 계정의 비밀번호 재설정 시 잠금이 해제되어야 한다")
        void resetPasswordUnlocksLockedAccount() {
            // given
            User user = createUser(1L, "uuid-1234");
            setField(user, "recoveryEmailLowerEnc", "enc_recovery_email_lower");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");
            // lock the account by simulating 5 failures
            for (int i = 0; i < 5; i++) {
                signInInfo.recordLoginFailure();
            }
            assertThat(signInInfo.isLocked()).isTrue();

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("login@email.com")).willReturn("enc_login_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_login_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_email_lower");
            given(passwordPolicyService.isSameAsCurrentPassword("NewPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "NewPass@1234")).willReturn(false);
            given(passwordUtil.encode("NewPass@1234")).willReturn("hashed_new");
            given(cacheManager.getCache("securityDashboard")).willReturn(cache);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "NewPass@1234");

            // when
            passwordService.resetPassword(request);

            // then
            assertThat(signInInfo.isLocked()).isFalse();
        }

        @Test
        @DisplayName("비밀번호 재설정 성공 시 캐시가 무효화되어야 한다")
        void resetPasswordEvictsCache() {
            // given
            User user = createUser(1L, "uuid-1234");
            setField(user, "recoveryEmailLowerEnc", "enc_recovery_email_lower");
            UserSignInInfo signInInfo = createSignInInfo(user, "hashed_old");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(passwordUtil.isValidPassword("NewPass@1234")).willReturn(true);
            given(encryptionService.encryptForSearch("login@email.com")).willReturn("enc_login_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_login_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_email_lower");
            given(passwordPolicyService.isSameAsCurrentPassword("NewPass@1234", "hashed_old")).willReturn(false);
            given(passwordPolicyService.isPasswordReused(1L, "NewPass@1234")).willReturn(false);
            given(passwordUtil.encode("NewPass@1234")).willReturn("hashed_new");
            given(cacheManager.getCache("securityDashboard")).willReturn(cache);

            ResetPasswordRequest request = createResetPasswordRequest("token-123", "recovery@email.com", "login@email.com", "NewPass@1234");

            // when
            passwordService.resetPassword(request);

            // then
            verify(cache).evict(1L);
        }
    }

    @Nested
    @DisplayName("복구 이메일로 계정 목록 조회")
    class GetAccountsByRecoveryEmail {

        @Test
        @DisplayName("복구 이메일로 연결된 계정 목록을 조회할 수 있어야 한다")
        void getAccountsSuccess() {
            // given
            User user1 = createUser(1L, "uuid-1");
            UserSignInInfo signInInfo1 = createSignInInfo(user1, "hash1");
            setField(signInInfo1, "loginEmailEnc", "enc_email1");

            User user2 = createUser(2L, "uuid-2");
            UserSignInInfo signInInfo2 = createSignInInfo(user2, "hash2");
            setField(signInInfo2, "loginEmailEnc", "enc_email2");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_lower");
            given(userSignInInfoRepository.findAllByRecoveryEmailLowerEncWithUser("enc_recovery_lower"))
                    .willReturn(List.of(signInInfo1, signInInfo2));
            given(encryptionService.decryptEmail("enc_email1")).willReturn("test1@email.com");
            given(encryptionService.decryptEmail("enc_email2")).willReturn("test2@email.com");

            // when
            RecoveryAccountsResponse response = passwordService.getAccountsByRecoveryEmail("token-123", "recovery@email.com");

            // then
            assertThat(response.getAccounts()).hasSize(2);
        }

        @Test
        @DisplayName("인증되지 않은 경우 실패해야 한다")
        void getAccountsFailsWithoutVerification() {
            // given
            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> passwordService.getAccountsByRecoveryEmail("token-123", "recovery@email.com"))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("복구 이메일로 연결된 계정이 없으면 실패해야 한다")
        void getAccountsFailsWhenNoAccountsFound() {
            // given
            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_lower");
            given(userSignInInfoRepository.findAllByRecoveryEmailLowerEncWithUser("enc_recovery_lower"))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> passwordService.getAccountsByRecoveryEmail("token-123", "recovery@email.com"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("이메일 마스킹이 올바르게 적용되어야 한다")
        void emailMaskingIsCorrect() {
            // given
            User user1 = createUser(1L, "uuid-1");
            UserSignInInfo signInInfo1 = createSignInInfo(user1, "hash1");
            setField(signInInfo1, "loginEmailEnc", "enc_email1");

            User user2 = createUser(2L, "uuid-2");
            UserSignInInfo signInInfo2 = createSignInInfo(user2, "hash2");
            setField(signInInfo2, "loginEmailEnc", "enc_email2");

            User user3 = createUser(3L, "uuid-3");
            UserSignInInfo signInInfo3 = createSignInInfo(user3, "hash3");
            setField(signInInfo3, "loginEmailEnc", "enc_email3");

            given(emailVerificationService.isVerifiedByTokenId("token-123", "recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn(true);
            given(encryptionService.encryptForSearch("recovery@email.com")).willReturn("enc_recovery_lower");
            given(userSignInInfoRepository.findAllByRecoveryEmailLowerEncWithUser("enc_recovery_lower"))
                    .willReturn(List.of(signInInfo1, signInInfo2, signInInfo3));
            given(encryptionService.decryptEmail("enc_email1")).willReturn("test@email.com");   // atIndex=4 → t***@email.com
            given(encryptionService.decryptEmail("enc_email2")).willReturn("a@b.com");           // atIndex=1 → no mask
            given(encryptionService.decryptEmail("enc_email3")).willReturn("ab@email.com");      // atIndex=2 → a***@email.com

            // when
            RecoveryAccountsResponse response = passwordService.getAccountsByRecoveryEmail("token-123", "recovery@email.com");

            // then
            assertThat(response.getAccounts().get(0).getMaskedEmail()).isEqualTo("t***@email.com");
            assertThat(response.getAccounts().get(1).getMaskedEmail()).isEqualTo("a@b.com");
            assertThat(response.getAccounts().get(2).getMaskedEmail()).isEqualTo("a***@email.com");
        }
    }

    // Helper methods
    private User createUser(Long id, String uuid) {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        setField(user, "id", id);
        setField(user, "userUuid", uuid);
        return user;
    }

    private UserSignInInfo createSignInInfo(User user, String passwordHash) {
        return UserSignInInfo.builder()
                .user(user)
                .loginEmailEnc("enc_email")
                .loginEmailLowerEnc("enc_email_lower")
                .passwordHash(passwordHash)
                .build();
    }

    private ChangePasswordRequest createChangePasswordRequest(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        setField(request, "currentPassword", currentPassword);
        setField(request, "newPassword", newPassword);
        return request;
    }

    private ResetPasswordRequest createResetPasswordRequest(String tokenId, String recoveryEmail, String loginEmail, String newPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        setField(request, "tokenId", tokenId);
        setField(request, "recoveryEmail", recoveryEmail);
        setField(request, "loginEmail", loginEmail);
        setField(request, "newPassword", newPassword);
        return request;
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
