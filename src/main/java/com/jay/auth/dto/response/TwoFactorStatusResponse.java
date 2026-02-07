package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TwoFactorStatusResponse {

    private boolean enabled;
    private int remainingBackupCodes;
    private LocalDateTime lastUsedAt;
}
