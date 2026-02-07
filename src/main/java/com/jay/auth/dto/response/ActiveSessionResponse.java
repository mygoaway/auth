package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ActiveSessionResponse {

    private String sessionId;
    private String deviceType;
    private String browser;
    private String os;
    private String ipAddress;
    private String location;
    private LocalDateTime lastActivity;
    private boolean currentSession;

    public static ActiveSessionResponse of(
            String sessionId,
            String deviceType,
            String browser,
            String os,
            String ipAddress,
            String location,
            LocalDateTime lastActivity,
            boolean currentSession) {
        return ActiveSessionResponse.builder()
                .sessionId(sessionId)
                .deviceType(deviceType)
                .browser(browser)
                .os(os)
                .ipAddress(ipAddress)
                .location(location)
                .lastActivity(lastActivity)
                .currentSession(currentSession)
                .build();
    }
}
