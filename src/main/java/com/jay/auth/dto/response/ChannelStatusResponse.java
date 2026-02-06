package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ChannelStatusResponse {

    private List<ChannelStatus> channels;

    @Getter
    @Builder
    public static class ChannelStatus {
        private String channelCode;
        private String description;
        private boolean linked;
        private String channelEmail;
        private LocalDateTime linkedAt;
    }
}
