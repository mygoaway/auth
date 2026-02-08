package com.jay.auth.security.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo extends OAuth2UserInfo {

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        // OAuth2 모드: "id" 필드 사용
        Object id = attributes.get("id");
        if (id != null) {
            return String.valueOf(id);
        }
        // OIDC 모드: "sub" 필드 사용 (Kakao OIDC의 subject claim)
        Object sub = attributes.get("sub");
        if (sub != null) {
            return String.valueOf(sub);
        }
        return null;
    }

    @Override
    public String getName() {
        // OIDC 모드: nickname claim 직접 확인
        Object nickname = attributes.get("nickname");
        if (nickname != null) {
            return (String) nickname;
        }
        // OAuth2 모드: properties에서 확인
        Map<String, Object> properties = getProperties();
        if (properties != null) {
            return (String) properties.get("nickname");
        }
        return null;
    }

    @Override
    public String getEmail() {
        // OIDC 모드: email claim 직접 확인
        Object email = attributes.get("email");
        if (email != null) {
            return (String) email;
        }
        // OAuth2 모드: kakao_account에서 확인
        Map<String, Object> kakaoAccount = getKakaoAccount();
        if (kakaoAccount != null) {
            return (String) kakaoAccount.get("email");
        }
        return null;
    }

    @Override
    public String getImageUrl() {
        // OIDC 모드: picture claim 직접 확인
        Object picture = attributes.get("picture");
        if (picture != null) {
            return (String) picture;
        }
        // OAuth2 모드: properties에서 확인
        Map<String, Object> properties = getProperties();
        if (properties != null) {
            return (String) properties.get("profile_image");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getProperties() {
        return (Map<String, Object>) attributes.get("properties");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getKakaoAccount() {
        return (Map<String, Object>) attributes.get("kakao_account");
    }
}
