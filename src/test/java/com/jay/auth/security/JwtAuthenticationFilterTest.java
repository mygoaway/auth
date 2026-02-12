package com.jay.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenStore tokenStore;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("유효한 토큰으로 필터 처리")
    class ValidToken {

        @Test
        @DisplayName("유효한 Access 토큰이면 SecurityContext에 인증 정보가 설정되어야 한다")
        void shouldSetAuthenticationWithValidToken() throws ServletException, IOException {
            // given
            String token = "valid-access-token";
            request.addHeader("Authorization", "Bearer " + token);

            given(jwtTokenProvider.validateToken(token)).willReturn(true);
            given(jwtTokenProvider.getTokenType(token)).willReturn(JwtTokenProvider.TokenType.ACCESS);
            given(jwtTokenProvider.getTokenId(token)).willReturn("token-id-1");
            given(tokenStore.isBlacklisted("token-id-1")).willReturn(false);
            given(jwtTokenProvider.getUserId(token)).willReturn(1L);
            given(jwtTokenProvider.getUserUuid(token)).willReturn("uuid-1234");
            given(jwtTokenProvider.getRole(token)).willReturn("USER");

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getPrincipal()).isInstanceOf(UserPrincipal.class);

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            assertThat(principal.getUserId()).isEqualTo(1L);
            assertThat(principal.getUserUuid()).isEqualTo("uuid-1234");
            assertThat(principal.getRole()).isEqualTo("USER");

            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_USER");

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("ADMIN 역할의 토큰이면 ROLE_USER와 ROLE_ADMIN 권한이 모두 설정되어야 한다")
        void shouldSetAdminAuthorities() throws ServletException, IOException {
            // given
            String token = "admin-access-token";
            request.addHeader("Authorization", "Bearer " + token);

            given(jwtTokenProvider.validateToken(token)).willReturn(true);
            given(jwtTokenProvider.getTokenType(token)).willReturn(JwtTokenProvider.TokenType.ACCESS);
            given(jwtTokenProvider.getTokenId(token)).willReturn("token-id-admin");
            given(tokenStore.isBlacklisted("token-id-admin")).willReturn(false);
            given(jwtTokenProvider.getUserId(token)).willReturn(2L);
            given(jwtTokenProvider.getUserUuid(token)).willReturn("uuid-admin");
            given(jwtTokenProvider.getRole(token)).willReturn("ADMIN");

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getAuthorities())
                    .extracting("authority")
                    .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("유효하지 않은 토큰으로 필터 처리")
    class InvalidToken {

        @Test
        @DisplayName("JWT 검증 실패 시 SecurityContext에 인증 정보가 설정되지 않아야 한다")
        void shouldNotSetAuthenticationWithInvalidToken() throws ServletException, IOException {
            // given
            String token = "invalid-token";
            request.addHeader("Authorization", "Bearer " + token);

            given(jwtTokenProvider.validateToken(token)).willReturn(false);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("토큰이 없는 경우 필터 처리")
    class NoToken {

        @Test
        @DisplayName("Authorization 헤더가 없으면 SecurityContext에 인증 정보가 설정되지 않아야 한다")
        void shouldNotSetAuthenticationWithoutAuthorizationHeader() throws ServletException, IOException {
            // given - 헤더 없음

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Bearer 접두사가 없는 Authorization 헤더면 인증 정보가 설정되지 않아야 한다")
        void shouldNotSetAuthenticationWithoutBearerPrefix() throws ServletException, IOException {
            // given
            request.addHeader("Authorization", "Basic some-token");

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("만료된 토큰으로 필터 처리")
    class ExpiredToken {

        @Test
        @DisplayName("만료된 토큰이면 SecurityContext에 인증 정보가 설정되지 않아야 한다")
        void shouldNotSetAuthenticationWithExpiredToken() throws ServletException, IOException {
            // given
            String token = "expired-token";
            request.addHeader("Authorization", "Bearer " + token);

            given(jwtTokenProvider.validateToken(token)).willReturn(false);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("블랙리스트 토큰으로 필터 처리")
    class BlacklistedToken {

        @Test
        @DisplayName("블랙리스트에 등록된 토큰이면 SecurityContext에 인증 정보가 설정되지 않아야 한다")
        void shouldNotSetAuthenticationWithBlacklistedToken() throws ServletException, IOException {
            // given
            String token = "blacklisted-token";
            request.addHeader("Authorization", "Bearer " + token);

            given(jwtTokenProvider.validateToken(token)).willReturn(true);
            given(jwtTokenProvider.getTokenType(token)).willReturn(JwtTokenProvider.TokenType.ACCESS);
            given(jwtTokenProvider.getTokenId(token)).willReturn("blacklisted-id");
            given(tokenStore.isBlacklisted("blacklisted-id")).willReturn(true);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Refresh 토큰으로 필터 처리")
    class RefreshTokenUsed {

        @Test
        @DisplayName("Refresh 토큰이 전달되면 SecurityContext에 인증 정보가 설정되지 않아야 한다")
        void shouldNotSetAuthenticationWithRefreshToken() throws ServletException, IOException {
            // given
            String token = "refresh-token";
            request.addHeader("Authorization", "Bearer " + token);

            given(jwtTokenProvider.validateToken(token)).willReturn(true);
            given(jwtTokenProvider.getTokenType(token)).willReturn(JwtTokenProvider.TokenType.REFRESH);

            // when
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // then
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }
}
