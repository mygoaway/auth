package com.jay.auth.security.oauth2;

import com.jay.auth.domain.enums.ChannelCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2UserInfoFactoryTest {

    @Nested
    @DisplayName("OAuth2UserInfo 생성")
    class GetOAuth2UserInfo {

        @Test
        @DisplayName("Google 사용자 정보를 올바르게 생성해야 한다")
        void shouldCreateGoogleUserInfo() {
            // given
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-id-123");
            attributes.put("name", "Google User");
            attributes.put("email", "google@example.com");
            attributes.put("picture", "https://google.com/photo.jpg");

            // when
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("google", attributes);

            // then
            assertThat(userInfo).isInstanceOf(GoogleOAuth2UserInfo.class);
            assertThat(userInfo.getId()).isEqualTo("google-id-123");
            assertThat(userInfo.getName()).isEqualTo("Google User");
            assertThat(userInfo.getEmail()).isEqualTo("google@example.com");
            assertThat(userInfo.getImageUrl()).isEqualTo("https://google.com/photo.jpg");
        }

        @Test
        @DisplayName("Kakao 사용자 정보를 올바르게 생성해야 한다 (OAuth2 모드)")
        void shouldCreateKakaoUserInfoOAuth2Mode() {
            // given
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", 12345L);

            Map<String, Object> properties = new HashMap<>();
            properties.put("nickname", "Kakao User");
            properties.put("profile_image", "https://kakao.com/photo.jpg");
            attributes.put("properties", properties);

            Map<String, Object> kakaoAccount = new HashMap<>();
            kakaoAccount.put("email", "kakao@example.com");
            attributes.put("kakao_account", kakaoAccount);

            // when
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("kakao", attributes);

            // then
            assertThat(userInfo).isInstanceOf(KakaoOAuth2UserInfo.class);
            assertThat(userInfo.getId()).isEqualTo("12345");
            assertThat(userInfo.getName()).isEqualTo("Kakao User");
            assertThat(userInfo.getEmail()).isEqualTo("kakao@example.com");
            assertThat(userInfo.getImageUrl()).isEqualTo("https://kakao.com/photo.jpg");
        }

        @Test
        @DisplayName("Kakao 사용자 정보를 올바르게 생성해야 한다 (OIDC 모드)")
        void shouldCreateKakaoUserInfoOidcMode() {
            // given
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "kakao-oidc-123");
            attributes.put("nickname", "Kakao OIDC User");
            attributes.put("email", "kakao-oidc@example.com");
            attributes.put("picture", "https://kakao.com/oidc-photo.jpg");

            // when
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("kakao", attributes);

            // then
            assertThat(userInfo).isInstanceOf(KakaoOAuth2UserInfo.class);
            assertThat(userInfo.getId()).isEqualTo("kakao-oidc-123");
            assertThat(userInfo.getName()).isEqualTo("Kakao OIDC User");
            assertThat(userInfo.getEmail()).isEqualTo("kakao-oidc@example.com");
            assertThat(userInfo.getImageUrl()).isEqualTo("https://kakao.com/oidc-photo.jpg");
        }

        @Test
        @DisplayName("Naver 사용자 정보를 올바르게 생성해야 한다")
        void shouldCreateNaverUserInfo() {
            // given
            Map<String, Object> attributes = new HashMap<>();
            Map<String, Object> response = new HashMap<>();
            response.put("id", "naver-id-123");
            response.put("name", "Naver User");
            response.put("email", "naver@example.com");
            response.put("profile_image", "https://naver.com/photo.jpg");
            attributes.put("response", response);

            // when
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("naver", attributes);

            // then
            assertThat(userInfo).isInstanceOf(NaverOAuth2UserInfo.class);
            assertThat(userInfo.getId()).isEqualTo("naver-id-123");
            assertThat(userInfo.getName()).isEqualTo("Naver User");
            assertThat(userInfo.getEmail()).isEqualTo("naver@example.com");
            assertThat(userInfo.getImageUrl()).isEqualTo("https://naver.com/photo.jpg");
        }

        @Test
        @DisplayName("대소문자를 구분하지 않고 provider를 매핑해야 한다")
        void shouldBeCaseInsensitive() {
            // given
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-id");

            // when
            OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("GOOGLE", attributes);

            // then
            assertThat(userInfo).isInstanceOf(GoogleOAuth2UserInfo.class);
        }
    }

    @Nested
    @DisplayName("지원하지 않는 Provider")
    class UnsupportedProvider {

        @Test
        @DisplayName("지원하지 않는 provider이면 IllegalArgumentException이 발생해야 한다")
        void shouldThrowExceptionForUnsupportedProvider() {
            // given
            Map<String, Object> attributes = new HashMap<>();

            // when & then
            assertThatThrownBy(() ->
                    OAuth2UserInfoFactory.getOAuth2UserInfo("facebook", attributes)
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown registration id");
        }
    }

    @Nested
    @DisplayName("ChannelCode 변환")
    class GetChannelCode {

        @Test
        @DisplayName("registrationId를 ChannelCode로 올바르게 변환해야 한다")
        void shouldConvertToChannelCode() {
            // when & then
            assertThat(OAuth2UserInfoFactory.getChannelCode("google")).isEqualTo(ChannelCode.GOOGLE);
            assertThat(OAuth2UserInfoFactory.getChannelCode("kakao")).isEqualTo(ChannelCode.KAKAO);
            assertThat(OAuth2UserInfoFactory.getChannelCode("naver")).isEqualTo(ChannelCode.NAVER);
        }

        @Test
        @DisplayName("대소문자를 구분하지 않고 ChannelCode로 변환해야 한다")
        void shouldConvertCaseInsensitively() {
            // when & then
            assertThat(OAuth2UserInfoFactory.getChannelCode("Google")).isEqualTo(ChannelCode.GOOGLE);
            assertThat(OAuth2UserInfoFactory.getChannelCode("KAKAO")).isEqualTo(ChannelCode.KAKAO);
        }
    }
}
