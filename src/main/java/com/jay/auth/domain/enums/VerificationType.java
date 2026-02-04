package com.jay.auth.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationType {

    SIGNUP("회원가입"),
    EMAIL_CHANGE("이메일변경"),
    PASSWORD_RESET("비밀번호재설정");

    private final String description;
}
