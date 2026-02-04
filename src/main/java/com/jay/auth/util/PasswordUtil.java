package com.jay.auth.util;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PasswordUtil {

    private final PasswordEncoder passwordEncoder;

    // 최소 8자, 영문, 숫자, 특수문자 포함
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$"
    );

    /**
     * 비밀번호 해싱 (BCrypt)
     * @param rawPassword 평문 비밀번호
     * @return 해싱된 비밀번호
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 비밀번호 검증
     * @param rawPassword 평문 비밀번호
     * @param encodedPassword 해싱된 비밀번호
     * @return 일치 여부
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 비밀번호 정책 검증
     * - 최소 8자 이상
     * - 영문자 포함
     * - 숫자 포함
     * - 특수문자 포함 (@$!%*#?&)
     * @param password 검증할 비밀번호
     * @return 정책 충족 여부
     */
    public boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
