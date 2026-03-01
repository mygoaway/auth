package com.jay.auth.service;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.ActiveSessionResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.exception.InvalidTokenException;
import com.jay.auth.security.JwtTokenProvider;
import com.jay.auth.security.TokenStore;
import com.jay.auth.service.metrics.AuthGaugeMetrics;
import com.jay.auth.service.metrics.AuthMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.jay.auth.util.DateTimeUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 토큰 발급/갱신/무효화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;
    private final AuthMetrics authMetrics;
    private final AuthGaugeMetrics authGaugeMetrics;
    private final MeterRegistry meterRegistry;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeUtil.ISO_FORMATTER;

    /**
     * 토큰 발급 (로그인 시)
     */
    public TokenResponse issueTokens(Long userId, String userUuid, ChannelCode channelCode, String role) {
        String accessToken = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, userUuid, channelCode, role);

        // Refresh Token Redis 저장
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);
        long refreshExpiration = jwtTokenProvider.getRefreshTokenExpiration();
        tokenStore.saveRefreshToken(userId, tokenId, refreshToken, refreshExpiration);

        log.info("Issued tokens for user: {}, channelCode: {}", userId, channelCode);
        authMetrics.recordTokenIssued("ACCESS", channelCode.name());
        authMetrics.recordTokenIssued("REFRESH", channelCode.name());
        authGaugeMetrics.incrementActiveSessions();

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiration() / 1000  // seconds
        );
    }

    /**
     * 토큰 발급 (세션 정보 포함)
     */
    public TokenResponse issueTokensWithSession(Long userId, String userUuid, ChannelCode channelCode,
            TokenStore.SessionInfo sessionInfo) {
        return issueTokensWithSession(userId, userUuid, channelCode, "USER", sessionInfo);
    }

    /**
     * 토큰 발급 (세션 정보 + 역할 포함)
     */
    public TokenResponse issueTokensWithSession(Long userId, String userUuid, ChannelCode channelCode,
            String role, TokenStore.SessionInfo sessionInfo) {
        String accessToken = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, userUuid, channelCode, role);

        // Refresh Token + Session 저장
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);
        long refreshExpiration = jwtTokenProvider.getRefreshTokenExpiration();
        tokenStore.saveRefreshTokenWithSession(userId, tokenId, refreshToken, refreshExpiration, sessionInfo);

        log.info("Issued tokens with session for user: {}, channelCode: {}", userId, channelCode);
        authMetrics.recordTokenIssued("ACCESS", channelCode.name());
        authMetrics.recordTokenIssued("REFRESH", channelCode.name());
        authGaugeMetrics.incrementActiveSessions();

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiration() / 1000
        );
    }

    /**
     * 토큰 갱신 (Refresh Token으로 새 Access Token 발급)
     * Refresh Token Rotation 적용: 새 Refresh Token도 함께 발급
     */
    public TokenResponse refreshTokens(String refreshToken) {
        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            authMetrics.recordTokenRefreshFailure();
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다");
        }

        // 2. 토큰 타입 확인
        if (jwtTokenProvider.getTokenType(refreshToken) != JwtTokenProvider.TokenType.REFRESH) {
            authMetrics.recordTokenRefreshFailure();
            throw new InvalidTokenException("리프레시 토큰이 아닙니다");
        }

        // 3. Redis에서 Refresh Token 존재 확인
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);

        if (!tokenStore.existsRefreshToken(userId, tokenId)) {
            // 이미 삭제된 tokenId로 재요청 → 토큰 탈취 후 재사용 시도 가능성
            Counter.builder("token_reuse_detected_total")
                    .description("만료/삭제된 리프레시 토큰 재사용 감지")
                    .register(meterRegistry)
                    .increment();
            log.warn("Refresh token reuse detected — revoking all sessions: userId={}, tokenId={}", userId, tokenId);
            tokenStore.deleteAllRefreshTokens(userId);
            authMetrics.recordTokenRefreshFailure();
            throw new InvalidTokenException("리프레시 토큰이 존재하지 않거나 이미 만료되었습니다");
        }

        // 4. 기존 Refresh Token 즉시 블랙리스트 등록 후 삭제 (재사용 방지)
        long remainingMs = jwtTokenProvider.getRemainingExpiration(refreshToken);
        if (remainingMs > 0) {
            tokenStore.addToBlacklist(tokenId, remainingMs);
        }
        tokenStore.deleteRefreshToken(userId, tokenId);

        Counter.builder("token_rotation_total")
                .description("리프레시 토큰 순환 횟수")
                .register(meterRegistry)
                .increment();

        // 5. 새 토큰 발급
        String userUuid = jwtTokenProvider.getUserUuid(refreshToken);
        ChannelCode channelCode = jwtTokenProvider.getChannelCode(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        log.info("Refreshed tokens for user: {}", userId);
        authMetrics.recordTokenRefreshSuccess();

        return issueTokens(userId, userUuid, channelCode, role);
    }

    /**
     * 로그아웃 (단일 세션)
     */
    public void logout(String accessToken, String refreshToken) {
        // Access Token 블랙리스트 등록
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            String accessTokenId = jwtTokenProvider.getTokenId(accessToken);
            long remainingExpiration = jwtTokenProvider.getRemainingExpiration(accessToken);
            tokenStore.addToBlacklist(accessTokenId, remainingExpiration);
        }

        // Refresh Token 삭제
        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            Long userId = jwtTokenProvider.getUserId(refreshToken);
            String tokenId = jwtTokenProvider.getTokenId(refreshToken);
            tokenStore.deleteRefreshToken(userId, tokenId);
        }

        log.info("User logged out");
        authMetrics.recordLogout("single");
        authGaugeMetrics.decrementActiveSessions();
    }

    /**
     * 전체 로그아웃 (모든 세션)
     */
    public void logoutAll(Long userId, String currentAccessToken) {
        // 현재 Access Token 블랙리스트 등록
        if (currentAccessToken != null && jwtTokenProvider.validateToken(currentAccessToken)) {
            String accessTokenId = jwtTokenProvider.getTokenId(currentAccessToken);
            long remainingExpiration = jwtTokenProvider.getRemainingExpiration(currentAccessToken);
            tokenStore.addToBlacklist(accessTokenId, remainingExpiration);
        }

        // 모든 Refresh Token 삭제
        tokenStore.deleteAllRefreshTokens(userId);

        log.info("User {} logged out from all sessions", userId);
        authMetrics.recordLogout("all");
        // 전체 로그아웃은 몇 개 세션이 삭제됐는지 모르므로 주기적 sync에서 보정됨
        authGaugeMetrics.decrementActiveSessions();
    }

    /**
     * Access Token 유효성 검증 (블랙리스트 포함)
     */
    public boolean validateAccessToken(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            return false;
        }

        // 블랙리스트 확인
        String tokenId = jwtTokenProvider.getTokenId(accessToken);
        if (tokenStore.isBlacklisted(tokenId)) {
            log.debug("Token is blacklisted: {}", tokenId);
            return false;
        }

        return true;
    }

    /**
     * 활성 세션 목록 조회
     */
    public List<ActiveSessionResponse> getActiveSessions(Long userId, String currentTokenId) {
        List<Map<String, String>> sessions = tokenStore.getAllSessions(userId);

        return sessions.stream()
                .map(session -> {
                    String sessionId = session.get("sessionId");
                    LocalDateTime lastActivity = null;
                    String lastActivityStr = session.get("lastActivity");
                    if (lastActivityStr != null && !lastActivityStr.isEmpty()) {
                        try {
                            lastActivity = LocalDateTime.parse(lastActivityStr, DATE_FORMATTER);
                        } catch (Exception e) {
                            log.warn("Failed to parse lastActivity: {}", lastActivityStr);
                        }
                    }

                    return ActiveSessionResponse.of(
                            sessionId,
                            session.get("deviceType"),
                            session.get("browser"),
                            session.get("os"),
                            session.get("ipAddress"),
                            session.get("location"),
                            lastActivity,
                            sessionId != null && sessionId.equals(currentTokenId)
                    );
                })
                .toList();
    }

    /**
     * 특정 세션 종료 (원격 로그아웃)
     */
    public void revokeSession(Long userId, String sessionId) {
        tokenStore.revokeSession(userId, sessionId);
        log.info("Revoked session {} for user {}", sessionId, userId);
    }

    /**
     * 현재 토큰의 토큰 ID 추출
     */
    public String getTokenId(String accessToken) {
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            return jwtTokenProvider.getTokenId(accessToken);
        }
        return null;
    }

    /**
     * 세션 활동 시간 갱신
     */
    public void updateSessionActivity(Long userId, String tokenId) {
        tokenStore.updateSessionActivity(userId, tokenId);
    }
}
