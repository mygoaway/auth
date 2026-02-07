package com.jay.auth.exception;

import lombok.Getter;

@Getter
public class PasswordPolicyException extends RuntimeException {

    private final String errorCode;

    public PasswordPolicyException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static PasswordPolicyException expired() {
        return new PasswordPolicyException("비밀번호가 만료되었습니다. 새 비밀번호로 변경해주세요.", "PASSWORD_EXPIRED");
    }

    public static PasswordPolicyException reused() {
        return new PasswordPolicyException("이전에 사용했던 비밀번호는 재사용할 수 없습니다.", "PASSWORD_REUSED");
    }

    public static PasswordPolicyException sameAsCurrent() {
        return new PasswordPolicyException("현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.", "PASSWORD_SAME_AS_CURRENT");
    }
}
