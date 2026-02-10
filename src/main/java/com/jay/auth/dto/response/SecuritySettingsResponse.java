package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySettingsResponse {

    private boolean loginNotificationEnabled;
    private boolean suspiciousActivityNotificationEnabled;
    private boolean accountLocked;
    private String lockReason;
    private int failedLoginAttempts;
    private int maxFailedAttempts;
}
