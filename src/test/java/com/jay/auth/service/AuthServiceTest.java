package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.EmailLoginRequest;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.exception.AuthenticationException;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.exception.InvalidPasswordException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.security.TokenStore;
import com.jay.auth.service.metrics.AuthMetrics;
import com.jay.auth.util.NicknameGenerator;
import com.jay.auth.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSignInInfoRepository userSignInInfoRepository;
    @Mock
    private UserChannelRepository userChannelRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordUtil passwordUtil;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private TotpService totpService;
    @Mock
    private NicknameGenerator nicknameGenerator;
    @Mock
    private AuthMetrics authMetrics;

    @Nested
    @DisplayName("이메일 회원가입")
    class SignUpWithEmail {

        @Test
        @DisplayName("정상적인 회원가입이 성공해야 한다")
        void signUpSuccess() {
            // given
            EmailSignUpRequest request = createSignUpRequest("token-123", "test@email.com", "Test@1234");

            given(nicknameGenerator.generateUnique(any())).willReturn("행복한고양이1234");
            given(passwordUtil.isValidPassword("Test@1234")).willReturn(true);
            given(emailVerificationService.isVerifiedByTokenId("token-123", "test@email.com", VerificationType.SIGNUP))
                    .willReturn(true);
            given(encryptionService.encryptEmail("test@email.com"))
                    .willReturn(new EncryptionService.EncryptedEmail("enc_email", "enc_email_lower"));
            given(userSignInInfoRepository.existsByLoginEmailLowerEnc("enc_email_lower")).willReturn(false);
            given(encryptionService.encryptNickname("행복한고양이1234")).willReturn("enc_nickname");
            given(encryptionService.encryptNicknameLower("행복한고양이1234")).willReturn("enc_nickname_lower");
            given(passwordUtil.encode("Test@1234")).willReturn("hashed_password");

            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User u = invocation.getArgument(0);
                setField(u, "id", 1L);
                setField(u, "userUuid", "uuid-1234");
                setField(u, "role", UserRole.USER);
                return u;
            });

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokens(eq(1L), eq("uuid-1234"), eq(ChannelCode.EMAIL), eq("USER")))
                    .willReturn(tokenResponse);

            // when
            SignUpResponse response = authService.signUpWithEmail(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("test@email.com");
            assertThat(response.getNickname()).isEqualTo("행복한고양이1234");
            assertThat(response.getToken().getAccessToken()).isEqualTo("access-token");

            verify(userRepository).save(any(User.class));
            verify(userSignInInfoRepository).save(any(UserSignInInfo.class));
            verify(userChannelRepository).save(any(UserChannel.class));
            verify(emailVerificationService).deleteVerificationByTokenId("token-123");
        }

        @Test
        @DisplayName("비밀번호 정책 미충족 시 실패해야 한다")
        void signUpFailsWithInvalidPassword() {
            // given
            EmailSignUpRequest request = createSignUpRequest("token-123", "test@email.com", "weak");
            given(nicknameGenerator.generateUnique(any())).willReturn("행복한고양이1234");
            given(passwordUtil.isValidPassword("weak")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.signUpWithEmail(request))
                    .isInstanceOf(InvalidPasswordException.class);
        }

        @Test
        @DisplayName("이메일 인증 미완료 시 실패해야 한다")
        void signUpFailsWithoutVerification() {
            // given
            EmailSignUpRequest request = createSignUpRequest("token-123", "test@email.com", "Test@1234");
            given(nicknameGenerator.generateUnique(any())).willReturn("행복한고양이1234");
            given(passwordUtil.isValidPassword("Test@1234")).willReturn(true);
            given(emailVerificationService.isVerifiedByTokenId("token-123", "test@email.com", VerificationType.SIGNUP))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.signUpWithEmail(request))
                    .isInstanceOf(InvalidVerificationException.class);
        }

        @Test
        @DisplayName("이메일 중복 시 실패해야 한다")
        void signUpFailsWithDuplicateEmail() {
            // given
            EmailSignUpRequest request = createSignUpRequest("token-123", "test@email.com", "Test@1234");
            given(nicknameGenerator.generateUnique(any())).willReturn("행복한고양이1234");
            given(passwordUtil.isValidPassword("Test@1234")).willReturn(true);
            given(emailVerificationService.isVerifiedByTokenId("token-123", "test@email.com", VerificationType.SIGNUP))
                    .willReturn(true);
            given(encryptionService.encryptEmail("test@email.com"))
                    .willReturn(new EncryptionService.EncryptedEmail("enc_email", "enc_email_lower"));
            given(userSignInInfoRepository.existsByLoginEmailLowerEnc("enc_email_lower")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signUpWithEmail(request))
                    .isInstanceOf(DuplicateEmailException.class);
        }
    }

    @Nested
    @DisplayName("이메일 로그인")
    class LoginWithEmail {

        @Test
        @DisplayName("정상적인 로그인이 성공해야 한다")
        void loginSuccess() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("Test@1234", "hashed_password")).willReturn(true);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokens(1L, "uuid-1234", ChannelCode.EMAIL, "USER")).willReturn(tokenResponse);

            // when
            LoginResponse response = authService.loginWithEmail(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(response.getEmail()).isEqualTo("test@email.com");
            assertThat(response.getNickname()).isEqualTo("테스트");
            assertThat(response.getToken().getAccessToken()).isEqualTo("access-token");
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인 시 실패해야 한다")
        void loginFailsWithInvalidEmail() {
            // given
            EmailLoginRequest request = createLoginRequest("unknown@email.com", "Test@1234");
            given(encryptionService.encryptForSearch("unknown@email.com")).willReturn("enc_unknown");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_unknown"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.loginWithEmail(request))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 실패하고 실패 횟수가 증가해야 한다")
        void loginFailsWithWrongPassword() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "WrongPass@1");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("WrongPass@1", "hashed_password")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.loginWithEmail(request))
                    .isInstanceOf(AuthenticationException.class);
            assertThat(signInInfo.getLoginFailCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("비활성 계정으로 로그인 시 실패해야 한다")
        void loginFailsWithInactiveAccount() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            user.updateStatus(UserStatus.DORMANT);
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));

            // when & then
            assertThatThrownBy(() -> authService.loginWithEmail(request))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        @DisplayName("잠금된 계정으로 로그인 시 실패해야 한다")
        void loginFailsWithLockedAccount() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();
            // trigger lock: 5 consecutive failures
            for (int i = 0; i < 5; i++) {
                signInInfo.recordLoginFailure();
            }

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));

            // when & then
            assertThatThrownBy(() -> authService.loginWithEmail(request))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        @DisplayName("PENDING_DELETE 계정 로그인이 허용되어야 한다")
        void loginSucceedsWithPendingDeleteAccount() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            user.updateStatus(UserStatus.PENDING_DELETE);
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("Test@1234", "hashed_password")).willReturn(true);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokens(1L, "uuid-1234", ChannelCode.EMAIL, "USER")).willReturn(tokenResponse);

            // when
            LoginResponse response = authService.loginWithEmail(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserUuid()).isEqualTo("uuid-1234");
        }
    }

    @Nested
    @DisplayName("이메일 로그인 (세션 포함)")
    class LoginWithEmailWithSession {

        @Test
        @DisplayName("세션 포함 로그인이 성공해야 한다")
        void loginWithSessionSuccess() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo("WEB", "Chrome", "macOS", "127.0.0.1", "Seoul");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("Test@1234", "hashed_password")).willReturn(true);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokensWithSession(eq(1L), eq("uuid-1234"), eq(ChannelCode.EMAIL), eq("USER"), eq(sessionInfo)))
                    .willReturn(tokenResponse);
            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordPolicyService.isPasswordExpired(signInInfo)).willReturn(false);
            given(passwordPolicyService.getDaysUntilExpiration(signInInfo)).willReturn(90);
            given(totpService.isTwoFactorRequired(1L)).willReturn(false);

            // when
            LoginResponse response = authService.loginWithEmail(request, sessionInfo);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getUserUuid()).isEqualTo("uuid-1234");
            verify(tokenService).issueTokensWithSession(eq(1L), eq("uuid-1234"), eq(ChannelCode.EMAIL), eq("USER"), eq(sessionInfo));
        }

        @Test
        @DisplayName("비밀번호 만료 상태가 응답에 포함되어야 한다")
        void loginWithSessionIncludesPasswordExpired() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo("WEB", "Chrome", "macOS", "127.0.0.1", "Seoul");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("Test@1234", "hashed_password")).willReturn(true);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokensWithSession(any(), any(), any(), any(), any())).willReturn(tokenResponse);
            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordPolicyService.isPasswordExpired(signInInfo)).willReturn(true);
            given(passwordPolicyService.getDaysUntilExpiration(signInInfo)).willReturn(0);
            given(totpService.isTwoFactorRequired(1L)).willReturn(false);

            // when
            LoginResponse response = authService.loginWithEmail(request, sessionInfo);

            // then
            assertThat(response.isPasswordExpired()).isTrue();
        }

        @Test
        @DisplayName("2FA 필요 상태가 응답에 포함되어야 한다")
        void loginWithSessionIncludesTwoFactorRequired() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo("WEB", "Chrome", "macOS", "127.0.0.1", "Seoul");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("Test@1234", "hashed_password")).willReturn(true);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokensWithSession(any(), any(), any(), any(), any())).willReturn(tokenResponse);
            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordPolicyService.isPasswordExpired(signInInfo)).willReturn(false);
            given(passwordPolicyService.getDaysUntilExpiration(signInInfo)).willReturn(90);
            given(totpService.isTwoFactorRequired(1L)).willReturn(true);

            // when
            LoginResponse response = authService.loginWithEmail(request, sessionInfo);

            // then
            assertThat(response.isTwoFactorRequired()).isTrue();
        }

        @Test
        @DisplayName("PENDING_DELETE 계정 로그인 시 pendingDeletion 플래그가 설정되어야 한다")
        void loginWithSessionIncludesPendingDeletion() {
            // given
            EmailLoginRequest request = createLoginRequest("test@email.com", "Test@1234");
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo("WEB", "Chrome", "macOS", "127.0.0.1", "Seoul");

            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            user.updateStatus(UserStatus.PENDING_DELETE);
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));
            given(passwordUtil.matches("Test@1234", "hashed_password")).willReturn(true);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트");

            TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
            given(tokenService.issueTokensWithSession(any(), any(), any(), any(), any())).willReturn(tokenResponse);
            given(userSignInInfoRepository.findByUserId(1L)).willReturn(Optional.of(signInInfo));
            given(passwordPolicyService.isPasswordExpired(signInInfo)).willReturn(false);
            given(passwordPolicyService.getDaysUntilExpiration(signInInfo)).willReturn(90);
            given(totpService.isTwoFactorRequired(1L)).willReturn(false);

            // when
            LoginResponse response = authService.loginWithEmail(request, sessionInfo);

            // then
            assertThat(response.isPendingDeletion()).isTrue();
        }
    }

    @Nested
    @DisplayName("이메일 존재 여부 확인")
    class IsEmailExists {

        @Test
        @DisplayName("이메일이 존재하면 true를 반환해야 한다")
        void emailExistsReturnsTrue() {
            // given
            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.existsByLoginEmailLowerEnc("enc_email_lower")).willReturn(true);

            // when
            boolean result = authService.isEmailExists("test@email.com");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("이메일이 없으면 false를 반환해야 한다")
        void emailExistsReturnsFalse() {
            // given
            given(encryptionService.encryptForSearch("unknown@email.com")).willReturn("enc_unknown");
            given(userSignInInfoRepository.existsByLoginEmailLowerEnc("enc_unknown")).willReturn(false);

            // when
            boolean result = authService.isEmailExists("unknown@email.com");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("사용자 ID 조회")
    class FindUserIdByEmail {

        @Test
        @DisplayName("이메일로 사용자 ID를 조회할 수 있어야 한다")
        void findUserIdByEmailSuccess() {
            // given
            User user = createUser(1L, "uuid-1234", "enc_email", "enc_email_lower", "enc_nickname");
            UserSignInInfo signInInfo = UserSignInInfo.builder()
                    .user(user)
                    .loginEmailEnc("enc_email")
                    .loginEmailLowerEnc("enc_email_lower")
                    .passwordHash("hashed_password")
                    .build();

            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_email_lower"))
                    .willReturn(Optional.of(signInInfo));

            // when
            Long userId = authService.findUserIdByEmail("test@email.com");

            // then
            assertThat(userId).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 null을 반환해야 한다")
        void findUserIdByEmailReturnsNull() {
            // given
            given(encryptionService.encryptForSearch("unknown@email.com")).willReturn("enc_unknown");
            given(userSignInInfoRepository.findByLoginEmailLowerEncWithUser("enc_unknown"))
                    .willReturn(Optional.empty());

            // when
            Long userId = authService.findUserIdByEmail("unknown@email.com");

            // then
            assertThat(userId).isNull();
        }
    }

    @Nested
    @DisplayName("소셜 채널 이메일 확인")
    class IsSocialChannelEmail {

        @Test
        @DisplayName("소셜 채널 이메일이면 true를 반환해야 한다")
        void socialChannelEmailReturnsTrue() {
            // given
            given(encryptionService.encryptForSearch("social@gmail.com")).willReturn("enc_social_lower");
            given(userChannelRepository.existsByChannelEmailLowerEncAndChannelCodeNot("enc_social_lower", ChannelCode.EMAIL))
                    .willReturn(true);

            // when
            boolean result = authService.isSocialChannelEmail("social@gmail.com");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("소셜 채널 이메일이 아니면 false를 반환해야 한다")
        void socialChannelEmailReturnsFalse() {
            // given
            given(encryptionService.encryptForSearch("test@email.com")).willReturn("enc_email_lower");
            given(userChannelRepository.existsByChannelEmailLowerEncAndChannelCodeNot("enc_email_lower", ChannelCode.EMAIL))
                    .willReturn(false);

            // when
            boolean result = authService.isSocialChannelEmail("test@email.com");

            // then
            assertThat(result).isFalse();
        }
    }

    // Helper methods
    private EmailSignUpRequest createSignUpRequest(String tokenId, String email, String password) {
        EmailSignUpRequest request = new EmailSignUpRequest();
        setField(request, "tokenId", tokenId);
        setField(request, "email", email);
        setField(request, "password", password);
        return request;
    }

    private EmailLoginRequest createLoginRequest(String email, String password) {
        EmailLoginRequest request = new EmailLoginRequest();
        setField(request, "email", email);
        setField(request, "password", password);
        return request;
    }

    private User createUser(Long id, String uuid, String emailEnc, String emailLowerEnc, String nicknameEnc) {
        User user = User.builder()
                .emailEnc(emailEnc)
                .emailLowerEnc(emailLowerEnc)
                .nicknameEnc(nicknameEnc)
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", id);
        setField(user, "userUuid", uuid);
        setField(user, "role", UserRole.USER);
        return user;
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
