package com.jay.auth.dto.request;

import com.jay.auth.domain.enums.IpRuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class IpRuleCreateRequest {

    @NotBlank(message = "IP 주소는 필수입니다")
    private String ipAddress;

    @NotNull(message = "규칙 타입은 필수입니다")
    private IpRuleType ruleType;

    private String reason;

    private LocalDateTime expiredAt;
}
