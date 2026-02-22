package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LoginFailureHotspotResponse {

    private List<HotspotEntry> hotspots;
    private long totalFailures;
    private String period;

    @Getter
    @Builder
    public static class HotspotEntry {
        private String ipAddress;
        private String location;
        private long failureCount;
        private String lastAttemptAt;
    }
}
