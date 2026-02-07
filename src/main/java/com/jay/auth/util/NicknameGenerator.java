package com.jay.auth.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

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
}
