package com.jay.auth.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 인증 서비스 비즈니스 메트릭 중앙 관리
 */
@Component
public class AuthMetrics {

    private final MeterRegistry registry;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLoginSuccess(String channel) {
        Counter.builder("auth_login_attempts_total")
                .tag("channel", channel)
                .tag("result", "success")
                .register(registry)
                .increment();
    }

    public void recordLoginFailure(String channel) {
        Counter.builder("auth_login_attempts_total")
                .tag("channel", channel)
                .tag("result", "fail")
                .register(registry)
                .increment();
    }

    public void recordSignUp(String channel) {
        Counter.builder("auth_signup_total")
                .tag("channel", channel)
                .register(registry)
                .increment();
    }

    public void recordTokenIssued(String type, String channel) {
        Counter.builder("auth_token_issued_total")
                .tag("type", type)
                .tag("channel", channel)
                .register(registry)
                .increment();
    }

    public void recordTokenRefreshSuccess() {
        Counter.builder("auth_token_refresh_total")
                .tag("result", "success")
                .register(registry)
                .increment();
    }

    public void recordTokenRefreshFailure() {
        Counter.builder("auth_token_refresh_total")
                .tag("result", "fail")
                .register(registry)
                .increment();
    }

    public void recordLogout(String type) {
        Counter.builder("auth_logout_total")
                .tag("type", type)
                .register(registry)
                .increment();
    }

    public void recordRateLimitedLogin(String by) {
        Counter.builder("auth_rate_limited_logins_total")
                .tag("by", by)
                .register(registry)
                .increment();
    }

    public void recordEmailVerificationSent(String type) {
        Counter.builder("auth_email_verification_total")
                .tag("type", type)
                .tag("action", "sent")
                .register(registry)
                .increment();
    }

    public void recordEmailVerificationSuccess(String type) {
        Counter.builder("auth_email_verification_total")
                .tag("type", type)
                .tag("action", "success")
                .register(registry)
                .increment();
    }

    public void recordEmailVerificationFailure(String type, String reason) {
        Counter.builder("auth_email_verification_total")
                .tag("type", type)
                .tag("action", "failure")
                .register(registry)
                .increment();
        Counter.builder("auth_email_verification_failure_total")
                .tag("type", type)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer 메서드 — auth_operation_duration_seconds
    // (AOP @AuthTimed에서 자동 기록. 직접 호출이 필요한 경우 아래 메서드 사용)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 인증 작업 소요 시간을 직접 기록한다.
     * AOP로 처리하기 어려운 콜백 기반 플로우(예: OAuth2 핸들러)에서 사용.
     *
     * @param operation 작업 이름 (예: "oauth2_login", "passkey_authentication")
     * @param channel   채널/IDP 이름 (예: "GOOGLE", "EMAIL")
     * @param success   성공 여부
     * @param duration  측정된 소요 시간
     */
    public void recordOperationDuration(String operation, String channel, boolean success, Duration duration) {
        Timer.builder("auth_operation_duration_seconds")
                .description("인증 서비스 주요 작업 실행 시간")
                .tag("operation", operation)
                .tag("channel", channel)
                .tag("success", String.valueOf(success))
                .register(registry)
                .record(duration);
    }

    /**
     * Timer.Sample을 시작한다. {@link #stopSample(Timer.Sample, String, String, boolean)}과 쌍으로 사용.
     */
    public Timer.Sample startSample() {
        return Timer.start(registry);
    }

    /**
     * 시작된 Timer.Sample을 종료하고 메트릭을 기록한다.
     */
    public void stopSample(Timer.Sample sample, String operation, String channel, boolean success) {
        sample.stop(Timer.builder("auth_operation_duration_seconds")
                .description("인증 서비스 주요 작업 실행 시간")
                .tag("operation", operation)
                .tag("channel", channel)
                .tag("success", String.valueOf(success))
                .register(registry));
    }

}
