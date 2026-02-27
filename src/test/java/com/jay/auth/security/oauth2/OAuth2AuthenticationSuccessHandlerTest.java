package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.security.TokenStore;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.OAuth2LinkStateService;
import com.jay.auth.service.SecurityNotificationService;
import com.jay.auth.service.TokenService;
import com.jay.auth.service.metrics.AuthMetrics;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2AuthenticationSuccessHandlerTest {

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Mock
    private TokenService tokenService;

    @Mock
    private OAuth2LinkStateService oAuth2LinkStateService;

    @Mock
    private LoginHistoryService loginHistoryService;

    @Mock
    private SecurityNotificationService securityNotificationService;

    @Mock
    private AuthMetrics authMetrics;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String REDIRECT_URI = "http://localhost:3000/oauth2/callback";
    private static final String LINK_SUCCESS_URI = "http://localhost:3000/oauth2/link/success";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        ReflectionTestUtils.setField(successHandler, "redirectUri", REDIRECT_URI);
        ReflectionTestUtils.setField(successHandler, "linkSuccessUri", LINK_SUCCESS_URI);
    }

    @Nested
    @DisplayName("일반 OAuth2 로그인 성공 처리")
    class NormalLogin {

        @Test
        @DisplayName("CustomOAuth2User로 로그인 성공 시 토큰과 함께 리다이렉트되어야 한다")
        void shouldRedirectWithTokensForOAuth2User() throws IOException {
            // given
            CustomOAuth2User oAuth2User = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.KAKAO,
                    Map.of("id", "kakao-123"), "id", "USER",
                    false, false, null
            );

            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(oAuth2User);

            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "Windows", "127.0.0.1", "Seoul");
            given(loginHistoryService.extractSessionInfo(request)).willReturn(sessionInfo);

            TokenResponse tokenResponse = TokenResponse.of("access-token-123", "refresh-token-123", 1800L);
            given(tokenService.issueTokensWithSession(eq(1L), eq("uuid-1234"), eq(ChannelCode.KAKAO), eq("USER"), any()))
                    .willReturn(tokenResponse);

            // when
            successHandler.onAuthenticationSuccess(request, response, authentication);

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).isNotNull();
            assertThat(redirectedUrl).startsWith(REDIRECT_URI);
            assertThat(redirectedUrl).contains("accessToken=access-token-123");
            assertThat(redirectedUrl).contains("refreshToken=refresh-token-123");
            assertThat(redirectedUrl).contains("expiresIn=1800");

            verify(loginHistoryService).recordLoginSuccess(eq(1L), eq(ChannelCode.KAKAO), eq(request));
            verify(securityNotificationService).notifyNewDeviceLogin(eq(1L), any());
            verify(oAuth2LinkStateService).removeLinkState(any());
        }

        @Test
        @DisplayName("CustomOidcUser로 로그인 성공 시 토큰과 함께 리다이렉트되어야 한다")
        void shouldRedirectWithTokensForOidcUser() throws IOException {
            // given
            CustomOidcUser oidcUser = new CustomOidcUser(
                    2L, "uuid-oidc", ChannelCode.GOOGLE,
                    null, null,
                    Map.of("sub", "google-123"), "sub", "USER",
                    false, false, null
            );

            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(oidcUser);

            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Desktop", "Chrome", "macOS", "10.0.0.1", "Seoul");
            given(loginHistoryService.extractSessionInfo(request)).willReturn(sessionInfo);

            TokenResponse tokenResponse = TokenResponse.of("oidc-access", "oidc-refresh", 1800L);
            given(tokenService.issueTokensWithSession(eq(2L), eq("uuid-oidc"), eq(ChannelCode.GOOGLE), eq("USER"), any()))
                    .willReturn(tokenResponse);

            // when
            successHandler.onAuthenticationSuccess(request, response, authentication);

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).isNotNull();
            assertThat(redirectedUrl).contains("accessToken=oidc-access");
            assertThat(redirectedUrl).contains("refreshToken=oidc-refresh");
            verify(loginHistoryService).recordLoginSuccess(eq(2L), eq(ChannelCode.GOOGLE), eq(request));
        }

        @Test
        @DisplayName("탈퇴 예정 사용자 로그인 시 pendingDeletion 파라미터가 포함되어야 한다")
        void shouldIncludePendingDeletionParam() throws IOException {
            // given
            CustomOAuth2User oAuth2User = new CustomOAuth2User(
                    3L, "uuid-pending", ChannelCode.NAVER,
                    Map.of("response", Map.of("id", "naver-123")), "response", "USER",
                    false, true, null
            );

            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(oAuth2User);

            TokenStore.SessionInfo sessionInfo = new TokenStore.SessionInfo(
                    "Mobile", "Safari", "iOS", "192.168.0.1", "Busan");
            given(loginHistoryService.extractSessionInfo(request)).willReturn(sessionInfo);

            TokenResponse tokenResponse = TokenResponse.of("at", "rt", 1800L);
            given(tokenService.issueTokensWithSession(eq(3L), eq("uuid-pending"), eq(ChannelCode.NAVER), eq("USER"), any()))
                    .willReturn(tokenResponse);

            // when
            successHandler.onAuthenticationSuccess(request, response, authentication);

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).isNotNull();
            assertThat(redirectedUrl).contains("pendingDeletion=true");
        }
    }

    @Nested
    @DisplayName("연동 모드 성공 처리")
    class LinkMode {

        @Test
        @DisplayName("연동 모드에서 성공 시 link-success URL로 리다이렉트되어야 한다")
        void shouldRedirectToLinkSuccessUri() throws IOException {
            // given
            CustomOAuth2User oAuth2User = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    Map.of("sub", "google-123"), "sub", "USER",
                    true, false, null
            );

            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(oAuth2User);

            // when
            successHandler.onAuthenticationSuccess(request, response, authentication);

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).isNotNull();
            assertThat(redirectedUrl).startsWith(LINK_SUCCESS_URI);
            assertThat(redirectedUrl).contains("channelCode=GOOGLE");
            assertThat(redirectedUrl).contains("success=true");

            // 토큰 발급하지 않아야 한다
            verify(tokenService, never()).issueTokensWithSession(anyLong(), anyString(), any(), anyString(), any());
            verify(loginHistoryService, never()).recordLoginSuccess(anyLong(), any(), any());
        }

        @Test
        @DisplayName("연동 모드에서 링크 상태 쿠키가 삭제되어야 한다")
        void shouldClearLinkStateCookieInLinkMode() throws IOException {
            // given
            CustomOidcUser oidcUser = new CustomOidcUser(
                    1L, "uuid-1234", ChannelCode.GOOGLE,
                    null, null,
                    Map.of("sub", "google-123"), "sub", "USER",
                    true, false, null
            );

            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(oidcUser);

            // when
            successHandler.onAuthenticationSuccess(request, response, authentication);

            // then
            Cookie[] cookies = response.getCookies();
            assertThat(cookies).isNotNull();
            boolean found = false;
            for (Cookie cookie : cookies) {
                if (OAuth2LinkController.LINK_STATE_COOKIE_NAME.equals(cookie.getName())) {
                    assertThat(cookie.getMaxAge()).isZero();
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Nested
    @DisplayName("예외 발생 시 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("처리 중 예외 발생 시 에러와 함께 리다이렉트되어야 한다")
        void shouldRedirectWithErrorOnException() throws IOException {
            // given
            CustomOAuth2User oAuth2User = new CustomOAuth2User(
                    1L, "uuid-1234", ChannelCode.KAKAO,
                    Map.of("id", "kakao-123"), "id", "USER",
                    false, false, null
            );

            Authentication authentication = mock(Authentication.class);
            given(authentication.getPrincipal()).willReturn(oAuth2User);
            given(loginHistoryService.extractSessionInfo(request))
                    .willThrow(new RuntimeException("session extraction failed"));

            // when
            successHandler.onAuthenticationSuccess(request, response, authentication);

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).isNotNull();
            assertThat(redirectedUrl).startsWith(REDIRECT_URI);
            assertThat(redirectedUrl).contains("error=");
        }
    }
}
