package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.*;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AdminService;
import com.jay.auth.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminController.class,
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
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private com.jay.auth.service.LoginAnalyticsService loginAnalyticsService;

    @BeforeEach
    void setUp() {
        UserPrincipal adminPrincipal = new UserPrincipal(1L, "admin-uuid", "ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("GET /api/v1/admin/dashboard")
    class GetDashboard {

        @Test
        @DisplayName("대시보드 조회 성공")
        void getDashboardSuccess() throws Exception {
            // given
            AdminDashboardResponse.UserStats stats = AdminDashboardResponse.UserStats.builder()
                    .totalUsers(100)
                    .activeUsers(80)
                    .dormantUsers(15)
                    .pendingDeleteUsers(5)
                    .todaySignups(3)
                    .build();

            AdminDashboardResponse.AdminUserInfo userInfo = AdminDashboardResponse.AdminUserInfo.builder()
                    .userId(2L)
                    .userUuid("user-uuid-1")
                    .email("test@email.com")
                    .nickname("테스트유저")
                    .status("ACTIVE")
                    .role("USER")
                    .channels(List.of("EMAIL", "GOOGLE"))
                    .createdAt("2025-01-01 10:00:00")
                    .lastLoginAt("2025-01-15 14:30:00")
                    .build();

            AdminDashboardResponse response = AdminDashboardResponse.builder()
                    .userStats(stats)
                    .recentUsers(List.of(userInfo))
                    .build();

            given(adminService.getDashboard()).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userStats.totalUsers").value(100))
                    .andExpect(jsonPath("$.userStats.activeUsers").value(80))
                    .andExpect(jsonPath("$.userStats.dormantUsers").value(15))
                    .andExpect(jsonPath("$.userStats.pendingDeleteUsers").value(5))
                    .andExpect(jsonPath("$.userStats.todaySignups").value(3))
                    .andExpect(jsonPath("$.recentUsers.length()").value(1))
                    .andExpect(jsonPath("$.recentUsers[0].email").value("test@email.com"))
                    .andExpect(jsonPath("$.recentUsers[0].role").value("USER"));
        }

        @Test
        @DisplayName("대시보드 조회 - 사용자 없음")
        void getDashboardEmpty() throws Exception {
            // given
            AdminDashboardResponse.UserStats stats = AdminDashboardResponse.UserStats.builder()
                    .totalUsers(0)
                    .activeUsers(0)
                    .dormantUsers(0)
                    .pendingDeleteUsers(0)
                    .todaySignups(0)
                    .build();

            AdminDashboardResponse response = AdminDashboardResponse.builder()
                    .userStats(stats)
                    .recentUsers(Collections.emptyList())
                    .build();

            given(adminService.getDashboard()).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userStats.totalUsers").value(0))
                    .andExpect(jsonPath("$.recentUsers.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/users/{userId}/role")
    class UpdateUserRole {

        @Test
        @DisplayName("사용자 역할 변경 성공")
        void updateUserRoleSuccess() throws Exception {
            // given
            willDoNothing().given(adminService).updateUserRole(1L, 2L, UserRole.ADMIN);

            // when & then
            mockMvc.perform(patch("/api/v1/admin/users/2/role")
                            .param("role", "ADMIN"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("존재하지 않는 사용자 역할 변경 시 404")
        void updateUserRoleUserNotFound() throws Exception {
            // given
            willThrow(new UserNotFoundException())
                    .given(adminService).updateUserRole(anyLong(), eq(999L), eq(UserRole.ADMIN));

            // when & then
            mockMvc.perform(patch("/api/v1/admin/users/999/role")
                            .param("role", "ADMIN"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/users/{userId}/status")
    class UpdateUserStatus {

        @Test
        @DisplayName("사용자 상태 변경 성공")
        void updateUserStatusSuccess() throws Exception {
            // given
            willDoNothing().given(adminService).updateUserStatus(1L, 2L, UserStatus.LOCKED);

            // when & then
            mockMvc.perform(patch("/api/v1/admin/users/2/status")
                            .param("status", "LOCKED"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("존재하지 않는 사용자 상태 변경 시 404")
        void updateUserStatusUserNotFound() throws Exception {
            // given
            willThrow(new UserNotFoundException())
                    .given(adminService).updateUserStatus(anyLong(), eq(999L), eq(UserStatus.LOCKED));

            // when & then
            mockMvc.perform(patch("/api/v1/admin/users/999/status")
                            .param("status", "LOCKED"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users")
    class SearchUsers {

        @Test
        @DisplayName("사용자 검색 성공")
        void searchUsersSuccess() throws Exception {
            // given
            AdminDashboardResponse.AdminUserInfo userInfo = AdminDashboardResponse.AdminUserInfo.builder()
                    .userId(1L)
                    .userUuid("uuid-1")
                    .email("test@email.com")
                    .nickname("테스트")
                    .status("ACTIVE")
                    .role("USER")
                    .channels(List.of("EMAIL"))
                    .createdAt("2025-01-01 10:00:00")
                    .build();

            AdminUserSearchResponse response = AdminUserSearchResponse.builder()
                    .users(List.of(userInfo))
                    .currentPage(0)
                    .totalPages(1)
                    .totalElements(1)
                    .build();

            given(adminService.searchUsers(any(), any(), anyInt(), anyInt())).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/users")
                            .param("keyword", "test")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.users[0].email").value("test@email.com"));
        }

        @Test
        @DisplayName("사용자 검색 - 결과 없음")
        void searchUsersEmpty() throws Exception {
            // given
            AdminUserSearchResponse response = AdminUserSearchResponse.builder()
                    .users(Collections.emptyList())
                    .currentPage(0)
                    .totalPages(0)
                    .totalElements(0)
                    .build();

            given(adminService.searchUsers(any(), any(), anyInt(), anyInt())).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.users.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/stats/logins")
    class GetLoginStats {

        @Test
        @DisplayName("로그인 통계 조회 성공")
        void getLoginStatsSuccess() throws Exception {
            // given
            AdminLoginStatsResponse response = AdminLoginStatsResponse.builder()
                    .todayLogins(150)
                    .todaySignups(10)
                    .activeUsersLast7Days(500)
                    .dailyLogins(List.of(Map.of("date", "2025-01-15", "count", 100)))
                    .dailySignups(List.of(Map.of("date", "2025-01-15", "count", 5)))
                    .build();

            given(adminService.getLoginStats()).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/stats/logins"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.todayLogins").value(150))
                    .andExpect(jsonPath("$.todaySignups").value(10))
                    .andExpect(jsonPath("$.activeUsersLast7Days").value(500))
                    .andExpect(jsonPath("$.dailyLogins.length()").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/security/events")
    class GetSecurityEvents {

        @Test
        @DisplayName("보안 이벤트 조회 성공")
        void getSecurityEventsSuccess() throws Exception {
            // given
            AdminSecurityEventsResponse.FailedLoginInfo failedLogin =
                    AdminSecurityEventsResponse.FailedLoginInfo.builder()
                            .userId(1L)
                            .ipAddress("192.168.1.1")
                            .browser("Chrome")
                            .failureReason("INVALID_PASSWORD")
                            .createdAt("2025-01-15 10:00:00")
                            .build();

            AdminSecurityEventsResponse.AuditEventInfo auditEvent =
                    AdminSecurityEventsResponse.AuditEventInfo.builder()
                            .userId(1L)
                            .action("PASSWORD_CHANGE")
                            .target("USER")
                            .success(true)
                            .createdAt("2025-01-15 10:00:00")
                            .build();

            AdminSecurityEventsResponse response = AdminSecurityEventsResponse.builder()
                    .failedLoginsToday(25)
                    .passwordChangesToday(5)
                    .accountLocksToday(2)
                    .recentFailedLogins(List.of(failedLogin))
                    .recentAuditEvents(List.of(auditEvent))
                    .build();

            given(adminService.getSecurityEvents()).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/security/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.failedLoginsToday").value(25))
                    .andExpect(jsonPath("$.passwordChangesToday").value(5))
                    .andExpect(jsonPath("$.accountLocksToday").value(2))
                    .andExpect(jsonPath("$.recentFailedLogins.length()").value(1))
                    .andExpect(jsonPath("$.recentAuditEvents.length()").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/stats/support")
    class GetSupportStats {

        @Test
        @DisplayName("고객센터 통계 조회 성공")
        void getSupportStatsSuccess() throws Exception {
            // given
            AdminSupportStatsResponse response = AdminSupportStatsResponse.builder()
                    .totalPosts(100)
                    .openPosts(20)
                    .inProgressPosts(15)
                    .resolvedPosts(50)
                    .closedPosts(15)
                    .todayPosts(3)
                    .build();

            given(adminService.getSupportStats()).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/admin/stats/support"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalPosts").value(100))
                    .andExpect(jsonPath("$.openPosts").value(20))
                    .andExpect(jsonPath("$.inProgressPosts").value(15))
                    .andExpect(jsonPath("$.resolvedPosts").value(50))
                    .andExpect(jsonPath("$.closedPosts").value(15))
                    .andExpect(jsonPath("$.todayPosts").value(3));
        }
    }
}
