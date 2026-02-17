package com.jay.auth.controller;

import com.jay.auth.dto.request.PasskeyAuthenticateRequest;
import com.jay.auth.dto.request.PasskeyRegisterRequest;
import com.jay.auth.dto.request.PasskeyRenameRequest;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.PasskeyAuthenticationOptionsResponse;
import com.jay.auth.dto.response.PasskeyListResponse;
import com.jay.auth.dto.response.PasskeyRegistrationOptionsResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.PasskeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Passkey", description = "패스키(WebAuthn) 인증 API")
@RestController
@RequiredArgsConstructor
public class PasskeyController {

    private final PasskeyService passkeyService;

    @Operation(summary = "패스키 등록 옵션 생성", description = "패스키 등록을 위한 챌린지와 옵션을 생성합니다")
    @PostMapping("/api/v1/passkey/register/options")
    public ResponseEntity<PasskeyRegistrationOptionsResponse> getRegistrationOptions(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        PasskeyRegistrationOptionsResponse options = passkeyService.generateRegistrationOptions(
                userPrincipal.getUserId());
        return ResponseEntity.ok(options);
    }

    @Operation(summary = "패스키 등록 검증", description = "패스키 등록을 검증하고 저장합니다")
    @PostMapping("/api/v1/passkey/register/verify")
    public ResponseEntity<Map<String, String>> verifyRegistration(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PasskeyRegisterRequest request) {

        passkeyService.verifyRegistration(userPrincipal.getUserId(), request);
        return ResponseEntity.ok(Map.of("message", "패스키가 등록되었습니다"));
    }

    @Operation(summary = "패스키 로그인 옵션 생성", description = "패스키 로그인을 위한 챌린지와 옵션을 생성합니다")
    @PostMapping("/api/v1/auth/passkey/login/options")
    public ResponseEntity<PasskeyAuthenticationOptionsResponse> getAuthenticationOptions() {

        PasskeyAuthenticationOptionsResponse options = passkeyService.generateAuthenticationOptions();
        return ResponseEntity.ok(options);
    }

    @Operation(summary = "패스키 로그인 검증", description = "패스키 인증을 검증하고 JWT를 발급합니다")
    @PostMapping("/api/v1/auth/passkey/login/verify")
    public ResponseEntity<LoginResponse> verifyAuthentication(
            @Valid @RequestBody PasskeyAuthenticateRequest request) {

        LoginResponse response = passkeyService.verifyAuthentication(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "패스키 목록 조회", description = "등록된 패스키 목록을 조회합니다")
    @GetMapping("/api/v1/passkey/list")
    public ResponseEntity<PasskeyListResponse> listPasskeys(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        PasskeyListResponse response = passkeyService.listPasskeys(userPrincipal.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "패스키 이름 변경", description = "패스키의 이름을 변경합니다")
    @PatchMapping("/api/v1/passkey/{id}")
    public ResponseEntity<Map<String, String>> renamePasskey(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody PasskeyRenameRequest request) {

        passkeyService.renamePasskey(userPrincipal.getUserId(), id, request.getDeviceName());
        return ResponseEntity.ok(Map.of("message", "패스키 이름이 변경되었습니다"));
    }

    @Operation(summary = "패스키 삭제", description = "등록된 패스키를 삭제합니다")
    @DeleteMapping("/api/v1/passkey/{id}")
    public ResponseEntity<Void> deletePasskey(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {

        passkeyService.deletePasskey(userPrincipal.getUserId(), id);
        return ResponseEntity.ok().build();
    }
}
