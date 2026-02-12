package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.AdminDashboardResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditLogService auditLogService;

    @Nested
    @DisplayName("대시보드 조회")
    class GetDashboard {

        @Test
        @DisplayName("대시보드 통계가 정확하게 반환되어야 한다")
        void getDashboardSuccess() {
            // given
            given(userRepository.count()).willReturn(100L);
            given(userRepository.countByStatus(UserStatus.ACTIVE)).willReturn(80L);
            given(userRepository.countByStatus(UserStatus.DORMANT)).willReturn(15L);
            given(userRepository.countByStatus(UserStatus.PENDING_DELETE)).willReturn(5L);
            given(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).willReturn(3L);

            User user = createUserWithChannels();
            given(userRepository.findRecentUsersWithChannels(any(PageRequest.class)))
                    .willReturn(List.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@email.com");
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트유저");

            // when
            AdminDashboardResponse response = adminService.getDashboard();

            // then
            assertThat(response.getUserStats().getTotalUsers()).isEqualTo(100L);
            assertThat(response.getUserStats().getActiveUsers()).isEqualTo(80L);
            assertThat(response.getUserStats().getDormantUsers()).isEqualTo(15L);
            assertThat(response.getUserStats().getPendingDeleteUsers()).isEqualTo(5L);
            assertThat(response.getUserStats().getTodaySignups()).isEqualTo(3L);
            assertThat(response.getRecentUsers()).hasSize(1);
            assertThat(response.getRecentUsers().get(0).getEmail()).isEqualTo("test@email.com");
            assertThat(response.getRecentUsers().get(0).getNickname()).isEqualTo("테스트유저");
        }

        @Test
        @DisplayName("최근 사용자가 없으면 빈 목록이 반환되어야 한다")
        void getDashboardWithNoRecentUsers() {
            // given
            given(userRepository.count()).willReturn(0L);
            given(userRepository.countByStatus(UserStatus.ACTIVE)).willReturn(0L);
            given(userRepository.countByStatus(UserStatus.DORMANT)).willReturn(0L);
            given(userRepository.countByStatus(UserStatus.PENDING_DELETE)).willReturn(0L);
            given(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).willReturn(0L);
            given(userRepository.findRecentUsersWithChannels(any(PageRequest.class)))
                    .willReturn(Collections.emptyList());

            // when
            AdminDashboardResponse response = adminService.getDashboard();

            // then
            assertThat(response.getRecentUsers()).isEmpty();
            assertThat(response.getUserStats().getTotalUsers()).isEqualTo(0L);
        }

        @Test
        @DisplayName("사용자에게 채널과 로그인 정보가 포함되어야 한다")
        void getDashboardWithUserDetails() {
            // given
            given(userRepository.count()).willReturn(1L);
            given(userRepository.countByStatus(UserStatus.ACTIVE)).willReturn(1L);
            given(userRepository.countByStatus(UserStatus.DORMANT)).willReturn(0L);
            given(userRepository.countByStatus(UserStatus.PENDING_DELETE)).willReturn(0L);
            given(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).willReturn(0L);

            User user = createUserWithSignInInfo();
            given(userRepository.findRecentUsersWithChannels(any(PageRequest.class)))
                    .willReturn(List.of(user));
            given(encryptionService.decryptEmail("enc_email")).willReturn("test@email.com");
            given(encryptionService.decryptNickname("enc_nickname")).willReturn("테스트유저");

            // when
            AdminDashboardResponse response = adminService.getDashboard();

            // then
            AdminDashboardResponse.AdminUserInfo userInfo = response.getRecentUsers().get(0);
            assertThat(userInfo.getUserId()).isEqualTo(1L);
            assertThat(userInfo.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(userInfo.getStatus()).isEqualTo("ACTIVE");
            assertThat(userInfo.getRole()).isEqualTo("USER");
            assertThat(userInfo.getChannels()).containsExactly("EMAIL");
            assertThat(userInfo.getLastLoginAt()).isNotNull();
        }

        @Test
        @DisplayName("이메일과 닉네임이 null인 사용자도 처리되어야 한다")
        void getDashboardWithNullEmailAndNickname() {
            // given
            given(userRepository.count()).willReturn(1L);
            given(userRepository.countByStatus(UserStatus.ACTIVE)).willReturn(1L);
            given(userRepository.countByStatus(UserStatus.DORMANT)).willReturn(0L);
            given(userRepository.countByStatus(UserStatus.PENDING_DELETE)).willReturn(0L);
            given(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).willReturn(0L);

            User user = createUserWithNullFields();
            given(userRepository.findRecentUsersWithChannels(any(PageRequest.class)))
                    .willReturn(List.of(user));

            // when
            AdminDashboardResponse response = adminService.getDashboard();

            // then
            AdminDashboardResponse.AdminUserInfo userInfo = response.getRecentUsers().get(0);
            assertThat(userInfo.getEmail()).isNull();
            assertThat(userInfo.getNickname()).isNull();
        }
    }

    @Nested
    @DisplayName("사용자 역할 변경")
    class UpdateUserRole {

        @Test
        @DisplayName("사용자 역할이 성공적으로 변경되어야 한다")
        void updateUserRoleSuccess() {
            // given
            User target = createUser(2L);
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            // when
            adminService.updateUserRole(1L, 2L, UserRole.ADMIN);

            // then
            assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
            verify(auditLogService).log(eq(1L), eq("USER_ROLE_CHANGE"), eq("ADMIN"),
                    eq("targetUserId=2, newRole=ADMIN"), eq(true));
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 역할 변경 시 실패해야 한다")
        void updateUserRoleFailsWithUserNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminService.updateUserRole(1L, 999L, UserRole.ADMIN))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("사용자 상태 변경")
    class UpdateUserStatus {

        @Test
        @DisplayName("사용자 상태가 성공적으로 변경되어야 한다")
        void updateUserStatusSuccess() {
            // given
            User target = createUser(2L);
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            // when
            adminService.updateUserStatus(1L, 2L, UserStatus.LOCKED);

            // then
            assertThat(target.getStatus()).isEqualTo(UserStatus.LOCKED);
            verify(auditLogService).log(eq(1L), eq("USER_STATUS_CHANGE"), eq("ADMIN"),
                    eq("targetUserId=2, newStatus=LOCKED"), eq(true));
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 상태 변경 시 실패해야 한다")
        void updateUserStatusFailsWithUserNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminService.updateUserStatus(1L, 999L, UserStatus.LOCKED))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("사용자를 휴면 상태로 변경할 수 있어야 한다")
        void updateUserStatusToDormant() {
            // given
            User target = createUser(2L);
            given(userRepository.findById(2L)).willReturn(Optional.of(target));

            // when
            adminService.updateUserStatus(1L, 2L, UserStatus.DORMANT);

            // then
            assertThat(target.getStatus()).isEqualTo(UserStatus.DORMANT);
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
        setField(user, "userUuid", "uuid-1234");
        setField(user, "role", UserRole.USER);
        return user;
    }

    private User createUserWithChannels() {
        User user = createUser(1L);
        UserChannel channel = UserChannel.builder()
                .user(user)
                .channelCode(ChannelCode.EMAIL)
                .channelKey("1")
                .channelEmailEnc("enc_ch_email")
                .channelEmailLowerEnc("enc_ch_email_lower")
                .build();
        return user;
    }

    private User createUserWithSignInInfo() {
        User user = createUserWithChannels();
        UserSignInInfo signInInfo = UserSignInInfo.builder()
                .user(user)
                .loginEmailEnc("enc_login_email")
                .loginEmailLowerEnc("enc_login_email_lower")
                .passwordHash("hashed_password")
                .build();
        setField(signInInfo, "lastLoginAt", LocalDateTime.of(2025, 1, 15, 10, 30, 0));
        return user;
    }

    private User createUserWithNullFields() {
        User user = User.builder()
                .status(UserStatus.ACTIVE)
                .build();
        setField(user, "id", 1L);
        setField(user, "userUuid", "uuid-5678");
        setField(user, "role", UserRole.USER);
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
