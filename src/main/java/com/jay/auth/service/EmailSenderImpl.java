package com.jay.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 이메일 발송 구현체 (개발용 - 로그 출력만)
 * 실제 운영에서는 app.email.provider=smtp 로 변경
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
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

    @Override
    public void sendPasskeyRegisteredAlert(String to, String deviceName, String registeredTime) {
        log.info("========================================");
        log.info("[보안 알림] 패스키 등록됨");
        log.info("수신자: {}", to);
        log.info("기기 이름: {}", deviceName);
        log.info("등록 시간: {}", registeredTime);
        log.info("========================================");
    }

    @Override
    public void sendPasskeyRemovedAlert(String to, String deviceName, String removedTime) {
        log.info("========================================");
        log.info("[보안 알림] 패스키 삭제됨");
        log.info("수신자: {}", to);
        log.info("기기 이름: {}", deviceName);
        log.info("삭제 시간: {}", removedTime);
        log.info("========================================");
    }

    @Override
    public void sendPasswordExpiringSoonAlert(String to, int daysLeft, String expireDate) {
        log.info("========================================");
        log.info("[보안 알림] 비밀번호 만료 임박");
        log.info("수신자: {}", to);
        log.info("남은 일수: {}일", daysLeft);
        log.info("만료 예정일: {}", expireDate);
        log.info("========================================");
    }

    @Override
    public void sendPasswordExpiredAlert(String to, String expireDate) {
        log.info("========================================");
        log.info("[보안 알림] 비밀번호 만료됨");
        log.info("수신자: {}", to);
        log.info("만료일: {}", expireDate);
        log.info("========================================");
    }

    @Override
    public void sendAccountLockedAlert(String to, String reason) {
        log.info("========================================");
        log.info("[보안 알림] 계정 잠금됨");
        log.info("수신자: {}", to);
        log.info("잠금 사유: {}", reason);
        log.info("========================================");
    }

    @Override
    public void sendPostLoginVerificationCode(String to, String code) {
        log.info("========================================");
        log.info("[보안 알림] 로그인 후 이메일 재인증 코드");
        log.info("수신자: {}", to);
        log.info("인증 코드: {}", code);
        log.info("========================================");
    }
}
