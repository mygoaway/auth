package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.ChannelStatusResponse;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

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
            given(cacheManager.getCache("userProfile")).willReturn(cache);
            given(cacheManager.getCache("securityDashboard")).willReturn(cache);

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
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("현재 사용자에게 연동된 계정은 사용 가능해야 한다")
        void availableWhenLinkedToCurrentUser() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel channel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .build();

            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(
                    ChannelCode.GOOGLE, "google-123")).willReturn(Optional.of(channel));

            // when
            boolean result = accountLinkingService.isSocialAccountAvailable(
                    ChannelCode.GOOGLE, "google-123", userId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("다른 사용자에게 연동된 계정은 사용 불가해야 한다")
        void notAvailableWhenLinkedToAnotherUser() {
            // given
            Long currentUserId = 1L;
            Long otherUserId = 2L;
            User otherUser = createUser(otherUserId);
            UserChannel channel = UserChannel.builder()
                    .user(otherUser)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .build();

            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(
                    ChannelCode.GOOGLE, "google-123")).willReturn(Optional.of(channel));

            // when
            boolean result = accountLinkingService.isSocialAccountAvailable(
                    ChannelCode.GOOGLE, "google-123", currentUserId);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("채널 상태 조회")
    class GetChannelsStatus {

        @Test
        @DisplayName("채널 상태가 정상 조회되어야 한다")
        void getChannelsStatusSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .channelEmailEnc("enc_email")
                    .channelEmailLowerEnc("enc_email_lower")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");

            // when
            ChannelStatusResponse response = accountLinkingService.getChannelsStatus(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getChannels()).isNotEmpty();
            // EMAIL should be linked
            ChannelStatusResponse.ChannelStatus emailStatus = response.getChannels().stream()
                    .filter(ch -> "EMAIL".equals(ch.getChannelCode()))
                    .findFirst().orElse(null);
            assertThat(emailStatus).isNotNull();
            assertThat(emailStatus.isLinked()).isTrue();
            assertThat(emailStatus.getChannelEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 예외가 발생해야 한다")
        void getChannelsStatusUserNotFound() {
            // given
            Long userId = 99L;
            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> accountLinkingService.getChannelsStatus(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("채널 연동 해제 - 추가 케이스")
    class UnlinkChannelAdditional {

        @Test
        @DisplayName("소셜 채널 연동 해제가 성공해야 한다")
        void unlinkSocialChannelSuccess() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .build();
            UserChannel googleChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            channels.add(googleChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));
            given(cacheManager.getCache("userProfile")).willReturn(cache);
            given(cacheManager.getCache("securityDashboard")).willReturn(cache);

            // when
            accountLinkingService.unlinkChannel(userId, ChannelCode.GOOGLE);

            // then
            verify(securityNotificationService).notifyAccountUnlinked(userId, ChannelCode.GOOGLE);
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 채널 해제 시 예외가 발생해야 한다")
        void unlinkChannelUserNotFound() {
            // given
            Long userId = 99L;
            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> accountLinkingService.unlinkChannel(userId, ChannelCode.GOOGLE))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("이메일 채널은 해제할 수 없어야 한다 (2개 채널 보유 시)")
        void unlinkEmailChannelFails() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .build();
            UserChannel googleChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            channels.add(googleChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));

            // when & then
            assertThatThrownBy(() -> accountLinkingService.unlinkChannel(userId, ChannelCode.EMAIL))
                    .isInstanceOf(AccountLinkingException.class);
        }
    }

    @Nested
    @DisplayName("소셜 계정 연동 - 추가 케이스")
    class LinkSocialAccountAdditional {

        @Test
        @DisplayName("이미 현재 사용자에게 연동된 소셜 계정이면 실패해야 한다")
        void linkFailsWhenAlreadyLinkedToSameUser() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            ChannelCode channelCode = ChannelCode.GOOGLE;
            String channelKey = "google-123";

            UserChannel existingChannel = UserChannel.builder()
                    .user(user)
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
        @DisplayName("이미 같은 채널 타입이 연동되어 있으면 실패해야 한다")
        void linkFailsWhenChannelTypeAlreadyLinked() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            ChannelCode channelCode = ChannelCode.GOOGLE;
            String channelKey = "google-new";

            UserChannel existingGoogleChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(channelCode)
                    .channelKey("google-old")
                    .build();

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey))
                    .willReturn(Optional.empty());
            given(userChannelRepository.findByUserIdAndChannelCode(userId, channelCode))
                    .willReturn(List.of(existingGoogleChannel));

            // when & then
            assertThatThrownBy(() ->
                    accountLinkingService.linkSocialAccount(userId, channelCode, channelKey, null))
                    .isInstanceOf(AccountLinkingException.class);
        }

        @Test
        @DisplayName("이메일 없이 소셜 계정 연동이 성공해야 한다")
        void linkSocialAccountWithoutEmail() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            ChannelCode channelCode = ChannelCode.KAKAO;
            String channelKey = "kakao-123";

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userChannelRepository.findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey))
                    .willReturn(Optional.empty());
            given(userChannelRepository.findByUserIdAndChannelCode(userId, channelCode))
                    .willReturn(Collections.emptyList());
            given(cacheManager.getCache("userProfile")).willReturn(cache);
            given(cacheManager.getCache("securityDashboard")).willReturn(cache);

            // when
            accountLinkingService.linkSocialAccount(userId, channelCode, channelKey, null);

            // then
            verify(userChannelRepository).save(any(UserChannel.class));
            verify(encryptionService, never()).encryptEmail(any());
            verify(securityNotificationService).notifyAccountLinked(userId, channelCode);
        }
    }

    @Nested
    @DisplayName("채널 상태 조회 - 다채널 케이스")
    class GetChannelsStatusAdditional {

        @Test
        @DisplayName("여러 채널이 연동된 경우 모두 포함되어야 한다")
        void getChannelsStatusWithMultipleChannels() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .channelEmailEnc("enc_email")
                    .channelEmailLowerEnc("enc_email_lower")
                    .build();
            UserChannel googleChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.GOOGLE)
                    .channelKey("google-123")
                    .channelEmailEnc("enc_google_email")
                    .channelEmailLowerEnc("enc_google_email_lower")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            channels.add(googleChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");
            given(encryptionService.decryptEmail("enc_google_email")).willReturn("test@gmail.com");

            // when
            ChannelStatusResponse response = accountLinkingService.getChannelsStatus(userId);

            // then
            ChannelStatusResponse.ChannelStatus emailStatus = response.getChannels().stream()
                    .filter(ch -> "EMAIL".equals(ch.getChannelCode()))
                    .findFirst().orElse(null);
            ChannelStatusResponse.ChannelStatus googleStatus = response.getChannels().stream()
                    .filter(ch -> "GOOGLE".equals(ch.getChannelCode()))
                    .findFirst().orElse(null);
            ChannelStatusResponse.ChannelStatus kakaoStatus = response.getChannels().stream()
                    .filter(ch -> "KAKAO".equals(ch.getChannelCode()))
                    .findFirst().orElse(null);

            assertThat(emailStatus).isNotNull();
            assertThat(emailStatus.isLinked()).isTrue();
            assertThat(googleStatus).isNotNull();
            assertThat(googleStatus.isLinked()).isTrue();
            assertThat(kakaoStatus).isNotNull();
            assertThat(kakaoStatus.isLinked()).isFalse();
        }

        @Test
        @DisplayName("이메일 없는 소셜 채널의 channelEmail은 null이어야 한다")
        void getChannelsStatusSocialChannelWithoutEmailHasNullChannelEmail() {
            // given
            Long userId = 1L;
            User user = createUser(userId);
            UserChannel emailChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.EMAIL)
                    .channelKey("test@example.com")
                    .channelEmailEnc("enc_email")
                    .channelEmailLowerEnc("enc_email_lower")
                    .build();
            // KAKAO without channelEmailEnc
            UserChannel kakaoChannel = UserChannel.builder()
                    .user(user)
                    .channelCode(ChannelCode.KAKAO)
                    .channelKey("kakao-123")
                    .build();
            List<UserChannel> channels = new ArrayList<>();
            channels.add(emailChannel);
            channels.add(kakaoChannel);
            setField(user, "channels", channels);

            given(userRepository.findByIdWithChannels(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");

            // when
            ChannelStatusResponse response = accountLinkingService.getChannelsStatus(userId);

            // then
            ChannelStatusResponse.ChannelStatus kakaoStatus = response.getChannels().stream()
                    .filter(ch -> "KAKAO".equals(ch.getChannelCode()))
                    .findFirst().orElse(null);
            assertThat(kakaoStatus).isNotNull();
            assertThat(kakaoStatus.isLinked()).isTrue();
            assertThat(kakaoStatus.getChannelEmail()).isNull();
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
