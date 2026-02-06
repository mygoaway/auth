package com.jay.auth.controller;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.request.RegisterPasswordRequest;
import com.jay.auth.dto.request.UpdatePhoneRequest;
import com.jay.auth.dto.request.UpdateProfileRequest;
import com.jay.auth.dto.request.UpdateRecoveryEmailRequest;
import com.jay.auth.dto.response.ChannelStatusResponse;
import com.jay.auth.dto.response.LoginHistoryResponse;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AccountLinkingService;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.UserService;

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

    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴를 처리합니다")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        userService.deleteAccount(userPrincipal.getUserId());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "연결된 채널 조회", description = "사용자의 연결된 소셜 채널 목록을 조회합니다")
    @GetMapping("/channels")
    public ResponseEntity<ChannelStatusResponse> getChannelsStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        ChannelStatusResponse response = accountLinkingService.getChannelsStatus(userPrincipal.getUserId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "이메일 비밀번호 등록", description = "소셜 로그인 사용자가 이메일 비밀번호를 등록합니다")
    @PostMapping("/register-password")
    public ResponseEntity<Void> registerEmailPassword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody RegisterPasswordRequest request) {

        accountLinkingService.registerEmailPassword(userPrincipal.getUserId(), request);

        return ResponseEntity.ok().build();
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
}
