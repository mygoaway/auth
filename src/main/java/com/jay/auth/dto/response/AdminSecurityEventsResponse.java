package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSecurityEventsResponse {

    private long failedLoginsToday;
    private long passwordChangesToday;
    private long accountLocksToday;
    private List<FailedLoginInfo> recentFailedLogins;
    private List<AuditEventInfo> recentAuditEvents;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedLoginInfo {
        private Long userId;
        private String ipAddress;
        private String browser;
        private String os;
        private String location;
        private String failureReason;
        private String createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEventInfo {
        private Long userId;
        private String action;
        private String target;
        private String detail;
        private String ipAddress;
        private boolean success;
        private String createdAt;
    }
}
