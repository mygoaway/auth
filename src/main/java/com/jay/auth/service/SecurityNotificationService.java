package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.security.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jay.auth.util.DateTimeUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 보안 관련 이메일 알림 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityNotificationService {

    private final EmailSender emailSender;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeUtil.DEFAULT_FORMATTER;

    /**
     * 새 기기 로그인 알림
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyNewDeviceLogin(Long userId, TokenStore.SessionInfo sessionInfo) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String deviceInfo = String.format("%s / %s (%s)",
                    sessionInfo.browser(), sessionInfo.os(), sessionInfo.deviceType());
            String loginTime = LocalDateTime.now().format(DATE_FORMATTER);

            emailSender.sendNewDeviceLoginAlert(
                    email,
                    deviceInfo,
                    sessionInfo.ipAddress(),
                    sessionInfo.location(),
                    loginTime
            );

            log.info("New device login notification sent to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send new device login notification", e);
        }
    }

    /**
     * 비밀번호 변경 알림
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyPasswordChanged(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String changeTime = LocalDateTime.now().format(DATE_FORMATTER);

            emailSender.sendPasswordChangedAlert(email, changeTime);

            log.info("Password changed notification sent to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send password changed notification", e);
        }
    }

    /**
     * 계정 연동 알림
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyAccountLinked(Long userId, ChannelCode channelCode) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String channelName = getChannelDisplayName(channelCode);
            String linkedTime = LocalDateTime.now().format(DATE_FORMATTER);

            emailSender.sendAccountLinkedAlert(email, channelName, linkedTime);

            log.info("Account linked notification sent to user: {}, channel: {}", userId, channelCode);
        } catch (Exception e) {
            log.error("Failed to send account linked notification", e);
        }
    }

    /**
     * 계정 연동 해제 알림
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyAccountUnlinked(Long userId, ChannelCode channelCode) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String channelName = getChannelDisplayName(channelCode);
            String unlinkedTime = LocalDateTime.now().format(DATE_FORMATTER);

            emailSender.sendAccountUnlinkedAlert(email, channelName, unlinkedTime);

            log.info("Account unlinked notification sent to user: {}, channel: {}", userId, channelCode);
        } catch (Exception e) {
            log.error("Failed to send account unlinked notification", e);
        }
    }

    /**
     * 비밀번호 만료 임박 알림
     * @param userId 사용자 ID
     * @param daysLeft 만료까지 남은 일수
     * @param expireDate 만료 예정일
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyPasswordExpiringSoon(Long userId, int daysLeft, LocalDateTime expireDate) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String expireDateStr = expireDate.format(DATE_FORMATTER);

            emailSender.sendPasswordExpiringSoonAlert(email, daysLeft, expireDateStr);

            log.info("비밀번호 만료 임박 알림 발송 완료 - userId: {}, daysLeft: {}", userId, daysLeft);
        } catch (Exception e) {
            log.error("비밀번호 만료 임박 알림 발송 실패 - userId: {}", userId, e);
        }
    }

    /**
     * 비밀번호 만료 알림
     * @param userId 사용자 ID
     * @param expireDate 만료일
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyPasswordExpired(Long userId, LocalDateTime expireDate) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String expireDateStr = expireDate.format(DATE_FORMATTER);

            emailSender.sendPasswordExpiredAlert(email, expireDateStr);

            log.info("비밀번호 만료 알림 발송 완료 - userId: {}", userId);
        } catch (Exception e) {
            log.error("비밀번호 만료 알림 발송 실패 - userId: {}", userId, e);
        }
    }

    /**
     * 패스키 등록 알림
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyPasskeyRegistered(Long userId, String deviceName) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String registeredTime = LocalDateTime.now().format(DATE_FORMATTER);

            emailSender.sendPasskeyRegisteredAlert(email, deviceName, registeredTime);

            log.info("패스키 등록 알림 발송 완료 - userId: {}, deviceName: {}", userId, deviceName);
        } catch (Exception e) {
            log.error("패스키 등록 알림 발송 실패 - userId: {}", userId, e);
        }
    }

    /**
     * 패스키 삭제 알림
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyPasskeyRemoved(Long userId, String deviceName) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmailEnc() == null) {
                return;
            }

            String email = encryptionService.decryptEmail(user.getEmailEnc());
            String removedTime = LocalDateTime.now().format(DATE_FORMATTER);

            emailSender.sendPasskeyRemovedAlert(email, deviceName, removedTime);

            log.info("패스키 삭제 알림 발송 완료 - userId: {}, deviceName: {}", userId, deviceName);
        } catch (Exception e) {
            log.error("패스키 삭제 알림 발송 실패 - userId: {}", userId, e);
        }
    }

    private String getChannelDisplayName(ChannelCode channelCode) {
        return switch (channelCode) {
            case EMAIL -> "이메일";
            case GOOGLE -> "Google";
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
        };
    }
}
