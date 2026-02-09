package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCleanupScheduler {

    private static final int DELETION_GRACE_PERIOD_DAYS = 30;
    private static final int DORMANT_THRESHOLD_DAYS = 90;
    private static final int LOGIN_HISTORY_RETENTION_DAYS = 180;

    private final UserRepository userRepository;
    private final UserTwoFactorRepository userTwoFactorRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    /**
     * 매일 새벽 3시에 실행
     * 1) PENDING_DELETE 상태에서 30일 경과한 사용자 영구 삭제
     * 2) 90일 이상 미접속 사용자 휴면 전환
     * 3) 180일 이상 된 로그인 이력 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void executeCleanup() {
        log.info("Account cleanup batch started");

        int permanentlyDeleted = processExpiredDeletions();
        int dormantConverted = processDormantAccounts();
        int historyDeleted = cleanupOldLoginHistory();

        log.info("Account cleanup batch completed - deleted: {}, dormant: {}, history cleaned: {}",
                permanentlyDeleted, dormantConverted, historyDeleted);
    }

    private int processExpiredDeletions() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(DELETION_GRACE_PERIOD_DAYS);
        List<User> expiredUsers = userRepository.findExpiredPendingDeletions(cutoffDate);

        for (User user : expiredUsers) {
            try {
                deleteUserData(user);
                log.info("Permanently deleted user: {}", user.getUserUuid());
            } catch (Exception e) {
                log.error("Failed to delete user {}: {}", user.getUserUuid(), e.getMessage());
            }
        }

        return expiredUsers.size();
    }

    private void deleteUserData(User user) {
        Long userId = user.getId();

        // 연관 데이터 삭제 (cascade로 처리되지 않는 항목들)
        userTwoFactorRepository.deleteByUserId(userId);
        passwordHistoryRepository.deleteByUserId(userId);
        loginHistoryRepository.deleteByUserId(userId);

        // User 삭제 (channels, signInInfo는 cascade로 자동 삭제)
        user.updateStatus(UserStatus.DELETED);
        user.getChannels().clear();
        if (user.getSignInInfo() != null) {
            user.setSignInInfo(null);
        }

        // 개인정보 제거
        user.updateEmail(null, null);
        user.updateRecoveryEmail(null, null);
        user.updatePhone(null);
        user.updateNickname(null);
    }

    private int processDormantAccounts() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(DORMANT_THRESHOLD_DAYS);
        List<User> dormantCandidates = userRepository.findDormantCandidates(cutoffDate);

        for (User user : dormantCandidates) {
            try {
                user.updateStatus(UserStatus.DORMANT);
                log.info("Converted to dormant: {}", user.getUserUuid());
            } catch (Exception e) {
                log.error("Failed to convert user {} to dormant: {}", user.getUserUuid(), e.getMessage());
            }
        }

        return dormantCandidates.size();
    }

    private int cleanupOldLoginHistory() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(LOGIN_HISTORY_RETENTION_DAYS);
        loginHistoryRepository.deleteOldHistory(cutoffDate);
        return 0; // deleteOldHistory는 void 반환
    }
}
