package com.jay.auth.dto.response;

import com.jay.auth.domain.enums.ChannelCode;
import lombok.Builder;
import lombok.Getter;

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

    public static LoginResult of(Long userId, String userUuid, String email, String nickname, ChannelCode channelCode) {
        return LoginResult.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .channelCode(channelCode)
                .build();
    }
}
