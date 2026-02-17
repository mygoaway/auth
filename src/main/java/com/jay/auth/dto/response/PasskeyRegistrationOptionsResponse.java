package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PasskeyRegistrationOptionsResponse {

    private String challenge;
    private RpInfo rp;
    private UserInfo user;
    private List<PubKeyCredParam> pubKeyCredParams;
    private Long timeout;
    private String attestation;
    private List<ExcludeCredential> excludeCredentials;

    @Getter
    @Builder
    public static class RpInfo {
        private String id;
        private String name;
    }

    @Getter
    @Builder
    public static class UserInfo {
        private String id;
        private String name;
        private String displayName;
    }

    @Getter
    @Builder
    public static class PubKeyCredParam {
        private String type;
        private int alg;
    }

    @Getter
    @Builder
    public static class ExcludeCredential {
        private String id;
        private String type;
    }
}
