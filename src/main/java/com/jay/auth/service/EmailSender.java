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
}
