package com.jay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasskeyRenameRequest {

    @NotBlank(message = "패스키 이름은 필수입니다")
    @Size(max = 100, message = "패스키 이름은 100자 이하여야 합니다")
    private String deviceName;
}
