package com.jay.auth.security.oauth2;

import com.jay.auth.domain.enums.ChannelCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOidcUserTest {

    private static final Map<String, Object> ATTRIBUTES = Map.of(
            "sub", "12345",
            "email", "test@example.com",
            "name", "Test User"
    );

    private static final OidcIdToken ID_TOKEN = new OidcIdToken(
            "token-value", Instant.now(), Instant.now().plusSeconds(3600), ATTRIBUTES
    );

    private static final OidcUserInfo USER_INFO = new OidcUserInfo(ATTRIBUTES);

    @Nested
    @DisplayName("기본 생성자 (7개 인자)")
    class BasicConstructor {

        @Test
        @DisplayName("기본 생성자로 생성 시 기본값이 올바르게 설정되어야 한다")
        void basicConstructorDefaults() {
            // when
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            // then
            assertThat(user.getUserId()).isEqualTo(1L);
            assertThat(user.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(user.getChannelCode()).isEqualTo(ChannelCode.GOOGLE);
            assertThat(user.getIdToken()).isEqualTo(ID_TOKEN);
            assertThat(user.getUserInfo()).isEqualTo(USER_INFO);
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
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            assertThat(user.getName()).isEqualTo("12345");
        }

        @Test
        @DisplayName("getAuthorities()는 ROLE_USER 권한을 포함해야 한다")
        void authoritiesContainRoleUser() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            assertThat(user.getAuthorities()).hasSize(1);
            assertThat(user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList())
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("getClaims()는 attributes를 반환해야 한다")
        void getClaimsReturnsAttributes() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            assertThat(user.getClaims()).isEqualTo(ATTRIBUTES);
        }
    }

    @Nested
    @DisplayName("링크 모드 생성자 (8개 인자)")
    class LinkModeConstructor {

        @Test
        @DisplayName("linkMode가 true로 설정되어야 한다")
        void linkModeTrue() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub", true
            );

            assertThat(user.isLinkMode()).isTrue();
            assertThat(user.getRole()).isEqualTo("USER");
            assertThat(user.isPendingDeletion()).isFalse();
        }

        @Test
        @DisplayName("linkMode가 false로 설정되어야 한다")
        void linkModeFalse() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub", false
            );

            assertThat(user.isLinkMode()).isFalse();
        }
    }

    @Nested
    @DisplayName("전체 인자 생성자 (11개 인자)")
    class FullConstructor {

        @Test
        @DisplayName("모든 필드가 올바르게 설정되어야 한다")
        void allFieldsSet() {
            LocalDateTime deletionTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub",
                    "ADMIN", true, true, deletionTime
            );

            assertThat(user.getUserId()).isEqualTo(1L);
            assertThat(user.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(user.getChannelCode()).isEqualTo(ChannelCode.GOOGLE);
            assertThat(user.getIdToken()).isEqualTo(ID_TOKEN);
            assertThat(user.getUserInfo()).isEqualTo(USER_INFO);
            assertThat(user.getRole()).isEqualTo("ADMIN");
            assertThat(user.isLinkMode()).isTrue();
            assertThat(user.isPendingDeletion()).isTrue();
            assertThat(user.getDeletionRequestedAt()).isEqualTo(deletionTime);
        }

        @Test
        @DisplayName("role이 null이면 기본값 'USER'가 설정되어야 한다")
        void nullRoleDefaultsToUser() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub",
                    null, false, false, null
            );

            assertThat(user.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("deletionRequestedAt이 null이어도 정상 생성되어야 한다")
        void nullDeletionRequestedAt() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub",
                    "USER", false, false, null
            );

            assertThat(user.getDeletionRequestedAt()).isNull();
            assertThat(user.isPendingDeletion()).isFalse();
        }
    }

    @Nested
    @DisplayName("OidcUser 인터페이스 메서드")
    class OidcUserInterfaceMethods {

        @Test
        @DisplayName("getIdToken()은 설정된 IdToken을 반환해야 한다")
        void getIdToken() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            assertThat(user.getIdToken()).isEqualTo(ID_TOKEN);
            assertThat(user.getIdToken().getTokenValue()).isEqualTo("token-value");
        }

        @Test
        @DisplayName("getUserInfo()는 설정된 UserInfo를 반환해야 한다")
        void getUserInfo() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            assertThat(user.getUserInfo()).isEqualTo(USER_INFO);
        }

        @Test
        @DisplayName("getAttributes()는 설정된 attributes를 반환해야 한다")
        void getAttributes() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    ID_TOKEN, USER_INFO, ATTRIBUTES, "sub"
            );

            assertThat(user.getAttributes()).containsEntry("sub", "12345");
            assertThat(user.getAttributes()).containsEntry("email", "test@example.com");
        }

        @Test
        @DisplayName("null IdToken과 UserInfo로도 생성 가능해야 한다")
        void nullIdTokenAndUserInfo() {
            CustomOidcUser user = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    null, null, ATTRIBUTES, "sub"
            );

            assertThat(user.getIdToken()).isNull();
            assertThat(user.getUserInfo()).isNull();
            assertThat(user.getAttributes()).isEqualTo(ATTRIBUTES);
        }
    }
}
