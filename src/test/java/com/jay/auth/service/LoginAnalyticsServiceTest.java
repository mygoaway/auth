package com.jay.auth.service;

import com.jay.auth.dto.response.LoginFailureHotspotResponse;
import com.jay.auth.dto.response.LoginHeatmapResponse;
import com.jay.auth.dto.response.LoginTimelineResponse;
import com.jay.auth.dto.response.UserLoginMapResponse;
import com.jay.auth.repository.LoginHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAnalyticsService 테스트")
class LoginAnalyticsServiceTest {

    @InjectMocks
    private LoginAnalyticsService loginAnalyticsService;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    // ─── 헬퍼 메서드 ─────────────────────────────────────────────────────────────

    private Object[] row(String loc, long total, long fail) {
        return new Object[]{loc, total, fail};
    }

    private Object[] rowWithTime(String ip, String loc, long count, LocalDateTime time) {
        return new Object[]{ip, loc, count, time};
    }

    // ─── getHeatmap ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getHeatmap()")
    class GetHeatmap {

        @Test
        @DisplayName("정상 집계 — country/city 파싱 및 failureRate 계산")
        void normalAggregation() {
            // given
            List<Object[]> rows = List.of(
                    row("Seoul, KR", 100L, 20L),
                    row("Tokyo, JP", 50L, 5L)
            );
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(30);

            // then
            assertThat(response.getHeatmap()).hasSize(2);
            assertThat(response.getPeriod()).isEqualTo("30d");

            LoginHeatmapResponse.CountryStats seoulStats = response.getHeatmap().get(0);
            assertThat(seoulStats.getCountry()).isEqualTo("KR");
            assertThat(seoulStats.getCity()).isEqualTo("Seoul");
            assertThat(seoulStats.getTotalCount()).isEqualTo(100L);
            assertThat(seoulStats.getFailureCount()).isEqualTo(20L);
            assertThat(seoulStats.getFailureRate()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("빈 결과 — 빈 히트맵 반환")
        void emptyResult() {
            // given
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(7);

            // then
            assertThat(response.getHeatmap()).isEmpty();
            assertThat(response.getSuspiciousRegions()).isEmpty();
            assertThat(response.getPeriod()).isEqualTo("7d");
        }

        @Test
        @DisplayName("SUSPICIOUS_COUNTRIES 감지 — CN/RU/KP/IR")
        void suspiciousCountriesDetected() {
            // given
            List<Object[]> rows = List.of(
                    row("Beijing, CN", 10L, 1L),
                    row("Moscow, RU", 20L, 2L),
                    row("Pyongyang, KP", 5L, 0L),
                    row("Tehran, IR", 8L, 0L)
            );
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(30);

            // then
            List<String> suspicious = response.getSuspiciousRegions();
            assertThat(suspicious).contains("CN", "RU", "KP", "IR");
        }

        @Test
        @DisplayName("failureRate >= 0.5 이면 suspiciousRegions에 추가됨")
        void highFailureRateMarkedAsSuspicious() {
            // given
            List<Object[]> rows = Collections.singletonList(
                    row("London, GB", 10L, 6L) // failureRate = 0.6
            );
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(30);

            // then
            assertThat(response.getSuspiciousRegions()).contains("GB");
        }

        @Test
        @DisplayName("location에 쉼표가 없으면 country=location, city=빈 문자열")
        void locationWithoutComma() {
            // given
            List<Object[]> rows = Collections.singletonList(row("Unknown", 5L, 0L));
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(30);

            // then
            LoginHeatmapResponse.CountryStats stats = response.getHeatmap().get(0);
            assertThat(stats.getCountry()).isEqualTo("Unknown");
            assertThat(stats.getCity()).isEqualTo("");
        }

        @Test
        @DisplayName("period 포맷 — '30d' 형태로 반환됨")
        void periodFormat() {
            // given
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(30);

            // then
            assertThat(response.getPeriod()).isEqualTo("30d");
        }

        @Test
        @DisplayName("total=0일 때 failureRate=0.0")
        void zeroTotalFailureRate() {
            // given
            List<Object[]> rows = Collections.singletonList(row("Paris, FR", 0L, 0L));
            given(loginHistoryRepository.countLoginsByLocation(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginHeatmapResponse response = loginAnalyticsService.getHeatmap(30);

            // then
            assertThat(response.getHeatmap().get(0).getFailureRate()).isEqualTo(0.0);
        }
    }

    // ─── getFailureHotspot ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFailureHotspot()")
    class GetFailureHotspot {

        @Test
        @DisplayName("정상 집계 — maskIp 적용 및 totalFailures 합산")
        void normalAggregation() {
            // given
            LocalDateTime lastAttempt = LocalDateTime.now().minusHours(1);
            List<Object[]> rows = List.of(
                    rowWithTime("192.168.1.100", "Seoul", 10L, lastAttempt),
                    rowWithTime("10.0.0.200", "Tokyo", 5L, lastAttempt)
            );
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(rows);

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getTotalFailures()).isEqualTo(15L);
            assertThat(response.getPeriod()).isEqualTo("7d");
            assertThat(response.getHotspots()).hasSize(2);

            LoginFailureHotspotResponse.HotspotEntry entry = response.getHotspots().get(0);
            assertThat(entry.getIpAddress()).isEqualTo("192.168.1.*");
            assertThat(entry.getLocation()).isEqualTo("Seoul");
            assertThat(entry.getFailureCount()).isEqualTo(10L);
            assertThat(entry.getLastAttemptAt()).isNotNull();
        }

        @Test
        @DisplayName("lastAttempt가 null이면 lastAttemptAt=null 반환")
        void nullLastAttempt() {
            // given
            List<Object[]> rows = Collections.singletonList(rowWithTime("192.168.1.1", "Seoul", 3L, null));
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(rows);

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getHotspots().get(0).getLastAttemptAt()).isNull();
        }

        @Test
        @DisplayName("빈 결과 — 빈 hotspots 및 totalFailures=0 반환")
        void emptyResult() {
            // given
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(Collections.emptyList());

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getHotspots()).isEmpty();
            assertThat(response.getTotalFailures()).isEqualTo(0L);
        }

        @Test
        @DisplayName("maskIp — '192.168.1.100' → '192.168.1.*'")
        void maskIpNormal() {
            // given
            List<Object[]> rows = Collections.singletonList(rowWithTime("192.168.1.100", "Seoul", 1L, null));
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(rows);

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getHotspots().get(0).getIpAddress()).isEqualTo("192.168.1.*");
        }

