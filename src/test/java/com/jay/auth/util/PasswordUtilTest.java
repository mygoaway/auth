package com.jay.auth.util;

import com.jay.auth.dto.response.PasswordAnalysisResponse;
import com.jay.auth.dto.response.PasswordAnalysisResponse.CheckItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("비밀번호 분석 - null/빈 값")
    class AnalyzePasswordNullOrEmpty {

        @Test
        @DisplayName("null 비밀번호는 score 0, CRITICAL 레벨이어야 한다")
        void nullPassword() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword(null);

            assertThat(result.getScore()).isZero();
            assertThat(result.getLevel()).isEqualTo("CRITICAL");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getChecks()).isEmpty();
            assertThat(result.getSuggestions()).containsExactly("비밀번호를 입력해주세요.");
        }

        @Test
        @DisplayName("빈 문자열 비밀번호는 score 0, CRITICAL 레벨이어야 한다")
        void emptyPassword() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("");

            assertThat(result.getScore()).isZero();
            assertThat(result.getLevel()).isEqualTo("CRITICAL");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getChecks()).isEmpty();
            assertThat(result.getSuggestions()).containsExactly("비밀번호를 입력해주세요.");
        }
    }

    @Nested
    @DisplayName("비밀번호 분석 - 강도 레벨")
    class AnalyzePasswordStrengthLevels {

        @Test
        @DisplayName("짧고 단순한 비밀번호는 CRITICAL/WEAK 레벨이어야 한다")
        void weakPassword() {
            // "abc" -> 길이 미달, 소문자만, 연속 문자 있음
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("abc");

            assertThat(result.getLevel()).isIn("CRITICAL", "WEAK");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getScore()).isLessThan(50);
        }

        @Test
        @DisplayName("중간 강도 비밀번호는 MEDIUM 레벨이어야 한다")
        void mediumPassword() {
            // "Test1234" -> 8자, 소문자, 대문자, 숫자 있음 (특수문자 없음) = 15+10+10+10 + 10(noSeq) + 10(noRepeat) + 5(notCommon) = 70
            // 70점은 STRONG이므로 약간 조정 필요
            // "Testt12" -> 7자(미달), 소문자, 대문자, 숫자 = 0+10+10+10 + 10(noSeq) + 10(noRepeat) + 5(notCommon) = 55
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Testt12");

            assertThat(result.getLevel()).isEqualTo("MEDIUM");
            assertThat(result.getScore()).isGreaterThanOrEqualTo(50);
            assertThat(result.getScore()).isLessThan(70);
        }

        @Test
        @DisplayName("강한 비밀번호는 STRONG 레벨이어야 한다")
        void strongPassword() {
            // "Testpw59" -> 8자(15), 소문자(10), 대문자(10), 숫자(10), noSpecial(0), notLong(0), noSeq(10), noRepeat(10), notCommon(5) = 70
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Testpw59");

            assertThat(result.getLevel()).isEqualTo("STRONG");
            assertThat(result.getScore()).isGreaterThanOrEqualTo(70);
            assertThat(result.getScore()).isLessThan(90);
        }

        @Test
        @DisplayName("12자 이상이고 모든 조건을 만족하는 비밀번호는 EXCELLENT 레벨이어야 한다")
        void excellentPassword() {
            // "MyStr0ng!Pass" -> 13자(15+15), 소문자(10), 대문자(10), 숫자(10), 특수문자(15), noSeq(10), noRepeat(10), notCommon(5) = 100
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("MyStr0ng!Pass");

            assertThat(result.getLevel()).isEqualTo("EXCELLENT");
            assertThat(result.getScore()).isGreaterThanOrEqualTo(90);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("비밀번호 분석 - 연속 문자 검사")
    class AnalyzePasswordSequentialChars {

        @Test
        @DisplayName("알파벳 연속 문자(abc)가 감지되어야 한다")
        void alphabeticSequential() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xabc!789012");

            CheckItem seqCheck = result.getChecks().stream()
                    .filter(c -> "NO_SEQUENTIAL".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(seqCheck.isPassed()).isFalse();
            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("연속된 문자"));
        }

        @Test
        @DisplayName("숫자 연속 문자(123)가 감지되어야 한다")
        void numericSequential() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xw123!ghij");

            CheckItem seqCheck = result.getChecks().stream()
                    .filter(c -> "NO_SEQUENTIAL".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(seqCheck.isPassed()).isFalse();
        }

        @Test
        @DisplayName("역순 연속 문자(cba)가 감지되어야 한다")
        void reverseSequential() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xcba!789012");

            CheckItem seqCheck = result.getChecks().stream()
                    .filter(c -> "NO_SEQUENTIAL".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(seqCheck.isPassed()).isFalse();
        }

        @Test
        @DisplayName("연속 문자가 없으면 통과해야 한다")
        void noSequentialChars() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xmq!79024");

            CheckItem seqCheck = result.getChecks().stream()
                    .filter(c -> "NO_SEQUENTIAL".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(seqCheck.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("비밀번호 분석 - 반복 문자 검사")
    class AnalyzePasswordRepeatedChars {

        @Test
        @DisplayName("반복 문자(aaa)가 감지되어야 한다")
        void repeatedAlpha() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xaaa!78902");

            CheckItem repCheck = result.getChecks().stream()
                    .filter(c -> "NO_REPEATED".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(repCheck.isPassed()).isFalse();
            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("반복"));
        }

        @Test
        @DisplayName("반복 숫자(111)가 감지되어야 한다")
        void repeatedDigit() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xw111!ghij");

            CheckItem repCheck = result.getChecks().stream()
                    .filter(c -> "NO_REPEATED".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(repCheck.isPassed()).isFalse();
        }

        @Test
        @DisplayName("반복 문자가 없으면 통과해야 한다")
        void noRepeatedChars() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Xmq!79024");

            CheckItem repCheck = result.getChecks().stream()
                    .filter(c -> "NO_REPEATED".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(repCheck.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("비밀번호 분석 - 흔한 비밀번호 검사")
    class AnalyzePasswordCommonPasswords {

        @Test
        @DisplayName("흔한 비밀번호(password123)가 감지되어야 한다")
        void commonPassword() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("password123");

            CheckItem commonCheck = result.getChecks().stream()
                    .filter(c -> "NOT_COMMON".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(commonCheck.isPassed()).isFalse();
            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("흔한 비밀번호"));
        }

        @Test
        @DisplayName("흔한 비밀번호는 대소문자 무시하고 감지해야 한다")
        void commonPasswordCaseInsensitive() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("PASSWORD123");

            CheckItem commonCheck = result.getChecks().stream()
                    .filter(c -> "NOT_COMMON".equals(c.getName()))
                    .findFirst().orElseThrow();
            assertThat(commonCheck.isPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("비밀번호 분석 - 체크 항목 및 제안")
    class AnalyzePasswordChecksAndSuggestions {

        @Test
        @DisplayName("모든 9개 체크 항목이 반환되어야 한다")
        void allChecksPresent() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("Test@1234");

            assertThat(result.getChecks()).hasSize(9);
            assertThat(result.getChecks().stream().map(CheckItem::getName))
                    .containsExactly(
                            "LENGTH", "LOWERCASE", "UPPERCASE", "DIGIT",
                            "SPECIAL", "LONG_PASSWORD", "NO_SEQUENTIAL", "NO_REPEATED", "NOT_COMMON"
                    );
        }

        @Test
        @DisplayName("모든 조건 충족 시 '안전한 비밀번호입니다!' 제안이 반환되어야 한다")
        void allPassedSuggestion() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("MyStr0ng!Pass");

            assertThat(result.getSuggestions()).containsExactly("안전한 비밀번호입니다!");
        }

        @Test
        @DisplayName("조건 미충족 시 해당 제안이 포함되어야 한다")
        void failedChecksSuggestions() {
            // "abcd" -> 길이 미달, 대문자 없음, 숫자 없음, 특수문자 없음, 연속문자 있음
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("abcd");

            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("8자 이상"));
            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("대문자"));
            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("숫자"));
            assertThat(result.getSuggestions()).anyMatch(s -> s.contains("특수문자"));
        }

        @Test
        @DisplayName("score는 최대 100을 넘지 않아야 한다")
        void scoreMaxIs100() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("MyStr0ng!Pass");

            assertThat(result.getScore()).isLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("valid 필드는 isValidPassword와 동일한 결과여야 한다")
        void validFieldConsistency() {
            String password = "Test@1234";
            PasswordAnalysisResponse result = passwordUtil.analyzePassword(password);

            assertThat(result.isValid()).isEqualTo(passwordUtil.isValidPassword(password));
        }

        @Test
        @DisplayName("유효하지 않은 비밀번호의 valid 필드는 false여야 한다")
        void invalidPasswordValidField() {
            PasswordAnalysisResponse result = passwordUtil.analyzePassword("simple");

            assertThat(result.isValid()).isFalse();
        }
    }
}
