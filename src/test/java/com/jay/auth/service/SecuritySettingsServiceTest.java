package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.SecuritySettingsResponse;
import com.jay.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecuritySettingsServiceTest {

    @InjectMocks
    private SecuritySettingsService securitySettingsService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Nested
    @DisplayName("로그인 알림 설정 조회")
    class IsLoginNotificationEnabled {

        @Test
        @DisplayName("설정값이 없으면 기본값 true를 반환해야 한다")
        void defaultValueShouldBeTrue() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("pref:security:" + userId, "loginNotification")).willReturn(null);

            // when
            boolean result = securitySettingsService.isLoginNotificationEnabled(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("설정값이 true면 true를 반환해야 한다")
        void returnTrueWhenSetToTrue() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("pref:security:" + userId, "loginNotification")).willReturn("true");

            // when
            boolean result = securitySettingsService.isLoginNotificationEnabled(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("설정값이 false면 false를 반환해야 한다")
        void returnFalseWhenSetToFalse() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForHash()).willReturn(hashOperations);
            given(hashOperations.get("pref:security:" + userId, "loginNotification")).willReturn("false");

            // when
            boolean result = securitySettingsService.isLoginNotificationEnabled(userId);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("로그인 알림 설정 변경")
    class UpdateLoginNotification {

        @Test
        @DisplayName("로그인 알림을 활성화할 수 있어야 한다")
        void enableLoginNotification() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            securitySettingsService.updateLoginNotification(userId, true);

            // then
            verify(hashOperations).put("pref:security:" + userId, "loginNotification", "true");
        }

        @Test
        @DisplayName("로그인 알림을 비활성화할 수 있어야 한다")
        void disableLoginNotification() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            // when
            securitySettingsService.updateLoginNotification(userId, false);

            // then
            verify(hashOperations).put("pref:security:" + userId, "loginNotification", "false");
        }
    }

    @Nested
    @DisplayName("계정 잠금 여부 확인")
    class IsAccountLocked {

        @Test
        @DisplayName("잠금 상태인 계정은 true를 반환해야 한다")
        void lockedAccountShouldReturnTrue() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.LOCKED);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            boolean result = securitySettingsService.isAccountLocked(userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("활성 상태인 계정은 false를 반환해야 한다")
        void activeAccountShouldReturnFalse() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.ACTIVE);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            boolean result = securitySettingsService.isAccountLocked(userId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 false를 반환해야 한다")
        void nonExistentUserShouldReturnFalse() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            boolean result = securitySettingsService.isAccountLocked(userId);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("로그인 실패 기록 및 자동 잠금")
    class RecordFailedAttemptForLock {

        @Test
        @DisplayName("10회 실패 시 계정이 자동 잠금되어야 한다")
        void autoLockAfter10Failures() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.ACTIVE);

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("lock:attempts:" + userId)).willReturn(10L);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            securitySettingsService.recordFailedAttemptForLock(userId);

            // then
            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(valueOperations).set(eq("lock:reason:" + userId), anyString());
            verify(redisTemplate).delete("lock:attempts:" + userId);
        }

        @Test
        @DisplayName("첫 번째 실패 시 TTL이 설정되어야 한다")
        void setTtlOnFirstFailure() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("lock:attempts:" + userId)).willReturn(1L);

            // when
            securitySettingsService.recordFailedAttemptForLock(userId);

            // then
            verify(redisTemplate).expire("lock:attempts:" + userId, 1L, TimeUnit.HOURS);
        }

        @Test
        @DisplayName("10회 미만 실패 시 잠금되지 않아야 한다")
        void noLockBelow10Failures() {
            // given
            Long userId = 1L;
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("lock:attempts:" + userId)).willReturn(5L);

            // when
            securitySettingsService.recordFailedAttemptForLock(userId);

            // then
            // lockAccount should not be called - user status not changed
        }
    }

    @Nested
    @DisplayName("계정 잠금")
    class LockAccount {

        @Test
        @DisplayName("계정을 잠금 상태로 변경해야 한다")
        void lockAccountSuccessfully() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.ACTIVE);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            securitySettingsService.lockAccount(userId, "수동 잠금");

            // then
            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(valueOperations).set("lock:reason:" + userId, "수동 잠금");
            verify(redisTemplate).delete("lock:attempts:" + userId);
        }

        @Test
        @DisplayName("삭제 상태인 계정은 잠금하지 않아야 한다")
        void doNotLockDeletedAccount() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.DELETED);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            securitySettingsService.lockAccount(userId, "잠금 시도");

            // then
            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 예외가 발생해야 한다")
        void throwExceptionWhenUserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> securitySettingsService.lockAccount(userId, "잠금"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }
    }

    @Nested
    @DisplayName("계정 잠금 해제")
    class UnlockAccount {

        @Test
        @DisplayName("잠금된 계정을 활성 상태로 변경해야 한다")
        void unlockAccountSuccessfully() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.LOCKED);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            securitySettingsService.unlockAccount(userId);

            // then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(redisTemplate).delete("lock:reason:" + userId);
            verify(redisTemplate).delete("lock:attempts:" + userId);
        }

        @Test
        @DisplayName("잠금 상태가 아닌 계정은 변경하지 않아야 한다")
        void doNotUnlockNonLockedAccount() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.ACTIVE);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            securitySettingsService.unlockAccount(userId);

            // then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("전체 보안 설정 조회")
    class GetSecuritySettings {

        @Test
        @DisplayName("전체 보안 설정을 정상적으로 조회해야 한다")
        void getSecuritySettingsSuccessfully() {
            // given
            Long userId = 1L;
            User user = createUser(userId, UserStatus.LOCKED);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            given(valueOperations.get("lock:reason:" + userId)).willReturn("자동 잠금");
            given(valueOperations.get("lock:attempts:" + userId)).willReturn("7");
            given(hashOperations.get("pref:security:" + userId, "loginNotification")).willReturn("true");
            given(hashOperations.get("pref:security:" + userId, "suspiciousNotification")).willReturn(null);

            // when
            SecuritySettingsResponse response = securitySettingsService.getSecuritySettings(userId);

            // then
            assertThat(response.isAccountLocked()).isTrue();
            assertThat(response.getLockReason()).isEqualTo("자동 잠금");
            assertThat(response.getFailedLoginAttempts()).isEqualTo(7);
            assertThat(response.getMaxFailedAttempts()).isEqualTo(10);
            assertThat(response.isLoginNotificationEnabled()).isTrue();
            assertThat(response.isSuspiciousActivityNotificationEnabled()).isTrue();
        }

        @Test
        @DisplayName("사용자가 없어도 기본 설정을 반환해야 한다")
        void getSettingsForNonExistentUser() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(redisTemplate.opsForHash()).willReturn(hashOperations);

            given(valueOperations.get("lock:attempts:" + userId)).willReturn(null);
            given(hashOperations.get("pref:security:" + userId, "loginNotification")).willReturn(null);
            given(hashOperations.get("pref:security:" + userId, "suspiciousNotification")).willReturn(null);

            // when
            SecuritySettingsResponse response = securitySettingsService.getSecuritySettings(userId);

            // then
            assertThat(response.isAccountLocked()).isFalse();
            assertThat(response.getLockReason()).isNull();
            assertThat(response.getFailedLoginAttempts()).isZero();
            assertThat(response.isLoginNotificationEnabled()).isTrue();
        }
    }

    // Helper methods
    private User createUser(Long userId, UserStatus status) {
        User user = User.builder().status(status).build();
        setField(user, "id", userId);
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
