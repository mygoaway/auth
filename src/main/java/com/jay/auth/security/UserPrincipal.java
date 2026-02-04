package com.jay.auth.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증된 사용자 정보
 */
@Getter
@RequiredArgsConstructor
public class UserPrincipal {

    private final Long userId;
    private final String userUuid;
}
