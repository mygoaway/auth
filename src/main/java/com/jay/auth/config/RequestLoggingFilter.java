package com.jay.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * API 요청/응답 로깅 필터
 * - 요청 메서드, URI, 응답 상태, 처리 시간 기록
 * - 헬스체크, 정적 리소스 등 제외
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            String clientIp = getClientIp(request);
            String uriPattern = normalizeUri(request.getRequestURI());

            Timer.builder("http_server_requests_custom")
                    .tag("method", request.getMethod())
                    .tag("uri", uriPattern)
                    .tag("status", String.valueOf(response.getStatus()))
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);

            log.info("HTTP {} {} - {} ({}ms) [IP: {}]",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    clientIp);
        }
    }

    private String normalizeUri(String uri) {
        // Replace numeric path segments with {id} to reduce cardinality
        return uri.replaceAll("/\\d+", "/{id}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/health")
                || path.startsWith("/swagger")
                || path.startsWith("/api-docs")
                || path.startsWith("/favicon");
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
