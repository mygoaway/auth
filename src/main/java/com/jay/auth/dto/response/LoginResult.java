package com.jay.auth.dto.response;

import com.jay.auth.domain.enums.ChannelCode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Internal DTO for login result before token issuance
 */
@Getter
@Builder
public class LoginResult {

    private Long userId;
    private String userUuid;
    private String email;
    private String nickname;
    private ChannelCode channelCode;
    private String role;
    private boolean pendingDeletion;
    private LocalDateTime deletionRequestedAt;

    public static LoginResult of(Long userId, String userUuid, String email, String nickname, ChannelCode channelCode) {
        return LoginResult.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .channelCode(channelCode)
                .role("USER")
                .pendingDeletion(false)
                .build();
    }

    public static LoginResult of(Long userId, String userUuid, String email, String nickname,
                                 ChannelCode channelCode, String role, boolean pendingDeletion,
                                 LocalDateTime deletionRequestedAt) {
        return LoginResult.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .channelCode(channelCode)
                .role(role != null ? role : "USER")
                .pendingDeletion(pendingDeletion)
                .deletionRequestedAt(deletionRequestedAt)
                .build();
    }
}
