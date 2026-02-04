package com.jay.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 인증 필터
 * Authorization 헤더에서 Bearer 토큰을 추출하여 인증 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token) && validateToken(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            String userUuid = jwtTokenProvider.getUserUuid(token);

            // 인증 객체 생성
            UserPrincipal principal = new UserPrincipal(userId, userUuid);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user: {}", userId);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean validateToken(String token) {
        // 1. JWT 유효성 검증
        if (!jwtTokenProvider.validateToken(token)) {
            return false;
        }

        // 2. Access Token인지 확인
        if (jwtTokenProvider.getTokenType(token) != JwtTokenProvider.TokenType.ACCESS) {
            log.debug("Not an access token");
            return false;
        }

        // 3. 블랙리스트 확인
        String tokenId = jwtTokenProvider.getTokenId(token);
        if (tokenStore.isBlacklisted(tokenId)) {
            log.debug("Token is blacklisted");
            return false;
        }

        return true;
    }
}
