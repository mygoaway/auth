package com.jay.auth.util;

import com.jay.auth.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        AppProperties.Encryption encryption = new AppProperties.Encryption();
        encryption.setSecretKey("test-32-character-secret-key!!!!");
        appProperties.setEncryption(encryption);

        encryptionUtil = new EncryptionUtil(appProperties);
        encryptionUtil.init();
    }

    @Test
    @DisplayName("암호화 후 복호화하면 원본과 동일해야 한다")
    void encryptAndDecrypt() {
        // given
        String originalText = "test@example.com";

        // when
        String encrypted = encryptionUtil.encrypt(originalText);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(originalText);
        assertThat(encrypted).isNotEqualTo(originalText);
    }

    @Test
    @DisplayName("같은 평문을 암호화해도 매번 다른 결과가 나와야 한다 (IV 랜덤)")
    void encryptWithRandomIV() {
        // given
        String originalText = "test@example.com";

        // when
        String encrypted1 = encryptionUtil.encrypt(originalText);
        String encrypted2 = encryptionUtil.encrypt(originalText);

        // then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionUtil.decrypt(encrypted1)).isEqualTo(originalText);
        assertThat(encryptionUtil.decrypt(encrypted2)).isEqualTo(originalText);
    }

    @Test
    @DisplayName("소문자 변환 후 검색용 암호화는 결정적이어야 한다")
    void encryptLower() {
        // given
        String email = "Test@Example.COM";

        // when
        String encrypted1 = encryptionUtil.encryptLower(email);
        String encrypted2 = encryptionUtil.encryptLower(email);
        String encrypted3 = encryptionUtil.encryptLower("test@example.com");

        // then - 같은 입력이면 같은 결과 (대소문자 무관)
        assertThat(encrypted1).isEqualTo(encrypted2);
        assertThat(encrypted1).isEqualTo(encrypted3);
    }

    @Test
    @DisplayName("null 입력 시 null을 반환해야 한다")
    void nullInput() {
        assertThat(encryptionUtil.encrypt(null)).isNull();
        assertThat(encryptionUtil.decrypt(null)).isNull();
        assertThat(encryptionUtil.encryptLower(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열 입력 시 null을 반환해야 한다")
    void emptyInput() {
        assertThat(encryptionUtil.encrypt("")).isNull();
        assertThat(encryptionUtil.decrypt("")).isNull();
        assertThat(encryptionUtil.encryptLower("")).isNull();
    }

    @Test
    @DisplayName("한글 암호화/복호화가 정상 동작해야 한다")
    void koreanText() {
        // given
        String korean = "홍길동";

        // when
        String encrypted = encryptionUtil.encrypt(korean);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(korean);
    }
}
