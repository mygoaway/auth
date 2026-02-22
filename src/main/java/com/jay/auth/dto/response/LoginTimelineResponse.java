package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LoginTimelineResponse {

    private List<HourlySlot> timeline;
    private int peakHour;
    private long peakCount;
    private String period;

    @Getter
    @Builder
    public static class HourlySlot {
        private int hour;
        private long successCount;
        private long failureCount;
    }
}