        @Test
        @DisplayName("maskIp — null IP → 'Unknown'")
        void maskIpNull() {
            // given
            List<Object[]> rows = Collections.singletonList(rowWithTime(null, "Seoul", 1L, null));
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(rows);

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getHotspots().get(0).getIpAddress()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("maskIp — 빈 문자열 IP → 'Unknown'")
        void maskIpEmpty() {
            // given
            List<Object[]> rows = Collections.singletonList(rowWithTime("", "Seoul", 1L, null));
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(rows);

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getHotspots().get(0).getIpAddress()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("maskIp — dot 없는 IP → 그대로 반환")
        void maskIpNoDot() {
            // given
            List<Object[]> rows = Collections.singletonList(rowWithTime("localhost", "Seoul", 1L, null));
            given(loginHistoryRepository.countFailuresByIp(any(LocalDateTime.class), any(Pageable.class)))
                    .willReturn(rows);

            // when
            LoginFailureHotspotResponse response = loginAnalyticsService.getFailureHotspot(7);

            // then
            assertThat(response.getHotspots().get(0).getIpAddress()).isEqualTo("localhost");
        }
    }

    // ─── getTimeline ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTimeline()")
    class GetTimeline {

        @Test
        @DisplayName("24개 HourlySlot 생성됨")
        void generates24HourlySlots() {
            // given
            given(loginHistoryRepository.countLoginsByHour(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            LoginTimelineResponse response = loginAnalyticsService.getTimeline(30);

            // then
            assertThat(response.getTimeline()).hasSize(24);
            for (int h = 0; h < 24; h++) {
                assertThat(response.getTimeline().get(h).getHour()).isEqualTo(h);
            }
        }

        @Test
        @DisplayName("peakHour 계산 — 가장 많은 시간대")
        void peakHourCalculation() {
            // given
            List<Object[]> rows = List.of(
                    new Object[]{9, 50L, 5L},   // hour=9: total=55
                    new Object[]{14, 80L, 10L},  // hour=14: total=90 (peak)
                    new Object[]{22, 30L, 3L}    // hour=22: total=33
            );
            given(loginHistoryRepository.countLoginsByHour(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginTimelineResponse response = loginAnalyticsService.getTimeline(30);

            // then
            assertThat(response.getPeakHour()).isEqualTo(14);
            assertThat(response.getPeakCount()).isEqualTo(90L);
        }

        @Test
        @DisplayName("빈 결과 — 모든 슬롯이 0, peakHour=0, peakCount=0")
        void emptyResultAllZeros() {
            // given
            given(loginHistoryRepository.countLoginsByHour(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            LoginTimelineResponse response = loginAnalyticsService.getTimeline(30);

            // then
            assertThat(response.getPeakHour()).isEqualTo(0);
            assertThat(response.getPeakCount()).isEqualTo(0L);
            response.getTimeline().forEach(slot -> {
                assertThat(slot.getSuccessCount()).isEqualTo(0L);
                assertThat(slot.getFailureCount()).isEqualTo(0L);
            });
        }

        @Test
        @DisplayName("period 포맷 검증")
        void periodFormat() {
            // given
            given(loginHistoryRepository.countLoginsByHour(any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            LoginTimelineResponse response = loginAnalyticsService.getTimeline(14);

            // then
            assertThat(response.getPeriod()).isEqualTo("14d");
        }

        @Test
        @DisplayName("데이터 슬롯에 success/failure 값이 올바르게 매핑됨")
        void slotDataMappedCorrectly() {
            // given
            List<Object[]> rows = Collections.singletonList(new Object[]{10, 100L, 20L});
            given(loginHistoryRepository.countLoginsByHour(any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            LoginTimelineResponse response = loginAnalyticsService.getTimeline(30);

            // then
            LoginTimelineResponse.HourlySlot slot = response.getTimeline().get(10);
            assertThat(slot.getSuccessCount()).isEqualTo(100L);
            assertThat(slot.getFailureCount()).isEqualTo(20L);
        }
    }

    // ─── getUserLoginMap ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserLoginMap()")
    class GetUserLoginMap {

        @Test
        @DisplayName("정상 집계 — locations 반환")
        void normalAggregation() {
            // given
            LocalDateTime lastLogin = LocalDateTime.now().minusDays(1);
            List<Object[]> rows = List.of(
                    new Object[]{"Seoul", 10L, 2L, lastLogin},
                    new Object[]{"Busan", 5L, 0L, lastLogin}
            );
            given(loginHistoryRepository.countLoginsByLocationForUser(any(Long.class), any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            UserLoginMapResponse response = loginAnalyticsService.getUserLoginMap(1L, 30);

            // then
            assertThat(response.getLocations()).hasSize(2);
            assertThat(response.getPeriod()).isEqualTo("30d");
        }

        @Test
        @DisplayName("successCount 내림차순 정렬됨")
        void sortedBySuccessCountDesc() {
            // given
            LocalDateTime lastLogin = LocalDateTime.now().minusDays(1);
            List<Object[]> rows = List.of(
                    new Object[]{"Busan", 3L, 0L, lastLogin},
                    new Object[]{"Seoul", 10L, 2L, lastLogin},
                    new Object[]{"Incheon", 7L, 1L, lastLogin}
            );
            given(loginHistoryRepository.countLoginsByLocationForUser(any(Long.class), any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            UserLoginMapResponse response = loginAnalyticsService.getUserLoginMap(1L, 30);

            // then
            List<UserLoginMapResponse.LocationEntry> locations = response.getLocations();
            assertThat(locations.get(0).getSuccessCount()).isEqualTo(10L); // Seoul
            assertThat(locations.get(1).getSuccessCount()).isEqualTo(7L);  // Incheon
            assertThat(locations.get(2).getSuccessCount()).isEqualTo(3L);  // Busan
        }

        @Test
        @DisplayName("lastLogin이 null이면 lastLoginAt=null 반환")
        void nullLastLogin() {
            // given
            List<Object[]> rows = Collections.singletonList(new Object[]{"Seoul", 5L, 1L, null});
            given(loginHistoryRepository.countLoginsByLocationForUser(any(Long.class), any(LocalDateTime.class)))
                    .willReturn(rows);

            // when
            UserLoginMapResponse response = loginAnalyticsService.getUserLoginMap(1L, 30);

            // then
            assertThat(response.getLocations().get(0).getLastLoginAt()).isNull();
        }

        @Test
        @DisplayName("빈 결과 — 빈 locations 반환")
        void emptyResult() {
            // given
            given(loginHistoryRepository.countLoginsByLocationForUser(any(Long.class), any(LocalDateTime.class)))
                    .willReturn(Collections.emptyList());

            // when
            UserLoginMapResponse response = loginAnalyticsService.getUserLoginMap(1L, 30);

            // then
            assertThat(response.getLocations()).isEmpty();
            assertThat(response.getPeriod()).isEqualTo("30d");
        }
    }
}
