package com.jay.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedDeviceResponse {

    private String deviceId;
    private String deviceType;
    private String browser;
    private String os;
    private String ipAddress;
    private String location;
    private String trustedAt;
    private String lastUsedAt;
}
