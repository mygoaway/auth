package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.dto.request.EmailLoginRequest;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.request.RefreshTokenRequest;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.PasswordAnalysisResponse;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.exception.AuthenticationException;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.exception.GlobalExceptionHandler;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.TokenStore;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AuthService;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.LoginRateLimitService;
import com.jay.auth.service.PasswordService;
import com.jay.auth.service.SecurityNotificationService;
import com.jay.auth.service.TokenService;
import com.jay.auth.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        com.jay.auth.config.RateLimitFilter.class,
                        com.jay.auth.config.RequestLoggingFilter.class,
                        com.jay.auth.config.SecurityHeadersFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private PasswordService passwordService;

    @MockitoBean
    private LoginRateLimitService loginRateLimitService;

    @MockitoBean
    private LoginHistoryService loginHistoryService;

    @MockitoBean
    private SecurityNotificationService securityNotificationService;

    @MockitoBean
    private PasswordUtil passwordUtil;

    @MockitoBean
    private com.jay.auth.service.SecuritySettingsService securitySettingsService;

    @BeforeEach
    void setUp() {
        // Mock session info extraction
        TokenStore.SessionInfo mockSessionInfo = new TokenStore.SessionInfo(
                "Desktop", "Chrome", "macOS", "127.0.0.1", null);
        given(loginHistoryService.extractSessionInfo(any())).willReturn(mockSessionInfo);
    }

    @Test
    @DisplayName("POST /api/v1/auth/email/signup - 회원가입 성공")
    void signUpSuccess() throws Exception {
        // given
        TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
        SignUpResponse signUpResponse = SignUpResponse.of("uuid-1234", "test@email.com", "테스트", tokenResponse);

        given(authService.signUpWithEmail(any(EmailSignUpRequest.class))).willReturn(signUpResponse);

        String requestBody = """
                {
                    "tokenId": "token-123",
                    "email": "test@email.com",
                    "password": "Test@1234",
                    "nickname": "테스트"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userUuid").value("uuid-1234"))
                .andExpect(jsonPath("$.email").value("test@email.com"))
                .andExpect(jsonPath("$.token.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/email/signup - 이메일 중복 시 409")
    void signUpDuplicateEmail() throws Exception {
        // given
        given(authService.signUpWithEmail(any(EmailSignUpRequest.class)))
                .willThrow(new DuplicateEmailException());

        String requestBody = """
                {
                    "tokenId": "token-123",
                    "email": "test@email.com",
                    "password": "Test@1234",
                    "nickname": "테스트"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/auth/email/signup - 유효성 검증 실패 시 400")
    void signUpValidationError() throws Exception {
        String requestBody = """
                {
                    "tokenId": "",
                    "email": "not-an-email",
                    "password": "short",
                    "nickname": "a"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/email/login - 로그인 성공")
    void loginSuccess() throws Exception {
        // given
        TokenResponse tokenResponse = TokenResponse.of("access-token", "refresh-token", 1800);
        LoginResponse loginResponse = LoginResponse.of(1L, "uuid-1234", "test@email.com", "테스트", tokenResponse);

        given(authService.loginWithEmail(any(EmailLoginRequest.class), any(TokenStore.SessionInfo.class))).willReturn(loginResponse);
        given(loginRateLimitService.isLoginAllowed(any(), any())).willReturn(true);

        String requestBody = """
                {
                    "email": "test@email.com",
                    "password": "Test@1234"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userUuid").value("uuid-1234"))
                .andExpect(jsonPath("$.email").value("test@email.com"))
                .andExpect(jsonPath("$.token.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/email/login - 로그인 실패 시 401")
    void loginFails() throws Exception {
        // given
        given(loginRateLimitService.isLoginAllowed(any(), any())).willReturn(true);
        given(authService.loginWithEmail(any(EmailLoginRequest.class), any(TokenStore.SessionInfo.class)))
                .willThrow(AuthenticationException.invalidCredentials());

        String requestBody = """
                {
                    "email": "test@email.com",
                    "password": "WrongPass@1"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/email/login - Rate Limit 초과 시 429")
    void loginRateLimited() throws Exception {
        // given
        given(loginRateLimitService.isLoginAllowed(any(), any())).willReturn(false);
        given(loginRateLimitService.getRetryAfterSeconds(any())).willReturn(300L);

        String requestBody = """
                {
                    "email": "test@email.com",
                    "password": "Test@1234"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 토큰 갱신 성공")
    void refreshSuccess() throws Exception {
        // given
        TokenResponse tokenResponse = TokenResponse.of("new-access-token", "new-refresh-token", 1800);
        given(tokenService.refreshTokens("old-refresh-token")).willReturn(tokenResponse);

        String requestBody = """
                {
                    "refreshToken": "old-refresh-token"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("POST /api/v1/auth/logout - 로그아웃 성공 (accessToken in body)")
        void logoutWithAccessTokenInBody() throws Exception {
            // given
            String requestBody = """
                    {
                        "accessToken": "access-token-123",
                        "refreshToken": "refresh-token-123"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(tokenService).logout("access-token-123", "refresh-token-123");
        }

        @Test
        @DisplayName("POST /api/v1/auth/logout - 로그아웃 성공 (accessToken in header)")
        void logoutWithAccessTokenInHeader() throws Exception {
            // given
            String requestBody = """
                    {
                        "refreshToken": "refresh-token-123"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .header("Authorization", "Bearer header-access-token"))
                    .andExpect(status().isOk());

            verify(tokenService).logout("header-access-token", "refresh-token-123");
        }
    }

    @Nested
    @DisplayName("전체 로그아웃")
    class LogoutAll {

        @Test
        @DisplayName("POST /api/v1/auth/logout-all - 전체 로그아웃 성공")
        void logoutAllSuccess() throws Exception {
            // given
            UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout-all")
                            .header("Authorization", "Bearer some-access-token"))
                    .andExpect(status().isOk());

            verify(tokenService).logoutAll(eq(1L), eq("some-access-token"));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("POST /api/v1/auth/password/change - 비밀번호 변경 성공")
        void changePasswordSuccess() throws Exception {
            // given
            UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String requestBody = """
                    {
                        "currentPassword": "OldPass@123",
                        "newPassword": "NewPass@456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/password/change")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loggedOut").value(true));

            verify(passwordService).changePassword(eq(1L), any());
        }
    }

    @Nested
    @DisplayName("비밀번호 강도 분석")
    class AnalyzePassword {

        @Test
        @DisplayName("POST /api/v1/auth/password/analyze - 비밀번호 강도 분석 성공")
        void analyzePasswordSuccess() throws Exception {
            // given
            PasswordAnalysisResponse response = PasswordAnalysisResponse.builder()
                    .score(80)
                    .level("STRONG")
                    .valid(true)
                    .checks(List.of())
                    .suggestions(List.of())
                    .build();

            given(passwordUtil.analyzePassword("TestPass@123")).willReturn(response);

            String requestBody = """
                    {
                        "password": "TestPass@123"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/password/analyze")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(80))
                    .andExpect(jsonPath("$.level").value("STRONG"));
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class ResetPassword {

        @Test
        @DisplayName("POST /api/v1/auth/password/reset - 비밀번호 재설정 성공")
        void resetPasswordSuccess() throws Exception {
            // given
            String requestBody = """
                    {
                        "tokenId": "reset-token-123",
                        "recoveryEmail": "recovery@email.com",
                        "newPassword": "NewPass@789"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(passwordService).resetPassword(any());
        }
    }

    @Nested
    @DisplayName("로그인 실패 시 기록")
    class LoginFailureRecording {

        @Test
        @DisplayName("POST /api/v1/auth/email/login - 로그인 실패 시 기록 후 예외 전파")
        void loginFailureRecordsHistoryWhenUserExists() throws Exception {
            // given
            given(loginRateLimitService.isLoginAllowed(any(), any())).willReturn(true);
            given(authService.loginWithEmail(any(EmailLoginRequest.class), any(TokenStore.SessionInfo.class)))
                    .willThrow(AuthenticationException.invalidCredentials());
            given(authService.findUserIdByEmail(any())).willReturn(1L);

            String requestBody = """
                    {
                        "email": "test@email.com",
                        "password": "WrongPass@1"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());

            verify(loginRateLimitService).recordFailedAttempt(any(), any());
            verify(loginHistoryService).recordLoginFailure(eq(1L), any(), any(), any());
        }
    }
}
