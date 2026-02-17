package com.jay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasskeyAuthenticateRequest {

    @NotBlank(message = "credential ID는 필수입니다")
    private String credentialId;

    @NotBlank(message = "authenticatorData는 필수입니다")
    private String authenticatorData;

    @NotBlank(message = "clientDataJSON은 필수입니다")
    private String clientDataJSON;

    @NotBlank(message = "signature는 필수입니다")
    private String signature;

    private String userHandle;
}
