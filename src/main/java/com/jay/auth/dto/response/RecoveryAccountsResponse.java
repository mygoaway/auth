package com.jay.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "복구 이메일에 연결된 계정 목록 응답")
public class RecoveryAccountsResponse {

    @Schema(description = "연결된 계정 목록")
    private List<AccountInfo> accounts;

    @Getter
    @AllArgsConstructor
    public static class AccountInfo {
        @Schema(description = "로그인 이메일", example = "john@gmail.com")
        private String loginEmail;

        @Schema(description = "마스킹된 이메일", example = "j***@gmail.com")
        private String maskedEmail;
    }
}
