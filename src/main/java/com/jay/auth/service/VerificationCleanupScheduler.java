package com.jay.auth.service;

import com.jay.auth.repository.EmailVerificationRepository;
import com.jay.auth.repository.PhoneVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료된 인증 레코드 자동 정리 스케줄러
 * 매 시간마다 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCleanupScheduler {

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    @Scheduled(cron = "0 0 * * * *")  // 매 시간 정각
    @Transactional
    public void cleanupExpiredVerifications() {
        LocalDateTime now = LocalDateTime.now();

        int deletedPhone = phoneVerificationRepository.deleteExpiredVerifications(now);
        int deletedEmail = emailVerificationRepository.deleteExpired(now);

        if (deletedPhone > 0 || deletedEmail > 0) {
            log.info("Expired verifications cleaned up - phone: {}, email: {}", deletedPhone, deletedEmail);
        }
    }
}
