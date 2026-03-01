package com.jay.auth.service.metrics;

import com.jay.auth.repository.UserPasskeyRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.repository.UserTwoFactorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthGaugeMetrics 테스트")
class AuthGaugeMetricsTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private UserSignInInfoRepository userSignInInfoRepository;

    @Mock
    private UserPasskeyRepository userPasskeyRepository;

    @Mock
    private UserTwoFactorRepository userTwoFactorRepository;

    private SimpleMeterRegistry registry;
    private AuthGaugeMetrics gaugeMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gaugeMetrics = new AuthGaugeMetrics(registry, stringRedisTemplate,
                userSignInInfoRepository, userPasskeyRepository, userTwoFactorRepository);
    }

    // ─── activeSessions ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("활성 세션 게이지")
    class ActiveSessions {

        @Test
        @DisplayName("incrementActiveSessions — 0 → 1로 증가")
        void increment() {
            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(0.0);

            gaugeMetrics.incrementActiveSessions();

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("decrementActiveSessions — 1 → 0으로 감소")
        void decrement() {
            gaugeMetrics.incrementActiveSessions();
            gaugeMetrics.decrementActiveSessions();

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("decrementActiveSessions — 0에서 호출해도 음수가 되지 않음")
        void decrementAtZeroStaysZero() {
            gaugeMetrics.decrementActiveSessions();

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("decrementActiveSessions(long count) — 벌크 감소")
        void decrementBulk() {
            gaugeMetrics.incrementActiveSessions();
            gaugeMetrics.incrementActiveSessions();
            gaugeMetrics.incrementActiveSessions();

            gaugeMetrics.decrementActiveSessions(2L);

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("decrementActiveSessions(long count) — 음수 방지")
        void decrementBulkNegativePrevention() {
            gaugeMetrics.incrementActiveSessions();
            gaugeMetrics.decrementActiveSessions(5L);

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(0.0);
        }
    }

    // ─── registeredPasskeys ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("등록된 패스키 게이지")
    class RegisteredPasskeys {

        @Test
        @DisplayName("incrementRegisteredPasskeys — 0 → 1로 증가")
        void increment() {
            gaugeMetrics.incrementRegisteredPasskeys();

            assertThat(registry.get("auth_registered_passkeys").gauge().value()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("decrementRegisteredPasskeys — 1 → 0으로 감소")
        void decrement() {
            gaugeMetrics.incrementRegisteredPasskeys();
            gaugeMetrics.decrementRegisteredPasskeys();

            assertThat(registry.get("auth_registered_passkeys").gauge().value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("decrementRegisteredPasskeys — 0에서 호출해도 음수가 되지 않음")
        void decrementAtZeroStaysZero() {
            gaugeMetrics.decrementRegisteredPasskeys();

            assertThat(registry.get("auth_registered_passkeys").gauge().value()).isEqualTo(0.0);
        }
    }

    // ─── syncActiveSessions ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("syncActiveSessions()")
    class SyncActiveSessions {

        @Test
        @DisplayName("Redis keys 3개 반환 시 activeSessions=3")
        void syncWithThreeKeys() {
            given(stringRedisTemplate.keys(anyString()))
                    .willReturn(Set.of("session:1:a", "session:2:b", "session:3:c"));

            gaugeMetrics.syncActiveSessions();

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("Redis keys null 반환 시 activeSessions=0")
        void syncWithNullKeys() {
            given(stringRedisTemplate.keys(anyString())).willReturn(null);

            gaugeMetrics.syncActiveSessions();

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Redis 예외 발생 시 기존 값 유지")
        void exceptionKeepsPreviousValue() {
            gaugeMetrics.incrementActiveSessions();
            gaugeMetrics.incrementActiveSessions();

            given(stringRedisTemplate.keys(anyString()))
                    .willThrow(new RuntimeException("Redis 연결 실패"));

            gaugeMetrics.syncActiveSessions();

            assertThat(registry.get("auth_active_sessions").gauge().value()).isEqualTo(2.0);
        }
    }

    // ─── syncRegisteredPasskeys ───────────────────────────────────────────────────

    @Nested
    @DisplayName("syncRegisteredPasskeys()")
    class SyncRegisteredPasskeys {

        @Test
        @DisplayName("userPasskeyRepository.count()=5 반환 시 registeredPasskeys=5")
        void syncWithFivePasskeys() {
            given(userPasskeyRepository.count()).willReturn(5L);

            gaugeMetrics.syncRegisteredPasskeys();

            assertThat(registry.get("auth_registered_passkeys").gauge().value()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("예외 발생 시 기존 값 유지")
        void exceptionKeepsPreviousValue() {
            gaugeMetrics.incrementRegisteredPasskeys();
            gaugeMetrics.incrementRegisteredPasskeys();

            given(userPasskeyRepository.count())
                    .willThrow(new RuntimeException("DB 연결 실패"));

            gaugeMetrics.syncRegisteredPasskeys();

            assertThat(registry.get("auth_registered_passkeys").gauge().value()).isEqualTo(2.0);
        }
    }

    // ─── refreshLockedAccountsCount ───────────────────────────────────────────────

    @Nested
    @DisplayName("refreshLockedAccountsCount()")
    class RefreshLockedAccountsCount {

        @Test
        @DisplayName("countLockedAccounts() 반환값으로 lockedAccounts 게이지 설정")
        void refreshWithCount() {
            given(userSignInInfoRepository.countLockedAccounts(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                    .willReturn(3L);

            gaugeMetrics.refreshLockedAccountsCount();

            assertThat(registry.get("auth_locked_accounts").gauge().value()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("예외 발생 시 기존 값 유지")
        void exceptionKeepsPreviousValue() {
            given(userSignInInfoRepository.countLockedAccounts(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                    .willReturn(2L);
            gaugeMetrics.refreshLockedAccountsCount();

            given(userSignInInfoRepository.countLockedAccounts(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                    .willThrow(new RuntimeException("DB 오류"));
            gaugeMetrics.refreshLockedAccountsCount();

            assertThat(registry.get("auth_locked_accounts").gauge().value()).isEqualTo(2.0);
        }
    }

    // ─── refresh2faEnabledUsersCount ──────────────────────────────────────────────

    @Nested
    @DisplayName("refresh2faEnabledUsersCount()")
    class Refresh2faEnabledUsersCount {

        @Test
        @DisplayName("countByEnabled(true) 반환값으로 2faEnabledUsers 게이지 설정")
        void refreshWithCount() {
            given(userTwoFactorRepository.countByEnabled(true)).willReturn(7L);

            gaugeMetrics.refresh2faEnabledUsersCount();

            assertThat(registry.get("auth_2fa_enabled_users").gauge().value()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("예외 발생 시 기존 값 유지")
        void exceptionKeepsPreviousValue() {
            given(userTwoFactorRepository.countByEnabled(true)).willReturn(4L);
            gaugeMetrics.refresh2faEnabledUsersCount();

            given(userTwoFactorRepository.countByEnabled(true))
                    .willThrow(new RuntimeException("DB 오류"));
            gaugeMetrics.refresh2faEnabledUsersCount();

            assertThat(registry.get("auth_2fa_enabled_users").gauge().value()).isEqualTo(4.0);
        }
    }
}
