package com.jay.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 전역 API Rate Limiting 필터
 * IP 기반으로 분당 요청 수를 제한
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate:api:";
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    // 인증 관련 엔드포인트는 더 엄격한 제한
    private static final String AUTH_RATE_LIMIT_PREFIX = "rate:auth:";
    private static final int MAX_AUTH_REQUESTS_PER_MINUTE = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();

        boolean isAuthEndpoint = path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/password/reset");

        String key;
        int maxRequests;

        if (isAuthEndpoint) {
            key = AUTH_RATE_LIMIT_PREFIX + clientIp;
            maxRequests = MAX_AUTH_REQUESTS_PER_MINUTE;
        } else {
            key = RATE_LIMIT_PREFIX + clientIp;
            maxRequests = MAX_REQUESTS_PER_MINUTE;
        }

        int currentCount = incrementAndGet(key);

        // Rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - currentCount)));

        if (currentCount > maxRequests) {
            Long ttl = redisTemplate.getExpire(key);
            long retryAfter = ttl != null && ttl > 0 ? ttl : 60;

            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"요청이 너무 많습니다. " + retryAfter + "초 후 다시 시도해주세요.\"}");

            log.warn("Rate limit exceeded: IP={}, path={}, count={}", clientIp, path, currentCount);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/health")
                || path.startsWith("/swagger")
                || path.startsWith("/api-docs");
    }

    private int incrementAndGet(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, WINDOW);
            }
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Rate limit check failed: {}", e.getMessage());
            return 0; // Redis 장애 시 rate limit 비활성화
        }
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
