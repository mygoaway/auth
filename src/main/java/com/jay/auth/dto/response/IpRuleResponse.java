package com.jay.auth.dto.response;

import com.jay.auth.domain.entity.IpAccessRule;
import com.jay.auth.domain.enums.IpRuleType;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class IpRuleResponse {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long ruleId;
    private String ipAddress;
    private IpRuleType ruleType;
    private String reason;
    private Long createdBy;
    private Boolean isActive;
    private String expiredAt;
    private String createdAt;

    public static IpRuleResponse from(IpAccessRule rule) {
        return IpRuleResponse.builder()
                .ruleId(rule.getId())
                .ipAddress(rule.getIpAddress())
                .ruleType(rule.getRuleType())
                .reason(rule.getReason())
                .createdBy(rule.getCreatedBy())
                .isActive(rule.getIsActive())
                .expiredAt(rule.getExpiredAt() != null ? rule.getExpiredAt().format(FORMATTER) : null)
                .createdAt(rule.getCreatedAt() != null ? rule.getCreatedAt().format(FORMATTER) : null)
                .build();
    }
}
