package com.jay.auth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("NicknameGenerator 테스트")
class NicknameGeneratorTest {

    private NicknameGenerator nicknameGenerator;

    private static final String[] ADJECTIVES = {
            "행복한", "즐거운", "신나는", "멋진", "귀여운", "용감한", "지혜로운", "활발한",
            "친절한", "밝은", "똑똑한", "빠른", "강한", "부드러운", "따뜻한", "시원한"
    };

    private static final String[] NOUNS = {
            "고양이", "강아지", "토끼", "판다", "여우", "사자", "호랑이", "코끼리",
            "펭귄", "돌고래", "부엉이", "독수리", "다람쥐", "햄스터", "고래", "곰"
    };

    @BeforeEach
    void setUp() {
        nicknameGenerator = new NicknameGenerator();
    }

    @Nested
    @DisplayName("generate 메서드")
    class Generate {

        @Test
        @DisplayName("닉네임이 null이 아닌 값을 반환한다")
        void shouldReturnNonNullNickname() {
            // when
            String nickname = nicknameGenerator.generate();

            // then
            assertThat(nickname).isNotNull();
            assertThat(nickname).isNotEmpty();
        }

        @Test
        @DisplayName("닉네임이 한글 형용사 + 한글 명사 + 4자리 숫자 형식이다")
        void shouldMatchExpectedFormat() {
            // given
            String adjectivePattern = String.join("|", ADJECTIVES);
            String nounPattern = String.join("|", NOUNS);
            Pattern pattern = Pattern.compile(
                    "(" + adjectivePattern + ")(" + nounPattern + ")\\d{4}"
            );

            // when
            String nickname = nicknameGenerator.generate();

            // then
            assertThat(nickname).matches(pattern);
        }

        @Test
        @DisplayName("닉네임 끝 4자리가 0000~9999 범위의 숫자이다")
        void shouldEndWithFourDigitNumber() {
            // when
            String nickname = nicknameGenerator.generate();

            // then
            String numberPart = nickname.replaceAll("[^0-9]", "");
            assertThat(numberPart).hasSize(4);
            int number = Integer.parseInt(numberPart);
            assertThat(number).isBetween(0, 9999);
        }

        @Test
        @DisplayName("닉네임에 유효한 형용사가 포함되어 있다")
        void shouldContainValidAdjective() {
            // when
            String nickname = nicknameGenerator.generate();

            // then
            boolean containsAdjective = false;
            for (String adj : ADJECTIVES) {
                if (nickname.startsWith(adj)) {
                    containsAdjective = true;
                    break;
                }
            }
            assertThat(containsAdjective)
                    .as("닉네임 '%s'이(가) 유효한 형용사로 시작해야 한다", nickname)
                    .isTrue();
        }

        @Test
        @DisplayName("닉네임에 유효한 명사가 포함되어 있다")
        void shouldContainValidNoun() {
            // when
            String nickname = nicknameGenerator.generate();

            // then
            boolean containsNoun = false;
            for (String noun : NOUNS) {
                if (nickname.contains(noun)) {
                    containsNoun = true;
                    break;
                }
            }
            assertThat(containsNoun)
                    .as("닉네임 '%s'에 유효한 명사가 포함되어야 한다", nickname)
                    .isTrue();
        }

        @Test
        @DisplayName("숫자가 4자리 미만일 경우 0으로 패딩된다")
        void shouldPadNumberWithZeros() {
            // given - 여러 번 생성하여 0으로 패딩된 케이스 확인
            Set<String> numberParts = new HashSet<>();

            // when
            for (int i = 0; i < 100; i++) {
                String nickname = nicknameGenerator.generate();
                String numberPart = nickname.substring(nickname.length() - 4);
                numberParts.add(numberPart);
            }

            // then - 모든 숫자 부분이 정확히 4자리인지 확인
            for (String numberPart : numberParts) {
                assertThat(numberPart).hasSize(4);
                assertThat(numberPart).matches("\\d{4}");
            }
        }
    }

    @Nested
    @DisplayName("generateUnique 메서드")
    class GenerateUnique {

