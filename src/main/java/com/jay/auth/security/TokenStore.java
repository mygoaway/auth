package com.jay.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private static final String SESSION_PREFIX = "session:";
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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
     * 세션 정보와 함께 Refresh Token 저장
     */
    public void saveRefreshTokenWithSession(Long userId, String tokenId, String refreshToken,
            long expirationMs, SessionInfo sessionInfo) {
        // Save refresh token
        String tokenKey = buildRefreshTokenKey(userId, tokenId);
        redisTemplate.opsForValue().set(tokenKey, refreshToken, expirationMs, TimeUnit.MILLISECONDS);

        // Save session info as Hash
        String sessionKey = buildSessionKey(userId, tokenId);
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("deviceType", sessionInfo.deviceType() != null ? sessionInfo.deviceType() : "UNKNOWN");
        sessionData.put("browser", sessionInfo.browser() != null ? sessionInfo.browser() : "UNKNOWN");
        sessionData.put("os", sessionInfo.os() != null ? sessionInfo.os() : "UNKNOWN");
        sessionData.put("ipAddress", sessionInfo.ipAddress() != null ? sessionInfo.ipAddress() : "");
        sessionData.put("location", sessionInfo.location() != null ? sessionInfo.location() : "");
        sessionData.put("lastActivity", LocalDateTime.now().format(DATE_FORMATTER));

        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, expirationMs, TimeUnit.MILLISECONDS);

        log.debug("Saved session for user: {}, tokenId: {}", userId, tokenId);
    }

    /**
     * 세션 정보 레코드
     */
    public record SessionInfo(String deviceType, String browser, String os, String ipAddress, String location) {}

    /**
     * 사용자의 모든 활성 세션 조회
     */
    public List<Map<String, String>> getAllSessions(Long userId) {
        String pattern = SESSION_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        List<Map<String, String>> sessions = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                Map<Object, Object> rawData = redisTemplate.opsForHash().entries(key);
                if (!rawData.isEmpty()) {
                    Map<String, String> sessionData = new HashMap<>();
                    // Extract tokenId from key: session:{userId}:{tokenId}
                    String tokenId = key.substring(key.lastIndexOf(":") + 1);
                    sessionData.put("sessionId", tokenId);
                    rawData.forEach((k, v) -> sessionData.put(k.toString(), v.toString()));
                    sessions.add(sessionData);
                }
            }
        }

        // Sort by lastActivity descending
        sessions.sort((a, b) -> {
            String timeA = a.get("lastActivity");
            String timeB = b.get("lastActivity");
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });

        return sessions;
    }

    /**
     * 세션 마지막 활동 시간 갱신
     */
    public void updateSessionActivity(Long userId, String tokenId) {
        String sessionKey = buildSessionKey(userId, tokenId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
            redisTemplate.opsForHash().put(sessionKey, "lastActivity", LocalDateTime.now().format(DATE_FORMATTER));
        }
    }

    /**
     * 특정 세션 삭제
     */
    public void revokeSession(Long userId, String tokenId) {
        deleteRefreshToken(userId, tokenId);
        String sessionKey = buildSessionKey(userId, tokenId);
        redisTemplate.delete(sessionKey);
        log.debug("Revoked session for user: {}, tokenId: {}", userId, tokenId);
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
     * 사용자의 모든 Refresh Token 및 세션 삭제 (전체 로그아웃)
     */
    public void deleteAllRefreshTokens(Long userId) {
        // Delete all refresh tokens
        String tokenPattern = REFRESH_TOKEN_PREFIX + userId + ":*";
        Set<String> tokenKeys = redisTemplate.keys(tokenPattern);
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
            log.debug("Deleted all refresh tokens for user: {}, count: {}", userId, tokenKeys.size());
        }

        // Delete all session info
        String sessionPattern = SESSION_PREFIX + userId + ":*";
        Set<String> sessionKeys = redisTemplate.keys(sessionPattern);
        if (sessionKeys != null && !sessionKeys.isEmpty()) {
            redisTemplate.delete(sessionKeys);
            log.debug("Deleted all sessions for user: {}, count: {}", userId, sessionKeys.size());
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

    private String buildSessionKey(Long userId, String tokenId) {
        return SESSION_PREFIX + userId + ":" + tokenId;
    }

    private String buildBlacklistKey(String tokenId) {
        return BLACKLIST_PREFIX + tokenId;
    }
}
