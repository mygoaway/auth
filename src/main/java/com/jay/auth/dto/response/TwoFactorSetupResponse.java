package com.jay.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TwoFactorSetupResponse {

    private String secret;
    private String qrCodeDataUrl;
}
