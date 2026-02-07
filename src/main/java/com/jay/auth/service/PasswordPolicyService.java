package com.jay.auth.service;

import com.jay.auth.domain.entity.PasswordHistory;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.repository.PasswordHistoryRepository;
import com.jay.auth.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordUtil passwordUtil;

    /**
     * 비밀번호 만료 일수 (기본 90일)
     */
    @Value("${app.security.password.expiration-days:90}")
    private int passwordExpirationDays;

    /**
     * 비밀번호 재사용 방지 개수 (기본 5개)
     */
    @Value("${app.security.password.history-count:5}")
    private int passwordHistoryCount;

    /**
     * 비밀번호 만료 여부 확인
     */
    public boolean isPasswordExpired(UserSignInInfo signInInfo) {
        if (signInInfo == null || signInInfo.getPasswordUpdatedAt() == null) {
            return false; // 첫 로그인이거나 업데이트 기록이 없으면 만료 아님
        }

        LocalDateTime expirationDate = signInInfo.getPasswordUpdatedAt()
                .plusDays(passwordExpirationDays);
        return LocalDateTime.now().isAfter(expirationDate);
    }

    /**
     * 비밀번호 만료까지 남은 일수
     */
    public int getDaysUntilExpiration(UserSignInInfo signInInfo) {
        if (signInInfo == null || signInInfo.getPasswordUpdatedAt() == null) {
            return passwordExpirationDays;
        }

        LocalDateTime expirationDate = signInInfo.getPasswordUpdatedAt()
                .plusDays(passwordExpirationDays);
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDateTime.now(), expirationDate);
        return Math.max(0, (int) daysRemaining);
    }

    /**
     * 비밀번호 재사용 여부 확인
     * @return true if password was previously used
     */
    public boolean isPasswordReused(Long userId, String newPassword) {
        List<PasswordHistory> histories = passwordHistoryRepository.findRecentByUserId(userId);

        // 최근 N개 비밀번호 이력 확인
        return histories.stream()
                .limit(passwordHistoryCount)
                .anyMatch(history -> passwordUtil.matches(newPassword, history.getPasswordHash()));
    }

    /**
     * 현재 비밀번호와 동일한지 확인
     */
    public boolean isSameAsCurrentPassword(String newPassword, String currentPasswordHash) {
        return passwordUtil.matches(newPassword, currentPasswordHash);
    }

    /**
     * 비밀번호 이력 저장
     */
    @Transactional
    public void savePasswordHistory(User user, String passwordHash) {
        PasswordHistory history = PasswordHistory.builder()
                .user(user)
                .passwordHash(passwordHash)
                .build();
        passwordHistoryRepository.save(history);

        // 오래된 이력 정리 (최근 N개만 유지)
        long count = passwordHistoryRepository.countByUserId(user.getId());
        if (count > passwordHistoryCount) {
            passwordHistoryRepository.deleteOldHistories(user.getId(), passwordHistoryCount);
        }

        log.debug("Password history saved for user: {}", user.getId());
    }

    /**
     * 사용자의 비밀번호 이력 삭제 (계정 탈퇴 시)
     */
    @Transactional
    public void deletePasswordHistory(Long userId) {
        passwordHistoryRepository.deleteByUserId(userId);
        log.debug("Password history deleted for user: {}", userId);
    }

    /**
     * 비밀번호 정책 정보
     */
    public PasswordPolicyInfo getPolicyInfo() {
        return new PasswordPolicyInfo(passwordExpirationDays, passwordHistoryCount);
    }

    public record PasswordPolicyInfo(int expirationDays, int historyCount) {}
}
