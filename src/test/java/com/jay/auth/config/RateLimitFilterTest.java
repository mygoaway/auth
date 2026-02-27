package com.jay.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter 테스트")
class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(redisTemplate, meterRegistry);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        request.setRemoteAddr("127.0.0.1");
    }

    @Nested
    @DisplayName("일반 엔드포인트 요청 제한")
    class GeneralEndpointRateLimit {

        @Test
        @DisplayName("제한 이하 요청은 정상 통과한다")
        void shouldPassWhenUnderLimit() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
        }

        @Test
        @DisplayName("제한 초과 시 429 상태코드를 반환한다")
        void shouldReturn429WhenOverLimit() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(61L);
            given(redisTemplate.getExpire(anyString())).willReturn(45L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("45");
            assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
        }

        @Test
        @DisplayName("제한 초과 시 filterChain이 호출되지 않는다")
        void shouldNotCallFilterChainWhenOverLimit() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(61L);
            given(redisTemplate.getExpire(anyString())).willReturn(30L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        @DisplayName("TTL이 null일 경우 Retry-After를 60으로 설정한다")
        void shouldSetRetryAfterTo60WhenTtlIsNull() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(61L);
            given(redisTemplate.getExpire(anyString())).willReturn(null);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        }
    }

    @Nested
    @DisplayName("인증 엔드포인트 요청 제한")
    class AuthEndpointRateLimit {

        @Test
        @DisplayName("로그인 엔드포인트는 분당 10회 제한이 적용된다")
        void shouldApplyStricterLimitForLoginEndpoint() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/auth/email/login");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(5L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("5");
        }

        @Test
        @DisplayName("회원가입 엔드포인트는 분당 10회 제한이 적용된다")
        void shouldApplyStricterLimitForSignupEndpoint() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/auth/email/signup");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        }

        @Test
        @DisplayName("비밀번호 리셋 엔드포인트는 분당 10회 제한이 적용된다")
        void shouldApplyStricterLimitForPasswordResetEndpoint() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/auth/password/reset");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        }

        @Test
        @DisplayName("인증 엔드포인트 제한 초과 시 429를 반환한다")
        void shouldReturn429WhenAuthLimitExceeded() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/auth/email/login");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(11L);
            given(redisTemplate.getExpire(anyString())).willReturn(50L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(429);
        }
    }

    @Nested
    @DisplayName("사용자 엔드포인트 요청 제한")
    class UserEndpointRateLimit {

        @Test
        @DisplayName("사용자 엔드포인트는 분당 200회 제한이 적용된다")
        void shouldApplyHigherLimitForUserEndpoint() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/users/me");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(100L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("200");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("100");
        }

        @Test
        @DisplayName("2FA 엔드포인트는 분당 200회 제한이 적용된다")
        void shouldApplyHigherLimitFor2faEndpoint() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/2fa/setup");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("200");
        }

        @Test
        @DisplayName("관리자 엔드포인트는 분당 200회 제한이 적용된다")
        void shouldApplyHigherLimitForAdminEndpoint() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/admin/users");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("200");
        }
    }

    @Nested
    @DisplayName("필터 제외 경로")
    class ExemptedPaths {

        @Test
        @DisplayName("health 엔드포인트는 rate limit이 적용되지 않는다")
        void shouldNotFilterHealthEndpoint() {
            // given
            request.setRequestURI("/api/v1/health");

            // when
            boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);

            // then
            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("swagger 경로는 rate limit이 적용되지 않는다")
        void shouldNotFilterSwaggerPath() {
            // given
            request.setRequestURI("/swagger-ui/index.html");

            // when
            boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);

            // then
            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("api-docs 경로는 rate limit이 적용되지 않는다")
        void shouldNotFilterApiDocsPath() {
            // given
            request.setRequestURI("/api-docs/v3");

            // when
            boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);

            // then
            assertThat(shouldNotFilter).isTrue();
        }

        @Test
        @DisplayName("일반 API 경로는 필터가 적용된다")
        void shouldFilterNormalApiPath() {
            // given
            request.setRequestURI("/api/v1/auth/email/login");

            // when
            boolean shouldNotFilter = rateLimitFilter.shouldNotFilter(request);

            // then
            assertThat(shouldNotFilter).isFalse();
        }
    }

    @Nested
    @DisplayName("클라이언트 IP 추출")
    class ClientIpExtraction {

        @Test
        @DisplayName("X-Forwarded-For 헤더가 있으면 해당 IP를 사용한다")
        void shouldUseXForwardedForHeader() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            request.addHeader("X-Forwarded-For", "10.0.0.1");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("rate:api:10.0.0.1")).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            then(valueOperations).should().increment("rate:api:10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For에 여러 IP가 있으면 첫 번째 IP를 사용한다")
        void shouldUseFirstIpFromXForwardedFor() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("rate:api:10.0.0.1")).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            then(valueOperations).should().increment("rate:api:10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For가 없고 X-Real-IP가 있으면 해당 IP를 사용한다")
        void shouldUseXRealIpHeader() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            request.addHeader("X-Real-IP", "192.168.1.1");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("rate:api:192.168.1.1")).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            then(valueOperations).should().increment("rate:api:192.168.1.1");
        }

        @Test
        @DisplayName("헤더가 모두 없으면 remoteAddr을 사용한다")
        void shouldUseRemoteAddrWhenNoHeaders() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            request.setRemoteAddr("127.0.0.1");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment("rate:api:127.0.0.1")).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            then(valueOperations).should().increment("rate:api:127.0.0.1");
        }
    }

    @Nested
    @DisplayName("Redis 장애 시 동작")
    class RedisFailure {

        @Test
        @DisplayName("Redis 장애 시 요청이 정상 통과한다")
        void shouldPassThroughWhenRedisFails() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willThrow(new RuntimeException("Redis connection failed"));

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }
    }

    @Nested
    @DisplayName("첫 번째 요청 시 TTL 설정")
    class FirstRequestTtl {

        @Test
        @DisplayName("첫 번째 요청(count=1)일 때 키의 만료시간을 설정한다")
        void shouldSetExpireOnFirstRequest() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(1L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            then(redisTemplate).should().expire(anyString(), any());
        }

        @Test
        @DisplayName("두 번째 이후 요청에서는 만료시간을 재설정하지 않는다")
        void shouldNotSetExpireOnSubsequentRequests() throws ServletException, IOException {
            // given
            request.setRequestURI("/api/v1/some/endpoint");
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.increment(anyString())).willReturn(5L);

            // when
            rateLimitFilter.doFilterInternal(request, response, filterChain);

            // then
            then(redisTemplate).should(never()).expire(anyString(), any());
        }
    }
}
