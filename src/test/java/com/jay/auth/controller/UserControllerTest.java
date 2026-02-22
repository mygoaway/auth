package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.*;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.UserService;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        com.jay.auth.config.RateLimitFilter.class,
                        com.jay.auth.config.RequestLoggingFilter.class,
                        com.jay.auth.config.SecurityHeadersFilter.class,
                        com.jay.auth.config.RequestIdFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private com.jay.auth.service.AccountLinkingService accountLinkingService;

    @MockitoBean
    private com.jay.auth.service.LoginHistoryService loginHistoryService;

    @MockitoBean
    private com.jay.auth.service.TokenService tokenService;

    @MockitoBean
    private com.jay.auth.service.SecurityDashboardService securityDashboardService;

    @MockitoBean
    private com.jay.auth.service.ActivityReportService activityReportService;

    @MockitoBean
    private com.jay.auth.service.TrustedDeviceService trustedDeviceService;

    @MockitoBean
    private com.jay.auth.service.SuspiciousActivityService suspiciousActivityService;

    @MockitoBean
    private com.jay.auth.service.SecuritySettingsService securitySettingsService;

    @MockitoBean
    private com.jay.auth.service.LoginAnalyticsService loginAnalyticsService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("GET /api/v1/users/profile - 프로필 조회 성공")
    void getProfileSuccess() throws Exception {
        // given
        UserProfileResponse response = UserProfileResponse.builder()
                .userUuid("uuid-1234")
                .email("test@email.com")
                .nickname("테스트")
                .status("ACTIVE")
                .channels(List.of(
                        UserProfileResponse.ChannelInfo.builder()
                                .channelCode("EMAIL")
                                .channelEmail("test@email.com")
                                .linkedAt(LocalDateTime.now())
                                .build()
                ))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(userService.getProfile(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userUuid").value("uuid-1234"))
                .andExpect(jsonPath("$.email").value("test@email.com"))
                .andExpect(jsonPath("$.nickname").value("테스트"))
                .andExpect(jsonPath("$.channels[0].channelCode").value("EMAIL"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/profile/nickname - 닉네임 변경 성공")
    void updateNicknameSuccess() throws Exception {
        // given
        String requestBody = """
                {
                    "nickname": "새닉네임"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/users/profile/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userService).updateNickname(eq(1L), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/profile/phone - 핸드폰 번호 변경 성공")
    void updatePhoneSuccess() throws Exception {
        // given
        String requestBody = """
                {
                    "phone": "010-1234-5678",
                    "tokenId": "token-123"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/users/profile/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userService).updatePhone(eq(1L), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/profile/recovery-email - 복구 이메일 변경 성공")
    void updateRecoveryEmailSuccess() throws Exception {
        // given
        String requestBody = """
                {
                    "recoveryEmail": "recovery@email.com",
                    "tokenId": "token-123"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/users/profile/recovery-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userService).updateRecoveryEmail(eq(1L), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - 회원 탈퇴 성공")
    void deleteAccountSuccess() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(1L);
    }

    @Nested
    @DisplayName("탈퇴 유예 취소")
    class CancelDeletion {

        @Test
        @DisplayName("POST /api/v1/users/me/cancel-deletion - 탈퇴 유예 취소 성공")
        void cancelDeletionSuccess() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/users/me/cancel-deletion"))
                    .andExpect(status().isOk());

            verify(userService).cancelDeletion(1L);
        }
    }

    @Nested
    @DisplayName("채널 조회/해제")
    class Channels {

        @Test
        @DisplayName("GET /api/v1/users/channels - 연결된 채널 조회 성공")
        void getChannelsStatusSuccess() throws Exception {
            // given
            ChannelStatusResponse response = ChannelStatusResponse.builder()
                    .channels(List.of(
                            ChannelStatusResponse.ChannelStatus.builder()
                                    .channelCode("EMAIL")
                                    .description("이메일")
                                    .linked(true)
                                    .channelEmail("test@email.com")
                                    .linkedAt(LocalDateTime.now())
                                    .build(),
                            ChannelStatusResponse.ChannelStatus.builder()
                                    .channelCode("GOOGLE")
                                    .description("구글")
                                    .linked(false)
                                    .build()
                    ))
                    .build();

            given(accountLinkingService.getChannelsStatus(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/channels"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.channels[0].channelCode").value("EMAIL"))
                    .andExpect(jsonPath("$.channels[0].linked").value(true))
                    .andExpect(jsonPath("$.channels[1].channelCode").value("GOOGLE"))
                    .andExpect(jsonPath("$.channels[1].linked").value(false));
        }

        @Test
        @DisplayName("DELETE /api/v1/users/channels/{channelCode} - 채널 연결 해제 성공")
        void unlinkChannelSuccess() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/users/channels/GOOGLE"))
                    .andExpect(status().isNoContent());

            verify(accountLinkingService).unlinkChannel(1L, ChannelCode.GOOGLE);
        }
    }

    @Nested
    @DisplayName("로그인 기록 조회")
    class LoginHistoryTests {

        @Test
        @DisplayName("GET /api/v1/users/login-history - 로그인 기록 조회 성공")
        void getLoginHistorySuccess() throws Exception {
            // given
            List<LoginHistoryResponse> histories = List.of(
                    LoginHistoryResponse.builder()
                            .id(1L)
                            .channelCode(ChannelCode.EMAIL)
                            .ipAddress("192.168.1.*")
                            .deviceType("Desktop")
                            .browser("Chrome")
                            .os("macOS")
                            .isSuccess(true)
                            .createdAt(LocalDateTime.now())
                            .build()
            );

            given(loginHistoryService.getRecentLoginHistory(1L, 10)).willReturn(histories);

            // when & then
            mockMvc.perform(get("/api/v1/users/login-history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].channelCode").value("EMAIL"))
                    .andExpect(jsonPath("$[0].deviceType").value("Desktop"));
        }

        @Test
        @DisplayName("GET /api/v1/users/login-history?limit=5 - 제한된 로그인 기록 조회")
        void getLoginHistoryWithCustomLimit() throws Exception {
            // given
            given(loginHistoryService.getRecentLoginHistory(1L, 5)).willReturn(Collections.emptyList());

            // when & then
            mockMvc.perform(get("/api/v1/users/login-history").param("limit", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested
    @DisplayName("세션 관리")
    class Sessions {

        @Test
        @DisplayName("GET /api/v1/users/sessions - 활성 세션 조회 성공")
        void getActiveSessionsSuccess() throws Exception {
            // given
            List<ActiveSessionResponse> sessions = List.of(
                    ActiveSessionResponse.of(
                            "session-1", "Desktop", "Chrome", "macOS",
                            "127.0.0.1", null, LocalDateTime.now(), true
                    )
            );

            given(tokenService.getTokenId(any())).willReturn("session-1");
            given(tokenService.getActiveSessions(eq(1L), any())).willReturn(sessions);

            // when & then
            mockMvc.perform(get("/api/v1/users/sessions")
                            .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sessionId").value("session-1"))
                    .andExpect(jsonPath("$[0].currentSession").value(true));
        }

        @Test
        @DisplayName("DELETE /api/v1/users/sessions/{sessionId} - 세션 종료 성공")
        void revokeSessionSuccess() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/users/sessions/session-123"))
                    .andExpect(status().isNoContent());

            verify(tokenService).revokeSession(1L, "session-123");
        }
    }

    @Nested
    @DisplayName("보안 대시보드")
    class SecurityDashboard {

        @Test
        @DisplayName("GET /api/v1/users/security/dashboard - 보안 대시보드 조회 성공")
        void getSecurityDashboardSuccess() throws Exception {
            // given
            SecurityDashboardResponse response = SecurityDashboardResponse.builder()
                    .securityScore(75)
                    .securityLevel("GOOD")
                    .factors(List.of())
                    .recentActivities(List.of())
                    .recommendations(List.of("2FA를 활성화하세요"))
                    .build();

            given(securityDashboardService.getSecurityDashboard(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/security/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.securityScore").value(75))
                    .andExpect(jsonPath("$.securityLevel").value("GOOD"));
        }
    }

    @Nested
    @DisplayName("신뢰할 수 있는 기기 관리")
    class TrustedDevices {

        @Test
        @DisplayName("GET /api/v1/users/devices/trusted - 신뢰 기기 목록 조회 성공")
        void getTrustedDevicesSuccess() throws Exception {
            // given
            List<TrustedDeviceResponse> devices = List.of(
                    TrustedDeviceResponse.builder()
                            .deviceId("device-1")
                            .deviceType("Desktop")
                            .browser("Chrome")
                            .os("macOS")
                            .build()
            );

            given(trustedDeviceService.getTrustedDevices(1L)).willReturn(devices);

            // when & then
            mockMvc.perform(get("/api/v1/users/devices/trusted"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].deviceId").value("device-1"))
                    .andExpect(jsonPath("$[0].deviceType").value("Desktop"));
        }

        @Test
        @DisplayName("POST /api/v1/users/devices/trusted - 현재 기기 신뢰 등록 성공")
        void trustCurrentDeviceSuccess() throws Exception {
            // given
            var sessionInfo = new com.jay.auth.security.TokenStore.SessionInfo(
                    "Desktop", "Chrome", "macOS", "127.0.0.1", null);
            given(loginHistoryService.extractSessionInfo(any())).willReturn(sessionInfo);

            // when & then
            mockMvc.perform(post("/api/v1/users/devices/trusted"))
                    .andExpect(status().isOk());

            verify(trustedDeviceService).trustDevice(eq(1L), any());
        }

        @Test
        @DisplayName("DELETE /api/v1/users/devices/trusted/{deviceId} - 신뢰 기기 삭제 성공")
        void removeTrustedDeviceSuccess() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/users/devices/trusted/device-123"))
                    .andExpect(status().isNoContent());

            verify(trustedDeviceService).removeTrustedDevice(1L, "device-123");
        }

        @Test
        @DisplayName("DELETE /api/v1/users/devices/trusted - 모든 신뢰 기기 삭제 성공")
        void removeAllTrustedDevicesSuccess() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/users/devices/trusted"))
                    .andExpect(status().isNoContent());

            verify(trustedDeviceService).removeAllTrustedDevices(1L);
        }
    }

    @Nested
    @DisplayName("의심스러운 활동 분석")
    class SuspiciousActivity {

        @Test
        @DisplayName("GET /api/v1/users/security/suspicious - 의심스러운 활동 조회 성공")
        void getSuspiciousActivitySuccess() throws Exception {
            // given
            SuspiciousActivityResponse response = SuspiciousActivityResponse.builder()
                    .riskLevel("LOW")
                    .riskScore(10)
                    .events(List.of())
                    .recommendations(List.of())
                    .build();

            given(suspiciousActivityService.analyzeRecentActivity(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/security/suspicious"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.riskLevel").value("LOW"))
                    .andExpect(jsonPath("$.riskScore").value(10));
        }
    }

    @Nested
    @DisplayName("주간 활동 리포트")
    class WeeklyActivity {

        @Test
        @DisplayName("GET /api/v1/users/activity/weekly - 주간 활동 리포트 조회 성공")
        void getWeeklyActivitySuccess() throws Exception {
            // given
            WeeklyActivityResponse response = WeeklyActivityResponse.builder()
                    .weekStart(LocalDateTime.now().minusDays(7))
                    .weekEnd(LocalDateTime.now())
                    .loginStats(WeeklyActivityResponse.LoginStats.builder()
                            .totalLogins(5)
                            .successfulLogins(4)
                            .failedLogins(1)
                            .build())
                    .build();

            given(activityReportService.getWeeklyReport(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/activity/weekly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginStats.totalLogins").value(5))
                    .andExpect(jsonPath("$.loginStats.successfulLogins").value(4));
        }
    }

    @Nested
    @DisplayName("보안 설정")
    class SecuritySettings {

        @Test
        @DisplayName("GET /api/v1/users/security/settings - 보안 설정 조회 성공")
        void getSecuritySettingsSuccess() throws Exception {
            // given
            SecuritySettingsResponse response = SecuritySettingsResponse.builder()
                    .loginNotificationEnabled(true)
                    .suspiciousActivityNotificationEnabled(false)
                    .accountLocked(false)
                    .failedLoginAttempts(0)
                    .maxFailedAttempts(5)
                    .build();

            given(securitySettingsService.getSecuritySettings(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users/security/settings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginNotificationEnabled").value(true))
                    .andExpect(jsonPath("$.accountLocked").value(false));
        }

        @Test
        @DisplayName("PATCH /api/v1/users/security/settings/login-notification - 로그인 알림 설정 변경 성공")
        void updateLoginNotificationSuccess() throws Exception {
            // given
            String requestBody = """
                    {
                        "enabled": true
                    }
                    """;

            // when & then
            mockMvc.perform(patch("/api/v1/users/security/settings/login-notification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(securitySettingsService).updateLoginNotification(1L, true);
        }

        @Test
        @DisplayName("PATCH /api/v1/users/security/settings/login-notification - enabled가 null이면 서비스 호출 안 함")
        void updateLoginNotificationNullEnabled() throws Exception {
            // given
            String requestBody = """
                    {}
                    """;

            // when & then
            mockMvc.perform(patch("/api/v1/users/security/settings/login-notification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PATCH /api/v1/users/security/settings/suspicious-notification - 의심 활동 알림 설정 변경 성공")
        void updateSuspiciousNotificationSuccess() throws Exception {
            // given
            String requestBody = """
                    {
                        "enabled": false
                    }
                    """;

            // when & then
            mockMvc.perform(patch("/api/v1/users/security/settings/suspicious-notification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(securitySettingsService).updateSuspiciousNotification(1L, false);
        }

        @Test
        @DisplayName("POST /api/v1/users/security/unlock - 계정 잠금 해제 성공")
        void unlockAccountSuccess() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/users/security/unlock"))
                    .andExpect(status().isOk());

            verify(securitySettingsService).unlockAccount(1L);
        }
    }
}
