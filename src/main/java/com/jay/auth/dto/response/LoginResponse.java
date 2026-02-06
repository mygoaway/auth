package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private Long userId;
    private String userUuid;
    private String email;
    private String nickname;
    private TokenResponse token;

    public static LoginResponse of(Long userId, String userUuid, String email, String nickname, TokenResponse tokenResponse) {
        return LoginResponse.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(tokenResponse)
                .build();
    }
}
