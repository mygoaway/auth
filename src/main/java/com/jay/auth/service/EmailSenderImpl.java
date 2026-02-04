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
        // TODO: 실제 이메일 발송 로직 구현
        log.info("========================================");
        log.info("이메일 발송 (개발 모드)");
        log.info("수신자: {}", to);
        log.info("인증 코드: {}", code);
        log.info("========================================");
    }
}
