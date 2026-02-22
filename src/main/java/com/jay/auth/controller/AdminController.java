package com.jay.auth.controller;

import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.*;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AdminService;
import com.jay.auth.service.AuditLogService;
import com.jay.auth.service.LoginAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;
    private final LoginAnalyticsService loginAnalyticsService;

    @Operation(summary = "관리자 대시보드 조회", description = "사용자 통계 및 최근 가입 사용자 목록을 조회합니다")
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal UserPrincipal adminPrincipal) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_DASHBOARD_VIEW", "ADMIN");
        AdminDashboardResponse response = adminService.getDashboard();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자 검색", description = "키워드와 상태로 사용자를 검색합니다")
    @GetMapping("/users")
    public ResponseEntity<AdminUserSearchResponse> searchUsers(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_USER_SEARCH", "ADMIN",
                "keyword=" + (keyword != null ? "set" : "none") + ", status=" + status, true);
        AdminUserSearchResponse response = adminService.searchUsers(keyword, status, page, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "로그인 통계 조회", description = "로그인 및 가입 통계를 조회합니다")
    @GetMapping("/stats/logins")
    public ResponseEntity<AdminLoginStatsResponse> getLoginStats(
            @AuthenticationPrincipal UserPrincipal adminPrincipal) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_LOGIN_STATS_VIEW", "ADMIN");
        AdminLoginStatsResponse response = adminService.getLoginStats();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "보안 이벤트 조회", description = "최근 보안 이벤트를 조회합니다")
    @GetMapping("/security/events")
    public ResponseEntity<AdminSecurityEventsResponse> getSecurityEvents(
            @AuthenticationPrincipal UserPrincipal adminPrincipal) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_SECURITY_EVENTS_VIEW", "ADMIN");
        AdminSecurityEventsResponse response = adminService.getSecurityEvents();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "고객센터 통계 조회", description = "고객센터 게시판 통계를 조회합니다")
    @GetMapping("/stats/support")
    public ResponseEntity<AdminSupportStatsResponse> getSupportStats(
            @AuthenticationPrincipal UserPrincipal adminPrincipal) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_SUPPORT_STATS_VIEW", "ADMIN");
        AdminSupportStatsResponse response = adminService.getSupportStats();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사용자 역할 변경", description = "특정 사용자의 역할을 변경합니다")
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<Void> updateUserRole(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @PathVariable Long userId,
            @RequestParam UserRole role) {
        adminService.updateUserRole(adminPrincipal.getUserId(), userId, role);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "사용자 상태 변경", description = "특정 사용자의 상태를 변경합니다")
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @PathVariable Long userId,
            @RequestParam UserStatus status) {
        adminService.updateUserStatus(adminPrincipal.getUserId(), userId, status);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그인 히트맵 조회", description = "국가/도시별 로그인 집계 및 의심 지역을 조회합니다")
    @GetMapping("/analytics/login-heatmap")
    public ResponseEntity<LoginHeatmapResponse> getLoginHeatmap(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @RequestParam(defaultValue = "30") int days) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_LOGIN_HEATMAP_VIEW", "ADMIN");
        LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(days);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "실패 핫스팟 조회", description = "로그인 실패 시도가 집중된 IP 목록을 조회합니다")
    @GetMapping("/analytics/failure-hotspot")
    public ResponseEntity<LoginFailureHotspotResponse> getFailureHotspot(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @RequestParam(defaultValue = "7") int days) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_FAILURE_HOTSPOT_VIEW", "ADMIN");
        LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(days);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "시간대별 로그인 타임라인", description = "시간대별 로그인 성공/실패 패턴을 조회합니다")
    @GetMapping("/analytics/timeline")
    public ResponseEntity<LoginTimelineResponse> getLoginTimeline(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @RequestParam(defaultValue = "30") int days) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_LOGIN_TIMELINE_VIEW", "ADMIN");
        LoginTimelineResponse response = loginAnalyticsService.getTimeline(days);
        return ResponseEntity.ok(response);
    }
}
