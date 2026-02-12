package com.jay.auth.service;

import com.jay.auth.domain.entity.PasswordHistory;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.repository.PasswordHistoryRepository;
import com.jay.auth.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    @InjectMocks
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private PasswordUtil passwordUtil;

    @BeforeEach
    void setUp() {
        setField(passwordPolicyService, "passwordExpirationDays", 90);
        setField(passwordPolicyService, "passwordHistoryCount", 5);
    }

    @Nested
    @DisplayName("비밀번호 만료 여부 확인 (isPasswordExpired)")
    class IsPasswordExpired {

        @Test
        @DisplayName("비밀번호가 90일을 초과하면 만료되어야 한다")
        void isPasswordExpiredWhenOverDays() {
            // given
            UserSignInInfo signInInfo = createSignInInfo();
            setField(signInInfo, "passwordUpdatedAt", LocalDateTime.now().minusDays(91));

            // when
            boolean result = passwordPolicyService.isPasswordExpired(signInInfo);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("비밀번호가 90일 미만이면 만료되지 않아야 한다")
        void isPasswordNotExpiredWhenUnderDays() {
            // given
            UserSignInInfo signInInfo = createSignInInfo();
            setField(signInInfo, "passwordUpdatedAt", LocalDateTime.now().minusDays(30));

            // when
            boolean result = passwordPolicyService.isPasswordExpired(signInInfo);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("비밀번호 변경 기록이 없으면 만료되지 않아야 한다")
        void isPasswordNotExpiredWhenNoUpdateRecord() {
            // given
            UserSignInInfo signInInfo = createSignInInfo();
            setField(signInInfo, "passwordUpdatedAt", null);

            // when
            boolean result = passwordPolicyService.isPasswordExpired(signInInfo);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("signInInfo가 null이면 만료되지 않아야 한다")
        void isPasswordNotExpiredWhenSignInInfoNull() {
            // when
            boolean result = passwordPolicyService.isPasswordExpired(null);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("비밀번호 만료까지 남은 일수 (getDaysUntilExpiration)")
    class GetDaysUntilExpiration {

        @Test
        @DisplayName("남은 일수를 올바르게 계산해야 한다")
        void getDaysUntilExpirationCalculatesCorrectly() {
            // given
            UserSignInInfo signInInfo = createSignInInfo();
            setField(signInInfo, "passwordUpdatedAt", LocalDateTime.now().minusDays(30));

            // when
            int result = passwordPolicyService.getDaysUntilExpiration(signInInfo);

            // then
            assertThat(result).isBetween(59, 61);
        }

        @Test
        @DisplayName("만료된 경우 0을 반환해야 한다")
        void getDaysUntilExpirationReturnsZeroWhenExpired() {
            // given
            UserSignInInfo signInInfo = createSignInInfo();
            setField(signInInfo, "passwordUpdatedAt", LocalDateTime.now().minusDays(100));

            // when
            int result = passwordPolicyService.getDaysUntilExpiration(signInInfo);

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("변경 기록이 없으면 기본 만료 일수를 반환해야 한다")
        void getDaysUntilExpirationReturnsDefaultWhenNoRecord() {
            // when
            int result = passwordPolicyService.getDaysUntilExpiration(null);

            // then
            assertThat(result).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("비밀번호 재사용 여부 확인 (isPasswordReused)")
    class IsPasswordReused {

        @Test
        @DisplayName("이전 비밀번호와 동일한 경우 true를 반환해야 한다")
        void isPasswordReusedWhenMatch() {
            // given
            Long userId = 1L;
            String newPassword = "NewPass@123";

            PasswordHistory history1 = createPasswordHistory("hash1");
            PasswordHistory history2 = createPasswordHistory("hash2");

            given(passwordHistoryRepository.findRecentByUserId(userId))
                    .willReturn(List.of(history1, history2));
            given(passwordUtil.matches(newPassword, "hash1")).willReturn(true);

            // when
            boolean result = passwordPolicyService.isPasswordReused(userId, newPassword);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("이전 비밀번호와 동일하지 않은 경우 false를 반환해야 한다")
        void isPasswordNotReusedWhenNoMatch() {
            // given
            Long userId = 1L;
            String newPassword = "NewPass@123";

            PasswordHistory history1 = createPasswordHistory("hash1");
            PasswordHistory history2 = createPasswordHistory("hash2");

            given(passwordHistoryRepository.findRecentByUserId(userId))
                    .willReturn(List.of(history1, history2));
            given(passwordUtil.matches(newPassword, "hash1")).willReturn(false);
            given(passwordUtil.matches(newPassword, "hash2")).willReturn(false);

            // when
            boolean result = passwordPolicyService.isPasswordReused(userId, newPassword);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("현재 비밀번호와 동일 여부 확인 (isSameAsCurrentPassword)")
    class IsSameAsCurrentPassword {

        @Test
        @DisplayName("현재 비밀번호와 동일한 경우 true를 반환해야 한다")
        void isSameAsCurrentPasswordTrue() {
            // given
            given(passwordUtil.matches("password", "currentHash")).willReturn(true);

            // when
            boolean result = passwordPolicyService.isSameAsCurrentPassword("password", "currentHash");

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("현재 비밀번호와 상이한 경우 false를 반환해야 한다")
        void isSameAsCurrentPasswordFalse() {
            // given
            given(passwordUtil.matches("newPassword", "currentHash")).willReturn(false);

            // when
            boolean result = passwordPolicyService.isSameAsCurrentPassword("newPassword", "currentHash");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("비밀번호 히스토리 저장 (savePasswordHistory)")
    class SavePasswordHistory {

        @Test
        @DisplayName("히스토리를 저장해야 한다")
        void savePasswordHistorySuccess() {
            // given
            User user = createUser(1L);
            String passwordHash = "hashed_password";

            given(passwordHistoryRepository.save(any(PasswordHistory.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(passwordHistoryRepository.countByUserId(1L)).willReturn(3L);

            // when
            passwordPolicyService.savePasswordHistory(user, passwordHash);

            // then
            verify(passwordHistoryRepository).save(any(PasswordHistory.class));
            verify(passwordHistoryRepository, never()).deleteOldHistories(any(), any(int.class));
        }

        @Test
        @DisplayName("히스토리가 최대 개수를 초과하면 오래된 것을 삭제해야 한다")
        void savePasswordHistoryDeletesOldEntries() {
            // given
            User user = createUser(1L);
            String passwordHash = "hashed_password";

            given(passwordHistoryRepository.save(any(PasswordHistory.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(passwordHistoryRepository.countByUserId(1L)).willReturn(6L);

            // when
            passwordPolicyService.savePasswordHistory(user, passwordHash);

            // then
            verify(passwordHistoryRepository).save(any(PasswordHistory.class));
            verify(passwordHistoryRepository).deleteOldHistories(1L, 5);
        }
    }

    @Nested
    @DisplayName("비밀번호 히스토리 삭제 (deletePasswordHistory)")
    class DeletePasswordHistory {

        @Test
        @DisplayName("사용자의 모든 히스토리를 삭제해야 한다")
        void deletePasswordHistorySuccess() {
            // given
            Long userId = 1L;

            // when
            passwordPolicyService.deletePasswordHistory(userId);

            // then
            verify(passwordHistoryRepository).deleteByUserId(userId);
        }
    }

    @Nested
    @DisplayName("비밀번호 정책 정보 조회 (getPolicyInfo)")
    class GetPolicyInfo {

        @Test
        @DisplayName("정책 정보를 올바르게 반환해야 한다")
        void getPolicyInfoReturnsCorrectInfo() {
            // when
            PasswordPolicyService.PasswordPolicyInfo policyInfo = passwordPolicyService.getPolicyInfo();

            // then
            assertThat(policyInfo.expirationDays()).isEqualTo(90);
            assertThat(policyInfo.historyCount()).isEqualTo(5);
        }
    }

    // Helper methods
    private User createUser(Long id) {
        User user = User.builder()
                .emailEnc("enc_email")
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", id);
        return user;
    }

    private UserSignInInfo createSignInInfo() {
        User user = createUser(1L);
        return UserSignInInfo.builder()
                .user(user)
                .loginEmailEnc("enc_email")
                .loginEmailLowerEnc("enc_email_lower")
                .passwordHash("hashed_password")
                .build();
    }

    private PasswordHistory createPasswordHistory(String passwordHash) {
        User user = createUser(1L);
        return PasswordHistory.builder()
                .user(user)
                .passwordHash(passwordHash)
                .build();
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
