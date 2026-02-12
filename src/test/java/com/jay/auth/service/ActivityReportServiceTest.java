package com.jay.auth.service;

import com.jay.auth.domain.entity.AuditLog;
import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.WeeklyActivityResponse;
import com.jay.auth.repository.AuditLogRepository;
import com.jay.auth.repository.LoginHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ActivityReportServiceTest {

    @InjectMocks
    private ActivityReportService activityReportService;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Nested
    @DisplayName("현재 주간 리포트 조회")
    class GetWeeklyReportCurrentWeek {

        @Test
        @DisplayName("로그인 통계, 기기 분석, 지역, 이벤트를 포함한 리포트를 반환해야 한다")
        void getWeeklyReportWithFullData() {
            // given
            Long userId = 1L;

            List<LoginHistory> loginHistories = List.of(
                    createLoginHistory(userId, ChannelCode.EMAIL, true, "Desktop", "Chrome", "Windows 10",
                            "192.168.1.1", "Seoul"),
                    createLoginHistory(userId, ChannelCode.EMAIL, true, "Desktop", "Chrome", "Windows 10",
                            "192.168.1.1", "Seoul"),
                    createLoginHistory(userId, ChannelCode.GOOGLE, true, "Mobile", "Safari", "iOS 17",
                            "10.0.0.1", "Busan"),
                    createLoginHistory(userId, ChannelCode.EMAIL, false, "Desktop", "Firefox", "Linux",
                            "172.16.0.1", "Seoul")
            );

            AuditLog auditLog = createAuditLog(userId, "PASSWORD_CHANGE", "비밀번호 변경");
            List<AuditLog> auditLogs = List.of(auditLog);

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(loginHistories);
            given(auditLogRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(auditLogs);

            // when
            WeeklyActivityResponse response = activityReportService.getWeeklyReport(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getWeekStart()).isNotNull();
            assertThat(response.getWeekEnd()).isNotNull();

            // Login stats
            WeeklyActivityResponse.LoginStats loginStats = response.getLoginStats();
            assertThat(loginStats.getTotalLogins()).isEqualTo(4);
            assertThat(loginStats.getSuccessfulLogins()).isEqualTo(3);
            assertThat(loginStats.getFailedLogins()).isEqualTo(1);
            assertThat(loginStats.getLoginsByChannel()).isNotEmpty();

            // Device breakdown - only successful logins
            assertThat(response.getDeviceBreakdown()).isNotEmpty();

            // Locations
            assertThat(response.getLocations()).isNotEmpty();

            // Account events
            assertThat(response.getAccountEvents()).hasSize(1);
            assertThat(response.getAccountEvents().get(0).getAction()).isEqualTo("PASSWORD_CHANGE");

            // Security alerts
            assertThat(response.getSecurityAlerts()).isNotEmpty();
        }

        @Test
        @DisplayName("5회 이상 로그인 실패 시 보안 경고가 포함되어야 한다")
        void securityAlertWhenManyFailedLogins() {
            // given
            Long userId = 1L;

            List<LoginHistory> loginHistories = List.of(
                    createLoginHistory(userId, ChannelCode.EMAIL, false, "Desktop", "Chrome", "Windows", "1.1.1.1", "Seoul"),
                    createLoginHistory(userId, ChannelCode.EMAIL, false, "Desktop", "Chrome", "Windows", "1.1.1.2", "Seoul"),
                    createLoginHistory(userId, ChannelCode.EMAIL, false, "Desktop", "Chrome", "Windows", "1.1.1.3", "Seoul"),
                    createLoginHistory(userId, ChannelCode.EMAIL, false, "Desktop", "Chrome", "Windows", "1.1.1.4", "Seoul"),
                    createLoginHistory(userId, ChannelCode.EMAIL, false, "Desktop", "Chrome", "Windows", "1.1.1.5", "Seoul")
            );

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(loginHistories);
            given(auditLogRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            WeeklyActivityResponse response = activityReportService.getWeeklyReport(userId);

            // then
            assertThat(response.getSecurityAlerts()).anyMatch(
                    alert -> alert.contains("로그인 실패가 5회 발생"));
        }
    }

    @Nested
    @DisplayName("데이터 없는 주간 리포트")
    class GetWeeklyReportNoData {

        @Test
        @DisplayName("데이터가 없어도 빈 리포트를 정상 반환해야 한다")
        void returnEmptyReport() {
            // given
            Long userId = 1L;

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());
            given(auditLogRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            WeeklyActivityResponse response = activityReportService.getWeeklyReport(userId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getLoginStats().getTotalLogins()).isZero();
            assertThat(response.getLoginStats().getSuccessfulLogins()).isZero();
            assertThat(response.getLoginStats().getFailedLogins()).isZero();
            assertThat(response.getDeviceBreakdown()).isEmpty();
            assertThat(response.getLocations()).isEmpty();
            assertThat(response.getAccountEvents()).isEmpty();
            assertThat(response.getSecurityAlerts()).hasSize(1);
            assertThat(response.getSecurityAlerts().get(0)).contains("특이 보안 이슈가 없습니다");
        }
    }

    @Nested
    @DisplayName("특정 날짜 주간 리포트")
    class GetWeeklyReportForSpecificDate {

        @Test
        @DisplayName("특정 주의 리포트를 정상적으로 조회해야 한다")
        void getReportForSpecificWeek() {
            // given
            Long userId = 1L;
            LocalDate targetWeekStart = LocalDate.of(2025, 1, 6); // Monday

            LoginHistory loginHistory = createLoginHistory(
                    userId, ChannelCode.EMAIL, true, "Desktop", "Chrome", "Windows 10",
                    "192.168.1.1", "Seoul");

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(List.of(loginHistory));
            given(auditLogRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            WeeklyActivityResponse response = activityReportService.getWeeklyReport(userId, targetWeekStart);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getWeekStart().toLocalDate()).isEqualTo(targetWeekStart.with(DayOfWeek.MONDAY));
            assertThat(response.getLoginStats().getTotalLogins()).isEqualTo(1);
            assertThat(response.getLoginStats().getSuccessfulLogins()).isEqualTo(1);
        }
    }

    // Helper methods
    private LoginHistory createLoginHistory(Long userId, ChannelCode channelCode, boolean success,
                                             String deviceType, String browser, String os,
                                             String ipAddress, String location) {
        LoginHistory history = LoginHistory.builder()
                .userId(userId)
                .channelCode(channelCode)
                .isSuccess(success)
                .deviceType(deviceType)
                .browser(browser)
                .os(os)
                .ipAddress(ipAddress)
                .location(location)
                .build();
        setField(history, "createdAt", LocalDateTime.now());
        return history;
    }

    private AuditLog createAuditLog(Long userId, String action, String detail) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .target("USER")
                .detail(detail)
                .isSuccess(true)
                .build();
        setField(auditLog, "createdAt", LocalDateTime.now());
        return auditLog;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
