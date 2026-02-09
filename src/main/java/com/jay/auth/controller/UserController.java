package com.jay.auth.controller;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.request.UpdatePhoneRequest;
import com.jay.auth.dto.request.UpdateProfileRequest;
import com.jay.auth.dto.request.UpdateRecoveryEmailRequest;
import com.jay.auth.dto.response.ActiveSessionResponse;
import com.jay.auth.dto.response.ChannelStatusResponse;
import com.jay.auth.dto.response.LoginHistoryResponse;
import com.jay.auth.dto.response.SecurityDashboardResponse;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.dto.response.WeeklyActivityResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AccountLinkingService;
import com.jay.auth.service.ActivityReportService;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.SecurityDashboardService;
import com.jay.auth.service.TokenService;
import com.jay.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountLinkingService accountLinkingService;
    private final LoginHistoryService loginHistoryService;
    private final TokenService tokenService;
    private final SecurityDashboardService securityDashboardService;
    private final ActivityReportService activityReportService;

    @Operation(summary = "프로필 조회", description = "현재 사용자의 프로필을 조회합니다")
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        UserProfileResponse response = userService.getProfile(userPrincipal.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "닉네임 변경", description = "닉네임을 변경합니다")
    @PatchMapping("/profile/nickname")
    public ResponseEntity<Void> updateNickname(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateProfileRequest request) {

        userService.updateNickname(userPrincipal.getUserId(), request);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "핸드폰 번호 변경", description = "핸드폰 번호를 변경합니다")
    @PatchMapping("/profile/phone")
    public ResponseEntity<Void> updatePhone(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdatePhoneRequest request) {

        userService.updatePhone(userPrincipal.getUserId(), request);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "복구 이메일 변경", description = "복구 이메일을 변경합니다")
    @PatchMapping("/profile/recovery-email")
    public ResponseEntity<Void> updateRecoveryEmail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateRecoveryEmailRequest request) {

        userService.updateRecoveryEmail(userPrincipal.getUserId(), request);

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴를 처리합니다 (30일 유예 기간)")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        userService.deleteAccount(userPrincipal.getUserId());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "탈퇴 유예 취소", description = "탈퇴 유예 상태를 취소하고 계정을 복구합니다")
    @PostMapping("/me/cancel-deletion")
    public ResponseEntity<Void> cancelDeletion(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        userService.cancelDeletion(userPrincipal.getUserId());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "연결된 채널 조회", description = "사용자의 연결된 소셜 채널 목록을 조회합니다")
    @GetMapping("/channels")
    public ResponseEntity<ChannelStatusResponse> getChannelsStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        ChannelStatusResponse response = accountLinkingService.getChannelsStatus(userPrincipal.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "채널 연결 해제", description = "소셜 계정 연결을 해제합니다")
    @DeleteMapping("/channels/{channelCode}")
    public ResponseEntity<Void> unlinkChannel(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable ChannelCode channelCode) {

        accountLinkingService.unlinkChannel(userPrincipal.getUserId(), channelCode);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "로그인 기록 조회", description = "최근 로그인 기록을 조회합니다")
    @GetMapping("/login-history")
    public ResponseEntity<List<LoginHistoryResponse>> getLoginHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "10") int limit) {

        List<LoginHistoryResponse> histories = loginHistoryService.getRecentLoginHistory(
                userPrincipal.getUserId(), Math.min(limit, 50));

        return ResponseEntity.ok(histories);
    }

    @Operation(summary = "활성 세션 조회", description = "현재 로그인 중인 세션 목록을 조회합니다")
    @GetMapping("/sessions")
    public ResponseEntity<List<ActiveSessionResponse>> getActiveSessions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        String currentTokenId = extractTokenId(httpRequest);
        List<ActiveSessionResponse> sessions = tokenService.getActiveSessions(
                userPrincipal.getUserId(), currentTokenId);

        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "세션 종료", description = "특정 세션을 원격으로 종료합니다")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String sessionId) {

        tokenService.revokeSession(userPrincipal.getUserId(), sessionId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "보안 대시보드 조회", description = "계정 보안 상태를 조회합니다")
    @GetMapping("/security/dashboard")
    public ResponseEntity<SecurityDashboardResponse> getSecurityDashboard(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        SecurityDashboardResponse response = securityDashboardService.getSecurityDashboard(
                userPrincipal.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "주간 활동 리포트", description = "이번 주 로그인 활동 및 보안 요약을 조회합니다")
    @GetMapping("/activity/weekly")
    public ResponseEntity<WeeklyActivityResponse> getWeeklyActivity(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        WeeklyActivityResponse response = activityReportService.getWeeklyReport(
                userPrincipal.getUserId());

        return ResponseEntity.ok(response);
    }

    private String extractTokenId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            return tokenService.getTokenId(accessToken);
        }
        return null;
    }
}
