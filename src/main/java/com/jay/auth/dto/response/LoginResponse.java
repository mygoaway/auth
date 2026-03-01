package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private boolean pendingDeletion;
    private LocalDateTime deletionRequestedAt;
    private boolean postLoginVerificationRequired;
    private String postLoginVerificationTokenId;

    public static LoginResponse of(Long userId, String userUuid, String email, String nickname, TokenResponse tokenResponse) {
        return LoginResponse.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(tokenResponse)
                .passwordExpired(false)
                .twoFactorRequired(false)
                .pendingDeletion(false)
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
                .pendingDeletion(false)
                .build();
    }

    public static LoginResponse of(Long userId, String userUuid, String email, String nickname,
                                   TokenResponse tokenResponse, boolean passwordExpired,
                                   Integer daysUntilExpiration, boolean twoFactorRequired,
                                   boolean pendingDeletion, LocalDateTime deletionRequestedAt) {
        return LoginResponse.builder()
                .userId(userId)
                .userUuid(userUuid)
                .email(email)
                .nickname(nickname)
                .token(tokenResponse)
                .passwordExpired(passwordExpired)
                .daysUntilPasswordExpiration(daysUntilExpiration)
                .twoFactorRequired(twoFactorRequired)
                .pendingDeletion(pendingDeletion)
                .deletionRequestedAt(deletionRequestedAt)
                .build();
    }
}
