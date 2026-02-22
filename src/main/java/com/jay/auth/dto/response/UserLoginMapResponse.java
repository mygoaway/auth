package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserLoginMapResponse {

    private List<LocationEntry> locations;
    private String period;

    @Getter
    @Builder
    public static class LocationEntry {
        private String location;
        private long successCount;
        private long failureCount;
        private String lastLoginAt;
    }
}
