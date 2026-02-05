package com.jay.auth.service;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.security.JwtTokenProvider;
import com.jay.auth.security.TokenStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private TokenStore tokenStore;

    @Nested
    @DisplayName("토큰 발급")
    class IssueTokens {

        @Test
        @DisplayName("토큰이 정상 발급되어야 한다")
        void issueTokensSuccess() {
            // given
            given(jwtTokenProvider.createAccessToken(1L, "uuid-1234", ChannelCode.EMAIL))
                    .willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(1L, "uuid-1234", ChannelCode.EMAIL))
                    .willReturn("refresh-token");
            given(jwtTokenProvider.getTokenId("refresh-token")).willReturn("token-id");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(1209600000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            // when
            TokenResponse response = tokenService.issueTokens(1L, "uuid-1234", ChannelCode.EMAIL);

            // then
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(1800);
            verify(tokenStore).saveRefreshToken(eq(1L), eq("token-id"), eq("refresh-token"), eq(1209600000L));
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class RefreshTokens {

        @Test
        @DisplayName("유효한 리프레시 토큰으로 갱신이 성공해야 한다")
        void refreshTokensSuccess() {
            // given
            String refreshToken = "valid-refresh-token";
            given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
            given(jwtTokenProvider.getTokenType(refreshToken)).willReturn(JwtTokenProvider.TokenType.REFRESH);
            given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
            given(jwtTokenProvider.getTokenId(refreshToken)).willReturn("old-token-id");
            given(tokenStore.existsRefreshToken(1L, "old-token-id")).willReturn(true);
            given(jwtTokenProvider.getUserUuid(refreshToken)).willReturn("uuid-1234");
            given(jwtTokenProvider.getChannelCode(refreshToken)).willReturn(ChannelCode.EMAIL);

            // 새 토큰 발급 mock
            given(jwtTokenProvider.createAccessToken(1L, "uuid-1234", ChannelCode.EMAIL))
                    .willReturn("new-access-token");
            given(jwtTokenProvider.createRefreshToken(1L, "uuid-1234", ChannelCode.EMAIL))
                    .willReturn("new-refresh-token");
            given(jwtTokenProvider.getTokenId("new-refresh-token")).willReturn("new-token-id");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(1209600000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            // when
            TokenResponse response = tokenService.refreshTokens(refreshToken);

            // then
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
            verify(tokenStore).deleteRefreshToken(1L, "old-token-id");
        }

        @Test
        @DisplayName("유효하지 않은 리프레시 토큰으로 갱신 시 실패해야 한다")
        void refreshTokensFailsWithInvalidToken() {
            // given
            given(jwtTokenProvider.validateToken("invalid-token")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> tokenService.refreshTokens("invalid-token"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Redis에 없는 리프레시 토큰으로 갱신 시 실패해야 한다")
        void refreshTokensFailsWithRevokedToken() {
            // given
            String refreshToken = "revoked-refresh-token";
            given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
            given(jwtTokenProvider.getTokenType(refreshToken)).willReturn(JwtTokenProvider.TokenType.REFRESH);
            given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
            given(jwtTokenProvider.getTokenId(refreshToken)).willReturn("revoked-token-id");
            given(tokenStore.existsRefreshToken(1L, "revoked-token-id")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> tokenService.refreshTokens(refreshToken))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("단일 세션 로그아웃이 성공해야 한다")
        void logoutSuccess() {
            // given
            String accessToken = "access-token";
            String refreshToken = "refresh-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.getTokenId(accessToken)).willReturn("access-token-id");
            given(jwtTokenProvider.getRemainingExpiration(accessToken)).willReturn(900000L);
            given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
            given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
            given(jwtTokenProvider.getTokenId(refreshToken)).willReturn("refresh-token-id");

            // when
            tokenService.logout(accessToken, refreshToken);

            // then
            verify(tokenStore).addToBlacklist("access-token-id", 900000L);
            verify(tokenStore).deleteRefreshToken(1L, "refresh-token-id");
        }

        @Test
        @DisplayName("전체 세션 로그아웃이 성공해야 한다")
        void logoutAllSuccess() {
            // given
            String accessToken = "access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.getTokenId(accessToken)).willReturn("access-token-id");
            given(jwtTokenProvider.getRemainingExpiration(accessToken)).willReturn(900000L);

            // when
            tokenService.logoutAll(1L, accessToken);

            // then
            verify(tokenStore).addToBlacklist("access-token-id", 900000L);
            verify(tokenStore).deleteAllRefreshTokens(1L);
        }
    }
}
