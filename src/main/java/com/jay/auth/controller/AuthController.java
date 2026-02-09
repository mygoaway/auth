package com.jay.auth.controller;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.request.*;
import com.jay.auth.dto.response.ChangePasswordResponse;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.PasswordAnalysisResponse;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.exception.RateLimitException;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AuthService;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.LoginRateLimitService;
import com.jay.auth.service.PasswordService;
import com.jay.auth.service.SecurityNotificationService;
import com.jay.auth.service.TokenService;
import com.jay.auth.util.PasswordUtil;
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
    private final PasswordService passwordService;
    private final LoginRateLimitService loginRateLimitService;
    private final LoginHistoryService loginHistoryService;
    private final SecurityNotificationService securityNotificationService;
    private final PasswordUtil passwordUtil;

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
            @Valid @RequestBody EmailLoginRequest request,
            HttpServletRequest httpRequest) {

        String email = request.getEmail();
        String ipAddress = getClientIp(httpRequest);

        // Rate limit check
        if (!loginRateLimitService.isLoginAllowed(email, ipAddress)) {
            long retryAfter = loginRateLimitService.getRetryAfterSeconds(email);
            throw new RateLimitException(retryAfter);
        }

        try {
            // Extract session info from request
            var sessionInfo = loginHistoryService.extractSessionInfo(httpRequest);

            // Login with session info
            LoginResponse response = authService.loginWithEmail(request, sessionInfo);

            // Clear failed attempts on success
            loginRateLimitService.clearFailedAttempts(email, ipAddress);

            // Record login history (async)
            loginHistoryService.recordLoginSuccess(response.getUserId(), ChannelCode.EMAIL, httpRequest);

            // Send new device login notification (async)
            securityNotificationService.notifyNewDeviceLogin(response.getUserId(), sessionInfo);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Record failed attempt
            loginRateLimitService.recordFailedAttempt(email, ipAddress);

            // Try to get user ID for history (if user exists)
            Long userId = authService.findUserIdByEmail(email);
            if (userId != null) {
                loginHistoryService.recordLoginFailure(userId, ChannelCode.EMAIL, e.getMessage(), httpRequest);
            }

            throw e;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
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

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인 후 새 비밀번호로 변경합니다. 변경 성공 시 모든 세션이 로그아웃됩니다.")
    @PostMapping("/password/change")
    public ResponseEntity<ChangePasswordResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ChangePasswordRequest request) {

        passwordService.changePassword(userPrincipal.getUserId(), request);

        return ResponseEntity.ok(ChangePasswordResponse.of(true));
    }

    @Operation(summary = "비밀번호 강도 분석", description = "비밀번호의 강도를 분석하고 상세 피드백을 제공합니다")
    @PostMapping("/password/analyze")
    public ResponseEntity<PasswordAnalysisResponse> analyzePassword(
            @RequestBody java.util.Map<String, String> request) {

        String password = request.get("password");
        PasswordAnalysisResponse response = passwordUtil.analyzePassword(password);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "비밀번호 재설정", description = "이메일 인증 후 비밀번호를 재설정합니다")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordService.resetPassword(request);

        return ResponseEntity.ok().build();
    }
}
