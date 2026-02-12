package com.jay.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    @Nested
    @DisplayName("UserPrincipal 생성")
    class Constructor {

        @Test
        @DisplayName("모든 필드가 정상적으로 설정되어야 한다")
        void shouldCreateWithAllFields() {
            // given
            Long userId = 1L;
            String userUuid = "uuid-1234-5678";
            String role = "USER";

            // when
            UserPrincipal principal = new UserPrincipal(userId, userUuid, role);

            // then
            assertThat(principal.getUserId()).isEqualTo(1L);
            assertThat(principal.getUserUuid()).isEqualTo("uuid-1234-5678");
            assertThat(principal.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("ADMIN 역할로 생성할 수 있어야 한다")
        void shouldCreateWithAdminRole() {
            // when
            UserPrincipal principal = new UserPrincipal(2L, "uuid-admin", "ADMIN");

            // then
            assertThat(principal.getUserId()).isEqualTo(2L);
            assertThat(principal.getUserUuid()).isEqualTo("uuid-admin");
            assertThat(principal.getRole()).isEqualTo("ADMIN");
        }
    }

    @Nested
    @DisplayName("UserPrincipal Getter")
    class Getters {

        @Test
        @DisplayName("getUserId가 올바른 값을 반환해야 한다")
        void shouldReturnCorrectUserId() {
            // given
            UserPrincipal principal = new UserPrincipal(100L, "uuid", "USER");

            // when & then
            assertThat(principal.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("getUserUuid가 올바른 값을 반환해야 한다")
        void shouldReturnCorrectUserUuid() {
            // given
            UserPrincipal principal = new UserPrincipal(1L, "test-uuid-value", "USER");

            // when & then
            assertThat(principal.getUserUuid()).isEqualTo("test-uuid-value");
        }

        @Test
        @DisplayName("getRole이 올바른 값을 반환해야 한다")
        void shouldReturnCorrectRole() {
            // given
            UserPrincipal principal = new UserPrincipal(1L, "uuid", "ADMIN");

            // when & then
            assertThat(principal.getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("null 값으로 생성해도 getter가 null을 반환해야 한다")
        void shouldHandleNullValues() {
            // given
            UserPrincipal principal = new UserPrincipal(null, null, null);

            // when & then
            assertThat(principal.getUserId()).isNull();
            assertThat(principal.getUserUuid()).isNull();
            assertThat(principal.getRole()).isNull();
        }
    }
}
