package com.jay.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * SMS 발송 구현체 (개발용 - 로그 출력만)
 * app.sms.provider=log 이거나 미설정 시 사용
 * 실제 운영에서는 CoolSmsSender 등으로 교체
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    @Override
    public void sendVerificationCode(String phone, String code) {
        log.info("========================================");
        log.info("[SMS] 인증 코드 발송");
        log.info("수신자: {}", phone);
        log.info("인증 코드: {}", code);
        log.info("========================================");
    }
}
