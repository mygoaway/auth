package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String userUuid;
    private String email;
    private String nickname;
    private TokenResponse token;

    public static LoginResponse of(String userUuid, String email, String nickname, TokenResponse tokenResponse) {
        return LoginResponse.builder()
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(tokenResponse)
                .build();
    }
}
