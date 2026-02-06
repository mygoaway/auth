package com.jay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerifyPhoneRequest {

    @NotBlank(message = "핸드폰 번호는 필수입니다")
    @Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "올바른 핸드폰 번호 형식이 아닙니다")
    private String phone;

    @NotBlank(message = "인증 코드는 필수입니다")
    @Size(min = 6, max = 6, message = "인증 코드는 6자리입니다")
    private String code;
}
