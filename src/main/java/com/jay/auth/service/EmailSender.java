package com.jay.auth.service;

/**
 * 이메일 발송 인터페이스
 * 실제 구현은 SMTP, SES 등으로 대체 가능
 */
public interface EmailSender {

    /**
     * 인증 코드 이메일 발송
     * @param to 수신자 이메일
     * @param code 인증 코드
     */
    void sendVerificationCode(String to, String code);

    /**
     * 새 기기 로그인 알림 발송
     */
    void sendNewDeviceLoginAlert(String to, String deviceInfo, String ipAddress, String location, String loginTime);

    /**
     * 비밀번호 변경 알림 발송
     */
    void sendPasswordChangedAlert(String to, String changeTime);

    /**
     * 계정 연동 알림 발송
     */
    void sendAccountLinkedAlert(String to, String channelName, String linkedTime);

    /**
     * 계정 연동 해제 알림 발송
     */
    void sendAccountUnlinkedAlert(String to, String channelName, String unlinkedTime);
}
