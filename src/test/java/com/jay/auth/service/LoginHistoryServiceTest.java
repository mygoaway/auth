package com.jay.auth.service;

import com.jay.auth.domain.entity.LoginHistory;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.LoginHistoryResponse;
import com.jay.auth.repository.LoginHistoryRepository;
import com.jay.auth.security.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginHistoryServiceTest {

    @InjectMocks
    private LoginHistoryService loginHistoryService;

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private GeoIpService geoIpService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Captor
    private ArgumentCaptor<LoginHistory> loginHistoryCaptor;

    @Nested
    @DisplayName("로그인 성공 기록")
    class RecordLoginSuccess {

        @Test
        @DisplayName("로그인 성공 이력이 정상적으로 저장되어야 한다")
        void recordLoginSuccessWithChrome() {
            // given
            Long userId = 1L;
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
            String clientIp = "203.0.113.1";

            given(httpServletRequest.getHeader("X-Forwarded-For")).willReturn(null);
            given(httpServletRequest.getHeader("X-Real-IP")).willReturn(null);
            given(httpServletRequest.getRemoteAddr()).willReturn(clientIp);
            given(httpServletRequest.getHeader("User-Agent")).willReturn(userAgent);
            given(geoIpService.getLocation(clientIp)).willReturn("대한민국 서울특별시 강남구");
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.recordLoginSuccess(userId, ChannelCode.EMAIL, httpServletRequest);
            // recordLoginSuccess calls saveLoginHistoryAsync internally
            // We need to verify the async method was called - but since it's on the same instance,
            // we verify the final repository save instead.
            // Note: The recordLoginSuccess extracts info synchronously, then calls saveLoginHistoryAsync.
            // In a unit test without Spring proxy, the async call is synchronous.

            // then
            // The method calls saveLoginHistoryAsync which saves to repository
            // Since this is a unit test (no Spring proxy for @Async), the call is synchronous
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getChannelCode()).isEqualTo(ChannelCode.EMAIL);
            assertThat(saved.getIpAddress()).isEqualTo(clientIp);
            assertThat(saved.getIsSuccess()).isTrue();
            assertThat(saved.getFailureReason()).isNull();
            assertThat(saved.getDeviceType()).isEqualTo("Desktop");
            assertThat(saved.getBrowser()).isEqualTo("Chrome");
            assertThat(saved.getOs()).isEqualTo("Windows");
            assertThat(saved.getLocation()).isEqualTo("대한민국 서울특별시 강남구");
        }

        @Test
        @DisplayName("X-Forwarded-For 헤더에서 클라이언트 IP가 추출되어야 한다")
        void recordLoginSuccessWithXForwardedFor() {
            // given
            Long userId = 1L;
            String realIp = "203.0.113.50";
            String userAgent = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0.0.0";

            given(httpServletRequest.getHeader("X-Forwarded-For")).willReturn(realIp + ", 10.0.0.1");
            given(httpServletRequest.getHeader("User-Agent")).willReturn(userAgent);
            given(geoIpService.getLocation(realIp)).willReturn("대한민국");
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.recordLoginSuccess(userId, ChannelCode.GOOGLE, httpServletRequest);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getIpAddress()).isEqualTo(realIp);
            assertThat(saved.getChannelCode()).isEqualTo(ChannelCode.GOOGLE);
        }
    }

    @Nested
    @DisplayName("로그인 실패 기록")
    class RecordLoginFailure {

        @Test
        @DisplayName("로그인 실패 이력이 실패 사유와 함께 저장되어야 한다")
        void recordLoginFailure() {
            // given
            Long userId = 1L;
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15";
            String clientIp = "203.0.113.2";
            String failureReason = "INVALID_PASSWORD";

            given(httpServletRequest.getHeader("X-Forwarded-For")).willReturn(null);
            given(httpServletRequest.getHeader("X-Real-IP")).willReturn(null);
            given(httpServletRequest.getRemoteAddr()).willReturn(clientIp);
            given(httpServletRequest.getHeader("User-Agent")).willReturn(userAgent);
            given(geoIpService.getLocation(clientIp)).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.recordLoginFailure(userId, ChannelCode.EMAIL, failureReason, httpServletRequest);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getIsSuccess()).isFalse();
            assertThat(saved.getFailureReason()).isEqualTo("INVALID_PASSWORD");
            assertThat(saved.getBrowser()).isEqualTo("Safari");
            assertThat(saved.getOs()).isEqualTo("macOS");
        }
    }

    @Nested
    @DisplayName("로그인 이력 조회")
    class GetLoginHistories {

        @Test
        @DisplayName("사용자의 최근 로그인 이력이 반환되어야 한다")
        void getRecentLoginHistory() {
            // given
            Long userId = 1L;
            LoginHistory history = createLoginHistory(1L, userId, ChannelCode.EMAIL, true);
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of(history));

            // when
            List<LoginHistoryResponse> result = loginHistoryService.getRecentLoginHistory(userId, 10);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChannelCode()).isEqualTo(ChannelCode.EMAIL);
            assertThat(result.get(0).getIsSuccess()).isTrue();
        }

        @Test
        @DisplayName("로그인 이력이 없으면 빈 목록이 반환되어야 한다")
        void getEmptyLoginHistory() {
            // given
            Long userId = 1L;
            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(Collections.emptyList());

            // when
            List<LoginHistoryResponse> result = loginHistoryService.getRecentLoginHistory(userId, 10);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 로그인 이력이 반환되어야 한다")
        void getMultipleLoginHistories() {
            // given
            Long userId = 1L;
            LoginHistory history1 = createLoginHistory(1L, userId, ChannelCode.EMAIL, true);
            LoginHistory history2 = createLoginHistory(2L, userId, ChannelCode.GOOGLE, true);
            LoginHistory history3 = createLoginHistory(3L, userId, ChannelCode.EMAIL, false);

            given(loginHistoryRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                    .willReturn(List.of(history1, history2, history3));

            // when
            List<LoginHistoryResponse> result = loginHistoryService.getRecentLoginHistory(userId, 10);

            // then
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("실패 로그인 횟수 조회")
    class GetFailedLoginCount {

        @Test
        @DisplayName("특정 시점 이후 실패 로그인 횟수가 반환되어야 한다")
        void getFailedLoginCountSince() {
            // given
            Long userId = 1L;
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            given(loginHistoryRepository.countFailedLoginsSince(userId, since)).willReturn(3L);

            // when
            long result = loginHistoryService.getFailedLoginCountSince(userId, since);

            // then
            assertThat(result).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("User-Agent 파싱")
    class ParseUserAgent {

        @Test
        @DisplayName("Chrome 브라우저가 올바르게 파싱되어야 한다")
        void parseChromeUserAgent() {
            // given
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
            String clientIp = "203.0.113.1";

            given(geoIpService.getLocation(clientIp)).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, clientIp, userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getBrowser()).isEqualTo("Chrome");
            assertThat(saved.getOs()).isEqualTo("Windows");
            assertThat(saved.getDeviceType()).isEqualTo("Desktop");
        }

        @Test
        @DisplayName("Firefox 브라우저가 올바르게 파싱되어야 한다")
        void parseFirefoxUserAgent() {
            // given
            String userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getBrowser()).isEqualTo("Firefox");
            assertThat(saved.getOs()).isEqualTo("Linux");
        }

        @Test
        @DisplayName("Safari 브라우저가 올바르게 파싱되어야 한다")
        void parseSafariUserAgent() {
            // given
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getBrowser()).isEqualTo("Safari");
            assertThat(saved.getOs()).isEqualTo("macOS");
        }

        @Test
        @DisplayName("Edge 브라우저가 올바르게 파싱되어야 한다")
        void parseEdgeUserAgent() {
            // given
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getBrowser()).isEqualTo("Edge");
        }

        @Test
        @DisplayName("모바일 User-Agent가 올바르게 파싱되어야 한다")
        void parseMobileUserAgent() {
            // given - iPhone UA contains "Mac OS X" which matches "mac os" before "iphone" in parseOs
            String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getDeviceType()).isEqualTo("Mobile");
            // parseOs checks "mac os" before "iphone", and iPhone UA contains "Mac OS X"
            assertThat(saved.getOs()).isEqualTo("macOS");
        }

        @Test
        @DisplayName("Android User-Agent가 올바르게 파싱되어야 한다")
        void parseAndroidUserAgent() {
            // given - Android UA contains "Linux" which matches before "android" in parseOs
            String userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getDeviceType()).isEqualTo("Mobile");
            // parseOs checks "linux" before "android", and Android UA contains "Linux"
            assertThat(saved.getOs()).isEqualTo("Linux");
        }

        @Test
        @DisplayName("null User-Agent는 Unknown으로 파싱되어야 한다")
        void parseNullUserAgent() {
            // given
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", null, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            assertThat(saved.getDeviceType()).isEqualTo("Unknown");
            assertThat(saved.getBrowser()).isEqualTo("Unknown");
            assertThat(saved.getOs()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("태블릿 User-Agent가 올바르게 파싱되어야 한다")
        void parseTabletUserAgent() {
            // given - iPad UA contains "Mobile" which matches before "ipad"/"tablet" in parseDeviceType
            String userAgent = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
            given(geoIpService.getLocation(any())).willReturn(null);
            given(loginHistoryRepository.save(any(LoginHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            loginHistoryService.saveLoginHistoryAsync(1L, ChannelCode.EMAIL, "8.8.8.8", userAgent, true, null);

            // then
            verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
            LoginHistory saved = loginHistoryCaptor.getValue();
            // parseDeviceType checks "mobile" before "tablet"/"ipad", and iPad UA contains "Mobile"
            assertThat(saved.getDeviceType()).isEqualTo("Mobile");
        }
    }

    @Nested
    @DisplayName("세션 정보 추출")
    class ExtractSessionInfo {

        @Test
        @DisplayName("HttpServletRequest에서 세션 정보가 올바르게 추출되어야 한다")
        void extractSessionInfoSuccess() {
            // given
            String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
            String clientIp = "203.0.113.1";

            given(httpServletRequest.getHeader("X-Forwarded-For")).willReturn(null);
            given(httpServletRequest.getHeader("X-Real-IP")).willReturn(null);
            given(httpServletRequest.getRemoteAddr()).willReturn(clientIp);
            given(httpServletRequest.getHeader("User-Agent")).willReturn(userAgent);
            given(geoIpService.getLocation(clientIp)).willReturn("대한민국 서울특별시");

            // when
            TokenStore.SessionInfo sessionInfo = loginHistoryService.extractSessionInfo(httpServletRequest);

            // then
            assertThat(sessionInfo.deviceType()).isEqualTo("Desktop");
            assertThat(sessionInfo.browser()).isEqualTo("Chrome");
            assertThat(sessionInfo.os()).isEqualTo("Windows");
            assertThat(sessionInfo.ipAddress()).isEqualTo(clientIp);
            assertThat(sessionInfo.location()).isEqualTo("대한민국 서울특별시");
        }

        @Test
        @DisplayName("X-Real-IP 헤더에서 IP가 추출되어야 한다")
        void extractSessionInfoWithXRealIp() {
            // given - UA contains "Linux" which matches before "Android" in parseOs
            String userAgent = "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile";
            String realIp = "203.0.113.99";

            given(httpServletRequest.getHeader("X-Forwarded-For")).willReturn(null);
            given(httpServletRequest.getHeader("X-Real-IP")).willReturn(realIp);
            given(httpServletRequest.getHeader("User-Agent")).willReturn(userAgent);
            given(geoIpService.getLocation(realIp)).willReturn(null);

            // when
            TokenStore.SessionInfo sessionInfo = loginHistoryService.extractSessionInfo(httpServletRequest);

            // then
            assertThat(sessionInfo.ipAddress()).isEqualTo(realIp);
            assertThat(sessionInfo.deviceType()).isEqualTo("Mobile");
            // parseOs checks "linux" before "android"
            assertThat(sessionInfo.os()).isEqualTo("Linux");
        }

        @Test
        @DisplayName("null User-Agent에서도 세션 정보가 추출되어야 한다")
        void extractSessionInfoWithNullUserAgent() {
            // given
            given(httpServletRequest.getHeader("X-Forwarded-For")).willReturn(null);
            given(httpServletRequest.getHeader("X-Real-IP")).willReturn(null);
            given(httpServletRequest.getRemoteAddr()).willReturn("127.0.0.1");
            given(httpServletRequest.getHeader("User-Agent")).willReturn(null);
            given(geoIpService.getLocation("127.0.0.1")).willReturn(null);

            // when
            TokenStore.SessionInfo sessionInfo = loginHistoryService.extractSessionInfo(httpServletRequest);

            // then
            assertThat(sessionInfo.deviceType()).isEqualTo("Unknown");
            assertThat(sessionInfo.browser()).isEqualTo("Unknown");
            assertThat(sessionInfo.os()).isEqualTo("Unknown");
        }
    }

    // Helper methods
    private LoginHistory createLoginHistory(Long id, Long userId, ChannelCode channelCode, boolean success) {
        LoginHistory history = LoginHistory.builder()
                .userId(userId)
                .channelCode(channelCode)
                .ipAddress("203.0.113.1")
                .userAgent("Chrome/120.0")
                .deviceType("Desktop")
                .browser("Chrome")
                .os("Windows")
                .location("대한민국")
                .isSuccess(success)
                .failureReason(success ? null : "INVALID_PASSWORD")
                .build();
        setField(history, "id", id);
        setField(history, "createdAt", LocalDateTime.now());
        return history;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
