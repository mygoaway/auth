package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.exception.AccountLinkingException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.security.oauth2.GoogleOAuth2UserInfo;
import com.jay.auth.security.oauth2.OAuth2UserInfo;
import com.jay.auth.util.NicknameGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuth2UserServiceTest {

    @InjectMocks
    private OAuth2UserService oAuth2UserService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserChannelRepository userChannelRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private NicknameGenerator nicknameGenerator;
    @Mock
    private CacheManager cacheManager;

    @Test
    @DisplayName("신규 소셜 사용자가 생성되어야 한다")
    void processNewOAuth2User() {
        // given
        OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                "sub", "google-123",
                "name", "Test User",
                "email", "test@gmail.com",
                "picture", "http://example.com/pic.jpg"
        ));

        given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-123"))
                .willReturn(Optional.empty());
        given(encryptionService.encryptEmail("test@gmail.com"))
                .willReturn(new EncryptionService.EncryptedEmail("enc_email", "enc_email_lower"));
        given(nicknameGenerator.generateUnique(any())).willReturn("행복한고양이1234");
        given(encryptionService.encryptNickname("행복한고양이1234")).willReturn("enc_nickname");
        given(encryptionService.encryptNicknameLower("행복한고양이1234")).willReturn("enc_nickname_lower");

        User savedUser = createUser(1L, "uuid-1234");
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // when
        User result = oAuth2UserService.processOAuth2User(ChannelCode.GOOGLE, userInfo);

        // then
        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(userChannelRepository).save(any(UserChannel.class));
    }

    @Test
    @DisplayName("기존 소셜 사용자는 재생성되지 않아야 한다")
    void processExistingOAuth2User() {
        // given
        OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                "sub", "google-123",
                "name", "Test User",
                "email", "test@gmail.com",
                "picture", "http://example.com/pic.jpg"
        ));

        User existingUser = createUser(1L, "uuid-1234");
        UserChannel existingChannel = UserChannel.builder()
                .user(existingUser)
                .channelCode(ChannelCode.GOOGLE)
                .channelKey("google-123")
                .channelEmailEnc("enc_email")
                .channelEmailLowerEnc("enc_email_lower")
                .build();

        given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-123"))
                .willReturn(Optional.of(existingChannel));
        given(encryptionService.encryptEmail("test@gmail.com"))
                .willReturn(new EncryptionService.EncryptedEmail("enc_email", "enc_email_lower"));

        // when
        User result = oAuth2UserService.processOAuth2User(ChannelCode.GOOGLE, userInfo);

        // then
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비활성 소셜 사용자로 로그인 시 예외가 발생해야 한다")
    void processInactiveOAuth2User() {
        // given
        OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                "sub", "google-123",
                "name", "Test User",
                "email", "test@gmail.com"
        ));

        User inactiveUser = createUser(1L, "uuid-1234");
        inactiveUser.updateStatus(UserStatus.DORMANT);
        UserChannel existingChannel = UserChannel.builder()
                .user(inactiveUser)
                .channelCode(ChannelCode.GOOGLE)
                .channelKey("google-123")
                .build();

        given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-123"))
                .willReturn(Optional.of(existingChannel));

        // when & then
        assertThatThrownBy(() -> oAuth2UserService.processOAuth2User(ChannelCode.GOOGLE, userInfo))
                .isInstanceOf(IllegalStateException.class);
    }

    @Nested
    @DisplayName("PENDING_DELETE 상태 사용자 로그인")
    class PendingDeleteUser {

        @Test
        @DisplayName("PENDING_DELETE 상태의 사용자는 로그인할 수 있어야 한다")
        void processOAuth2UserWithPendingDeleteStatus() {
            // given
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-123",
                    "name", "Test User",
                    "email", "test@gmail.com",
                    "picture", "http://example.com/pic.jpg"
            ));

            User pendingDeleteUser = createUser(1L, "uuid-1234");
            pendingDeleteUser.updateStatus(UserStatus.PENDING_DELETE);
            UserChannel existingChannel = UserChannel.builder()
                    .user(pendingDeleteUser)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .build();

            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-123"))
                    .willReturn(Optional.of(existingChannel));
            given(encryptionService.encryptEmail("test@gmail.com"))
                    .willReturn(new EncryptionService.EncryptedEmail("enc_email", "enc_email_lower"));

            // when
            User result = oAuth2UserService.processOAuth2User(ChannelCode.GOOGLE, userInfo);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("이메일 없는 OAuth2 사용자")
    class NullEmailUser {

        @Test
        @DisplayName("이메일이 null인 신규 사용자가 생성되어야 한다")
        void processNewOAuth2UserWithNullEmail() {
            // given
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-456",
                    "name", "No Email User"
            ));

            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-456"))
                    .willReturn(Optional.empty());
            given(nicknameGenerator.generateUnique(any())).willReturn("행복한강아지5678");
            given(encryptionService.encryptNickname("행복한강아지5678")).willReturn("enc_nickname2");
            given(encryptionService.encryptNicknameLower("행복한강아지5678")).willReturn("enc_nickname2_lower");

            User savedUser = createUser(2L, "uuid-5678");
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // when
            User result = oAuth2UserService.processOAuth2User(ChannelCode.GOOGLE, userInfo);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).save(any(User.class));
            verify(userChannelRepository).save(any(UserChannel.class));
            verify(encryptionService, never()).encryptEmail(any());
        }
    }

    @Nested
    @DisplayName("OAuth2 계정 연동 (linking)")
    class ProcessOAuth2UserForLinking {

        @Test
        @DisplayName("기존 사용자에게 소셜 계정을 연동할 수 있어야 한다")
        void processOAuth2UserForLinkingSuccess() {
            // given
            TransactionSynchronizationManager.initSynchronization();
            try {
                Long userId = 1L;
                OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                        "sub", "google-new",
                        "name", "Test User",
                        "email", "test@gmail.com",
                        "picture", "http://example.com/pic.jpg"
                ));

                User user = createUser(userId, "uuid-1234");
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-new"))
                        .willReturn(Optional.empty());
                given(userChannelRepository.findByUserIdAndChannelCode(userId, ChannelCode.GOOGLE))
                        .willReturn(Collections.emptyList());
                given(encryptionService.encryptEmail("test@gmail.com"))
                        .willReturn(new EncryptionService.EncryptedEmail("enc_email", "enc_email_lower"));

                // when
                User result = oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(userId);
                verify(userChannelRepository).save(any(UserChannel.class));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("존재하지 않는 사용자에게 연동 시 예외가 발생해야 한다")
        void processOAuth2UserForLinkingUserNotFound() {
            // given
            Long userId = 99L;
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-999",
                    "name", "Test User",
                    "email", "test@gmail.com"
            ));

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("비활성 사용자에게 연동 시 예외가 발생해야 한다")
        void processOAuth2UserForLinkingInactiveUser() {
            // given
            Long userId = 1L;
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-123",
                    "name", "Test User",
                    "email", "test@gmail.com"
            ));

            User inactiveUser = createUser(userId, "uuid-1234");
            inactiveUser.updateStatus(UserStatus.LOCKED);
            given(userRepository.findById(userId)).willReturn(Optional.of(inactiveUser));

            // when & then
            assertThatThrownBy(() ->
                    oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("다른 사용자에게 이미 연동된 소셜 계정으로 연동 시 예외가 발생해야 한다")
        void processOAuth2UserForLinkingAlreadyLinkedToAnother() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-taken",
                    "name", "Test User",
                    "email", "test@gmail.com"
            ));

            User user = createUser(userId, "uuid-1234");
            User otherUser = createUser(otherUserId, "uuid-5678");

            UserChannel existingChannel = UserChannel.builder()
                    .user(otherUser)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-taken")
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-taken"))
                    .willReturn(Optional.of(existingChannel));

            // when & then
            assertThatThrownBy(() ->
                    oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo))
                    .isInstanceOf(AccountLinkingException.class);
        }

        @Test
        @DisplayName("이미 현재 사용자에게 연동된 소셜 계정이면 이메일만 업데이트해야 한다")
        void processOAuth2UserForLinkingAlreadyLinkedToSameUser() {
            // given
            Long userId = 1L;
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-mine",
                    "name", "Test User",
                    "email", "newemail@gmail.com",
                    "picture", "http://example.com/pic.jpg"
            ));

            User user = createUser(userId, "uuid-1234");
            UserChannel existingChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-mine")
                    .channelEmailEnc("old_enc_email")
                    .channelEmailLowerEnc("old_enc_email_lower")
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-mine"))
                    .willReturn(Optional.of(existingChannel));
            given(encryptionService.encryptEmail("newemail@gmail.com"))
                    .willReturn(new EncryptionService.EncryptedEmail("new_enc", "new_enc_lower"));

            // when
            User result = oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo);

            // then
            assertThat(result).isNotNull();
            verify(userChannelRepository, never()).save(any(UserChannel.class));
        }

        @Test
        @DisplayName("이미 같은 채널 타입이 연동되어 있으면 예외가 발생해야 한다")
        void processOAuth2UserForLinkingChannelTypeAlreadyLinked() {
            // given
            Long userId = 1L;
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-different",
                    "name", "Test User",
                    "email", "test@gmail.com"
            ));

            User user = createUser(userId, "uuid-1234");
            UserChannel existingGoogleChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-existing")
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-different"))
                    .willReturn(Optional.empty());
            given(userChannelRepository.findByUserIdAndChannelCode(userId, ChannelCode.GOOGLE))
                    .willReturn(List.of(existingGoogleChannel));

            // when & then
            assertThatThrownBy(() ->
                    oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo))
                    .isInstanceOf(AccountLinkingException.class);
        }

        @Test
        @DisplayName("이메일 없이 소셜 계정을 연동할 수 있어야 한다")
        void processOAuth2UserForLinkingWithNullEmail() {
            // given
            TransactionSynchronizationManager.initSynchronization();
            try {
                Long userId = 1L;
                OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                        "sub", "google-noemail",
                        "name", "No Email"
                ));

                User user = createUser(userId, "uuid-1234");
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-noemail"))
                        .willReturn(Optional.empty());
                given(userChannelRepository.findByUserIdAndChannelCode(userId, ChannelCode.GOOGLE))
                        .willReturn(Collections.emptyList());

                // when
                User result = oAuth2UserService.processOAuth2UserForLinking(userId, ChannelCode.GOOGLE, userInfo);

                // then
                assertThat(result).isNotNull();
                verify(userChannelRepository).save(any(UserChannel.class));
                verify(encryptionService, never()).encryptEmail(any());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Nested
    @DisplayName("채널 이메일 업데이트")
    class UpdateChannelEmail {

        @Test
        @DisplayName("기존 사용자의 채널 이메일이 null인 경우에도 처리되어야 한다")
        void processExistingUserWithNullEmailNoUpdate() {
            // given
            OAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(Map.of(
                    "sub", "google-123",
                    "name", "Test User"
            ));

            User existingUser = createUser(1L, "uuid-1234");
            UserChannel existingChannel = UserChannel.builder()
                    .user(existingUser)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .build();

            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(ChannelCode.GOOGLE, "google-123"))
                    .willReturn(Optional.of(existingChannel));

            // when
            User result = oAuth2UserService.processOAuth2User(ChannelCode.GOOGLE, userInfo);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            verify(encryptionService, never()).encryptEmail(any());
        }
    }

    // Helper
    private User createUser(Long id, String uuid) {
        User user = User.builder()
                .emailEnc("enc_email")
                .emailLowerEnc("enc_email_lower")
                .nicknameEnc("enc_nickname")
                .status(UserStatus.ACTIVE)
                .build();
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
            java.lang.reflect.Field uuidField = User.class.getDeclaredField("userUuid");
            uuidField.setAccessible(true);
            uuidField.set(user, uuid);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
