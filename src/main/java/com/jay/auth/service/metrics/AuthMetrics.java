package com.jay.auth.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

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

}
