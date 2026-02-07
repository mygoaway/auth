package com.jay.auth.service;

import com.jay.auth.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자 정보 암호화/복호화 서비스
 * 이메일, 핸드폰 번호, 닉네임 등 개인정보 암호화 처리
 */
@Service
@RequiredArgsConstructor
public class EncryptionService {

    private final EncryptionUtil encryptionUtil;

    /**
     * 이메일 암호화 결과
     */
    public record EncryptedEmail(String encrypted, String encryptedLower) {}

    /**
     * 이메일 암호화
     * @param email 평문 이메일
     * @return 원본 암호화 + 소문자 암호화
     */
    public EncryptedEmail encryptEmail(String email) {
        if (email == null || email.isEmpty()) {
            return new EncryptedEmail(null, null);
        }
        return new EncryptedEmail(
                encryptionUtil.encrypt(email),
                encryptionUtil.encryptLower(email)
        );
    }

    /**
     * 이메일 복호화
     * @param encryptedEmail 암호화된 이메일
     * @return 평문 이메일
     */
    public String decryptEmail(String encryptedEmail) {
        return encryptionUtil.decrypt(encryptedEmail);
    }

    /**
     * 핸드폰 번호 암호화
     * @param phone 평문 핸드폰 번호
     * @return 암호화된 핸드폰 번호
     */
    public String encryptPhone(String phone) {
        return encryptionUtil.encrypt(phone);
    }

    /**
     * 핸드폰 번호 복호화
     * @param encryptedPhone 암호화된 핸드폰 번호
     * @return 평문 핸드폰 번호
     */
    public String decryptPhone(String encryptedPhone) {
        return encryptionUtil.decrypt(encryptedPhone);
    }

    /**
     * 닉네임 암호화
     * @param nickname 평문 닉네임
     * @return 암호화된 닉네임
     */
    public String encryptNickname(String nickname) {
        return encryptionUtil.encrypt(nickname);
    }

    /**
     * 닉네임 복호화
     * @param encryptedNickname 암호화된 닉네임
     * @return 평문 닉네임
     */
    public String decryptNickname(String encryptedNickname) {
        return encryptionUtil.decrypt(encryptedNickname);
    }

    /**
     * 소문자 변환 후 암호화 (검색용)
     * @param text 평문
     * @return 소문자 변환 후 암호화된 값
     */
    public String encryptForSearch(String text) {
        return encryptionUtil.encryptLower(text);
    }

    /**
     * 범용 암호화
     * @param plainText 평문
     * @return 암호화된 값
     */
    public String encrypt(String plainText) {
        return encryptionUtil.encrypt(plainText);
    }

    /**
     * 범용 복호화
     * @param encryptedText 암호화된 값
     * @return 평문
     */
    public String decrypt(String encryptedText) {
        return encryptionUtil.decrypt(encryptedText);
    }
}
