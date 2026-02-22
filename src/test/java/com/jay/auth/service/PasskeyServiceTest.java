package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserPasskey;
import com.jay.auth.dto.response.PasskeyAuthenticationOptionsResponse;
import com.jay.auth.dto.response.PasskeyListResponse;
import com.jay.auth.dto.response.PasskeyRegistrationOptionsResponse;
import com.jay.auth.exception.PasskeyException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserPasskeyRepository;
import com.jay.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasskeyServiceTest {

    @InjectMocks
    private PasskeyService passkeyService;

    @Mock
    private UserPasskeyRepository userPasskeyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SecurityNotificationService securityNotificationService;

    @Nested
    @DisplayName("패스키 등록 옵션 생성 (generateRegistrationOptions)")
    class GenerateRegistrationOptions {

        @Test
        @DisplayName("정상적으로 등록 옵션을 생성해야 한다")
        void generateRegistrationOptionsSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_nickname");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userPasskeyRepository.countByUserId(userId)).willReturn(0L);
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("testuser");
            given(userPasskeyRepository.findByUserId(userId)).willReturn(List.of());
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

            setField(passkeyService, "rpId", "localhost");
            setField(passkeyService, "rpName", "Authly");

            // when
            PasskeyRegistrationOptionsResponse response = passkeyService.generateRegistrationOptions(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getChallenge()).isNotNull();
            assertThat(response.getRp().getId()).isEqualTo("localhost");
            assertThat(response.getRp().getName()).isEqualTo("Authly");
            assertThat(response.getUser().getName()).isEqualTo("testuser");
            assertThat(response.getPubKeyCredParams()).hasSize(2);
            assertThat(response.getExcludeCredentials()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 경우 실패해야 한다")
        void generateRegistrationOptionsFails_UserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> passkeyService.generateRegistrationOptions(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("패스키 한도 초과 시 실패해야 한다")
        void generateRegistrationOptionsFails_LimitExceeded() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null);

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userPasskeyRepository.countByUserId(userId)).willReturn(10L);

            // when & then
            assertThatThrownBy(() -> passkeyService.generateRegistrationOptions(userId))
                    .isInstanceOf(PasskeyException.class);
        }

        @Test
        @DisplayName("이미 등록된 패스키가 있으면 excludeCredentials에 포함해야 한다")
        void generateRegistrationOptionsWithExclude() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null);

            UserPasskey existing = UserPasskey.builder()
                    .user(user)
                    .credentialId("existing-cred-id")
                    .publicKey(new byte[]{1, 2, 3})
                    .deviceName("기존 패스키")
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userPasskeyRepository.countByUserId(userId)).willReturn(1L);
            given(userPasskeyRepository.findByUserId(userId)).willReturn(List.of(existing));
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

            setField(passkeyService, "rpId", "localhost");
            setField(passkeyService, "rpName", "Authly");

            // when
            PasskeyRegistrationOptionsResponse response = passkeyService.generateRegistrationOptions(userId);

            // then
            assertThat(response.getExcludeCredentials()).hasSize(1);
            assertThat(response.getExcludeCredentials().get(0).getId()).isEqualTo("existing-cred-id");
        }
    }

    @Nested
    @DisplayName("패스키 인증 옵션 생성 (generateAuthenticationOptions)")
    class GenerateAuthenticationOptions {

        @Test
        @DisplayName("정상적으로 인증 옵션을 생성해야 한다")
        void generateAuthenticationOptionsSuccess() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            setField(passkeyService, "rpId", "localhost");

            // when
            PasskeyAuthenticationOptionsResponse response = passkeyService.generateAuthenticationOptions();

            // then
            assertThat(response).isNotNull();
            assertThat(response.getChallenge()).isNotNull();
            assertThat(response.getRpId()).isEqualTo("localhost");
            assertThat(response.getUserVerification()).isEqualTo("preferred");
            assertThat(response.getAllowCredentials()).isEmpty();
        }
    }

    @Nested
    @DisplayName("패스키 목록 조회 (listPasskeys)")
    class ListPasskeys {

        @Test
        @DisplayName("등록된 패스키 목록을 반환해야 한다")
        void listPasskeysSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null);
            UserPasskey passkey1 = createPasskey(1L, user, "cred-1", "MacBook");
            UserPasskey passkey2 = createPasskey(2L, user, "cred-2", "iPhone");

            given(userPasskeyRepository.findByUserId(userId)).willReturn(List.of(passkey1, passkey2));

            // when
            PasskeyListResponse response = passkeyService.listPasskeys(userId);

            // then
            assertThat(response.getPasskeys()).hasSize(2);
            assertThat(response.getPasskeys().get(0).getDeviceName()).isEqualTo("MacBook");
            assertThat(response.getPasskeys().get(1).getDeviceName()).isEqualTo("iPhone");
        }

        @Test
        @DisplayName("등록된 패스키가 없으면 빈 목록을 반환해야 한다")
        void listPasskeysEmpty() {
            // given
            Long userId = 1L;
            given(userPasskeyRepository.findByUserId(userId)).willReturn(List.of());

            // when
            PasskeyListResponse response = passkeyService.listPasskeys(userId);

            // then
            assertThat(response.getPasskeys()).isEmpty();
        }
    }

    @Nested
    @DisplayName("패스키 삭제 (deletePasskey)")
    class DeletePasskey {

        @Test
        @DisplayName("패스키를 정상적으로 삭제해야 한다")
        void deletePasskeySuccess() {
            // given
            Long userId = 1L;
            Long passkeyId = 10L;
            User user = createUser(userId, null);
            UserPasskey passkey = createPasskey(passkeyId, user, "cred-1", "테스트");

            given(userPasskeyRepository.findByIdAndUserId(passkeyId, userId)).willReturn(Optional.of(passkey));

            // when
            passkeyService.deletePasskey(userId, passkeyId);

            // then
            verify(userPasskeyRepository).delete(passkey);
        }

        @Test
        @DisplayName("존재하지 않는 패스키 삭제 시 실패해야 한다")
        void deletePasskeyFails_NotFound() {
            // given
            Long userId = 1L;
            Long passkeyId = 999L;
            given(userPasskeyRepository.findByIdAndUserId(passkeyId, userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> passkeyService.deletePasskey(userId, passkeyId))
                    .isInstanceOf(PasskeyException.class);
        }
    }

    @Nested
    @DisplayName("패스키 이름 변경 (renamePasskey)")
    class RenamePasskey {

        @Test
        @DisplayName("패스키 이름을 정상적으로 변경해야 한다")
        void renamePasskeySuccess() {
            // given
            Long userId = 1L;
            Long passkeyId = 10L;
            User user = createUser(userId, null);
            UserPasskey passkey = createPasskey(passkeyId, user, "cred-1", "기존 이름");

            given(userPasskeyRepository.findByIdAndUserId(passkeyId, userId)).willReturn(Optional.of(passkey));

            // when
            passkeyService.renamePasskey(userId, passkeyId, "새 이름");

            // then
            assertThat(passkey.getDeviceName()).isEqualTo("새 이름");
        }

        @Test
        @DisplayName("존재하지 않는 패스키 이름 변경 시 실패해야 한다")
        void renamePasskeyFails_NotFound() {
            // given
            Long userId = 1L;
            Long passkeyId = 999L;
            given(userPasskeyRepository.findByIdAndUserId(passkeyId, userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> passkeyService.renamePasskey(userId, passkeyId, "새 이름"))
                    .isInstanceOf(PasskeyException.class);
        }
    }

    @Nested
    @DisplayName("패스키 존재 확인 (hasPasskeys)")
    class HasPasskeys {

        @Test
        @DisplayName("패스키가 있으면 true를 반환해야 한다")
        void hasPasskeysTrue() {
            // given
            given(userPasskeyRepository.existsByUserId(1L)).willReturn(true);

            // when & then
            assertThat(passkeyService.hasPasskeys(1L)).isTrue();
        }

        @Test
        @DisplayName("패스키가 없으면 false를 반환해야 한다")
        void hasPasskeysFalse() {
            // given
            given(userPasskeyRepository.existsByUserId(1L)).willReturn(false);

            // when & then
            assertThat(passkeyService.hasPasskeys(1L)).isFalse();
        }
    }

    // Helper methods
    private User createUser(Long userId, String nicknameEnc) {
        User user = User.builder()
                .nicknameEnc(nicknameEnc)
                .build();
        setField(user, "id", userId);
        setField(user, "userUuid", "uuid-0000-" + userId);
        return user;
    }

    private UserPasskey createPasskey(Long id, User user, String credentialId, String deviceName) {
        UserPasskey passkey = UserPasskey.builder()
                .user(user)
                .credentialId(credentialId)
                .publicKey(new byte[]{1, 2, 3})
                .deviceName(deviceName)
                .build();
        setField(passkey, "id", id);
        setField(passkey, "createdAt", LocalDateTime.now());
        return passkey;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
