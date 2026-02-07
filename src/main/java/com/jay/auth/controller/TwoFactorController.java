package com.jay.auth.controller;

import com.jay.auth.dto.request.TwoFactorVerifyRequest;
import com.jay.auth.dto.response.TwoFactorSetupResponse;
import com.jay.auth.dto.response.TwoFactorStatusResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.TotpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Two-Factor Auth", description = "2단계 인증 API")
@RestController
@RequestMapping("/api/v1/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TotpService totpService;

    @Operation(summary = "2FA 상태 조회", description = "현재 2단계 인증 상태를 조회합니다")
    @GetMapping("/status")
    public ResponseEntity<TwoFactorStatusResponse> getStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        TwoFactorStatusResponse status = totpService.getTwoFactorStatus(userPrincipal.getUserId());
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "2FA 설정 시작", description = "2단계 인증 설정을 시작하고 QR 코드를 반환합니다")
    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        TwoFactorSetupResponse response = totpService.setupTwoFactor(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "2FA 활성화", description = "코드 확인 후 2단계 인증을 활성화합니다")
    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enable(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        List<String> backupCodes = totpService.enableTwoFactor(userPrincipal.getUserId(), request.getCode());
        return ResponseEntity.ok(Map.of(
                "message", "2단계 인증이 활성화되었습니다",
                "backupCodes", backupCodes
        ));
    }

    @Operation(summary = "2FA 비활성화", description = "코드 확인 후 2단계 인증을 비활성화합니다")
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        totpService.disableTwoFactor(userPrincipal.getUserId(), request.getCode());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "2FA 코드 검증", description = "2단계 인증 코드를 검증합니다")
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Boolean>> verify(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        boolean valid = totpService.verifyCode(userPrincipal.getUserId(), request.getCode());
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    @Operation(summary = "백업 코드 재생성", description = "새로운 백업 코드를 생성합니다")
    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateBackupCodes(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        List<String> backupCodes = totpService.regenerateBackupCodes(userPrincipal.getUserId(), request.getCode());
        return ResponseEntity.ok(Map.of(
                "message", "백업 코드가 재생성되었습니다",
                "backupCodes", backupCodes
        ));
    }
}
