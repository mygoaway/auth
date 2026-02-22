package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LoginHeatmapResponse {

    private List<CountryStats> heatmap;
    private List<String> suspiciousRegions;
    private String period;

    @Getter
    @Builder
    public static class CountryStats {
        private String country;
        private String city;
        private long totalCount;
        private long failureCount;
        private double failureRate;
    }
}
