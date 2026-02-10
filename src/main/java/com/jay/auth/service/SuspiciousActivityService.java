package com.jay.auth.service;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.dto.response.SuspiciousActivityResponse;
import com.jay.auth.dto.response.SuspiciousActivityResponse.SuspiciousEvent;
import com.jay.auth.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousActivityService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final TrustedDeviceService trustedDeviceService;

    private static final int ANALYSIS_DAYS = 7;
    private static final int FAILED_LOGIN_THRESHOLD = 5;
    private static final int MULTIPLE_LOCATION_THRESHOLD = 3;
    private static final int RAPID_LOGIN_THRESHOLD = 5;
    private static final int RAPID_LOGIN_MINUTES = 10;

    @Transactional(readOnly = true)
    public SuspiciousActivityResponse analyzeRecentActivity(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(ANALYSIS_DAYS);
        LocalDateTime until = LocalDateTime.now().plusMinutes(1);

        List<LoginHistory> histories = loginHistoryRepository.findByUserIdAndPeriod(userId, since, until);
        List<SuspiciousEvent> events = new ArrayList<>();

        detectBruteForceAttempts(histories, events);
        detectMultipleLocations(histories, events);
        detectRapidLogins(histories, events);
        detectUnusualHours(histories, events);
        detectNewDeviceLogin(histories, events);

        int riskScore = calculateRiskScore(events);
        String riskLevel = getRiskLevel(riskScore);
        List<String> recommendations = generateRecommendations(events, riskLevel);

        events.sort((a, b) -> {
            if (b.getDetectedAt() == null) return -1;
            if (a.getDetectedAt() == null) return 1;
            return b.getDetectedAt().compareTo(a.getDetectedAt());
        });

        return SuspiciousActivityResponse.builder()
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .events(events)
                .recommendations(recommendations)
                .build();
    }

    private void detectBruteForceAttempts(List<LoginHistory> histories, List<SuspiciousEvent> events) {
        List<LoginHistory> failed = histories.stream()
                .filter(h -> !h.getIsSuccess())
                .collect(Collectors.toList());

        if (failed.size() >= FAILED_LOGIN_THRESHOLD) {
            Map<String, Long> failedByIp = failed.stream()
                    .collect(Collectors.groupingBy(
                            h -> h.getIpAddress() != null ? h.getIpAddress() : "unknown",
                            Collectors.counting()));

            for (Map.Entry<String, Long> entry : failedByIp.entrySet()) {
                if (entry.getValue() >= FAILED_LOGIN_THRESHOLD) {
                    LoginHistory lastFailed = failed.stream()
                            .filter(h -> entry.getKey().equals(h.getIpAddress()))
                            .findFirst().orElse(failed.get(0));

                    events.add(SuspiciousEvent.builder()
                            .type("BRUTE_FORCE")
                            .severity("HIGH")
                            .description("IP " + entry.getKey() + "에서 " + entry.getValue() + "회 로그인 실패가 감지되었습니다.")
                            .ipAddress(entry.getKey())
                            .location(lastFailed.getLocation())
                            .detectedAt(lastFailed.getCreatedAt())
                            .build());
                }
            }
        }
    }

    private void detectMultipleLocations(List<LoginHistory> histories, List<SuspiciousEvent> events) {
        List<LoginHistory> successful = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .filter(h -> h.getLocation() != null && !h.getLocation().isEmpty())
                .collect(Collectors.toList());

        Set<String> uniqueLocations = successful.stream()
                .map(LoginHistory::getLocation)
                .collect(Collectors.toSet());

        if (uniqueLocations.size() >= MULTIPLE_LOCATION_THRESHOLD) {
            LoginHistory latest = successful.stream().findFirst().orElse(null);
            events.add(SuspiciousEvent.builder()
                    .type("MULTIPLE_LOCATIONS")
                    .severity("MEDIUM")
                    .description(uniqueLocations.size() + "개의 서로 다른 지역에서 로그인이 감지되었습니다: " +
                            String.join(", ", uniqueLocations))
                    .ipAddress(latest != null ? latest.getIpAddress() : null)
                    .location(latest != null ? latest.getLocation() : null)
                    .detectedAt(latest != null ? latest.getCreatedAt() : LocalDateTime.now())
                    .build());
        }
    }

    private void detectRapidLogins(List<LoginHistory> histories, List<SuspiciousEvent> events) {
        List<LoginHistory> successful = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .sorted(Comparator.comparing(LoginHistory::getCreatedAt).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < successful.size(); i++) {
            LocalDateTime windowEnd = successful.get(i).getCreatedAt();
            LocalDateTime windowStart = windowEnd.minusMinutes(RAPID_LOGIN_MINUTES);

            long countInWindow = successful.stream()
                    .filter(h -> !h.getCreatedAt().isBefore(windowStart) && !h.getCreatedAt().isAfter(windowEnd))
                    .count();

            if (countInWindow >= RAPID_LOGIN_THRESHOLD) {
                events.add(SuspiciousEvent.builder()
                        .type("RAPID_LOGIN")
                        .severity("MEDIUM")
                        .description(RAPID_LOGIN_MINUTES + "분 이내에 " + countInWindow + "회 로그인이 감지되었습니다.")
                        .ipAddress(successful.get(i).getIpAddress())
                        .location(successful.get(i).getLocation())
                        .detectedAt(successful.get(i).getCreatedAt())
                        .build());
                break;
            }
        }
    }

    private void detectUnusualHours(List<LoginHistory> histories, List<SuspiciousEvent> events) {
        List<LoginHistory> lateNightLogins = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .filter(h -> {
                    int hour = h.getCreatedAt().getHour();
                    return hour >= 1 && hour <= 5;
                })
                .collect(Collectors.toList());

        if (!lateNightLogins.isEmpty()) {
            LoginHistory latest = lateNightLogins.get(0);
            events.add(SuspiciousEvent.builder()
                    .type("UNUSUAL_HOURS")
                    .severity("LOW")
                    .description("새벽 시간대(01:00~05:00)에 " + lateNightLogins.size() + "회 로그인이 감지되었습니다.")
                    .ipAddress(latest.getIpAddress())
                    .location(latest.getLocation())
                    .detectedAt(latest.getCreatedAt())
                    .build());
        }
    }

    private void detectNewDeviceLogin(List<LoginHistory> histories, List<SuspiciousEvent> events) {
        Map<String, LoginHistory> deviceFirstSeen = new LinkedHashMap<>();

        List<LoginHistory> sorted = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .sorted(Comparator.comparing(LoginHistory::getCreatedAt))
                .collect(Collectors.toList());

        for (LoginHistory h : sorted) {
            String deviceKey = (h.getBrowser() != null ? h.getBrowser() : "") + "|"
                    + (h.getOs() != null ? h.getOs() : "") + "|"
                    + (h.getDeviceType() != null ? h.getDeviceType() : "");
            deviceFirstSeen.putIfAbsent(deviceKey, h);
        }

        LocalDateTime recentThreshold = LocalDateTime.now().minusDays(1);
        for (Map.Entry<String, LoginHistory> entry : deviceFirstSeen.entrySet()) {
            LoginHistory h = entry.getValue();
            if (h.getCreatedAt().isAfter(recentThreshold)) {
                events.add(SuspiciousEvent.builder()
                        .type("NEW_DEVICE")
                        .severity("LOW")
                        .description("새로운 기기에서 로그인: " + h.getBrowser() + " / " + h.getOs())
                        .ipAddress(h.getIpAddress())
                        .location(h.getLocation())
                        .detectedAt(h.getCreatedAt())
                        .build());
            }
        }
    }

    private int calculateRiskScore(List<SuspiciousEvent> events) {
        int score = 0;
        for (SuspiciousEvent event : events) {
            switch (event.getSeverity()) {
                case "HIGH" -> score += 30;
                case "MEDIUM" -> score += 15;
                case "LOW" -> score += 5;
            }
        }
        return Math.min(score, 100);
    }

    private String getRiskLevel(int score) {
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        if (score > 0) return "LOW";
        return "SAFE";
    }

    private List<String> generateRecommendations(List<SuspiciousEvent> events, String riskLevel) {
        List<String> recommendations = new ArrayList<>();

        boolean hasBruteForce = events.stream().anyMatch(e -> "BRUTE_FORCE".equals(e.getType()));
        boolean hasMultipleLocations = events.stream().anyMatch(e -> "MULTIPLE_LOCATIONS".equals(e.getType()));

        if (hasBruteForce) {
            recommendations.add("비밀번호를 즉시 변경해주세요.");
            recommendations.add("2단계 인증(2FA)을 활성화하면 무차별 대입 공격을 방어할 수 있습니다.");
        }

        if (hasMultipleLocations) {
            recommendations.add("여러 지역에서의 로그인이 본인 활동인지 확인해주세요.");
            recommendations.add("의심스러운 세션이 있다면 '전체 로그아웃'을 수행해주세요.");
        }

        if ("HIGH".equals(riskLevel) || "MEDIUM".equals(riskLevel)) {
            recommendations.add("신뢰할 수 있는 기기를 등록하여 보안을 강화하세요.");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("최근 7일간 특이한 보안 위협이 감지되지 않았습니다.");
        }

        return recommendations;
    }
}
