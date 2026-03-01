package com.jay.auth.config;

import com.jay.auth.service.IpAccessService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP 기반 접근 허용/차단 필터.
 * SecurityHeadersFilter 이후, RateLimitFilter 이전에 실행된다.
 * Redis 캐시를 통해 빠르게 차단 여부를 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpAccessFilter extends OncePerRequestFilter {

    private final IpAccessService ipAccessService;
    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);

        if (ipAccessService.isBlocked(clientIp)) {
            Counter.builder("ip_blocked_total")
                    .tag("reason", "manual_block")
                    .register(meterRegistry)
                    .increment();

            log.warn("Blocked request from IP: {}, path: {}", clientIp, request.getRequestURI());

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"IP_BLOCKED\",\"message\":\"접근이 차단된 IP입니다.\"}}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/health")
                || path.startsWith("/swagger")
                || path.startsWith("/api-docs")
                || path.startsWith("/actuator");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
