package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PasskeyAuthenticationOptionsResponse {

    private String challenge;
    private Long timeout;
    private String rpId;
    private List<AllowCredential> allowCredentials;
    private String userVerification;

    @Getter
    @Builder
    public static class AllowCredential {
        private String id;
        private String type;
        private List<String> transports;
    }
}
