package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhoneVerificationResponse {

    private String tokenId;
    private String message;
    private int expiresInSeconds;

    public static PhoneVerificationResponse sent(String tokenId, int expiresInSeconds) {
        return PhoneVerificationResponse.builder()
                .tokenId(tokenId)
                .message("인증 코드가 발송되었습니다")
                .expiresInSeconds(expiresInSeconds)
                .build();
    }

    public static PhoneVerificationResponse verified(String tokenId) {
        return PhoneVerificationResponse.builder()
                .tokenId(tokenId)
                .message("핸드폰 인증이 완료되었습니다")
                .build();
    }
}
