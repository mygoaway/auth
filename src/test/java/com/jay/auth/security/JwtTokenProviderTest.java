package com.jay.auth.security;

import com.jay.auth.config.AppProperties;
import com.jay.auth.domain.enums.ChannelCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        AppProperties.Jwt jwt = new AppProperties.Jwt();
        jwt.setSecret("test-jwt-secret-key-must-be-at-least-32-characters-long");
        jwt.setAccessTokenExpiration(1800000L);  // 30분
        jwt.setRefreshTokenExpiration(1209600000L);  // 14일
        jwt.setIssuer("auth-service-test");
        appProperties.setJwt(jwt);

        jwtTokenProvider = new JwtTokenProvider(appProperties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("Access Token 생성 및 검증")
    void createAndValidateAccessToken() {
        // given
        Long userId = 1L;
        String userUuid = "test-uuid-1234";
        ChannelCode channelCode = ChannelCode.EMAIL;

        // when
        String token = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode);

        // then
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getUserUuid(token)).isEqualTo(userUuid);
        assertThat(jwtTokenProvider.getChannelCode(token)).isEqualTo(channelCode);
        assertThat(jwtTokenProvider.getTokenType(token)).isEqualTo(JwtTokenProvider.TokenType.ACCESS);
    }

    @Test
    @DisplayName("Refresh Token 생성 및 검증")
    void createAndValidateRefreshToken() {
        // given
        Long userId = 1L;
        String userUuid = "test-uuid-1234";
        ChannelCode channelCode = ChannelCode.GOOGLE;

        // when
        String token = jwtTokenProvider.createRefreshToken(userId, userUuid, channelCode);

        // then
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getTokenType(token)).isEqualTo(JwtTokenProvider.TokenType.REFRESH);
    }

    @Test
    @DisplayName("토큰 ID가 매번 다르게 생성되어야 한다")
    void tokenIdShouldBeUnique() {
        // given
        Long userId = 1L;
        String userUuid = "test-uuid-1234";
        ChannelCode channelCode = ChannelCode.EMAIL;

        // when
        String token1 = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode);
        String token2 = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode);

        // then
        String tokenId1 = jwtTokenProvider.getTokenId(token1);
        String tokenId2 = jwtTokenProvider.getTokenId(token2);
        assertThat(tokenId1).isNotEqualTo(tokenId2);
    }

    @Test
    @DisplayName("잘못된 토큰은 검증에 실패해야 한다")
    void invalidTokenShouldFail() {
        assertThat(jwtTokenProvider.validateToken("invalid.token.here")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("토큰 만료 시간이 올바르게 설정되어야 한다")
    void tokenExpirationShouldBeCorrect() {
        // given
        Long userId = 1L;
        String userUuid = "test-uuid-1234";
        ChannelCode channelCode = ChannelCode.EMAIL;

        // when
        String accessToken = jwtTokenProvider.createAccessToken(userId, userUuid, channelCode);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, userUuid, channelCode);

        // then
        long accessRemaining = jwtTokenProvider.getRemainingExpiration(accessToken);
        long refreshRemaining = jwtTokenProvider.getRemainingExpiration(refreshToken);

        // Access Token: 30분 이내
        assertThat(accessRemaining).isGreaterThan(0).isLessThanOrEqualTo(1800000L);
        // Refresh Token: 14일 이내
        assertThat(refreshRemaining).isGreaterThan(0).isLessThanOrEqualTo(1209600000L);
    }
}
