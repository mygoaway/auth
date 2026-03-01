package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 계정 잠금 전용 서비스.
 * 로그인 실패 누적 → 자동 잠금 → 이메일 알림 → Admin 수동 해제 흐름을 담당.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailSender emailSender;
    private final EncryptionService encryptionService;
    private final MeterRegistry meterRegistry;

    private static final String LOCK_ATTEMPTS_PREFIX  = "lock:attempts:";
    private static final String LOCK_REASON_PREFIX    = "lock:reason:";
    private static final int    MAX_FAILED_ATTEMPTS   = 10;
    private static final long   ATTEMPTS_WINDOW_HOURS = 1;

    /**
     * 로그인 실패를 기록하고, 임계치 초과 시 계정을 자동 잠금한다.
     */
    @Transactional
    public void recordFailedAttempt(Long userId) {
        String key = LOCK_ATTEMPTS_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ATTEMPTS_WINDOW_HOURS, TimeUnit.HOURS);
        }

        log.debug("Failed login attempt recorded: userId={}, count={}", userId, count);

        if (count != null && count >= MAX_FAILED_ATTEMPTS) {
            String reason = "로그인 " + count + "회 연속 실패로 자동 잠금되었습니다.";
            lockAccount(userId, reason, true);
        }
    }

    /**
     * 로그인 성공 시 실패 카운트를 초기화한다.
     */
    public void clearFailedAttempts(Long userId) {
        redisTemplate.delete(LOCK_ATTEMPTS_PREFIX + userId);
    }

    /**
     * 계정을 잠금한다.
     *
     * @param notify true이면 비동기로 사용자에게 잠금 알림 이메일을 발송한다.
     */
    @Transactional
    public void lockAccount(Long userId, String reason, boolean notify) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.PENDING_DELETE) {
            return;
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            return;
        }

        user.updateStatus(UserStatus.LOCKED);
        redisTemplate.opsForValue().set(LOCK_REASON_PREFIX + userId, reason);
        redisTemplate.delete(LOCK_ATTEMPTS_PREFIX + userId);

        Counter.builder("account_locked_total")
                .tag("reason", notify ? "auto" : "manual")
                .register(meterRegistry)
                .increment();

        log.warn("Account locked: userId={}, reason={}", userId, reason);

        if (notify) {
            sendLockNotificationAsync(user, reason);
        }
    }

    /**
     * 계정 잠금을 해제한다 (Admin 수동).
     */
    @Transactional
    public void unlockAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.LOCKED) {
            return;
        }

        user.updateStatus(UserStatus.ACTIVE);
        redisTemplate.delete(LOCK_REASON_PREFIX + userId);
        redisTemplate.delete(LOCK_ATTEMPTS_PREFIX + userId);

        Counter.builder("account_unlocked_total")
                .tag("by", "admin")
                .register(meterRegistry)
                .increment();

        log.info("Account unlocked: userId={}", userId);
    }

    /**
     * 현재 실패 횟수를 반환한다.
     */
    public int getFailedAttemptCount(Long userId) {
        Object val = redisTemplate.opsForValue().get(LOCK_ATTEMPTS_PREFIX + userId);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 잠금 사유를 반환한다.
     */
    public String getLockReason(Long userId) {
        Object val = redisTemplate.opsForValue().get(LOCK_REASON_PREFIX + userId);
        return val != null ? val.toString() : null;
    }

    @Async
    public void sendLockNotificationAsync(User user, String reason) {
        try {
            if (user.getEmailEnc() == null) return;
            String email = encryptionService.decryptEmail(user.getEmailEnc());
            emailSender.sendAccountLockedAlert(email, reason);
            log.info("Account lock notification sent: userId={}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send account lock notification: userId={}", user.getId(), e);
        }
    }
}
