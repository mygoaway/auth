package com.jay.auth.controller;

import com.jay.auth.dto.request.UpdatePhoneRequest;
import com.jay.auth.dto.request.UpdateProfileRequest;
import com.jay.auth.dto.request.UpdateRecoveryEmailRequest;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.UserService;
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
}
