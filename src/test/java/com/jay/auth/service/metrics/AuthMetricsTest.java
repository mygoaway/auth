package com.jay.auth.service.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthMetrics 테스트")
class AuthMetricsTest {

    private SimpleMeterRegistry registry;
    private AuthMetrics authMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        authMetrics = new AuthMetrics(registry);
    }

    @Nested
    @DisplayName("로그인 메트릭")
    class LoginMetrics {

        @Test
        @DisplayName("recordLoginSuccess — auth_login_attempts_total{result=success} 증가")
        void recordLoginSuccess() {
            authMetrics.recordLoginSuccess("EMAIL");

            double count = registry.counter("auth_login_attempts_total",
                    "channel", "EMAIL", "result", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordLoginFailure — auth_login_attempts_total{result=fail} 증가")
        void recordLoginFailure() {
            authMetrics.recordLoginFailure("GOOGLE");

            double count = registry.counter("auth_login_attempts_total",
                    "channel", "GOOGLE", "result", "fail").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("멱등성 — 2회 호출 시 count=2.0")
        void idempotency() {
            authMetrics.recordLoginSuccess("EMAIL");
            authMetrics.recordLoginSuccess("EMAIL");

            double count = registry.counter("auth_login_attempts_total",
                    "channel", "EMAIL", "result", "success").count();
            assertThat(count).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("회원가입 메트릭")
    class SignUpMetrics {

        @Test
        @DisplayName("recordSignUp — auth_signup_total{channel} 증가")
        void recordSignUp() {
            authMetrics.recordSignUp("EMAIL");

            double count = registry.counter("auth_signup_total", "channel", "EMAIL").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("토큰 메트릭")
    class TokenMetrics {

        @Test
        @DisplayName("recordTokenIssued — auth_token_issued_total{type,channel} 증가")
        void recordTokenIssued() {
            authMetrics.recordTokenIssued("ACCESS", "EMAIL");

            double count = registry.counter("auth_token_issued_total",
                    "type", "ACCESS", "channel", "EMAIL").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordTokenRefreshSuccess — auth_token_refresh_total{result=success} 증가")
        void recordTokenRefreshSuccess() {
            authMetrics.recordTokenRefreshSuccess();

            double count = registry.counter("auth_token_refresh_total", "result", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordTokenRefreshFailure — auth_token_refresh_total{result=fail} 증가")
        void recordTokenRefreshFailure() {
            authMetrics.recordTokenRefreshFailure();

            double count = registry.counter("auth_token_refresh_total", "result", "fail").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("로그아웃 메트릭")
    class LogoutMetrics {

        @Test
        @DisplayName("recordLogout — auth_logout_total{type} 증가")
        void recordLogout() {
            authMetrics.recordLogout("single");

            double count = registry.counter("auth_logout_total", "type", "single").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("레이트 리밋 메트릭")
    class RateLimitMetrics {

        @Test
        @DisplayName("recordRateLimitedLogin — auth_rate_limited_logins_total{by} 증가")
        void recordRateLimitedLogin() {
            authMetrics.recordRateLimitedLogin("email");

            double count = registry.counter("auth_rate_limited_logins_total", "by", "email").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("이메일 인증 메트릭")
    class EmailVerificationMetrics {

        @Test
        @DisplayName("recordEmailVerificationSent — auth_email_verification_total{action=sent} 증가")
        void recordEmailVerificationSent() {
            authMetrics.recordEmailVerificationSent("SIGNUP");

            double count = registry.counter("auth_email_verification_total",
                    "type", "SIGNUP", "action", "sent").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordEmailVerificationSuccess — auth_email_verification_total{action=success} 증가")
        void recordEmailVerificationSuccess() {
            authMetrics.recordEmailVerificationSuccess("SIGNUP");

            double count = registry.counter("auth_email_verification_total",
                    "type", "SIGNUP", "action", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordEmailVerificationFailure — 메인 카운터 + failure 카운터 2개 증가")
        void recordEmailVerificationFailure() {
            authMetrics.recordEmailVerificationFailure("SIGNUP", "expired");

            double mainCount = registry.counter("auth_email_verification_total",
                    "type", "SIGNUP", "action", "failure").count();
            double failureCount = registry.counter("auth_email_verification_failure_total",
                    "type", "SIGNUP", "reason", "expired").count();

            assertThat(mainCount).isEqualTo(1.0);
            assertThat(failureCount).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("타이머 메트릭")
    class TimerMetrics {

        @Test
        @DisplayName("recordOperationDuration — Timer 등록 확인")
        void recordOperationDuration() {
            authMetrics.recordOperationDuration("oauth2_login", "GOOGLE", true, Duration.ofMillis(100));

            long timerCount = registry.timer("auth_operation_duration_seconds",
                    "operation", "oauth2_login", "channel", "GOOGLE", "success", "true").count();
            assertThat(timerCount).isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("startSample/stopSample — 타이머 기록 후 count=1")
        void startAndStopSample() throws InterruptedException {
            Timer.Sample sample = authMetrics.startSample();
            authMetrics.stopSample(sample, "passkey_authentication", "PASSKEY", true);

            long count = registry.timer("auth_operation_duration_seconds",
                    "operation", "passkey_authentication", "channel", "PASSKEY", "success", "true").count();
            assertThat(count).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("IP 접근 제어 메트릭")
    class IpMetrics {

        @Test
        @DisplayName("recordIpBlocked — ip_blocked_total{reason} 증가")
        void recordIpBlocked() {
            authMetrics.recordIpBlocked("manual");

            double count = registry.counter("ip_blocked_total", "reason", "manual").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("recordIpRuleCreated — ip_rule_created_total{type} 증가")
        void recordIpRuleCreated() {
            authMetrics.recordIpRuleCreated("BLOCK");

            double count = registry.counter("ip_rule_created_total", "type", "BLOCK").count();
            assertThat(count).isEqualTo(1.0);
        }
    }
}
