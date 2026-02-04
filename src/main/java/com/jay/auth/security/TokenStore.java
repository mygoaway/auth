package com.jay.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 토큰 저장소
 * - Refresh Token 저장/조회/삭제
 * - Access Token 블랙리스트 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenStore {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    /**
     * Refresh Token 저장
     * Key: refresh:{userId}:{tokenId}
     */
    public void saveRefreshToken(Long userId, String tokenId, String refreshToken, long expirationMs) {
        String key = buildRefreshTokenKey(userId, tokenId);
        redisTemplate.opsForValue().set(key, refreshToken, expirationMs, TimeUnit.MILLISECONDS);
        log.debug("Saved refresh token for user: {}, tokenId: {}", userId, tokenId);
    }

    /**
     * Refresh Token 조회
     */
    public String getRefreshToken(Long userId, String tokenId) {
        String key = buildRefreshTokenKey(userId, tokenId);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Refresh Token 존재 여부 확인
     */
    public boolean existsRefreshToken(Long userId, String tokenId) {
        String key = buildRefreshTokenKey(userId, tokenId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Refresh Token 삭제 (단일)
     */
    public void deleteRefreshToken(Long userId, String tokenId) {
        String key = buildRefreshTokenKey(userId, tokenId);
        redisTemplate.delete(key);
        log.debug("Deleted refresh token for user: {}, tokenId: {}", userId, tokenId);
    }

    /**
     * 사용자의 모든 Refresh Token 삭제 (전체 로그아웃)
     */
    public void deleteAllRefreshTokens(Long userId) {
        String pattern = REFRESH_TOKEN_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Deleted all refresh tokens for user: {}, count: {}", userId, keys.size());
        }
    }

    /**
     * Access Token 블랙리스트 등록 (로그아웃 시)
     * Key: blacklist:{tokenId}
     */
    public void addToBlacklist(String tokenId, long remainingExpirationMs) {
        if (remainingExpirationMs <= 0) {
            return;
        }
        String key = buildBlacklistKey(tokenId);
        redisTemplate.opsForValue().set(key, "1", remainingExpirationMs, TimeUnit.MILLISECONDS);
        log.debug("Added token to blacklist: {}", tokenId);
    }

    /**
     * Access Token 블랙리스트 확인
     */
    public boolean isBlacklisted(String tokenId) {
        String key = buildBlacklistKey(tokenId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String buildRefreshTokenKey(Long userId, String tokenId) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + tokenId;
    }

    private String buildBlacklistKey(String tokenId) {
        return BLACKLIST_PREFIX + tokenId;
    }
}
