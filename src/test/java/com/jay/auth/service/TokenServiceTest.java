package com.jay.auth.service;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.ActiveSessionResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.exception.InvalidTokenException;
import com.jay.auth.security.JwtTokenProvider;
import com.jay.auth.security.TokenStore;
import com.jay.auth.service.metrics.AuthMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private TokenStore tokenStore;
    @Mock
    private AuthMetrics authMetrics;

    @Nested
    @DisplayName("토큰 발급")
    class IssueTokens {

        @Test
        @DisplayName("토큰이 정상 발급되어야 한다")
        void issueTokensSuccess() {
            // given
            given(jwtTokenProvider.createAccessToken(1L, "uuid-1234", ChannelCode.EMAIL, "USER"))
                    .willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(1L, "uuid-1234", ChannelCode.EMAIL, "USER"))
                    .willReturn("refresh-token");
            given(jwtTokenProvider.getTokenId("refresh-token")).willReturn("token-id");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(1209600000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            // when
            TokenResponse response = tokenService.issueTokens(1L, "uuid-1234", ChannelCode.EMAIL, "USER");

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
            given(jwtTokenProvider.getRole(refreshToken)).willReturn("USER");

            // 새 토큰 발급 mock
            given(jwtTokenProvider.createAccessToken(1L, "uuid-1234", ChannelCode.EMAIL, "USER"))
                    .willReturn("new-access-token");
            given(jwtTokenProvider.createRefreshToken(1L, "uuid-1234", ChannelCode.EMAIL, "USER"))
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
                    .isInstanceOf(InvalidTokenException.class);
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
                    .isInstanceOf(InvalidTokenException.class);
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
        @DisplayName("null 토큰으로 로그아웃해도 예외가 발생하지 않아야 한다")
        void logoutWithNullTokens() {
            // when
            tokenService.logout(null, null);

            // then
            verify(tokenStore, never()).addToBlacklist(any(), anyLong());
            verify(tokenStore, never()).deleteRefreshToken(any(), any());
        }

        @Test
        @DisplayName("유효하지 않은 액세스 토큰으로 로그아웃 시 블랙리스트 등록하지 않아야 한다")
        void logoutWithInvalidAccessToken() {
            // given
            String accessToken = "invalid-access-token";
            String refreshToken = "refresh-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(false);
            given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
            given(jwtTokenProvider.getUserId(refreshToken)).willReturn(1L);
            given(jwtTokenProvider.getTokenId(refreshToken)).willReturn("refresh-token-id");

            // when
            tokenService.logout(accessToken, refreshToken);

            // then
            verify(tokenStore, never()).addToBlacklist(any(), anyLong());
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

        @Test
        @DisplayName("null 액세스 토큰으로 전체 로그아웃해도 Refresh Token은 삭제되어야 한다")
        void logoutAllWithNullAccessToken() {
            // when
            tokenService.logoutAll(1L, null);

            // then
            verify(tokenStore, never()).addToBlacklist(any(), anyLong());
            verify(tokenStore).deleteAllRefreshTokens(1L);
        }
    }

    @Nested
    @DisplayName("토큰 발급 (세션 정보 포함)")
    class IssueTokensWithSession {

        @Test
        @DisplayName("세션 정보와 함께 토큰이 발급되어야 한다")
        void issueTokensWithSessionSuccess() {
            // given
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "macOS", "127.0.0.1", null);

            given(jwtTokenProvider.createAccessToken(1L, "uuid-1234", ChannelCode.EMAIL, "USER"))
                    .willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(1L, "uuid-1234", ChannelCode.EMAIL, "USER"))
                    .willReturn("refresh-token");
            given(jwtTokenProvider.getTokenId("refresh-token")).willReturn("token-id");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(1209600000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            // when
            TokenResponse response = tokenService.issueTokensWithSession(
                    1L, "uuid-1234", ChannelCode.EMAIL, "USER", sessionInfo);

            // then
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            verify(tokenStore).saveRefreshTokenWithSession(
                    eq(1L), eq("token-id"), eq("refresh-token"), eq(1209600000L), eq(sessionInfo));
        }

        @Test
        @DisplayName("기본 역할(USER)로 세션 정보와 함께 토큰이 발급되어야 한다")
        void issueTokensWithSessionDefaultRole() {
            // given
            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "macOS", "127.0.0.1", null);

            given(jwtTokenProvider.createAccessToken(1L, "uuid-1234", ChannelCode.GOOGLE, "USER"))
                    .willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(1L, "uuid-1234", ChannelCode.GOOGLE, "USER"))
                    .willReturn("refresh-token");
            given(jwtTokenProvider.getTokenId("refresh-token")).willReturn("token-id");
            given(jwtTokenProvider.getRefreshTokenExpiration()).willReturn(1209600000L);
            given(jwtTokenProvider.getAccessTokenExpiration()).willReturn(1800000L);

            // when
            TokenResponse response = tokenService.issueTokensWithSession(
                    1L, "uuid-1234", ChannelCode.GOOGLE, sessionInfo);

            // then
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            verify(tokenStore).saveRefreshTokenWithSession(
                    eq(1L), eq("token-id"), eq("refresh-token"), eq(1209600000L), eq(sessionInfo));
        }
    }

    @Nested
    @DisplayName("Access Token 검증")
    class ValidateAccessToken {

        @Test
        @DisplayName("유효한 Access Token 검증이 성공해야 한다")
        void validateAccessTokenSuccess() {
            // given
            String accessToken = "valid-access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.getTokenId(accessToken)).willReturn("token-id");
            given(tokenStore.isBlacklisted("token-id")).willReturn(false);

            // when
            boolean result = tokenService.validateAccessToken(accessToken);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효하지 않은 Access Token 검증이 실패해야 한다")
        void validateInvalidAccessToken() {
            // given
            String accessToken = "invalid-access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(false);

            // when
            boolean result = tokenService.validateAccessToken(accessToken);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("블랙리스트에 등록된 Access Token 검증이 실패해야 한다")
        void validateBlacklistedAccessToken() {
            // given
            String accessToken = "blacklisted-access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.getTokenId(accessToken)).willReturn("token-id");
            given(tokenStore.isBlacklisted("token-id")).willReturn(true);

            // when
            boolean result = tokenService.validateAccessToken(accessToken);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("활성 세션 목록 조회")
    class GetActiveSessions {

        @Test
        @DisplayName("활성 세션 목록이 반환되어야 한다")
        void getActiveSessionsSuccess() {
            // given
            Map<String, String> sessionData = new HashMap<>();
            sessionData.put("sessionId", "session-1");
            sessionData.put("deviceType", "Desktop");
            sessionData.put("browser", "Chrome");
            sessionData.put("os", "macOS");
            sessionData.put("ipAddress", "127.0.0.1");
            sessionData.put("location", "");
            sessionData.put("lastActivity", LocalDateTime.now().toString());

            given(tokenStore.getAllSessions(1L)).willReturn(List.of(sessionData));

            // when
            List<ActiveSessionResponse> sessions = tokenService.getActiveSessions(1L, "session-1");

            // then
            assertThat(sessions).hasSize(1);
            assertThat(sessions.get(0).getSessionId()).isEqualTo("session-1");
            assertThat(sessions.get(0).isCurrentSession()).isTrue();
        }

        @Test
        @DisplayName("빈 세션 목록이 반환되어야 한다")
        void getActiveSessionsEmpty() {
            // given
            given(tokenStore.getAllSessions(1L)).willReturn(Collections.emptyList());

            // when
            List<ActiveSessionResponse> sessions = tokenService.getActiveSessions(1L, "session-1");

            // then
            assertThat(sessions).isEmpty();
        }

        @Test
        @DisplayName("현재 세션이 아닌 경우 currentSession이 false여야 한다")
        void getActiveSessionsNotCurrentSession() {
            // given
            Map<String, String> sessionData = new HashMap<>();
            sessionData.put("sessionId", "session-2");
            sessionData.put("deviceType", "Mobile");
            sessionData.put("browser", "Safari");
            sessionData.put("os", "iOS");
            sessionData.put("ipAddress", "192.168.1.1");
            sessionData.put("location", "");
            sessionData.put("lastActivity", "");

            given(tokenStore.getAllSessions(1L)).willReturn(List.of(sessionData));

            // when
            List<ActiveSessionResponse> sessions = tokenService.getActiveSessions(1L, "session-1");

            // then
            assertThat(sessions).hasSize(1);
            assertThat(sessions.get(0).isCurrentSession()).isFalse();
        }
    }

    @Nested
    @DisplayName("세션 종료")
    class RevokeSession {

        @Test
        @DisplayName("특정 세션이 종료되어야 한다")
        void revokeSessionSuccess() {
            // when
            tokenService.revokeSession(1L, "session-123");

            // then
            verify(tokenStore).revokeSession(1L, "session-123");
        }
    }

    @Nested
    @DisplayName("토큰 ID 추출")
    class GetTokenId {

        @Test
        @DisplayName("유효한 액세스 토큰에서 토큰 ID가 추출되어야 한다")
        void getTokenIdSuccess() {
            // given
            String accessToken = "valid-access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.getTokenId(accessToken)).willReturn("token-id-123");

            // when
            String tokenId = tokenService.getTokenId(accessToken);

            // then
            assertThat(tokenId).isEqualTo("token-id-123");
        }

        @Test
        @DisplayName("null 액세스 토큰에서 null이 반환되어야 한다")
        void getTokenIdWithNullToken() {
            // when
            String tokenId = tokenService.getTokenId(null);

            // then
            assertThat(tokenId).isNull();
        }

        @Test
        @DisplayName("유효하지 않은 액세스 토큰에서 null이 반환되어야 한다")
        void getTokenIdWithInvalidToken() {
            // given
            String accessToken = "invalid-access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(false);

            // when
            String tokenId = tokenService.getTokenId(accessToken);

            // then
            assertThat(tokenId).isNull();
        }
    }

    @Nested
    @DisplayName("세션 활동 시간 갱신")
    class UpdateSessionActivity {

        @Test
        @DisplayName("세션 활동 시간이 갱신되어야 한다")
        void updateSessionActivitySuccess() {
            // when
            tokenService.updateSessionActivity(1L, "token-id-123");

            // then
            verify(tokenStore).updateSessionActivity(1L, "token-id-123");
        }
    }

    @Nested
    @DisplayName("토큰 갱신 - 추가 케이스")
    class RefreshTokensAdditional {

        @Test
        @DisplayName("Access Token으로 갱신 시도 시 실패해야 한다")
        void refreshTokensFailsWithAccessToken() {
            // given
            String accessToken = "valid-access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.getTokenType(accessToken)).willReturn(JwtTokenProvider.TokenType.ACCESS);

            // when & then
            assertThatThrownBy(() -> tokenService.refreshTokens(accessToken))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
