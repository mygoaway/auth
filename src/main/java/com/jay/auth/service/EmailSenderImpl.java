package com.jay.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이메일 발송 구현체 (개발용 - 로그 출력만)
 * 실제 운영에서는 SMTP, AWS SES 등으로 교체
 */
@Slf4j
@Service
public class EmailSenderImpl implements EmailSender {

    @Override
    public void sendVerificationCode(String to, String code) {
        log.info("========================================");
        log.info("[이메일] 인증 코드 발송");
        log.info("수신자: {}", to);
        log.info("인증 코드: {}", code);
        log.info("========================================");
    }

    @Override
    public void sendNewDeviceLoginAlert(String to, String deviceInfo, String ipAddress, String location, String loginTime) {
        log.info("========================================");
        log.info("[보안 알림] 새 기기에서 로그인");
        log.info("수신자: {}", to);
        log.info("기기 정보: {}", deviceInfo);
        log.info("IP 주소: {}", ipAddress);
        log.info("위치: {}", location != null ? location : "알 수 없음");
        log.info("시간: {}", loginTime);
        log.info("========================================");
    }

    @Override
    public void sendPasswordChangedAlert(String to, String changeTime) {
        log.info("========================================");
        log.info("[보안 알림] 비밀번호 변경됨");
        log.info("수신자: {}", to);
        log.info("변경 시간: {}", changeTime);
        log.info("========================================");
    }

    @Override
    public void sendAccountLinkedAlert(String to, String channelName, String linkedTime) {
        log.info("========================================");
        log.info("[보안 알림] 계정 연동됨");
        log.info("수신자: {}", to);
        log.info("연동 채널: {}", channelName);
        log.info("연동 시간: {}", linkedTime);
        log.info("========================================");
    }

    @Override
    public void sendAccountUnlinkedAlert(String to, String channelName, String unlinkedTime) {
        log.info("========================================");
        log.info("[보안 알림] 계정 연동 해제됨");
        log.info("수신자: {}", to);
        log.info("해제 채널: {}", channelName);
        log.info("해제 시간: {}", unlinkedTime);
        log.info("========================================");
    }
}
