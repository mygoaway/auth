package com.jay.auth.exception;

import org.springframework.http.HttpStatus;

public class IpRuleNotFoundException extends BusinessException {

    public IpRuleNotFoundException() {
        super("IP 규칙을 찾을 수 없습니다", "IP_RULE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
