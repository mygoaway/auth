package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.SecuritySettingsResponse;
import com.jay.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecuritySettingsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String PREF_PREFIX = "pref:security:";
    private static final String LOCK_REASON_PREFIX = "lock:reason:";
    private static final String LOCK_ATTEMPTS_PREFIX = "lock:attempts:";
    private static final int MAX_FAILED_BEFORE_LOCK = 10;
    private static final long LOCK_ATTEMPTS_WINDOW_HOURS = 1;

    // --- Notification Preferences ---

    public boolean isLoginNotificationEnabled(Long userId) {
        Object val = redisTemplate.opsForHash().get(buildPrefKey(userId), "loginNotification");
        return val == null || "true".equals(val.toString());
    }

    public boolean isSuspiciousNotificationEnabled(Long userId) {
        Object val = redisTemplate.opsForHash().get(buildPrefKey(userId), "suspiciousNotification");
        return val == null || "true".equals(val.toString());
    }

    public void updateLoginNotification(Long userId, boolean enabled) {
        redisTemplate.opsForHash().put(buildPrefKey(userId), "loginNotification", String.valueOf(enabled));
        log.info("Login notification updated: userId={}, enabled={}", userId, enabled);
    }

    public void updateSuspiciousNotification(Long userId, boolean enabled) {
        redisTemplate.opsForHash().put(buildPrefKey(userId), "suspiciousNotification", String.valueOf(enabled));
        log.info("Suspicious notification updated: userId={}, enabled={}", userId, enabled);
    }

    // --- Account Lock Management ---

    @Transactional
    public void recordFailedAttemptForLock(Long userId) {
        String key = LOCK_ATTEMPTS_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCK_ATTEMPTS_WINDOW_HOURS, TimeUnit.HOURS);
        }

        if (count != null && count >= MAX_FAILED_BEFORE_LOCK) {
            lockAccount(userId, "로그인 " + count + "회 연속 실패로 자동 잠금되었습니다.");
        }
    }

    @Transactional
    public void lockAccount(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.PENDING_DELETE) {
            return;
        }

        user.updateStatus(UserStatus.LOCKED);
        redisTemplate.opsForValue().set(LOCK_REASON_PREFIX + userId, reason);
        redisTemplate.delete(LOCK_ATTEMPTS_PREFIX + userId);

        log.warn("Account locked: userId={}, reason={}", userId, reason);
    }

    @Transactional
    public void unlockAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.LOCKED) {
            return;
        }

        user.updateStatus(UserStatus.ACTIVE);
        redisTemplate.delete(LOCK_REASON_PREFIX + userId);
        redisTemplate.delete(LOCK_ATTEMPTS_PREFIX + userId);

        log.info("Account unlocked: userId={}", userId);
    }

    public boolean isAccountLocked(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.LOCKED)
                .orElse(false);
    }

    // --- Get All Settings ---

    @Transactional(readOnly = true)
    public SecuritySettingsResponse getSecuritySettings(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        boolean locked = user != null && user.getStatus() == UserStatus.LOCKED;

        String lockReason = null;
        if (locked) {
            Object reason = redisTemplate.opsForValue().get(LOCK_REASON_PREFIX + userId);
            lockReason = reason != null ? reason.toString() : "계정이 잠금되었습니다.";
        }

        int failedAttempts = 0;
        Object attemptsVal = redisTemplate.opsForValue().get(LOCK_ATTEMPTS_PREFIX + userId);
        if (attemptsVal != null) {
            try {
                failedAttempts = Integer.parseInt(attemptsVal.toString());
            } catch (NumberFormatException ignored) {}
        }

        return SecuritySettingsResponse.builder()
                .loginNotificationEnabled(isLoginNotificationEnabled(userId))
                .suspiciousActivityNotificationEnabled(isSuspiciousNotificationEnabled(userId))
                .accountLocked(locked)
                .lockReason(lockReason)
                .failedLoginAttempts(failedAttempts)
                .maxFailedAttempts(MAX_FAILED_BEFORE_LOCK)
                .build();
    }

    private String buildPrefKey(Long userId) {
        return PREF_PREFIX + userId;
    }
}
