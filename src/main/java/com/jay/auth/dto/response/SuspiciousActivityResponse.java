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
public class SuspiciousActivityResponse {

    private String riskLevel;
    private int riskScore;
    private List<SuspiciousEvent> events;
    private List<String> recommendations;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousEvent {
        private String type;
        private String severity;
        private String description;
        private String ipAddress;
        private String location;
        private LocalDateTime detectedAt;
    }
}
