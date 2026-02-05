package com.jay.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateRecoveryEmailRequest {

    @NotBlank(message = "복구 이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String recoveryEmail;
}
