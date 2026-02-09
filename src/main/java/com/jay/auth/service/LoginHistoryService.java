package com.jay.auth.service;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.LoginHistoryResponse;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.security.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final GeoIpService geoIpService;

    /**
     * Record successful login
     * HttpServletRequest에서 필요한 정보를 동기적으로 추출 후 비동기 저장
     */
    public void recordLoginSuccess(Long userId, ChannelCode channelCode, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        saveLoginHistoryAsync(userId, channelCode, clientIp, userAgent, true, null);
    }

    /**
     * Record failed login attempt
     * HttpServletRequest에서 필요한 정보를 동기적으로 추출 후 비동기 저장
     */
    public void recordLoginFailure(Long userId, ChannelCode channelCode, String reason, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        saveLoginHistoryAsync(userId, channelCode, clientIp, userAgent, false, reason);
    }

    @Async
    @Transactional
    public void saveLoginHistoryAsync(Long userId, ChannelCode channelCode,
                                       String clientIp, String userAgent,
                                       boolean isSuccess, String failureReason) {
        String location = geoIpService.getLocation(clientIp);

        LoginHistory history = LoginHistory.builder()
                .userId(userId)
                .channelCode(channelCode)
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .deviceType(parseDeviceType(userAgent))
                .browser(parseBrowser(userAgent))
                .os(parseOs(userAgent))
                .location(location)
                .isSuccess(isSuccess)
                .failureReason(failureReason)
                .build();

        loginHistoryRepository.save(history);
        log.debug("Login history recorded: userId={}, channelCode={}, success={}", userId, channelCode, isSuccess);
    }

    /**
     * Get recent login history for user
     */
    @Transactional(readOnly = true)
    public List<LoginHistoryResponse> getRecentLoginHistory(Long userId, int limit) {
        List<LoginHistory> histories = loginHistoryRepository.findRecentByUserId(
                userId, PageRequest.of(0, limit));

        return histories.stream()
                .map(LoginHistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get count of failed login attempts since a time
     */
    @Transactional(readOnly = true)
    public long getFailedLoginCountSince(Long userId, LocalDateTime since) {
        return loginHistoryRepository.countFailedLoginsSince(userId, since);
    }

    /**
     * Extract session info from HTTP request
     */
    public TokenStore.SessionInfo extractSessionInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIp(request);
        String location = geoIpService.getLocation(clientIp);

        return new TokenStore.SessionInfo(
                parseDeviceType(userAgent),
                parseBrowser(userAgent),
                parseOs(userAgent),
                clientIp,
                location
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // If multiple IPs (from proxy), take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null) return "Unknown";
        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("mobile") || userAgent.contains("android") || userAgent.contains("iphone")) {
            return "Mobile";
        } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("chrome") && !userAgent.contains("edg")) {
            return "Chrome";
        } else if (userAgent.contains("firefox")) {
            return "Firefox";
        } else if (userAgent.contains("safari") && !userAgent.contains("chrome")) {
            return "Safari";
        } else if (userAgent.contains("edg")) {
            return "Edge";
        } else if (userAgent.contains("opera") || userAgent.contains("opr")) {
            return "Opera";
        }
        return "Unknown";
    }

    private String parseOs(String userAgent) {
        if (userAgent == null) return "Unknown";
        userAgent = userAgent.toLowerCase();
        if (userAgent.contains("windows")) {
            return "Windows";
        } else if (userAgent.contains("mac os")) {
            return "macOS";
        } else if (userAgent.contains("linux")) {
            return "Linux";
        } else if (userAgent.contains("android")) {
            return "Android";
        } else if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
            return "iOS";
        }
        return "Unknown";
    }
}
