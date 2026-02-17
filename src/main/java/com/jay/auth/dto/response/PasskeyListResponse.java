package com.jay.auth.dto.response;

import com.jay.auth.domain.entity.UserPasskey;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PasskeyListResponse {

    private List<PasskeyInfo> passkeys;

    @Getter
    @Builder
    public static class PasskeyInfo {
        private Long id;
        private String deviceName;
        private LocalDateTime createdAt;
        private LocalDateTime lastUsedAt;
    }

    public static PasskeyListResponse from(List<UserPasskey> passkeys) {
        List<PasskeyInfo> infos = passkeys.stream()
                .map(p -> PasskeyInfo.builder()
                        .id(p.getId())
                        .deviceName(p.getDeviceName())
                        .createdAt(p.getCreatedAt())
                        .lastUsedAt(p.getLastUsedAt())
                        .build())
                .toList();

        return PasskeyListResponse.builder()
                .passkeys(infos)
                .build();
    }
}
