package com.jay.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service to manage login rate limiting to prevent brute force attacks.
 * Tracks failed login attempts per email and IP address.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String EMAIL_ATTEMPTS_PREFIX = "login:email:";
    private static final String IP_ATTEMPTS_PREFIX = "login:ip:";
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS_PER_EMAIL = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 20;

    /**
     * Record a failed login attempt for an email
     */
    public void recordFailedAttempt(String email, String ipAddress) {
        incrementAttempt(EMAIL_ATTEMPTS_PREFIX + email.toLowerCase());
        incrementAttempt(IP_ATTEMPTS_PREFIX + ipAddress);
        log.debug("Recorded failed login attempt: email={}, ip={}", email, ipAddress);
    }

    /**
     * Clear failed attempts after successful login
     */
    public void clearFailedAttempts(String email, String ipAddress) {
        redisTemplate.delete(EMAIL_ATTEMPTS_PREFIX + email.toLowerCase());
        // Note: We don't clear IP attempts on success to prevent circumvention
        log.debug("Cleared failed login attempts for email: {}", email);
    }

    /**
     * Check if login is allowed (not rate limited)
     */
    public boolean isLoginAllowed(String email, String ipAddress) {
        int emailAttempts = getAttemptCount(EMAIL_ATTEMPTS_PREFIX + email.toLowerCase());
        int ipAttempts = getAttemptCount(IP_ATTEMPTS_PREFIX + ipAddress);

        if (emailAttempts >= MAX_ATTEMPTS_PER_EMAIL) {
            log.warn("Login rate limited for email: {} (attempts: {})", email, emailAttempts);
            return false;
        }

        if (ipAttempts >= MAX_ATTEMPTS_PER_IP) {
            log.warn("Login rate limited for IP: {} (attempts: {})", ipAddress, ipAttempts);
            return false;
        }

        return true;
    }

    /**
     * Get remaining attempts for an email
     */
    public int getRemainingAttempts(String email) {
        int attempts = getAttemptCount(EMAIL_ATTEMPTS_PREFIX + email.toLowerCase());
        return Math.max(0, MAX_ATTEMPTS_PER_EMAIL - attempts);
    }

    /**
     * Get time until rate limit expires for an email
     */
    public long getRetryAfterSeconds(String email) {
        Long ttl = redisTemplate.getExpire(EMAIL_ATTEMPTS_PREFIX + email.toLowerCase());
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    private void incrementAttempt(String key) {
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            // Set expiration only on first attempt
            redisTemplate.expire(key, WINDOW_DURATION);
        }
    }

    private int getAttemptCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
