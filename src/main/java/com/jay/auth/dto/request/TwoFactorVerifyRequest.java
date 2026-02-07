package com.jay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorVerifyRequest {

    @NotBlank(message = "인증 코드를 입력해주세요")
    @Pattern(regexp = "^[0-9]{6,8}$", message = "인증 코드는 6~8자리 숫자여야 합니다")
    private String code;
}
