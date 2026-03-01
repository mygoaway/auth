package com.jay.auth.config;

import com.jay.auth.service.IpAccessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpAccessFilter 테스트")
class IpAccessFilterTest {

    private IpAccessFilter ipAccessFilter;

    @Mock
    private IpAccessService ipAccessService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        ipAccessFilter = new IpAccessFilter(ipAccessService, new SimpleMeterRegistry());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        request.setRemoteAddr("1.2.3.4");
    }

    @Nested
    @DisplayName("차단된 IP 처리")
    class BlockedIp {

        @Test
        @DisplayName("차단된 IP는 403 응답을 반환하고 필터 체인을 진행하지 않는다")
        void blockedIpReturns403() throws ServletException, IOException {
            request.setRequestURI("/api/v1/auth/email/login");
            given(ipAccessService.isBlocked("1.2.3.4")).willReturn(true);

            ipAccessFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("IP_BLOCKED");
            assertThat(filterChain.getRequest()).isNull();
        }

        @Test
        @DisplayName("차단된 IP 응답 본문에 에러 코드가 포함된다")
        void blockedIpResponseBody() throws ServletException, IOException {
            request.setRequestURI("/api/v1/users/me");
            given(ipAccessService.isBlocked("1.2.3.4")).willReturn(true);

            ipAccessFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getContentAsString()).contains("\"code\":\"IP_BLOCKED\"");
        }
    }

    @Nested
    @DisplayName("허용된 IP 처리")
    class AllowedIp {

        @Test
        @DisplayName("차단되지 않은 IP는 필터 체인을 정상 통과한다")
        void allowedIpPassesThrough() throws ServletException, IOException {
            request.setRequestURI("/api/v1/auth/email/login");
            given(ipAccessService.isBlocked("1.2.3.4")).willReturn(false);

            ipAccessFilter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(filterChain.getRequest()).isNotNull();
        }
    }

    @Nested
    @DisplayName("필터 제외 경로")
    class ExemptedPaths {

        @Test
        @DisplayName("health 엔드포인트는 IP 필터를 거치지 않는다")
        void healthEndpointExempt() {
            request.setRequestURI("/api/v1/health");
            assertThat(ipAccessFilter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("actuator 경로는 IP 필터를 거치지 않는다")
        void actuatorExempt() {
            request.setRequestURI("/actuator/prometheus");
            assertThat(ipAccessFilter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("swagger 경로는 IP 필터를 거치지 않는다")
        void swaggerExempt() {
            request.setRequestURI("/swagger-ui/index.html");
            assertThat(ipAccessFilter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("일반 API 경로는 IP 필터가 적용된다")
        void normalApiPathFiltered() {
            request.setRequestURI("/api/v1/users/me");
            assertThat(ipAccessFilter.shouldNotFilter(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("클라이언트 IP 추출")
    class ClientIpExtraction {

        @Test
        @DisplayName("X-Forwarded-For 헤더로부터 IP를 추출한다")
        void extractIpFromXForwardedFor() throws ServletException, IOException {
            request.setRequestURI("/api/v1/auth/email/login");
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
            given(ipAccessService.isBlocked("10.0.0.1")).willReturn(false);

            ipAccessFilter.doFilterInternal(request, response, filterChain);

            assertThat(filterChain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("X-Real-IP 헤더로부터 IP를 추출한다")
        void extractIpFromXRealIp() throws ServletException, IOException {
            request.setRequestURI("/api/v1/auth/email/login");
            request.addHeader("X-Real-IP", "172.16.0.5");
            given(ipAccessService.isBlocked("172.16.0.5")).willReturn(false);

            ipAccessFilter.doFilterInternal(request, response, filterChain);

            assertThat(filterChain.getRequest()).isNotNull();
        }
    }
}
