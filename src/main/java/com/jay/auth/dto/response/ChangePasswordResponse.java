package com.jay.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "비밀번호 변경 응답")
public class ChangePasswordResponse {

    @Schema(description = "전체 로그아웃 여부", example = "true")
    private boolean loggedOut;

    public static ChangePasswordResponse of(boolean loggedOut) {
        return ChangePasswordResponse.builder()
                .loggedOut(loggedOut)
                .build();
    }
}
