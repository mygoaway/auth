package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.AdminDashboardResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long dormantUsers = userRepository.countByStatus(UserStatus.DORMANT);
        long pendingDeleteUsers = userRepository.countByStatus(UserStatus.PENDING_DELETE);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todaySignups = userRepository.countByCreatedAtAfter(todayStart);

        AdminDashboardResponse.UserStats stats = AdminDashboardResponse.UserStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .dormantUsers(dormantUsers)
                .pendingDeleteUsers(pendingDeleteUsers)
                .todaySignups(todaySignups)
                .build();

        List<User> recentUsers = userRepository.findRecentUsersWithChannels(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminDashboardResponse.AdminUserInfo> userInfos = recentUsers.stream()
                .map(this::toAdminUserInfo)
                .toList();

        return AdminDashboardResponse.builder()
                .userStats(stats)
                .recentUsers(userInfos)
                .build();
    }

    @Transactional
    public void updateUserRole(Long adminUserId, Long targetUserId, UserRole role) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(UserNotFoundException::new);

        target.updateRole(role);
        auditLogService.log(adminUserId, "USER_ROLE_CHANGE", "ADMIN",
                "targetUserId=" + targetUserId + ", newRole=" + role.name(), true);

        log.info("Admin {} changed user {} role to {}", adminUserId, targetUserId, role);
    }

    @Transactional
    public void updateUserStatus(Long adminUserId, Long targetUserId, UserStatus status) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(UserNotFoundException::new);

        target.updateStatus(status);
        auditLogService.log(adminUserId, "USER_STATUS_CHANGE", "ADMIN",
                "targetUserId=" + targetUserId + ", newStatus=" + status.name(), true);

        log.info("Admin {} changed user {} status to {}", adminUserId, targetUserId, status);
    }

    private AdminDashboardResponse.AdminUserInfo toAdminUserInfo(User user) {
        String email = user.getEmailEnc() != null
                ? encryptionService.decryptEmail(user.getEmailEnc()) : null;
        String nickname = user.getNicknameEnc() != null
                ? encryptionService.decryptNickname(user.getNicknameEnc()) : null;

        List<String> channels = user.getChannels().stream()
                .map(ch -> ch.getChannelCode().name())
                .toList();

        String lastLoginAt = null;
        if (user.getSignInInfo() != null && user.getSignInInfo().getLastLoginAt() != null) {
            lastLoginAt = user.getSignInInfo().getLastLoginAt().format(DATE_FORMAT);
        }

        return AdminDashboardResponse.AdminUserInfo.builder()
                .userId(user.getId())
                .userUuid(user.getUserUuid())
                .email(email)
                .nickname(nickname)
                .status(user.getStatus().name())
                .role(user.getRole().name())
                .channels(channels)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_FORMAT) : null)
                .lastLoginAt(lastLoginAt)
                .build();
    }
}
