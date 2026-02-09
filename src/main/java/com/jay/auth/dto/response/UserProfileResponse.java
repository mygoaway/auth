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
public class UserProfileResponse {

    private String userUuid;
    private String email;
    private String recoveryEmail;
    private String phone;
    private String nickname;
    private String status;
    private List<ChannelInfo> channels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelInfo {
        private String channelCode;
        private String channelEmail;
        private LocalDateTime linkedAt;
    }
}
