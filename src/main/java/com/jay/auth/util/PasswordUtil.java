package com.jay.auth.util;

import com.jay.auth.dto.response.PasswordAnalysisResponse;
import com.jay.auth.dto.response.PasswordAnalysisResponse.CheckItem;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "12345678", "qwerty12", "abc12345", "password1",
            "iloveyou", "sunshine1", "princess1", "football1", "charlie1",
            "access14", "master12", "dragon12", "monkey12", "letmein1",
            "login123", "welcome1", "shadow12", "ashley12", "michael1",
            "qwerty123", "password123", "1234567890", "admin123", "test1234"
    );

    public PasswordAnalysisResponse analyzePassword(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordAnalysisResponse.builder()
                    .score(0).level("CRITICAL").valid(false)
                    .checks(List.of()).suggestions(List.of("비밀번호를 입력해주세요."))
                    .build();
        }

        List<CheckItem> checks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int score = 0;

        // 1. 길이 검사
        boolean lengthOk = password.length() >= 8;
        checks.add(CheckItem.builder().name("LENGTH").description("8자 이상").passed(lengthOk).build());
        if (lengthOk) score += 15;
        else suggestions.add("비밀번호는 최소 8자 이상이어야 합니다.");

        // 2. 소문자
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        checks.add(CheckItem.builder().name("LOWERCASE").description("소문자 포함").passed(hasLower).build());
        if (hasLower) score += 10;
        else suggestions.add("소문자를 포함해주세요.");

        // 3. 대문자
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        checks.add(CheckItem.builder().name("UPPERCASE").description("대문자 포함").passed(hasUpper).build());
        if (hasUpper) score += 10;
        else suggestions.add("대문자를 포함해주세요.");

        // 4. 숫자
        boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();
        checks.add(CheckItem.builder().name("DIGIT").description("숫자 포함").passed(hasDigit).build());
        if (hasDigit) score += 10;
        else suggestions.add("숫자를 포함해주세요.");

        // 5. 특수문자
        boolean hasSpecial = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find();
        checks.add(CheckItem.builder().name("SPECIAL").description("특수문자 포함").passed(hasSpecial).build());
        if (hasSpecial) score += 15;
        else suggestions.add("특수문자(!@#$%^&* 등)를 포함해주세요.");

        // 6. 길이 보너스
        boolean longPassword = password.length() >= 12;
        checks.add(CheckItem.builder().name("LONG_PASSWORD").description("12자 이상 (보너스)").passed(longPassword).build());
        if (longPassword) score += 15;

        // 7. 연속 문자 검사
        boolean noSequential = !hasSequentialChars(password);
        checks.add(CheckItem.builder().name("NO_SEQUENTIAL").description("연속된 문자 없음").passed(noSequential).build());
        if (noSequential) score += 10;
        else suggestions.add("연속된 문자(abc, 123 등)를 피해주세요.");

        // 8. 반복 문자 검사
        boolean noRepeated = !hasRepeatedChars(password);
        checks.add(CheckItem.builder().name("NO_REPEATED").description("반복된 문자 없음").passed(noRepeated).build());
        if (noRepeated) score += 10;
        else suggestions.add("같은 문자의 반복(aaa, 111 등)을 피해주세요.");

        // 9. 흔한 비밀번호 검사
        boolean notCommon = !COMMON_PASSWORDS.contains(password.toLowerCase());
        checks.add(CheckItem.builder().name("NOT_COMMON").description("흔한 비밀번호 아님").passed(notCommon).build());
        if (notCommon) score += 5;
        else suggestions.add("너무 흔한 비밀번호입니다. 더 독특한 비밀번호를 사용해주세요.");

        score = Math.min(score, 100);
        String level = getStrengthLevel(score);
        boolean valid = isValidPassword(password);

        if (suggestions.isEmpty()) {
            suggestions.add("안전한 비밀번호입니다!");
        }

        return PasswordAnalysisResponse.builder()
                .score(score).level(level).valid(valid)
                .checks(checks).suggestions(suggestions)
                .build();
    }

    private boolean hasSequentialChars(String password) {
        String lower = password.toLowerCase();
        for (int i = 0; i < lower.length() - 2; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            char c3 = lower.charAt(i + 2);
            if (c2 == c1 + 1 && c3 == c2 + 1) return true;
            if (c2 == c1 - 1 && c3 == c2 - 1) return true;
        }
        return false;
    }

    private boolean hasRepeatedChars(String password) {
        for (int i = 0; i < password.length() - 2; i++) {
            if (password.charAt(i) == password.charAt(i + 1)
                    && password.charAt(i) == password.charAt(i + 2)) {
                return true;
            }
        }
        return false;
    }

    private String getStrengthLevel(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 70) return "STRONG";
        if (score >= 50) return "MEDIUM";
        if (score >= 30) return "WEAK";
        return "CRITICAL";
    }
}
