package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationResponse {

    private String tokenId;
    private String message;
    private int expiresInSeconds;

    public static VerificationResponse sent(String tokenId, int expiresInSeconds) {
        return VerificationResponse.builder()
                .tokenId(tokenId)
                .message("인증 코드가 발송되었습니다")
                .expiresInSeconds(expiresInSeconds)
                .build();
    }

    public static VerificationResponse verified() {
        return VerificationResponse.builder()
                .message("이메일 인증이 완료되었습니다")
                .build();
    }
}
