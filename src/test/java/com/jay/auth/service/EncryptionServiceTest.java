package com.jay.auth.service;

import com.jay.auth.util.EncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

    @InjectMocks
    private EncryptionService encryptionService;

    @Mock
    private EncryptionUtil encryptionUtil;

    @Nested
    @DisplayName("이메일 암호화")
    class EncryptEmail {

        @Test
        @DisplayName("이메일이 원본 암호화 + 소문자 암호화로 반환되어야 한다")
        void encryptEmailSuccess() {
            // given
            String email = "Test@Email.com";
            given(encryptionUtil.encrypt(email)).willReturn("encrypted_original");
            given(encryptionUtil.encryptLower(email)).willReturn("encrypted_lower");

            // when
            EncryptionService.EncryptedEmail result = encryptionService.encryptEmail(email);

            // then
            assertThat(result.encrypted()).isEqualTo("encrypted_original");
            assertThat(result.encryptedLower()).isEqualTo("encrypted_lower");
            verify(encryptionUtil).encrypt(email);
            verify(encryptionUtil).encryptLower(email);
        }

        @Test
        @DisplayName("null 이메일은 null을 반환해야 한다")
        void encryptEmailWithNull() {
            // when
            EncryptionService.EncryptedEmail result = encryptionService.encryptEmail(null);

            // then
            assertThat(result.encrypted()).isNull();
            assertThat(result.encryptedLower()).isNull();
        }

        @Test
        @DisplayName("빈 이메일은 null을 반환해야 한다")
        void encryptEmailWithEmpty() {
            // when
            EncryptionService.EncryptedEmail result = encryptionService.encryptEmail("");

            // then
            assertThat(result.encrypted()).isNull();
            assertThat(result.encryptedLower()).isNull();
        }
    }

    @Nested
    @DisplayName("이메일 복호화")
    class DecryptEmail {

        @Test
        @DisplayName("암호화된 이메일이 복호화되어야 한다")
        void decryptEmailSuccess() {
            // given
            given(encryptionUtil.decrypt("encrypted_email")).willReturn("test@email.com");

            // when
            String result = encryptionService.decryptEmail("encrypted_email");

            // then
            assertThat(result).isEqualTo("test@email.com");
            verify(encryptionUtil).decrypt("encrypted_email");
        }

        @Test
        @DisplayName("null 암호문은 null을 반환해야 한다")
        void decryptEmailWithNull() {
            // given
            given(encryptionUtil.decrypt(null)).willReturn(null);

            // when
            String result = encryptionService.decryptEmail(null);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("핸드폰 번호 암호화")
    class EncryptPhone {

        @Test
        @DisplayName("핸드폰 번호가 암호화되어야 한다")
        void encryptPhoneSuccess() {
            // given
            given(encryptionUtil.encrypt("010-1234-5678")).willReturn("encrypted_phone");

            // when
            String result = encryptionService.encryptPhone("010-1234-5678");

            // then
            assertThat(result).isEqualTo("encrypted_phone");
            verify(encryptionUtil).encrypt("010-1234-5678");
        }
    }

    @Nested
    @DisplayName("핸드폰 번호 복호화")
    class DecryptPhone {

        @Test
        @DisplayName("암호화된 핸드폰 번호가 복호화되어야 한다")
        void decryptPhoneSuccess() {
            // given
            given(encryptionUtil.decrypt("encrypted_phone")).willReturn("010-1234-5678");

            // when
            String result = encryptionService.decryptPhone("encrypted_phone");

            // then
            assertThat(result).isEqualTo("010-1234-5678");
            verify(encryptionUtil).decrypt("encrypted_phone");
        }
    }

    @Nested
    @DisplayName("닉네임 암호화")
    class EncryptNickname {

        @Test
        @DisplayName("닉네임이 암호화되어야 한다")
        void encryptNicknameSuccess() {
            // given
            given(encryptionUtil.encrypt("테스트닉네임")).willReturn("encrypted_nickname");

            // when
            String result = encryptionService.encryptNickname("테스트닉네임");

            // then
            assertThat(result).isEqualTo("encrypted_nickname");
            verify(encryptionUtil).encrypt("테스트닉네임");
        }
    }

    @Nested
    @DisplayName("닉네임 복호화")
    class DecryptNickname {

        @Test
        @DisplayName("암호화된 닉네임이 복호화되어야 한다")
        void decryptNicknameSuccess() {
            // given
            given(encryptionUtil.decrypt("encrypted_nickname")).willReturn("테스트닉네임");

            // when
            String result = encryptionService.decryptNickname("encrypted_nickname");

            // then
            assertThat(result).isEqualTo("테스트닉네임");
            verify(encryptionUtil).decrypt("encrypted_nickname");
        }
    }

    @Nested
    @DisplayName("검색용 암호화")
    class EncryptForSearch {

        @Test
        @DisplayName("소문자 변환 후 암호화되어야 한다")
        void encryptForSearchSuccess() {
            // given
            given(encryptionUtil.encryptLower("Test@Email.com")).willReturn("encrypted_lower");

            // when
            String result = encryptionService.encryptForSearch("Test@Email.com");

            // then
            assertThat(result).isEqualTo("encrypted_lower");
            verify(encryptionUtil).encryptLower("Test@Email.com");
        }
    }

    @Nested
    @DisplayName("범용 암호화/복호화")
    class EncryptDecrypt {

        @Test
        @DisplayName("범용 암호화가 동작해야 한다")
        void encryptSuccess() {
            // given
            given(encryptionUtil.encrypt("plain_text")).willReturn("encrypted_text");

            // when
            String result = encryptionService.encrypt("plain_text");

            // then
            assertThat(result).isEqualTo("encrypted_text");
        }

        @Test
        @DisplayName("범용 복호화가 동작해야 한다")
        void decryptSuccess() {
            // given
            given(encryptionUtil.decrypt("encrypted_text")).willReturn("plain_text");

            // when
            String result = encryptionService.decrypt("encrypted_text");

            // then
            assertThat(result).isEqualTo("plain_text");
        }
    }
}
