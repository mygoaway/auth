package com.jay.auth.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostCategory {
    ACCOUNT("계정"),
    LOGIN("로그인"),
    SECURITY("보안"),
    OTHER("기타");

    private final String description;
}
