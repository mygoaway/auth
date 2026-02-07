package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SecurityDashboardResponse {

    private int securityScore;
    private String securityLevel;
    private List<SecurityFactor> factors;
    private List<SecurityActivity> recentActivities;
    private List<String> recommendations;

    @Getter
    @Builder
    public static class SecurityFactor {
        private String name;
        private String description;
        private int score;
        private int maxScore;
        private boolean enabled;
    }

    @Getter
    @Builder
    public static class SecurityActivity {
        private String type;
        private String description;
        private String ipAddress;
        private String location;
        private String deviceInfo;
        private LocalDateTime occurredAt;
    }
}
