package com.jay.auth.service;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.security.JwtTokenProvider;
import com.jay.auth.security.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 토큰 발급/갱신/무효화 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    /**
     * 토큰 발급 (로그인 시)
     */
    public TokenResponse issueTokens(Long userId, String userUuid, ChannelCode channelCode) {
        String accessToken = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, userUuid, channelCode);

        // Refresh Token Redis 저장
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);
        long refreshExpiration = jwtTokenProvider.getRefreshTokenExpiration();
        tokenStore.saveRefreshToken(userId, tokenId, refreshToken, refreshExpiration);

        log.info("Issued tokens for user: {}, channelCode: {}", userId, channelCode);

        return TokenResponse.of(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpiration() / 1000  // seconds
        );
    }

    /**
     * 토큰 갱신 (Refresh Token으로 새 Access Token 발급)
     * Refresh Token Rotation 적용: 새 Refresh Token도 함께 발급
     */
    public TokenResponse refreshTokens(String refreshToken) {
        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        // 2. 토큰 타입 확인
        if (jwtTokenProvider.getTokenType(refreshToken) != JwtTokenProvider.TokenType.REFRESH) {
            throw new IllegalArgumentException("Not a refresh token");
        }

        // 3. Redis에서 Refresh Token 존재 확인
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String tokenId = jwtTokenProvider.getTokenId(refreshToken);

        if (!tokenStore.existsRefreshToken(userId, tokenId)) {
            throw new IllegalArgumentException("Refresh token not found or already revoked");
        }

        // 4. 기존 Refresh Token 삭제
        tokenStore.deleteRefreshToken(userId, tokenId);

        // 5. 새 토큰 발급
        String userUuid = jwtTokenProvider.getUserUuid(refreshToken);
        ChannelCode channelCode = jwtTokenProvider.getChannelCode(refreshToken);

        log.info("Refreshed tokens for user: {}", userId);

        return issueTokens(userId, userUuid, channelCode);
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
}
