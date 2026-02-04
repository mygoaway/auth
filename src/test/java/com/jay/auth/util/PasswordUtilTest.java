package com.jay.auth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilTest {

    private PasswordUtil passwordUtil;

    @BeforeEach
    void setUp() {
        passwordUtil = new PasswordUtil(new BCryptPasswordEncoder());
    }

    @Test
    @DisplayName("비밀번호 인코딩 후 매칭이 성공해야 한다")
    void encodeAndMatches() {
        // given
        String rawPassword = "Test@1234";

        // when
        String encoded = passwordUtil.encode(rawPassword);

        // then
        assertThat(passwordUtil.matches(rawPassword, encoded)).isTrue();
        assertThat(passwordUtil.matches("wrongPassword", encoded)).isFalse();
    }

    @Test
    @DisplayName("같은 비밀번호도 매번 다르게 인코딩되어야 한다")
    void encodeDifferentEachTime() {
        // given
        String rawPassword = "Test@1234";

        // when
        String encoded1 = passwordUtil.encode(rawPassword);
        String encoded2 = passwordUtil.encode(rawPassword);

        // then
        assertThat(encoded1).isNotEqualTo(encoded2);
        assertThat(passwordUtil.matches(rawPassword, encoded1)).isTrue();
        assertThat(passwordUtil.matches(rawPassword, encoded2)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Test@1234",      // 정상
            "Password@1",     // 최소 8자
            "Ab@12345678"     // 긴 비밀번호
    })
    @DisplayName("유효한 비밀번호는 정책을 통과해야 한다")
    void validPassword(String password) {
        assertThat(passwordUtil.isValidPassword(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Test123",        // 특수문자 없음
            "Test@abc",       // 숫자 없음
            "1234@567",       // 영문 없음
            "Te@1234",        // 8자 미만
            ""                // 빈 문자열
    })
    @DisplayName("유효하지 않은 비밀번호는 정책을 통과하지 못해야 한다")
    void invalidPassword(String password) {
        assertThat(passwordUtil.isValidPassword(password)).isFalse();
    }

    @Test
    @DisplayName("null 비밀번호는 정책을 통과하지 못해야 한다")
    void nullPassword() {
        assertThat(passwordUtil.isValidPassword(null)).isFalse();
    }
}
