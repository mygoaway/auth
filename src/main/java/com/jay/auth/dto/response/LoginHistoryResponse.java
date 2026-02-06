package com.jay.auth.dto.response;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.enums.ChannelCode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LoginHistoryResponse {

    private Long id;
    private ChannelCode channelCode;
    private String ipAddress;
    private String deviceType;
    private String browser;
    private String os;
    private String location;
    private Boolean isSuccess;
    private String failureReason;
    private LocalDateTime createdAt;

    public static LoginHistoryResponse from(LoginHistory history) {
        return LoginHistoryResponse.builder()
                .id(history.getId())
                .channelCode(history.getChannelCode())
                .ipAddress(maskIpAddress(history.getIpAddress()))
                .deviceType(history.getDeviceType())
                .browser(history.getBrowser())
                .os(history.getOs())
                .location(history.getLocation())
                .isSuccess(history.getIsSuccess())
                .failureReason(history.getFailureReason())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private static String maskIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "Unknown";
        }
        // Mask last octet for privacy
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".*";
        }
        return ip;
    }
}
