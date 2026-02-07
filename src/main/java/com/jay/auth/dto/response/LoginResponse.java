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
    private boolean passwordExpired;
    private Integer daysUntilPasswordExpiration;
    private boolean twoFactorRequired;

    public static LoginResponse of(Long userId, String userUuid, String email, String nickname, TokenResponse tokenResponse) {
        return LoginResponse.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(tokenResponse)
                .passwordExpired(false)
                .twoFactorRequired(false)
                .build();
    }

    public static LoginResponse of(Long userId, String userUuid, String email, String nickname,
                                   TokenResponse tokenResponse, boolean passwordExpired,
                                   Integer daysUntilExpiration, boolean twoFactorRequired) {
        return LoginResponse.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(tokenResponse)
                .passwordExpired(passwordExpired)
                .daysUntilPasswordExpiration(daysUntilExpiration)
                .twoFactorRequired(twoFactorRequired)
                .build();
    }
}
