package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.dto.response.AdminDashboardResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AdminService;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
                        com.jay.auth.config.SecurityHeadersFilter.class
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
}
