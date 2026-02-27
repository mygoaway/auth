package com.jay.auth.service;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.SuspiciousActivityResponse;
import com.jay.auth.repository.LoginHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SuspiciousActivityServiceTest {

    @InjectMocks
    private SuspiciousActivityService suspiciousActivityService;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private TrustedDeviceService trustedDeviceService;

    @Nested
    @DisplayName("최근 활동 분석 (analyzeRecentActivity)")
    class AnalyzeRecentActivity {

        @Test
        @DisplayName("안전한 활동인 경우 SAFE 리스크 레벨이어야 한다")
        void analyzeRecentActivitySafe() {
            // given
            Long userId = 1L;
            // 3일 전 낮 12시 로그인 (NEW_DEVICE 감지 기준 1일 밖, 비정상 시간대 아님)
            List<LoginHistory> histories = List.of(
                    createLoginHistory(userId, true, "192.168.1.1", "Seoul", "Chrome", "macOS", "Desktop",
                            LocalDateTime.now().minusDays(3).withHour(12).withMinute(0).withSecond(0))
            );

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getRiskLevel()).isEqualTo("SAFE");
            assertThat(response.getRiskScore()).isEqualTo(0);
        }

        @Test
        @DisplayName("무차별 대입 공격이 감지되어야 한다 (5회 이상 실패)")
        void analyzeRecentActivityDetectsBruteForce() {
            // given
            Long userId = 1L;
            List<LoginHistory> histories = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                histories.add(createLoginHistory(userId, false, "10.0.0.1", "Seoul", "Chrome", "macOS", "Desktop",
                        LocalDateTime.now().minusHours(i)));
            }

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getEvents()).anyMatch(
                    event -> "BRUTE_FORCE".equals(event.getType())
            );
            assertThat(response.getRiskScore()).isGreaterThanOrEqualTo(30);
        }

        @Test
        @DisplayName("다중 지역 로그인이 감지되어야 한다 (3개 이상 지역)")
        void analyzeRecentActivityDetectsMultipleLocations() {
            // given
            Long userId = 1L;
            List<LoginHistory> histories = List.of(
                    createLoginHistory(userId, true, "1.1.1.1", "Seoul", "Chrome", "macOS", "Desktop",
                            LocalDateTime.now().minusHours(1)),
                    createLoginHistory(userId, true, "2.2.2.2", "Busan", "Chrome", "macOS", "Desktop",
                            LocalDateTime.now().minusHours(2)),
                    createLoginHistory(userId, true, "3.3.3.3", "New York", "Chrome", "macOS", "Desktop",
                            LocalDateTime.now().minusHours(3))
            );

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getEvents()).anyMatch(
                    event -> "MULTIPLE_LOCATIONS".equals(event.getType())
            );
        }

        @Test
        @DisplayName("빠른 연속 로그인이 감지되어야 한다 (10분 이내 5회 이상)")
        void analyzeRecentActivityDetectsRapidLogins() {
            // given
            Long userId = 1L;
            LocalDateTime baseTime = LocalDateTime.now().minusMinutes(5);
            List<LoginHistory> histories = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                histories.add(createLoginHistory(userId, true, "1.1.1.1", "Seoul", "Chrome", "macOS", "Desktop",
                        baseTime.plusMinutes(i)));
            }

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getEvents()).anyMatch(
                    event -> "RAPID_LOGIN".equals(event.getType())
            );
        }

        @Test
        @DisplayName("비정상 시간대 로그인이 감지되어야 한다 (01:00~05:00)")
        void analyzeRecentActivityDetectsUnusualHours() {
            // given
            Long userId = 1L;
            LocalDateTime lateNight = LocalDateTime.now().withHour(3).withMinute(0);
            List<LoginHistory> histories = List.of(
                    createLoginHistory(userId, true, "1.1.1.1", "Seoul", "Chrome", "macOS", "Desktop", lateNight)
            );

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getEvents()).anyMatch(
                    event -> "UNUSUAL_HOURS".equals(event.getType())
            );
        }

        @Test
        @DisplayName("새 기기 로그인이 감지되어야 한다")
        void analyzeRecentActivityDetectsNewDevice() {
            // given
            Long userId = 1L;
            LocalDateTime recentTime = LocalDateTime.now().minusHours(1);
            List<LoginHistory> histories = List.of(
                    createLoginHistory(userId, true, "1.1.1.1", "Seoul", "Firefox", "Linux", "Desktop", recentTime)
            );

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getEvents()).anyMatch(
                    event -> "NEW_DEVICE".equals(event.getType())
            );
        }

        @Test
        @DisplayName("복합 위험 시나리오에서 HIGH 레벨을 반환해야 한다")
        void analyzeRecentActivityHighRisk() {
            // given
            Long userId = 1L;
            List<LoginHistory> histories = new ArrayList<>();

            // Brute force from IP1: 6 failed (HIGH = 30pts)
            for (int i = 0; i < 6; i++) {
                histories.add(createLoginHistory(userId, false, "10.0.0.1", "Seoul", "Chrome", "macOS", "Desktop",
                        LocalDateTime.now().minusHours(i)));
            }

            // Brute force from IP2: 5 failed (HIGH = 30pts) → 합계 60pts = HIGH
            for (int i = 0; i < 5; i++) {
                histories.add(createLoginHistory(userId, false, "10.0.0.2", "Busan", "Firefox", "Windows", "Desktop",
                        LocalDateTime.now().minusHours(i)));
            }

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getRiskLevel()).isEqualTo("HIGH");
            assertThat(response.getRiskScore()).isGreaterThanOrEqualTo(60);
            assertThat(response.getRecommendations()).isNotEmpty();
        }

        @Test
        @DisplayName("이벤트가 시간순으로 정렬되어야 한다")
        void analyzeRecentActivitySortsByTime() {
            // given
            Long userId = 1L;
            List<LoginHistory> histories = new ArrayList<>();

            // Multiple failures for brute force detection
            for (int i = 0; i < 6; i++) {
                histories.add(createLoginHistory(userId, false, "10.0.0.1", "Seoul", "Chrome", "macOS", "Desktop",
                        LocalDateTime.now().minusDays(i)));
            }

            given(loginHistoryRepository.findByUserIdAndPeriod(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .willReturn(histories);

            // when
            SuspiciousActivityResponse response = suspiciousActivityService.analyzeRecentActivity(userId);

            // then
            assertThat(response.getEvents()).isNotEmpty();
            if (response.getEvents().size() > 1) {
                for (int i = 0; i < response.getEvents().size() - 1; i++) {
                    LocalDateTime current = response.getEvents().get(i).getDetectedAt();
                    LocalDateTime next = response.getEvents().get(i + 1).getDetectedAt();
                    if (current != null && next != null) {
                        assertThat(current).isAfterOrEqualTo(next);
                    }
                }
            }
        }
    }

    // Helper methods
    private LoginHistory createLoginHistory(Long userId, boolean isSuccess, String ipAddress,
                                            String location, String browser, String os,
                                            String deviceType, LocalDateTime createdAt) {
        LoginHistory history = LoginHistory.builder()
                .userId(userId)
                .channelCode(ChannelCode.EMAIL)
                .ipAddress(ipAddress)
                .browser(browser)
                .os(os)
                .deviceType(deviceType)
                .location(location)
                .isSuccess(isSuccess)
                .build();
        setField(history, "createdAt", createdAt);
        return history;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
