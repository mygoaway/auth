package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLockService 테스트")
class AccountLockServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private EmailSender emailSender;
    @Mock private EncryptionService encryptionService;
    @Mock private ValueOperations<String, Object> valueOperations;

    private AccountLockService accountLockService;

    @BeforeEach
    void setUp() {
        accountLockService = new AccountLockService(
                userRepository, redisTemplate, emailSender, encryptionService, new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("recordFailedAttempt()")
    class RecordFailedAttempt {

        @Test
        @DisplayName("실패 횟수가 임계치 미만이면 잠금하지 않는다")
        void belowThresholdNoLock() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(5L);

            accountLockService.recordFailedAttempt(1L);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("실패 횟수가 임계치(10)에 도달하면 계정을 잠금한다")
        void atThresholdLockAccount() {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(10L);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(redisTemplate.delete(anyString())).willReturn(true);

            accountLockService.recordFailedAttempt(1L);

            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        }
    }

    @Nested
    @DisplayName("lockAccount()")
    class LockAccount {

        @Test
        @DisplayName("ACTIVE 계정을 잠금한다")
        void lockActiveAccount() {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(redisTemplate.delete(anyString())).willReturn(true);

            accountLockService.lockAccount(1L, "테스트 잠금", false);

            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        }

        @Test
        @DisplayName("이미 잠금된 계정은 중복 잠금하지 않는다")
        void skipAlreadyLocked() {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            accountLockService.lockAccount(1L, "재잠금 시도", false);

            // status가 변경되지 않음 (이미 LOCKED)
            assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 UserNotFoundException")
        void userNotFound() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountLockService.lockAccount(999L, "이유", false))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("DELETED 계정은 잠금하지 않는다")
        void skipDeletedAccount() {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "status", UserStatus.DELETED);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            accountLockService.lockAccount(1L, "삭제 계정 잠금 시도", false);

            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("unlockAccount()")
    class UnlockAccount {

        @Test
        @DisplayName("잠금된 계정을 해제한다")
        void unlockLockedAccount() {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "status", UserStatus.LOCKED);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(redisTemplate.delete(anyString())).willReturn(true);

            accountLockService.unlockAccount(1L);

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 계정에 unlock 호출 시 아무것도 하지 않는다")
        void skipActiveAccount() {
            User user = User.builder().build();
            ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            accountLockService.unlockAccount(1L);

            then(redisTemplate).should(never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("getFailedAttemptCount()")
    class GetFailedAttemptCount {

        @Test
        @DisplayName("Redis에 저장된 실패 횟수를 반환한다")
        void returnsCount() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("7");

            assertThat(accountLockService.getFailedAttemptCount(1L)).isEqualTo(7);
        }

        @Test
        @DisplayName("Redis 값이 없으면 0을 반환한다")
        void returnsZeroWhenNull() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            assertThat(accountLockService.getFailedAttemptCount(1L)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("clearFailedAttempts()")
    class ClearFailedAttempts {

        @Test
        @DisplayName("실패 카운트 Redis 키를 삭제한다")
        void deletesKey() {
            given(redisTemplate.delete(anyString())).willReturn(true);

            accountLockService.clearFailedAttempts(1L);

            then(redisTemplate).should().delete("lock:attempts:1");
        }
    }
}
