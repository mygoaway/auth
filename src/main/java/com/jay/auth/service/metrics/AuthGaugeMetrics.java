package com.jay.auth.service.metrics;

import com.jay.auth.repository.UserPasskeyRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.repository.UserTwoFactorRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 인증 서비스 게이지 메트릭
 *
 * <p>Gauge는 Counter와 달리 값이 증가하거나 감소할 수 있는 지표에 사용한다.
 * <ul>
 *   <li>auth_active_sessions — Redis에 존재하는 활성 세션 수 (로그인 시 증가, 로그아웃 시 감소)</li>
 *   <li>auth_registered_passkeys — DB에 등록된 패스키 총 수 (등록 시 증가, 삭제 시 감소)</li>
 *   <li>auth_locked_accounts — 현재 잠금 상태인 계정 수 (잠금 발생 시 증가, 해제/만료 시 감소)</li>
 *   <li>auth_2fa_enabled_users — 2FA를 활성화한 사용자 수 (활성화 시 증가, 비활성화 시 감소)</li>
 * </ul>
 */
@Slf4j
@Component
public class AuthGaugeMetrics {

    private static final String SESSION_KEY_PATTERN = "session:*";

    // 실시간 추적: 이벤트 발생 시 증감 (Redis/DB 조회 없이 즉시 반영)
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong registeredPasskeys = new AtomicLong(0);

    // 주기적 갱신: DB 집계 쿼리로 정확한 최신값 유지
    private final AtomicLong lockedAccounts = new AtomicLong(0);
    private final AtomicLong twoFactorEnabledUsers = new AtomicLong(0);

    private final StringRedisTemplate stringRedisTemplate;
    private final UserSignInInfoRepository userSignInInfoRepository;
    private final UserPasskeyRepository userPasskeyRepository;
    private final UserTwoFactorRepository userTwoFactorRepository;

    public AuthGaugeMetrics(MeterRegistry registry,
                            StringRedisTemplate stringRedisTemplate,
                            UserSignInInfoRepository userSignInInfoRepository,
                            UserPasskeyRepository userPasskeyRepository,
                            UserTwoFactorRepository userTwoFactorRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userSignInInfoRepository = userSignInInfoRepository;
        this.userPasskeyRepository = userPasskeyRepository;
        this.userTwoFactorRepository = userTwoFactorRepository;

        Gauge.builder("auth_active_sessions", activeSessions, AtomicLong::doubleValue)
                .description("현재 활성 세션 수 (Redis session:* 키 기반)")
                .register(registry);

        Gauge.builder("auth_registered_passkeys", registeredPasskeys, AtomicLong::doubleValue)
                .description("등록된 패스키 총 수")
                .register(registry);

        Gauge.builder("auth_locked_accounts", lockedAccounts, AtomicLong::doubleValue)
                .description("현재 잠금 상태인 계정 수")
                .register(registry);

        Gauge.builder("auth_2fa_enabled_users", twoFactorEnabledUsers, AtomicLong::doubleValue)
                .description("2FA를 활성화한 사용자 수")
                .register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 실시간 업데이트 메서드 — 이벤트 발생 시 서비스에서 직접 호출
    // ─────────────────────────────────────────────────────────────────────────

    /** 로그인 성공 또는 토큰 발급 시 활성 세션 증가 */
    public void incrementActiveSessions() {
        activeSessions.incrementAndGet();
    }

    /** 로그아웃 시 활성 세션 감소 */
    public void decrementActiveSessions() {
        activeSessions.updateAndGet(v -> Math.max(0, v - 1));
    }

    /** 전체 로그아웃 시 활성 세션 n개 감소 */
    public void decrementActiveSessions(long count) {
        activeSessions.updateAndGet(v -> Math.max(0, v - count));
    }

    /** 패스키 등록 시 증가 */
    public void incrementRegisteredPasskeys() {
        registeredPasskeys.incrementAndGet();
    }

    /** 패스키 삭제 시 감소 */
    public void decrementRegisteredPasskeys() {
        registeredPasskeys.updateAndGet(v -> Math.max(0, v - 1));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 주기적 동기화 — 앱 재시작 후 초기값 설정 및 드리프트 보정
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 1분마다 Redis의 실제 세션 키 수로 activeSessions를 보정한다.
     * 앱 재시작, 직접 Redis 조작 등으로 인한 AtomicLong 드리프트를 방지한다.
     */
    @Scheduled(fixedRate = 60_000)
    public void syncActiveSessions() {
        try {
            Set<String> keys = stringRedisTemplate.keys(SESSION_KEY_PATTERN);
            long count = keys != null ? keys.size() : 0;
            activeSessions.set(count);
        } catch (Exception e) {
            log.warn("활성 세션 게이지 동기화 실패", e);
        }
    }

    /**
     * 1분마다 DB의 실제 패스키 수로 registeredPasskeys를 보정한다.
     */
    @Scheduled(fixedRate = 60_000)
    public void syncRegisteredPasskeys() {
        try {
            long count = userPasskeyRepository.count();
            registeredPasskeys.set(count);
        } catch (Exception e) {
            log.warn("등록된 패스키 게이지 동기화 실패", e);
        }
    }

    /**
     * 2분마다 현재 잠금 상태 계정 수를 갱신한다.
     * 잠금은 5회 실패 시 설정되고, 30분 후 자동 만료되므로 주기적 갱신이 적합하다.
     */
    @Scheduled(fixedRate = 120_000)
    public void refreshLockedAccountsCount() {
        try {
            long count = userSignInInfoRepository.countLockedAccounts(LocalDateTime.now());
            lockedAccounts.set(count);
        } catch (Exception e) {
            log.warn("잠금 계정 게이지 갱신 실패", e);
        }
    }

    /**
     * 5분마다 2FA 활성화 사용자 수를 갱신한다.
     * 2FA 상태는 자주 변경되지 않으므로 긴 주기로 충분하다.
     */
    @Scheduled(fixedRate = 300_000)
    public void refresh2faEnabledUsersCount() {
        try {
            long count = userTwoFactorRepository.countByEnabled(true);
            twoFactorEnabledUsers.set(count);
        } catch (Exception e) {
            log.warn("2FA 활성화 사용자 게이지 갱신 실패", e);
        }
    }
}
