package com.jay.auth.service;

import com.jay.auth.dto.response.LoginFailureHotspotResponse;
import com.jay.auth.dto.response.LoginHeatmapResponse;
import com.jay.auth.dto.response.LoginTimelineResponse;
import com.jay.auth.dto.response.UserLoginMapResponse;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoginAnalyticsService {

    private static final Set<String> SUSPICIOUS_COUNTRIES = Set.of("CN", "RU", "KP", "IR");
    private static final double HIGH_FAILURE_RATE_THRESHOLD = 0.5;
    private static final int HOTSPOT_LIMIT = 20;
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeUtil.DEFAULT_FORMATTER;

    private final LoginHistoryRepository loginHistoryRepository;

    /**
     * 국가/도시별 로그인 집계 히트맵 (관리자용)
     */
    @Cacheable(value = "loginHeatmap", key = "#days")
    @Transactional(readOnly = true)
    public LoginHeatmapResponse getHeatmap(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = loginHistoryRepository.countLoginsByLocation(since);

        List<LoginHeatmapResponse.CountryStats> heatmap = new ArrayList<>();
        List<String> suspiciousRegions = new ArrayList<>();

        for (Object[] row : rows) {
            String location = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long failures = ((Number) row[2]).longValue();
            double failureRate = total > 0 ? (double) failures / total : 0.0;

            String[] parts = location.split(",");
            String country = parts.length > 1 ? parts[parts.length - 1].trim() : location;
            String city = parts.length > 1 ? parts[0].trim() : "";

            heatmap.add(LoginHeatmapResponse.CountryStats.builder()
                    .country(country)
                    .city(city)
                    .totalCount(total)
                    .failureCount(failures)
                    .failureRate(Math.round(failureRate * 100.0) / 100.0)
                    .build());

            if (failureRate >= HIGH_FAILURE_RATE_THRESHOLD && !suspiciousRegions.contains(country)) {
                suspiciousRegions.add(country);
            }
            SUSPICIOUS_COUNTRIES.stream()
                    .filter(location::contains)
                    .filter(c -> !suspiciousRegions.contains(c))
                    .forEach(suspiciousRegions::add);
        }

        return LoginHeatmapResponse.builder()
                .heatmap(heatmap)
                .suspiciousRegions(suspiciousRegions)
                .period(days + "d")
                .build();
    }

    /**
     * 실패 시도 집중 IP 핫스팟 (관리자용)
     */
    @Cacheable(value = "loginHotspot", key = "#days")
    @Transactional(readOnly = true)
    public LoginFailureHotspotResponse getFailureHotspot(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = loginHistoryRepository.countFailuresByIp(
                since, PageRequest.of(0, HOTSPOT_LIMIT));

        long totalFailures = 0;
        List<LoginFailureHotspotResponse.HotspotEntry> hotspots = new ArrayList<>();

        for (Object[] row : rows) {
            String ip = (String) row[0];
            String location = (String) row[1];
            long count = ((Number) row[2]).longValue();
            LocalDateTime lastAttempt = (LocalDateTime) row[3];

            totalFailures += count;
            hotspots.add(LoginFailureHotspotResponse.HotspotEntry.builder()
                    .ipAddress(maskIp(ip))
                    .location(location)
                    .failureCount(count)
                    .lastAttemptAt(lastAttempt != null ? lastAttempt.format(DATETIME_FORMAT) : null)
                    .build());
        }

        return LoginFailureHotspotResponse.builder()
                .hotspots(hotspots)
                .totalFailures(totalFailures)
                .period(days + "d")
                .build();
    }

    /**
     * 시간대별 로그인 패턴 타임라인 (관리자용)
     */
    @Cacheable(value = "loginTimeline", key = "#days")
    @Transactional(readOnly = true)
    public LoginTimelineResponse getTimeline(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = loginHistoryRepository.countLoginsByHour(since);

        long[] successByHour = new long[24];
        long[] failureByHour = new long[24];

        for (Object[] row : rows) {
            int hour = ((Number) row[0]).intValue();
            successByHour[hour] = ((Number) row[1]).longValue();
            failureByHour[hour] = ((Number) row[2]).longValue();
        }

        List<LoginTimelineResponse.HourlySlot> timeline = new ArrayList<>();
        int peakHour = 0;
        long peakCount = 0;

        for (int h = 0; h < 24; h++) {
            timeline.add(LoginTimelineResponse.HourlySlot.builder()
                    .hour(h)
                    .successCount(successByHour[h])
                    .failureCount(failureByHour[h])
                    .build());

            long total = successByHour[h] + failureByHour[h];
            if (total > peakCount) {
                peakCount = total;
                peakHour = h;
            }
        }

        return LoginTimelineResponse.builder()
                .timeline(timeline)
                .peakHour(peakHour)
                .peakCount(peakCount)
                .period(days + "d")
                .build();
    }

    /**
     * 본인 로그인 위치 이력 (사용자용)
     */
    @Transactional(readOnly = true)
    public UserLoginMapResponse getUserLoginMap(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = loginHistoryRepository.countLoginsByLocationForUser(userId, since);

        List<UserLoginMapResponse.LocationEntry> locations = new ArrayList<>();

        for (Object[] row : rows) {
            String location = (String) row[0];
            long successCount = ((Number) row[1]).longValue();
            long failureCount = ((Number) row[2]).longValue();
            LocalDateTime lastLogin = (LocalDateTime) row[3];

            locations.add(UserLoginMapResponse.LocationEntry.builder()
                    .location(location)
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .lastLoginAt(lastLogin != null ? lastLogin.format(DATETIME_FORMAT) : null)
                    .build());
        }

        locations.sort(Comparator.comparingLong(UserLoginMapResponse.LocationEntry::getSuccessCount).reversed());

        return UserLoginMapResponse.builder()
                .locations(locations)
                .period(days + "d")
                .build();
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) return "Unknown";
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) + ".*" : ip;
    }
}
