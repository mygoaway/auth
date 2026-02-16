package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginStatsResponse {

    private long todayLogins;
    private long todaySignups;
    private long activeUsersLast7Days;
    private List<Map<String, Object>> dailyLogins;
    private List<Map<String, Object>> dailySignups;
}
