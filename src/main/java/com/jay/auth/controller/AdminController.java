package com.jay.auth.controller;

import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.AdminDashboardResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AdminService;
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

    @Operation(summary = "관리자 대시보드 조회", description = "사용자 통계 및 최근 가입 사용자 목록을 조회합니다")
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        AdminDashboardResponse response = adminService.getDashboard();
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
}
