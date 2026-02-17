package com.jay.auth.service;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.SecurityDashboardResponse;
import com.jay.auth.dto.response.TwoFactorStatusResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserPasskeyRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SecurityDashboardServiceTest {

    @InjectMocks
    private SecurityDashboardService securityDashboardService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSignInInfoRepository userSignInInfoRepository;
    @Mock
    private UserChannelRepository userChannelRepository;
    @Mock
    private LoginHistoryRepository loginHistoryRepository;
    @Mock
    private UserPasskeyRepository userPasskeyRepository;
    @Mock
    private TotpService totpService;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private EncryptionService encryptionService;

    @Nested
    @DisplayName("보안 대시보드 조회 (getSecurityDashboard)")
    class GetSecurityDashboard {

        @Test
        @DisplayName("모든 보안 요소가 충족되면 높은 점수를 반환해야 한다")
        void getSecurityDashboardWithAllFactorsFulfilled() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_recovery_email");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // 2FA enabled (30pts)
            TwoFactorStatusResponse twoFactorStatus = TwoFactorStatusResponse.builder()
                    .enabled(true)
                    .remainingBackupCodes(8)
                    .build();
            given(totpService.getTwoFactorStatus(userId)).willReturn(twoFactorStatus);

            // Password healthy (25pts) - recent change, not expired
            UserSignInInfo signInInfo = createSignInInfo(user);
            setField(signInInfo, "passwordUpdatedAt", LocalDateTime.now().minusDays(10));
            given(userSignInInfoRepository.findByUserId(userId)).willReturn(Optional.of(signInInfo));
            given(passwordPolicyService.isPasswordExpired(signInInfo)).willReturn(false);
            given(passwordPolicyService.getDaysUntilExpiration(signInInfo)).willReturn(80);

            // Social accounts linked (15pts)
            given(userChannelRepository.countByUserId(userId)).willReturn(3L);

            // Recent login activity - all successful (15pts)
            LoginHistory successLogin = createLoginHistory(userId, true, "Seoul");
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of(successLogin));

            // when
            SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getSecurityScore()).isGreaterThanOrEqualTo(80);
            assertThat(response.getSecurityLevel()).isIn("EXCELLENT", "GOOD");
            assertThat(response.getFactors()).isNotEmpty();
        }

        @Test
        @DisplayName("최소 보안 상태에서는 낮은 점수를 반환해야 한다")
        void getSecurityDashboardWithMinimalSecurity() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null); // no recovery email

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // 2FA disabled (0pts)
            TwoFactorStatusResponse twoFactorStatus = TwoFactorStatusResponse.builder()
                    .enabled(false)
                    .remainingBackupCodes(0)
                    .build();
            given(totpService.getTwoFactorStatus(userId)).willReturn(twoFactorStatus);

            // No sign in info (social only)
            given(userSignInInfoRepository.findByUserId(userId)).willReturn(Optional.empty());

            // Only 1 channel (10pts)
            given(userChannelRepository.countByUserId(userId)).willReturn(1L);

            // No recent login
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getSecurityScore()).isLessThan(60);
            assertThat(response.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("2FA가 활성화되면 점수에 반영되어야 한다")
        void getSecurityDashboardWith2FAEnabled() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            TwoFactorStatusResponse twoFactorStatus = TwoFactorStatusResponse.builder()
                    .enabled(true)
                    .remainingBackupCodes(8)
                    .build();
            given(totpService.getTwoFactorStatus(userId)).willReturn(twoFactorStatus);
            given(userSignInInfoRepository.findByUserId(userId)).willReturn(Optional.empty());
            given(userChannelRepository.countByUserId(userId)).willReturn(1L);
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(userId);

            // then
            assertThat(response.getFactors()).anyMatch(
                    factor -> "2FA_ENABLED".equals(factor.getName()) && factor.getScore() == 30
            );
        }

        @Test
        @DisplayName("복구 이메일이 설정된 경우 점수에 반영되어야 한다")
        void getSecurityDashboardWithRecoveryEmail() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_recovery_email");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            TwoFactorStatusResponse twoFactorStatus = TwoFactorStatusResponse.builder()
                    .enabled(false)
                    .remainingBackupCodes(0)
                    .build();
            given(totpService.getTwoFactorStatus(userId)).willReturn(twoFactorStatus);
            given(userSignInInfoRepository.findByUserId(userId)).willReturn(Optional.empty());
            given(userChannelRepository.countByUserId(userId)).willReturn(1L);
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(userId);

            // then
            assertThat(response.getFactors()).anyMatch(
                    factor -> "RECOVERY_EMAIL".equals(factor.getName()) && factor.getScore() == 15
            );
        }

        @Test
        @DisplayName("비활성 보안 요소에 대한 권장사항을 생성해야 한다")
        void getSecurityDashboardGeneratesRecommendations() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null); // no recovery email

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            TwoFactorStatusResponse twoFactorStatus = TwoFactorStatusResponse.builder()
                    .enabled(false)
                    .remainingBackupCodes(0)
                    .build();
            given(totpService.getTwoFactorStatus(userId)).willReturn(twoFactorStatus);
            given(userSignInInfoRepository.findByUserId(userId)).willReturn(Optional.empty());
            given(userChannelRepository.countByUserId(userId)).willReturn(1L);
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(userId);

            // then
            assertThat(response.getRecommendations()).isNotEmpty();
            assertThat(response.getRecommendations()).anyMatch(
                    r -> r.contains("2단계 인증")
            );
            assertThat(response.getRecommendations()).anyMatch(
                    r -> r.contains("복구 이메일")
            );
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 경우 예외를 발생시켜야 한다")
        void getSecurityDashboardFailsUserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> securityDashboardService.getSecurityDashboard(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("비밀번호가 만료된 경우 낮은 비밀번호 점수를 반환해야 한다")
        void getSecurityDashboardWithExpiredPassword() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            TwoFactorStatusResponse twoFactorStatus = TwoFactorStatusResponse.builder()
                    .enabled(false)
                    .remainingBackupCodes(0)
                    .build();
            given(totpService.getTwoFactorStatus(userId)).willReturn(twoFactorStatus);

            UserSignInInfo signInInfo = createSignInInfo(user);
            given(userSignInInfoRepository.findByUserId(userId)).willReturn(Optional.of(signInInfo));
            given(passwordPolicyService.isPasswordExpired(signInInfo)).willReturn(true);

            given(userChannelRepository.countByUserId(userId)).willReturn(1L);
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of());

            // when
            SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(userId);

            // then
            assertThat(response.getFactors()).anyMatch(
                    factor -> "PASSWORD_HEALTH".equals(factor.getName()) && factor.getScore() == 5
            );
        }
    }

    // Helper methods
    private User createUser(Long id, String recoveryEmailEnc) {
        User user = User.builder()
                .emailEnc("enc_email")
                .recoveryEmailEnc(recoveryEmailEnc)
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", id);
        setField(user, "userUuid", "uuid-" + id);
        return user;
    }

    private UserSignInInfo createSignInInfo(User user) {
        return UserSignInInfo.builder()
                .user(user)
                .loginEmailEnc("enc_email")
                .loginEmailLowerEnc("enc_email_lower")
                .passwordHash("hashed_password")
                .build();
    }

    private LoginHistory createLoginHistory(Long userId, boolean isSuccess, String location) {
        LoginHistory history = LoginHistory.builder()
                .userId(userId)
                .channelCode(ChannelCode.EMAIL)
                .ipAddress("192.168.1.1")
                .browser("Chrome")
                .os("macOS")
                .location(location)
                .isSuccess(isSuccess)
                .build();
        setField(history, "createdAt", LocalDateTime.now());
        return history;
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
