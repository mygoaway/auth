package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.security.TokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityNotificationServiceTest {

    @InjectMocks
    private SecurityNotificationService securityNotificationService;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("새 기기 로그인 알림")
    class NotifyNewDeviceLogin {

        @Test
        @DisplayName("새 기기 로그인 시 이메일 알림을 발송해야 한다")
        void sendNewDeviceLoginNotification() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");

            // when
            securityNotificationService.notifyNewDeviceLogin(userId, sessionInfo);

            // then
            verify(emailSender).sendNewDeviceLoginAlert(
                    eq("test@example.com"), anyString(), eq("192.168.1.1"), eq("Seoul"), anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 알림을 발송하지 않아야 한다")
        void doNotSendWhenUserNotFound() {
            // given
            Long userId = 999L;
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            securityNotificationService.notifyNewDeviceLogin(userId, sessionInfo);

            // then
            verify(emailSender, never()).sendNewDeviceLoginAlert(
                    anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("이메일이 없는 사용자는 알림을 발송하지 않아야 한다")
        void doNotSendWhenNoEmail() {
            // given
            Long userId = 1L;
            User user = createUser(userId, null);
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows 10", "192.168.1.1", "Seoul");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            // when
            securityNotificationService.notifyNewDeviceLogin(userId, sessionInfo);

            // then
            verify(emailSender, never()).sendNewDeviceLoginAlert(
                    anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 알림")
    class NotifyPasswordChanged {

        @Test
        @DisplayName("비밀번호 변경 시 이메일 알림을 발송해야 한다")
        void sendPasswordChangedNotification() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");

            // when
            securityNotificationService.notifyPasswordChanged(userId);

            // then
            verify(emailSender).sendPasswordChangedAlert(eq("test@example.com"), anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 알림을 발송하지 않아야 한다")
        void doNotSendWhenUserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            securityNotificationService.notifyPasswordChanged(userId);

            // then
            verify(emailSender, never()).sendPasswordChangedAlert(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("계정 연동 알림")
    class NotifyAccountLinked {

        @Test
        @DisplayName("계정 연동 시 이메일 알림을 발송해야 한다")
        void sendAccountLinkedNotification() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");

            // when
            securityNotificationService.notifyAccountLinked(userId, ChannelCode.GOOGLE);

            // then
            verify(emailSender).sendAccountLinkedAlert(eq("test@example.com"), eq("Google"), anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 알림을 발송하지 않아야 한다")
        void doNotSendWhenUserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            securityNotificationService.notifyAccountLinked(userId, ChannelCode.GOOGLE);

            // then
            verify(emailSender, never()).sendAccountLinkedAlert(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("계정 연동 해제 알림")
    class NotifyAccountUnlinked {

        @Test
        @DisplayName("계정 연동 해제 시 이메일 알림을 발송해야 한다")
        void sendAccountUnlinkedNotification() {
            // given
            Long userId = 1L;
            User user = createUser(userId, "enc_email");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@example.com");

            // when
            securityNotificationService.notifyAccountUnlinked(userId, ChannelCode.KAKAO);

            // then
            verify(emailSender).sendAccountUnlinkedAlert(eq("test@example.com"), eq("카카오"), anyString());
        }

        @Test
        @DisplayName("사용자가 없으면 알림을 발송하지 않아야 한다")
        void doNotSendWhenUserNotFound() {
            // given
            Long userId = 999L;
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // when
            securityNotificationService.notifyAccountUnlinked(userId, ChannelCode.NAVER);

            // then
            verify(emailSender, never()).sendAccountUnlinkedAlert(anyString(), anyString(), anyString());
        }
    }

    // Helper methods
    private User createUser(Long userId, String emailEnc) {
        User user = User.builder()
                .emailEnc(emailEnc)
                .build();
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
