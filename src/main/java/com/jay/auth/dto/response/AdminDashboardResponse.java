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
public class AdminDashboardResponse {

    private UserStats userStats;
    private List<AdminUserInfo> recentUsers;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStats {
        private long totalUsers;
        private long activeUsers;
        private long dormantUsers;
        private long pendingDeleteUsers;
        private long todaySignups;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserInfo {
        private Long userId;
        private String userUuid;
        private String email;
        private String nickname;
        private String status;
        private String role;
        private List<String> channels;
        private String createdAt;
        private String lastLoginAt;
    }
}
