package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.repository.PasswordHistoryRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserTwoFactorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AccountCleanupSchedulerTest {

    @InjectMocks
    private AccountCleanupScheduler accountCleanupScheduler;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserTwoFactorRepository userTwoFactorRepository;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Nested
    @DisplayName("만료된 탈퇴 예정 계정 정리")
    class CleanupExpiredDeletions {

        @Test
        @DisplayName("탈퇴 유예 기간이 지난 사용자가 영구 삭제되어야 한다")
        void processExpiredDeletions() {
            // given
            User expiredUser = createExpiredUser(1L);
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(List.of(expiredUser));
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            verify(userTwoFactorRepository).deleteByUserId(1L);
            verify(passwordHistoryRepository).deleteByUserId(1L);
            verify(loginHistoryRepository).deleteByUserId(1L);
            assertThat(expiredUser.getStatus()).isEqualTo(UserStatus.DELETED);
            assertThat(expiredUser.getEmailEnc()).isNull();
            assertThat(expiredUser.getNicknameEnc()).isNull();
            assertThat(expiredUser.getPhoneEnc()).isNull();
        }

        @Test
        @DisplayName("만료된 사용자가 없으면 삭제가 수행되지 않아야 한다")
        void noExpiredDeletions() {
            // given
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            verify(userTwoFactorRepository, never()).deleteByUserId(any());
            verify(passwordHistoryRepository, never()).deleteByUserId(any());
        }

        @Test
        @DisplayName("SignInInfo가 있는 사용자의 개인정보가 제거되어야 한다")
        void processExpiredDeletionsWithSignInInfo() {
            // given
            User expiredUser = createExpiredUserWithSignInInfo(1L);
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(List.of(expiredUser));
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            assertThat(expiredUser.getSignInInfo()).isNull();
            assertThat(expiredUser.getChannels()).isEmpty();
        }

        @Test
        @DisplayName("여러 만료 사용자가 모두 처리되어야 한다")
        void processMultipleExpiredDeletions() {
            // given
            User user1 = createExpiredUser(1L);
            User user2 = createExpiredUser(2L);
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(List.of(user1, user2));
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            verify(userTwoFactorRepository).deleteByUserId(1L);
            verify(userTwoFactorRepository).deleteByUserId(2L);
            assertThat(user1.getStatus()).isEqualTo(UserStatus.DELETED);
            assertThat(user2.getStatus()).isEqualTo(UserStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("휴면 계정 전환")
    class ConvertDormantAccounts {

        @Test
        @DisplayName("90일 이상 미접속 사용자가 휴면 상태로 변환되어야 한다")
        void processDormantAccounts() {
            // given
            User dormantCandidate = createUser(1L);
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(List.of(dormantCandidate));

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            assertThat(dormantCandidate.getStatus()).isEqualTo(UserStatus.DORMANT);
        }

        @Test
        @DisplayName("여러 휴면 대상 사용자가 모두 처리되어야 한다")
        void processMultipleDormantCandidates() {
            // given
            User user1 = createUser(1L);
            User user2 = createUser(2L);
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(List.of(user1, user2));

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            assertThat(user1.getStatus()).isEqualTo(UserStatus.DORMANT);
            assertThat(user2.getStatus()).isEqualTo(UserStatus.DORMANT);
        }

        @Test
        @DisplayName("휴면 대상이 없으면 상태 변경이 수행되지 않아야 한다")
        void noDormantCandidates() {
            // given
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            verify(loginHistoryRepository).deleteOldHistory(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("오래된 로그인 이력 삭제")
    class CleanupOldLoginHistory {

        @Test
        @DisplayName("180일 이상 된 로그인 이력이 삭제되어야 한다")
        void cleanupOldHistory() {
            // given
            given(userRepository.findExpiredPendingDeletions(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());
            given(userRepository.findDormantCandidates(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            accountCleanupScheduler.executeCleanup();

            // then
            verify(loginHistoryRepository).deleteOldHistory(any(LocalDateTime.class));
        }
    }

    // Helper methods
    private User createUser(Long id) {
        User user = User.builder()
                .emailEnc("enc_email")
                .emailLowerEnc("enc_email_lower")
                .nicknameEnc("enc_nickname")
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", id);
        setField(user, "userUuid", "uuid-" + id);
        return user;
    }

    private User createExpiredUser(Long id) {
        User user = User.builder()
                .emailEnc("enc_email")
                .emailLowerEnc("enc_email_lower")
                .nicknameEnc("enc_nickname")
                .phoneEnc("enc_phone")
                .status(UserStatus.PENDING_DELETE)
                .build();
        setField(user, "id", id);
        setField(user, "userUuid", "uuid-" + id);
        return user;
    }

    private User createExpiredUserWithSignInInfo(Long id) {
        User user = createExpiredUser(id);
        UserChannel channel = UserChannel.builder()
                .user(user)
                .channelCode(ChannelCode.EMAIL)
                .channelKey(String.valueOf(id))
                .channelEmailEnc("enc_ch_email")
                .channelEmailLowerEnc("enc_ch_email_lower")
                .build();
        UserSignInInfo signInInfo = UserSignInInfo.builder()
                .user(user)
                .loginEmailEnc("enc_login_email")
                .loginEmailLowerEnc("enc_login_email_lower")
                .passwordHash("hashed_password")
                .build();
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
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
