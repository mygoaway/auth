package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationFailureHandlerTest {

    @InjectMocks
    private OAuth2AuthenticationFailureHandler failureHandler;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final String REDIRECT_URI = "http://localhost:3000/oauth2/callback";
    private static final String LINK_SUCCESS_URI = "http://localhost:3000/oauth2/link/success";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        ReflectionTestUtils.setField(failureHandler, "redirectUri", REDIRECT_URI);
        ReflectionTestUtils.setField(failureHandler, "linkSuccessUri", LINK_SUCCESS_URI);
    }

    @Nested
    @DisplayName("일반 로그인 실패 처리")
    class NormalLoginFailure {

        @Test
        @DisplayName("일반 로그인 실패 시 callback URL로 에러와 함께 리다이렉트되어야 한다")
        void shouldRedirectToCallbackWithError() throws IOException {
            // given
            String errorMessage = "authentication_failed";
            OAuth2Error oAuth2Error = new OAuth2Error("auth_error", errorMessage, null);
            OAuth2AuthenticationException exception = new OAuth2AuthenticationException(oAuth2Error, errorMessage);

            // when
            failureHandler.onAuthenticationFailure(request, response, exception);

            // then
            String expectedUrl = REDIRECT_URI + "?error=" +
                    URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            assertThat(response.getRedirectedUrl()).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("링크 상태 쿠키가 삭제되어야 한다")
        void shouldClearLinkStateCookie() throws IOException {
            // given
            OAuth2Error oAuth2Error = new OAuth2Error("error", "fail", null);
            OAuth2AuthenticationException exception = new OAuth2AuthenticationException(oAuth2Error, "fail");

            // when
            failureHandler.onAuthenticationFailure(request, response, exception);

            // then
            Cookie[] cookies = response.getCookies();
            assertThat(cookies).isNotNull();
            boolean found = false;
            for (Cookie cookie : cookies) {
                if (OAuth2LinkController.LINK_STATE_COOKIE_NAME.equals(cookie.getName())) {
                    assertThat(cookie.getMaxAge()).isZero();
                    assertThat(cookie.getValue()).isEmpty();
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Nested
    @DisplayName("연동 모드 실패 처리")
    class LinkModeFailure {

        @Test
        @DisplayName("연동 모드에서 실패 시 link-success URL로 에러와 함께 리다이렉트되어야 한다")
        void shouldRedirectToLinkSuccessUriWithError() throws IOException {
            // given
            request.setCookies(new Cookie(OAuth2LinkController.LINK_STATE_COOKIE_NAME, "link-state-123"));

            String errorMessage = "account_linking_error";
            OAuth2Error oAuth2Error = new OAuth2Error("link_error", errorMessage, null);
            OAuth2AuthenticationException exception = new OAuth2AuthenticationException(oAuth2Error, errorMessage);

            // when
            failureHandler.onAuthenticationFailure(request, response, exception);

            // then
            String expectedUrl = LINK_SUCCESS_URI + "?error=" +
                    URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            assertThat(response.getRedirectedUrl()).isEqualTo(expectedUrl);
        }

        @Test
        @DisplayName("연동 모드에서도 링크 상태 쿠키가 삭제되어야 한다")
        void shouldClearLinkStateCookieInLinkMode() throws IOException {
            // given
            request.setCookies(new Cookie(OAuth2LinkController.LINK_STATE_COOKIE_NAME, "link-state-123"));

            OAuth2Error oAuth2Error = new OAuth2Error("error", "link_fail", null);
            OAuth2AuthenticationException exception = new OAuth2AuthenticationException(oAuth2Error, "link_fail");

            // when
            failureHandler.onAuthenticationFailure(request, response, exception);

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
    @DisplayName("쿠키가 없는 경우")
    class NoCookies {

        @Test
        @DisplayName("쿠키가 null이면 일반 모드로 처리되어야 한다")
        void shouldTreatAsNormalModeWhenNoCookies() throws IOException {
            // given - 쿠키 없음
            OAuth2Error oAuth2Error = new OAuth2Error("error", "no_cookies", null);
            OAuth2AuthenticationException exception = new OAuth2AuthenticationException(oAuth2Error, "no_cookies");

            // when
            failureHandler.onAuthenticationFailure(request, response, exception);

            // then
            String expectedUrl = REDIRECT_URI + "?error=" +
                    URLEncoder.encode("no_cookies", StandardCharsets.UTF_8);
            assertThat(response.getRedirectedUrl()).isEqualTo(expectedUrl);
        }
    }
}
