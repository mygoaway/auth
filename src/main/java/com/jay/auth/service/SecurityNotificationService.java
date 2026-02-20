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
            log.info("Passkey registered notification for user: {}, device: {}, email: {}", userId, deviceName, email);
        } catch (Exception e) {
            log.error("Failed to send passkey registered notification", e);
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
            log.info("Passkey removed notification for user: {}, device: {}, email: {}", userId, deviceName, email);
        } catch (Exception e) {
            log.error("Failed to send passkey removed notification", e);
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
