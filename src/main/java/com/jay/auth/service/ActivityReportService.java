package com.jay.auth.service;

import com.jay.auth.domain.entity.AuditLog;
import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.dto.response.WeeklyActivityResponse;
import com.jay.auth.dto.response.WeeklyActivityResponse.*;
import com.jay.auth.repository.AuditLogRepository;
import com.jay.auth.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityReportService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public WeeklyActivityResponse getWeeklyReport(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDateTime since = weekStart.atStartOfDay();
        LocalDateTime until = today.plusDays(1).atStartOfDay();

        return buildReport(userId, since, until);
    }

    @Transactional(readOnly = true)
    public WeeklyActivityResponse getWeeklyReport(Long userId, LocalDate targetWeekStart) {
        LocalDate weekStart = targetWeekStart.with(DayOfWeek.MONDAY);
        LocalDateTime since = weekStart.atStartOfDay();
        LocalDateTime until = weekStart.plusWeeks(1).atTime(LocalTime.MAX);

        return buildReport(userId, since, until);
    }

    private WeeklyActivityResponse buildReport(Long userId, LocalDateTime since, LocalDateTime until) {
        List<LoginHistory> loginHistories = loginHistoryRepository.findByUserIdAndPeriod(userId, since, until);
        List<AuditLog> auditLogs = auditLogRepository.findByUserIdAndPeriod(userId, since, until);

        LoginStats loginStats = buildLoginStats(loginHistories);
        List<DeviceBreakdown> deviceBreakdown = buildDeviceBreakdown(loginHistories);
        List<LocationInfo> locations = buildLocationInfo(loginHistories);
        List<AccountEvent> accountEvents = buildAccountEvents(auditLogs);
        List<String> securityAlerts = buildSecurityAlerts(loginHistories);

        return WeeklyActivityResponse.builder()
                .weekStart(since)
                .weekEnd(until)
                .loginStats(loginStats)
                .deviceBreakdown(deviceBreakdown)
                .locations(locations)
                .accountEvents(accountEvents)
                .securityAlerts(securityAlerts)
                .build();
    }

    private LoginStats buildLoginStats(List<LoginHistory> histories) {
        long success = histories.stream().filter(LoginHistory::getIsSuccess).count();
        long failed = histories.size() - success;

        Map<String, Long> byChannel = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .collect(Collectors.groupingBy(
                        h -> h.getChannelCode().name(),
                        Collectors.counting()));

        List<ChannelCount> channelCounts = byChannel.entrySet().stream()
                .map(e -> ChannelCount.builder().channel(e.getKey()).count(e.getValue()).build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        return LoginStats.builder()
                .totalLogins(histories.size())
                .successfulLogins((int) success)
                .failedLogins((int) failed)
                .loginsByChannel(channelCounts)
                .build();
    }

    private List<DeviceBreakdown> buildDeviceBreakdown(List<LoginHistory> histories) {
        return histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .collect(Collectors.groupingBy(
                        h -> h.getDeviceType() + "|" + h.getBrowser() + "|" + h.getOs(),
                        Collectors.counting()))
                .entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|", -1);
                    return DeviceBreakdown.builder()
                            .deviceType(parts[0])
                            .browser(parts[1])
                            .os(parts[2])
                            .count(e.getValue())
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    private List<LocationInfo> buildLocationInfo(List<LoginHistory> histories) {
        return histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .filter(h -> h.getLocation() != null && !h.getLocation().isEmpty())
                .collect(Collectors.groupingBy(LoginHistory::getLocation, Collectors.counting()))
                .entrySet().stream()
                .map(e -> LocationInfo.builder().location(e.getKey()).count(e.getValue()).build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    private List<AccountEvent> buildAccountEvents(List<AuditLog> auditLogs) {
        return auditLogs.stream()
                .map(log -> AccountEvent.builder()
                        .action(log.getAction())
                        .detail(log.getDetail())
                        .occurredAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> buildSecurityAlerts(List<LoginHistory> histories) {
        List<String> alerts = new ArrayList<>();

        long failedCount = histories.stream().filter(h -> !h.getIsSuccess()).count();
        if (failedCount >= 5) {
            alerts.add("이번 주 로그인 실패가 " + failedCount + "회 발생했습니다. 비밀번호 변경을 권장합니다.");
        }

        long uniqueIps = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .map(LoginHistory::getIpAddress)
                .distinct().count();
        if (uniqueIps >= 5) {
            alerts.add("이번 주 " + uniqueIps + "개의 서로 다른 IP에서 로그인이 감지되었습니다.");
        }

        long uniqueLocations = histories.stream()
                .filter(LoginHistory::getIsSuccess)
                .map(LoginHistory::getLocation)
                .filter(l -> l != null && !l.isEmpty())
                .distinct().count();
        if (uniqueLocations >= 3) {
            alerts.add("이번 주 " + uniqueLocations + "개 지역에서 로그인이 감지되었습니다. 본인 활동이 아니라면 비밀번호를 변경해주세요.");
        }

        if (alerts.isEmpty()) {
            alerts.add("이번 주 특이 보안 이슈가 없습니다.");
        }

        return alerts;
    }
}
