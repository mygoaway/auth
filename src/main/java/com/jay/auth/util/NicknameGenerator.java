package com.jay.auth.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Function;

@Component
public class NicknameGenerator {

    private static final String[] ADJECTIVES = {
            "행복한", "즐거운", "신나는", "멋진", "귀여운", "용감한", "지혜로운", "활발한",
            "친절한", "밝은", "똑똑한", "빠른", "강한", "부드러운", "따뜻한", "시원한"
    };

    private static final String[] NOUNS = {
            "고양이", "강아지", "토끼", "판다", "여우", "사자", "호랑이", "코끼리",
            "펭귄", "돌고래", "부엉이", "독수리", "다람쥐", "햄스터", "고래", "곰"
    };

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        int number = random.nextInt(10000);
        return String.format("%s%s%04d", adjective, noun, number);
    }

    /**
     * 중복 체크 함수를 받아 고유한 닉네임을 생성한다.
     * 1차 시도가 중복이면 UUID 앞 8자를 suffix로 사용해 고유성을 보장한다.
     *
     * @param existsChecker 닉네임이 이미 존재하는지 확인하는 함수 (true = 중복)
     * @return 고유한 닉네임
     */
    public String generateUnique(Function<String, Boolean> existsChecker) {
        String nickname = generate();
        if (!existsChecker.apply(nickname)) {
            return nickname;
        }
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return adjective + noun + uuidSuffix;
    }
}
