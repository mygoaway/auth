package com.jay.auth.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostStatus {
    OPEN("대기중"),
    IN_PROGRESS("처리중"),
    RESOLVED("해결됨"),
    CLOSED("종료");

    private final String description;
}
