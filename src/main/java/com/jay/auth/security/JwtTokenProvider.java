package com.jay.auth.security;

import com.jay.auth.config.AppProperties;
import com.jay.auth.domain.enums.ChannelCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private SecretKey secretKey;

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USER_UUID = "userUuid";
    private static final String CLAIM_CHANNEL_CODE = "channelCode";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_TOKEN_ID = "jti";

    public enum TokenType {
        ACCESS, REFRESH
    }

    @PostConstruct
    public void init() {
        String secret = appProperties.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access Token 생성
     */
    public String createAccessToken(Long userId, String userUuid, ChannelCode channelCode) {
        return createToken(userId, userUuid, channelCode, TokenType.ACCESS,
                appProperties.getJwt().getAccessTokenExpiration());
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(Long userId, String userUuid, ChannelCode channelCode) {
        return createToken(userId, userUuid, channelCode, TokenType.REFRESH,
                appProperties.getJwt().getRefreshTokenExpiration());
    }

    private String createToken(Long userId, String userUuid, ChannelCode channelCode,
                               TokenType tokenType, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USER_UUID, userUuid)
                .claim(CLAIM_CHANNEL_CODE, channelCode.name())
                .claim(CLAIM_TOKEN_TYPE, tokenType.name())
                .claim(CLAIM_TOKEN_ID, tokenId)
                .issuer(appProperties.getJwt().getIssuer())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 토큰 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 토큰에서 Claims 추출
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    /**
     * 토큰에서 사용자 UUID 추출
     */
    public String getUserUuid(String token) {
        Claims claims = getClaims(token);
        return claims.get(CLAIM_USER_UUID, String.class);
    }

    /**
     * 토큰에서 채널 코드 추출
     */
    public ChannelCode getChannelCode(String token) {
        Claims claims = getClaims(token);
        String channelCode = claims.get(CLAIM_CHANNEL_CODE, String.class);
        return ChannelCode.valueOf(channelCode);
    }

    /**
     * 토큰에서 토큰 타입 추출
     */
    public TokenType getTokenType(String token) {
        Claims claims = getClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        return TokenType.valueOf(tokenType);
    }

    /**
     * 토큰에서 토큰 ID (jti) 추출
     */
    public String getTokenId(String token) {
        Claims claims = getClaims(token);
        return claims.get(CLAIM_TOKEN_ID, String.class);
    }

    /**
     * 토큰 만료 시간 추출
     */
    public Date getExpiration(String token) {
        Claims claims = getClaims(token);
        return claims.getExpiration();
    }

    /**
     * 토큰 남은 만료 시간 (밀리초)
     */
    public long getRemainingExpiration(String token) {
        Date expiration = getExpiration(token);
        return expiration.getTime() - System.currentTimeMillis();
    }

    /**
     * Access Token 만료 시간 반환
     */
    public long getAccessTokenExpiration() {
        return appProperties.getJwt().getAccessTokenExpiration();
    }

    /**
     * Refresh Token 만료 시간 반환
     */
    public long getRefreshTokenExpiration() {
        return appProperties.getJwt().getRefreshTokenExpiration();
    }
}
