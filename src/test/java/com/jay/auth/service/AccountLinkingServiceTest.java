package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.exception.AccountLinkingException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountLinkingServiceTest {

    @InjectMocks
    private AccountLinkingService accountLinkingService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserChannelRepository userChannelRepository;
    @Mock
    private UserSignInInfoRepository userSignInInfoRepository;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private PasswordUtil passwordUtil;
    @Mock
    private SecurityNotificationService securityNotificationService;

    @Nested
    @DisplayName("소셜 계정 연동")
    class LinkSocialAccount {

        @Test
        @DisplayName("소셜 계정 연동이 성공해야 한다")
        void linkSocialAccountSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            ChannelCode channelCode = ChannelCode.GOOGLE;
            String channelKey = "google-123";
            String email = "test@gmail.com";

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey))
                    .willReturn(Optional.empty());
            given(userChannelRepository.findByUserIdAndChannelCode(userId, channelCode))
                    .willReturn(Collections.emptyList());
            given(encryptionService.encryptEmail(email))
                    .willReturn(new EncryptionService.EncryptedEmail("enc", "enc_lower"));

            // when
            accountLinkingService.linkSocialAccount(userId, channelCode, channelKey, email);

            // then
            verify(userChannelRepository).save(any(UserChannel.class));
            verify(securityNotificationService).notifyAccountLinked(userId, channelCode);
        }

        @Test
        @DisplayName("다른 사용자에게 연동된 계정이면 실패해야 한다")
        void linkFailsWhenLinkedToAnotherUser() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            User user = createUser(userId);
            User otherUser = createUser(otherUserId);
            ChannelCode channelCode = ChannelCode.GOOGLE;
            String channelKey = "google-123";

            UserChannel existingChannel = UserChannel.builder()
                    .user(otherUser)
                    .channelCode(channelCode)
                    .channelKey(channelKey)
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey))
                    .willReturn(Optional.of(existingChannel));

            // when & then
            assertThatThrownBy(() ->
                    accountLinkingService.linkSocialAccount(userId, channelCode, channelKey, null))
                    .isInstanceOf(AccountLinkingException.class);
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 실패해야 한다")
        void linkFailsWhenUserNotFound() {
            // given
            Long userId = 1L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    accountLinkingService.linkSocialAccount(userId, ChannelCode.GOOGLE, "key", null))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("채널 연동 해제")
    class UnlinkChannel {

        @Test
        @DisplayName("마지막 채널은 해제할 수 없어야 한다")
        void unlinkFailsWithLastChannel() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() ->
                    accountLinkingService.unlinkChannel(userId, ChannelCode.EMAIL))
                    .isInstanceOf(AccountLinkingException.class);
        }

        @Test
        @DisplayName("연동되지 않은 채널은 해제할 수 없어야 한다")
        void unlinkFailsWhenChannelNotFound() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() ->
                    accountLinkingService.unlinkChannel(userId, ChannelCode.GOOGLE))
                    .isInstanceOf(AccountLinkingException.class);
        }
    }

    @Nested
    @DisplayName("소셜 계정 사용 가능 여부")
    class IsSocialAccountAvailable {

        @Test
        @DisplayName("사용되지 않은 계정은 사용 가능해야 한다")
        void availableWhenNotUsed() {
            // given
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(
                    ChannelCode.GOOGLE, "google-123")).willReturn(Optional.empty());

            // when
            boolean result = accountLinkingService.isSocialAccountAvailable(
                    ChannelCode.GOOGLE, "google-123", 1L);

            // then
            assert result;
        }
    }

    // Helper methods
    private User createUser(Long userId) {
        User user = User.builder().build();
        setField(user, "id", userId);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
