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

    /**
     * 패스키 등록 알림 발송
     */
    void sendPasskeyRegisteredAlert(String to, String deviceName, String registeredTime);

    /**
     * 패스키 삭제 알림 발송
     */
    void sendPasskeyRemovedAlert(String to, String deviceName, String removedTime);

    /**
     * 비밀번호 만료 임박 알림 발송
     * @param to 수신자 이메일
     * @param daysLeft 만료까지 남은 일수
     * @param expireDate 만료 예정일 (yyyy-MM-dd HH:mm 형식)
     */
    void sendPasswordExpiringSoonAlert(String to, int daysLeft, String expireDate);

    /**
     * 비밀번호 만료 알림 발송
     * @param to 수신자 이메일
     * @param expireDate 만료일
     */
    void sendPasswordExpiredAlert(String to, String expireDate);
}
