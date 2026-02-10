package com.jay.auth.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {

    ACTIVE("정상"),
    LOCKED("잠금"),
    DORMANT("휴면"),
    PENDING_DELETE("탈퇴예정"),
    DELETED("탈퇴");

    private final String description;
}
