package com.jay.auth.service;

import com.jay.auth.domain.entity.AuditLog;
import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.PostStatus;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.*;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.AuditLogRepository;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.repository.SupportPostRepository;
import com.jay.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jay.auth.util.DateTimeUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final SupportPostRepository supportPostRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeUtil.DEFAULT_FORMATTER;

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

    @Transactional(readOnly = true)
    public AdminUserSearchResponse searchUsers(String keyword, UserStatus status, int page, int size) {
        String encryptedKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            EncryptionService.EncryptedEmail enc = encryptionService.encryptEmail(keyword.toLowerCase());
            encryptedKeyword = enc.encryptedLower();
        }

        Page<User> userPage = userRepository.searchUsers(
                encryptedKeyword, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminDashboardResponse.AdminUserInfo> users = userPage.getContent().stream()
                .map(this::toAdminUserInfo)
                .toList();

        return AdminUserSearchResponse.builder()
                .users(users)
                .currentPage(userPage.getNumber())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminLoginStatsResponse getLoginStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDaysAgo = todayStart.minusDays(7);

        long todayLogins = loginHistoryRepository.countByCreatedAtAfter(todayStart);
        long todaySignups = userRepository.countByCreatedAtAfter(todayStart);
        long activeUsersLast7Days = loginHistoryRepository.countDistinctActiveUsersSince(sevenDaysAgo);

        List<Object[]> dailyLoginData = loginHistoryRepository.countDailyLogins(sevenDaysAgo);
        List<Map<String, Object>> dailyLogins = dailyLoginData.stream()
                .map(row -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("date", row[0].toString());
                    map.put("count", row[1]);
                    return map;
                })
                .toList();

        List<Object[]> dailySignupData = userRepository.countDailySignups(sevenDaysAgo);
        List<Map<String, Object>> dailySignups = dailySignupData.stream()
                .map(row -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("date", row[0].toString());
                    map.put("count", row[1]);
                    return map;
                })
                .toList();

        return AdminLoginStatsResponse.builder()
                .todayLogins(todayLogins)
                .todaySignups(todaySignups)
                .activeUsersLast7Days(activeUsersLast7Days)
                .dailyLogins(dailyLogins)
                .dailySignups(dailySignups)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminSecurityEventsResponse getSecurityEvents() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);

        long failedLoginsToday = auditLogRepository.countByActionSince("LOGIN_FAILED", todayStart);
        long passwordChangesToday = auditLogRepository.countByActionSince("PASSWORD_CHANGE", todayStart);
        long accountLocksToday = auditLogRepository.countByActionSince("ACCOUNT_LOCKED", todayStart);

        List<LoginHistory> failedLogins = loginHistoryRepository.findRecentFailedLogins(
                last24Hours, PageRequest.of(0, 20));
        List<AdminSecurityEventsResponse.FailedLoginInfo> failedLoginInfos = failedLogins.stream()
                .map(h -> AdminSecurityEventsResponse.FailedLoginInfo.builder()
                        .userId(h.getUserId())
                        .ipAddress(h.getIpAddress())
                        .browser(h.getBrowser())
                        .os(h.getOs())
                        .location(h.getLocation())
                        .failureReason(h.getFailureReason())
                        .createdAt(h.getCreatedAt() != null ? h.getCreatedAt().format(DATE_FORMAT) : null)
                        .build())
                .toList();

        List<AuditLog> auditLogs = auditLogRepository.findRecentLogs(
                last24Hours, PageRequest.of(0, 30));
        List<AdminSecurityEventsResponse.AuditEventInfo> auditEventInfos = auditLogs.stream()
                .map(a -> AdminSecurityEventsResponse.AuditEventInfo.builder()
                        .userId(a.getUserId())
                        .action(a.getAction())
                        .target(a.getTarget())
                        .detail(a.getDetail())
                        .ipAddress(a.getIpAddress())
                        .success(a.getIsSuccess())
                        .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().format(DATE_FORMAT) : null)
                        .build())
                .toList();

        return AdminSecurityEventsResponse.builder()
                .failedLoginsToday(failedLoginsToday)
                .passwordChangesToday(passwordChangesToday)
                .accountLocksToday(accountLocksToday)
                .recentFailedLogins(failedLoginInfos)
                .recentAuditEvents(auditEventInfos)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminSupportStatsResponse getSupportStats() {
        long totalPosts = supportPostRepository.count();
        long openPosts = supportPostRepository.countByStatus(PostStatus.OPEN);
        long inProgressPosts = supportPostRepository.countByStatus(PostStatus.IN_PROGRESS);
        long resolvedPosts = supportPostRepository.countByStatus(PostStatus.RESOLVED);
        long closedPosts = supportPostRepository.countByStatus(PostStatus.CLOSED);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayPosts = supportPostRepository.countByCreatedAtAfter(todayStart);

        return AdminSupportStatsResponse.builder()
                .totalPosts(totalPosts)
                .openPosts(openPosts)
                .inProgressPosts(inProgressPosts)
                .resolvedPosts(resolvedPosts)
                .closedPosts(closedPosts)
                .todayPosts(todayPosts)
                .build();
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