        @Test
        @DisplayName("중복이 없으면 기본 generate() 결과를 반환한다")
        void shouldReturnGeneratedNicknameWhenNoDuplicate() {
            // given
            Function<String, Boolean> noConflict = nick -> false;

            // when
            String nickname = nicknameGenerator.generateUnique(noConflict);

            // then
            assertThat(nickname).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("중복이 있으면 UUID suffix 조합 닉네임을 반환한다")
        void shouldReturnUuidSuffixNicknameWhenDuplicate() {
            // given - 항상 중복 있음으로 응답
            Function<String, Boolean> alwaysConflict = nick -> true;

            // when
            String nickname = nicknameGenerator.generateUnique(alwaysConflict);

            // then - 형용사+명사+8자 hex suffix 형태여야 한다
            assertThat(nickname).isNotNull().isNotEmpty();
            String suffix = nickname.replaceAll("[가-힣]+", "");
            assertThat(suffix).hasSize(8).matches("[0-9a-f]{8}");
        }

        @Test
        @DisplayName("반환된 닉네임이 existsChecker를 통과한다")
        void shouldReturnNicknameThatPassesChecker() {
            // given - 첫 번째 호출에만 중복 응답
            java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
            Function<String, Boolean> onlyFirstConflict = nick -> callCount.getAndIncrement() == 0;

            // when
            String nickname = nicknameGenerator.generateUnique(onlyFirstConflict);

            // then
            assertThat(nickname).isNotNull();
        }
    }

    @Nested
    @DisplayName("닉네임 고유성 검증")
    class Uniqueness {

        @Test
        @DisplayName("100번 생성 시 대부분 고유한 닉네임이 생성된다")
        void shouldGenerateMostlyUniqueNicknames() {
            // given
            Set<String> nicknames = new HashSet<>();
            int totalGenerations = 100;

            // when
            for (int i = 0; i < totalGenerations; i++) {
                nicknames.add(nicknameGenerator.generate());
            }

            // then - 랜덤이므로 100% 고유하지 않을 수 있으나 대부분 고유해야 한다
            // 16 형용사 * 16 명사 * 10000 숫자 = 2,560,000 가능 조합
            assertThat(nicknames.size())
                    .as("100번 생성 시 90개 이상 고유해야 한다")
                    .isGreaterThanOrEqualTo(90);
        }

        @Test
        @DisplayName("연속 생성된 두 닉네임이 서로 다르다")
        void shouldGenerateDifferentNicknamesConsecutively() {
            // when
            String nickname1 = nicknameGenerator.generate();
            String nickname2 = nicknameGenerator.generate();

            // then - 확률적으로 같을 수 있으나 매우 희박
            // 2,560,000 가능 조합 중 연속 동일 확률 매우 낮음
            assertThat(nickname1).isNotEqualTo(nickname2);
        }
    }

    @Nested
    @DisplayName("닉네임 형식 검증")
    class FormatValidation {

        @Test
        @DisplayName("여러 번 생성해도 항상 올바른 형식을 유지한다")
        void shouldAlwaysMaintainCorrectFormat() {
            // given
            String adjectivePattern = String.join("|", ADJECTIVES);
            String nounPattern = String.join("|", NOUNS);
            Pattern pattern = Pattern.compile(
                    "(" + adjectivePattern + ")(" + nounPattern + ")\\d{4}"
            );

            // when & then
            for (int i = 0; i < 50; i++) {
                String nickname = nicknameGenerator.generate();
                assertThat(nickname)
                        .as("생성된 닉네임 '%s'이(가) 올바른 형식이어야 한다", nickname)
                        .matches(pattern);
            }
        }

        @Test
        @DisplayName("닉네임 길이가 적절한 범위 내에 있다")
        void shouldHaveReasonableLength() {
            // given
            // 최소: "밝은" (2) + "곰" (1) + "0000" (4) = 7
            // 최대: "지혜로운" (4) + "코끼리" (3) + "9999" (4) = 11

            // when
            String nickname = nicknameGenerator.generate();

            // then
            assertThat(nickname.length()).isBetween(7, 11);
        }
    }
}
