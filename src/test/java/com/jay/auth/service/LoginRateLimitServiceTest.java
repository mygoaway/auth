package com.jay.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginRateLimitServiceTest {

    @InjectMocks
    private LoginRateLimitService loginRateLimitService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Nested
    @DisplayName("로그인 허용 여부 확인 (isLoginAllowed)")
    class IsLoginAllowed {

        @Test
        @DisplayName("시도 횟수가 미초과인 경우 로그인을 허용해야 한다")
        void isLoginAllowedWhenUnderLimit() {
            // given
            String email = "test@email.com";
            String ip = "192.168.1.1";

            given(valueOperations.get("login:email:" + email)).willReturn("3");
            given(valueOperations.get("login:ip:" + ip)).willReturn("10");

            // when
            boolean result = loginRateLimitService.isLoginAllowed(email, ip);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("이메일 기준 최대 횟수 초과 시 차단해야 한다")
        void isLoginBlockedByEmailLimit() {
            // given
            String email = "test@email.com";
            String ip = "192.168.1.1";

            given(valueOperations.get("login:email:" + email)).willReturn("5");
            given(valueOperations.get("login:ip:" + ip)).willReturn("0");

            // when
            boolean result = loginRateLimitService.isLoginAllowed(email, ip);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("IP 기준 최대 횟수 초과 시 차단해야 한다")
        void isLoginBlockedByIpLimit() {
            // given
            String email = "test@email.com";
            String ip = "192.168.1.1";

            given(valueOperations.get("login:email:" + email)).willReturn("0");
            given(valueOperations.get("login:ip:" + ip)).willReturn("20");

            // when
            boolean result = loginRateLimitService.isLoginAllowed(email, ip);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("카운터가 없는 경우(null) 로그인을 허용해야 한다")
        void isLoginAllowedWhenNoCounter() {
            // given
            String email = "test@email.com";
            String ip = "192.168.1.1";

            given(valueOperations.get("login:email:" + email)).willReturn(null);
            given(valueOperations.get("login:ip:" + ip)).willReturn(null);

            // when
            boolean result = loginRateLimitService.isLoginAllowed(email, ip);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("실패 기록 (recordFailedAttempt)")
    class RecordFailedAttempt {

        @Test
        @DisplayName("이메일과 IP의 실패 카운터를 증가시켜야 한다")
        void recordFailedAttemptIncrementsCounters() {
            // given
            String email = "Test@Email.com";
            String ip = "192.168.1.1";

            given(valueOperations.increment("login:email:" + email.toLowerCase())).willReturn(1L);
            given(valueOperations.increment("login:ip:" + ip)).willReturn(1L);

            // when
            loginRateLimitService.recordFailedAttempt(email, ip);

            // then
            verify(valueOperations).increment("login:email:" + email.toLowerCase());
            verify(valueOperations).increment("login:ip:" + ip);
            verify(redisTemplate).expire(eq("login:email:" + email.toLowerCase()), eq(Duration.ofMinutes(15)));
            verify(redisTemplate).expire(eq("login:ip:" + ip), eq(Duration.ofMinutes(15)));
        }

        @Test
        @DisplayName("첫 번째 시도가 아닌 경우 TTL을 재설정하지 않아야 한다")
        void recordFailedAttemptDoesNotResetTtlOnSubsequentAttempts() {
            // given
            String email = "test@email.com";
            String ip = "192.168.1.1";

            given(valueOperations.increment("login:email:" + email)).willReturn(3L);
            given(valueOperations.increment("login:ip:" + ip)).willReturn(5L);

            // when
            loginRateLimitService.recordFailedAttempt(email, ip);

            // then
            verify(valueOperations).increment("login:email:" + email);
            verify(valueOperations).increment("login:ip:" + ip);
        }
    }

    @Nested
    @DisplayName("실패 횟수 초기화 (clearFailedAttempts)")
    class ClearFailedAttempts {

        @Test
        @DisplayName("성공 시 이메일 카운터를 초기화해야 한다")
        void clearFailedAttemptsClearsEmailCounter() {
            // given
            String email = "Test@Email.com";
            String ip = "192.168.1.1";

            // when
            loginRateLimitService.clearFailedAttempts(email, ip);

            // then
            verify(redisTemplate).delete("login:email:" + email.toLowerCase());
        }
    }

    @Nested
    @DisplayName("재시도 대기 시간 조회 (getRetryAfterSeconds)")
    class GetRetryAfterSeconds {

        @Test
        @DisplayName("TTL이 있는 경우 남은 시간을 반환해야 한다")
        void getRetryAfterSecondsReturnsRemainingTtl() {
            // given
            String email = "test@email.com";
            given(redisTemplate.getExpire("login:email:" + email)).willReturn(600L);

            // when
            long result = loginRateLimitService.getRetryAfterSeconds(email);

            // then
            assertThat(result).isEqualTo(600L);
        }

        @Test
        @DisplayName("TTL이 없는 경우 0을 반환해야 한다")
        void getRetryAfterSecondsReturnsZeroWhenNoTtl() {
            // given
            String email = "test@email.com";
            given(redisTemplate.getExpire("login:email:" + email)).willReturn(-1L);

            // when
            long result = loginRateLimitService.getRetryAfterSeconds(email);

            // then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("키가 없는 경우 0을 반환해야 한다")
        void getRetryAfterSecondsReturnsZeroWhenKeyMissing() {
            // given
            String email = "test@email.com";
            given(redisTemplate.getExpire("login:email:" + email)).willReturn(null);

            // when
            long result = loginRateLimitService.getRetryAfterSeconds(email);

            // then
            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("남은 시도 횟수 조회 (getRemainingAttempts)")
    class GetRemainingAttempts {

        @Test
        @DisplayName("시도 횟수가 있는 경우 남은 횟수를 반환해야 한다")
        void getRemainingAttemptsWithExistingCount() {
            // given
            String email = "test@email.com";
            given(valueOperations.get("login:email:" + email)).willReturn("3");

            // when
            int result = loginRateLimitService.getRemainingAttempts(email);

            // then
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("시도 횟수가 없는 경우 최대 횟수를 반환해야 한다")
        void getRemainingAttemptsWithNoCount() {
            // given
            String email = "test@email.com";
            given(valueOperations.get("login:email:" + email)).willReturn(null);

            // when
            int result = loginRateLimitService.getRemainingAttempts(email);

            // then
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("최대 횟수를 초과한 경우 0을 반환해야 한다")
        void getRemainingAttemptsWhenExceeded() {
            // given
            String email = "test@email.com";
            given(valueOperations.get("login:email:" + email)).willReturn("10");

            // when
            int result = loginRateLimitService.getRemainingAttempts(email);

            // then
            assertThat(result).isEqualTo(0);
        }
    }
}
