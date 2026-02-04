package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignUpResponse {

    private String userUuid;
    private String email;
    private String nickname;
    private TokenResponse token;

    public static SignUpResponse of(String userUuid, String email, String nickname, TokenResponse token) {
        return SignUpResponse.builder()
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(token)
                .build();
    }
}
