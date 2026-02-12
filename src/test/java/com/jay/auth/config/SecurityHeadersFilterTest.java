package com.jay.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityHeadersFilter 테스트")
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter securityHeadersFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        securityHeadersFilter = new SecurityHeadersFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Nested
    @DisplayName("보안 헤더 설정")
    class SecurityHeaders {

        @Test
        @DisplayName("X-Content-Type-Options 헤더가 nosniff로 설정된다")
        void shouldSetXContentTypeOptions() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }

        @Test
        @DisplayName("X-XSS-Protection 헤더가 올바르게 설정된다")
        void shouldSetXXssProtection() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("1; mode=block");
        }

        @Test
        @DisplayName("X-Frame-Options 헤더가 DENY로 설정된다")
        void shouldSetXFrameOptions() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        }

        @Test
        @DisplayName("Referrer-Policy 헤더가 올바르게 설정된다")
        void shouldSetReferrerPolicy() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        }

        @Test
        @DisplayName("Permissions-Policy 헤더가 올바르게 설정된다")
        void shouldSetPermissionsPolicy() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=(), geolocation=()");
        }

        @Test
        @DisplayName("모든 보안 헤더가 한 번에 설정된다")
        void shouldSetAllSecurityHeaders() throws ServletException, IOException {
            // given
            request.setRequestURI("/some/page");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-Content-Type-Options")).isNotNull();
            assertThat(response.getHeader("X-XSS-Protection")).isNotNull();
            assertThat(response.getHeader("X-Frame-Options")).isNotNull();
            assertThat(response.getHeader("Referrer-Policy")).isNotNull();
            assertThat(response.getHeader("Permissions-Policy")).isNotNull();
        }
    }

    @Nested
    @DisplayName("API 경로 캐시 제어")
    class ApiCacheControl {

        @Test
        @DisplayName("API 경로 요청 시 Cache-Control 헤더가 설정된다")
        void shouldSetCacheControlForApiPath() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
        }

        @Test
        @DisplayName("API 경로 요청 시 Pragma 헤더가 no-cache로 설정된다")
        void shouldSetPragmaForApiPath() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/auth/email/login");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        }

        @Test
        @DisplayName("다양한 API 경로에서 캐시 제어가 적용된다")
        void shouldSetCacheControlForVariousApiPaths() throws ServletException, IOException {
            // given
            String[] apiPaths = {
                    "/api/v1/users/me",
                    "/api/v1/auth/email/login",
                    "/api/v1/2fa/setup",
                    "/api/v1/admin/users"
            };

            for (String path : apiPaths) {
                MockHttpServletResponse freshResponse = new MockHttpServletResponse();
                request.setRequestURI(path);

                // when
                securityHeadersFilter.doFilterInternal(request, freshResponse, new MockFilterChain());

                // then
                assertThat(freshResponse.getHeader("Cache-Control"))
                        .as("경로 '%s'에 Cache-Control이 설정되어야 한다", path)
                        .isEqualTo("no-store, no-cache, must-revalidate");
                assertThat(freshResponse.getHeader("Pragma"))
                        .as("경로 '%s'에 Pragma가 설정되어야 한다", path)
                        .isEqualTo("no-cache");
            }
        }
    }

    @Nested
    @DisplayName("비 API 경로 캐시 제어")
    class NonApiCacheControl {

        @Test
        @DisplayName("비 API 경로에서는 Cache-Control 헤더가 설정되지 않는다")
        void shouldNotSetCacheControlForNonApiPath() throws ServletException, IOException {
            // given
            request.setRequestURI("/index.html");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Cache-Control")).isNull();
        }

        @Test
        @DisplayName("비 API 경로에서는 Pragma 헤더가 설정되지 않는다")
        void shouldNotSetPragmaForNonApiPath() throws ServletException, IOException {
            // given
            request.setRequestURI("/static/css/style.css");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Pragma")).isNull();
        }

        @Test
        @DisplayName("다양한 비 API 경로에서 캐시 제어가 적용되지 않는다")
        void shouldNotSetCacheControlForVariousNonApiPaths() throws ServletException, IOException {
            // given
            String[] nonApiPaths = {
                    "/index.html",
                    "/static/js/app.js",
                    "/favicon.ico",
                    "/swagger-ui/index.html"
            };

            for (String path : nonApiPaths) {
                MockHttpServletResponse freshResponse = new MockHttpServletResponse();
                request.setRequestURI(path);

                // when
                securityHeadersFilter.doFilterInternal(request, freshResponse, new MockFilterChain());

                // then
                assertThat(freshResponse.getHeader("Cache-Control"))
                        .as("경로 '%s'에 Cache-Control이 설정되지 않아야 한다", path)
                        .isNull();
                assertThat(freshResponse.getHeader("Pragma"))
                        .as("경로 '%s'에 Pragma가 설정되지 않아야 한다", path)
                        .isNull();
            }
        }

        @Test
        @DisplayName("비 API 경로에서도 보안 헤더는 설정된다")
        void shouldStillSetSecurityHeadersForNonApiPath() throws ServletException, IOException {
            // given
            request.setRequestURI("/index.html");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("1; mode=block");
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
            assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
            assertThat(response.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=(), geolocation=()");
        }
    }

    @Nested
    @DisplayName("필터 체인 호출")
    class FilterChainInvocation {

        @Test
        @DisplayName("필터 처리 후 다음 필터 체인이 호출된다")
        void shouldInvokeFilterChain() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");

            // when
            securityHeadersFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(filterChain.getRequest()).isNotNull();
            assertThat(filterChain.getResponse()).isNotNull();
        }
    }
}
