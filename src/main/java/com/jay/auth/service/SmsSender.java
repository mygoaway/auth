package com.jay.auth.service;

/**
 * SMS 발송 인터페이스
 * 실제 구현은 CoolSMS, Aligo, AWS SNS 등으로 대체 가능
 */
public interface SmsSender {

    /**
     * 인증 코드 SMS 발송
     * @param phone 수신 핸드폰 번호
     * @param code 인증 코드
     */
    void sendVerificationCode(String phone, String code);
}
