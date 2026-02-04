package com.jay.auth.security.oauth2;

import com.jay.auth.domain.enums.ChannelCode;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (registrationId.equalsIgnoreCase(ChannelCode.GOOGLE.name())) {
            return new GoogleOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(ChannelCode.KAKAO.name())) {
            return new KakaoOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(ChannelCode.NAVER.name())) {
            return new NaverOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(ChannelCode.FACEBOOK.name())) {
            return new FacebookOAuth2UserInfo(attributes);
        } else {
            throw new IllegalArgumentException("Unknown registration id: " + registrationId);
        }
    }

    public static ChannelCode getChannelCode(String registrationId) {
        return ChannelCode.valueOf(registrationId.toUpperCase());
    }
}
