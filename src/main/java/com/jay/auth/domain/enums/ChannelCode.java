package com.jay.auth.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChannelCode {

    EMAIL("이메일"),
    GOOGLE("구글"),
    KAKAO("카카오"),
    NAVER("네이버");

    private final String description;
}
