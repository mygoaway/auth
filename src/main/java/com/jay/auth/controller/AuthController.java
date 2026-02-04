package com.jay.auth.controller;

import com.jay.auth.dto.request.EmailLoginRequest;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.request.LogoutRequest;
import com.jay.auth.dto.request.RefreshTokenRequest;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AuthService;
import com.jay.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @Operation(summary = "이메일 회원가입", description = "이메일 인증 완료 후 회원가입을 진행합니다")
    @PostMapping("/email/signup")
    public ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody EmailSignUpRequest request) {

        SignUpResponse response = authService.signUpWithEmail(request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "이메일 로그인", description = "이메일과 비밀번호로 로그인합니다")
    @PostMapping("/email/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody EmailLoginRequest request) {

        LoginResponse response = authService.loginWithEmail(request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰을 발급합니다")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        TokenResponse response = tokenService.refreshTokens(request.getRefreshToken());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 로그아웃합니다")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest) {

        String accessToken = request.getAccessToken();
        if (accessToken == null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                accessToken = authHeader.substring(7);
            }
        }

        tokenService.logout(accessToken, request.getRefreshToken());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "전체 로그아웃", description = "모든 기기에서 로그아웃합니다")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest httpRequest) {

        String accessToken = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        tokenService.logoutAll(userPrincipal.getUserId(), accessToken);

        return ResponseEntity.ok().build();
    }
}
