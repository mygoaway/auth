package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.security.oauth2.GoogleOAuth2UserInfo;
import com.jay.auth.security.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
        given(encryptionService.encryptNickname("Test User")).willReturn("enc_nickname");

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
