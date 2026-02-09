package com.jay.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 보안 관련 HTTP 헤더를 자동으로 추가하는 필터
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // XSS 방지
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Clickjacking 방지
        response.setHeader("X-Frame-Options", "DENY");

        // MIME 스니핑 방지
        response.setHeader("Content-Type", response.getContentType());

        // Referrer 정보 제한
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // 권한 정책
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        // 캐시 제어 (API 응답)
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
