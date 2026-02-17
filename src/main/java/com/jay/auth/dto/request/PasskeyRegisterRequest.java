package com.jay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasskeyRegisterRequest {

    @NotBlank(message = "credential ID는 필수입니다")
    private String credentialId;

    @NotBlank(message = "attestationObject는 필수입니다")
    private String attestationObject;

    @NotBlank(message = "clientDataJSON은 필수입니다")
    private String clientDataJSON;

    private String transports;

    private String deviceName;
}
