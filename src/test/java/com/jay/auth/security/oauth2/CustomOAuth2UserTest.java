package com.jay.auth.security.oauth2;

import com.jay.auth.domain.enums.ChannelCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOAuth2UserTest {

    private static final Map<String, Object> ATTRIBUTES = Map.of(
            "sub", "12345",
            "email", "test@example.com",
            "name", "Test User"
    );

    @Nested
    @DisplayName("기본 생성자 (5개 인자)")
    class BasicConstructor {

        @Test
        @DisplayName("기본 생성자로 생성 시 기본값이 올바르게 설정되어야 한다")
        void basicConstructorDefaults() {
            // when
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE, ATTRIBUTES, "sub"
            );

            // then
            assertThat(user.getUserId()).isEqualTo(1L);
            assertThat(user.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(user.getChannelCode()).isEqualTo(ChannelCode.GOOGLE);
            assertThat(user.getAttributes()).isEqualTo(ATTRIBUTES);
            assertThat(user.getNameAttributeKey()).isEqualTo("sub");
            assertThat(user.getRole()).isEqualTo("USER");
            assertThat(user.isLinkMode()).isFalse();
            assertThat(user.isPendingDeletion()).isFalse();
            assertThat(user.getDeletionRequestedAt()).isNull();
        }

        @Test
        @DisplayName("getName()은 nameAttributeKey에 해당하는 속성값을 반환해야 한다")
        void getNameReturnsAttributeValue() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE, ATTRIBUTES, "sub"
            );

            assertThat(user.getName()).isEqualTo("12345");
        }

        @Test
        @DisplayName("getAuthorities()는 ROLE_USER 권한을 포함해야 한다")
        void authoritiesContainRoleUser() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE, ATTRIBUTES, "sub"
            );

            assertThat(user.getAuthorities()).hasSize(1);
            assertThat(user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList())
                    .containsExactly("ROLE_USER");
        }
    }

    @Nested
    @DisplayName("링크 모드 생성자 (6개 인자)")
    class LinkModeConstructor {

        @Test
        @DisplayName("linkMode가 true로 설정되어야 한다")
        void linkModeTrue() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.KAKAO, ATTRIBUTES, "sub", true
            );

            assertThat(user.isLinkMode()).isTrue();
            assertThat(user.getRole()).isEqualTo("USER");
            assertThat(user.isPendingDeletion()).isFalse();
        }

        @Test
        @DisplayName("linkMode가 false로 설정되어야 한다")
        void linkModeFalse() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.KAKAO, ATTRIBUTES, "sub", false
            );

            assertThat(user.isLinkMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("전체 인자 생성자 (9개 인자)")
    class FullConstructor {

        @Test
        @DisplayName("모든 필드가 올바르게 설정되어야 한다")
        void allFieldsSet() {
            LocalDateTime deletionTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.NAVER, ATTRIBUTES, "sub",
                    "ADMIN", true, true, deletionTime
            );

            assertThat(user.getUserId()).isEqualTo(1L);
            assertThat(user.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(user.getChannelCode()).isEqualTo(ChannelCode.NAVER);
            assertThat(user.getRole()).isEqualTo("ADMIN");
            assertThat(user.isLinkMode()).isTrue();
            assertThat(user.isPendingDeletion()).isTrue();
            assertThat(user.getDeletionRequestedAt()).isEqualTo(deletionTime);
        }

        @Test
        @DisplayName("role이 null이면 기본값 'USER'가 설정되어야 한다")
        void nullRoleDefaultsToUser() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE, ATTRIBUTES, "sub",
                    null, false, false, null
            );

            assertThat(user.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("deletionRequestedAt이 null이어도 정상 생성되어야 한다")
        void nullDeletionRequestedAt() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE, ATTRIBUTES, "sub",
                    "USER", false, false, null
            );

            assertThat(user.getDeletionRequestedAt()).isNull();
            assertThat(user.isPendingDeletion()).isFalse();
        }
    }

    @Nested
    @DisplayName("getAttributes 메서드")
    class GetAttributes {

        @Test
        @DisplayName("설정된 attributes를 그대로 반환해야 한다")
        void returnsAttributes() {
            CustomOAuth2User user = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE, ATTRIBUTES, "sub"
            );

            assertThat(user.getAttributes()).containsEntry("sub", "12345");
            assertThat(user.getAttributes()).containsEntry("email", "test@example.com");
            assertThat(user.getAttributes()).containsEntry("name", "Test User");
        }
    }
}
