package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyActivityResponse {

    private LocalDateTime weekStart;
    private LocalDateTime weekEnd;
    private LoginStats loginStats;
    private List<DeviceBreakdown> deviceBreakdown;
    private List<LocationInfo> locations;
    private List<AccountEvent> accountEvents;
    private List<String> securityAlerts;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginStats {
        private int totalLogins;
        private int successfulLogins;
        private int failedLogins;
        private List<ChannelCount> loginsByChannel;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelCount {
        private String channel;
        private long count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceBreakdown {
        private String deviceType;
        private String browser;
        private String os;
        private long count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String location;
        private long count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountEvent {
        private String action;
        private String detail;
        private LocalDateTime occurredAt;
    }
}
