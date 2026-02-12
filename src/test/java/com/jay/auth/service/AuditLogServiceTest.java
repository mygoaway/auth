package com.jay.auth.service;

import com.jay.auth.domain.entity.AuditLog;
import com.jay.auth.repository.AuditLogRepository;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @InjectMocks
    private AuditLogService auditLogService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

    @Nested
    @DisplayName("사용자 행동 로그 기록")
    class LogUserAction {

        @Test
        @DisplayName("상세 정보와 함께 감사 로그가 저장되어야 한다")
        void logWithDetailAndSuccess() {
            // given
            Long userId = 1L;
            String action = "LOGIN";
            String target = "AUTH";
            String detail = "Email login success";
            boolean success = true;

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(userId, action, target, detail, success);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getUserId()).isEqualTo(userId);
            assertThat(savedLog.getAction()).isEqualTo(action);
            assertThat(savedLog.getTarget()).isEqualTo(target);
            assertThat(savedLog.getDetail()).isEqualTo(detail);
            assertThat(savedLog.getIsSuccess()).isTrue();
        }

        @Test
        @DisplayName("실패 로그가 저장되어야 한다")
        void logFailureEvent() {
            // given
            Long userId = 1L;
            String action = "LOGIN";
            String target = "AUTH";
            String detail = "Invalid password";
            boolean success = false;

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(userId, action, target, detail, success);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getIsSuccess()).isFalse();
            assertThat(savedLog.getDetail()).isEqualTo("Invalid password");
        }

        @Test
        @DisplayName("userId가 null이어도 로그가 저장되어야 한다")
        void logWithNullUserId() {
            // given
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(null, "SYSTEM_EVENT", "SYSTEM", "Batch job completed", true);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getUserId()).isNull();
            assertThat(savedLog.getAction()).isEqualTo("SYSTEM_EVENT");
        }
    }

    @Nested
    @DisplayName("간소화 로그 기록")
    class LogSimple {

        @Test
        @DisplayName("detail 없이 성공 로그가 저장되어야 한다")
        void logWithoutDetail() {
            // given
            Long userId = 1L;
            String action = "PROFILE_VIEW";
            String target = "USER";

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(userId, action, target);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getUserId()).isEqualTo(userId);
            assertThat(savedLog.getAction()).isEqualTo(action);
            assertThat(savedLog.getTarget()).isEqualTo(target);
            assertThat(savedLog.getDetail()).isNull();
            assertThat(savedLog.getIsSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("보안 이벤트 로그 기록")
    class LogSecurityEvent {

        @Test
        @DisplayName("보안 관련 이벤트가 저장되어야 한다")
        void logSecurityEvent() {
            // given
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(1L, "2FA_ENABLED", "SECURITY", "TOTP 2FA enabled", true);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getAction()).isEqualTo("2FA_ENABLED");
            assertThat(savedLog.getTarget()).isEqualTo("SECURITY");
        }
    }

    @Nested
    @DisplayName("관리자 행동 로그 기록")
    class LogAdminAction {

        @Test
        @DisplayName("관리자 행동이 기록되어야 한다")
        void logAdminAction() {
            // given
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(1L, "USER_ROLE_CHANGE", "ADMIN",
                    "targetUserId=2, newRole=ADMIN", true);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getAction()).isEqualTo("USER_ROLE_CHANGE");
            assertThat(savedLog.getTarget()).isEqualTo("ADMIN");
            assertThat(savedLog.getDetail()).contains("targetUserId=2");
        }
    }

    @Nested
    @DisplayName("시스템 이벤트 로그 기록")
    class LogSystemEvent {

        @Test
        @DisplayName("시스템 이벤트가 userId 없이 기록되어야 한다")
        void logSystemEvent() {
            // given
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(null, "CLEANUP_BATCH", "SYSTEM", "Deleted 5 expired accounts", true);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getUserId()).isNull();
            assertThat(savedLog.getAction()).isEqualTo("CLEANUP_BATCH");
            assertThat(savedLog.getTarget()).isEqualTo("SYSTEM");
        }
    }

    @Nested
    @DisplayName("RequestContext 없이 로그 기록")
    class LogWithoutRequestContext {

        @Test
        @DisplayName("RequestContext가 없으면 IP와 UserAgent가 null로 저장되어야 한다")
        void logWithoutHttpRequest() {
            // given
            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            auditLogService.log(1L, "BATCH_JOB", "SYSTEM", "Scheduled task", true);

            // then
            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getIpAddress()).isNull();
            assertThat(savedLog.getUserAgent()).isNull();
        }
    }

    @Nested
    @DisplayName("RequestContext 있을 때 로그 기록")
    class LogWithRequestContext {

        @Test
        @DisplayName("RequestContext가 있으면 IP와 UserAgent가 저장되어야 한다")
        void logWithHttpRequest() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.100");
            request.addHeader("User-Agent", "Mozilla/5.0 Chrome/120");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            try {
                // when
                auditLogService.log(1L, "LOGIN", "AUTH", "Login success", true);

                // then
                verify(auditLogRepository).save(auditLogCaptor.capture());
                AuditLog savedLog = auditLogCaptor.getValue();
                assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.100");
                assertThat(savedLog.getUserAgent()).isEqualTo("Mozilla/5.0 Chrome/120");
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        @DisplayName("X-Forwarded-For 헤더가 있으면 해당 IP가 저장되어야 한다")
        void logWithXForwardedForHeader() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1");
            request.addHeader("User-Agent", "TestAgent");
            request.setRemoteAddr("192.168.1.100");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            try {
                // when
                auditLogService.log(1L, "LOGIN", "AUTH", "Login via proxy", true);

                // then
                verify(auditLogRepository).save(auditLogCaptor.capture());
                AuditLog savedLog = auditLogCaptor.getValue();
                assertThat(savedLog.getIpAddress()).isEqualTo("10.0.0.1");
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        @DisplayName("X-Forwarded-For에 여러 IP가 있으면 첫 번째 IP가 저장되어야 한다")
        void logWithMultipleXForwardedForIps() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3");
            request.addHeader("User-Agent", "TestAgent");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            try {
                // when
                auditLogService.log(1L, "LOGIN", "AUTH", "Multiple proxies", true);

                // then
                verify(auditLogRepository).save(auditLogCaptor.capture());
                AuditLog savedLog = auditLogCaptor.getValue();
                assertThat(savedLog.getIpAddress()).isEqualTo("10.0.0.1");
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        @DisplayName("X-Real-IP 헤더가 있으면 해당 IP가 저장되어야 한다")
        void logWithXRealIpHeader() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Real-IP", "172.16.0.1");
            request.addHeader("User-Agent", "TestAgent");
            request.setRemoteAddr("192.168.1.100");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> invocation.getArgument(0));

            try {
                // when
                auditLogService.log(1L, "LOGIN", "AUTH", "Via X-Real-IP", true);

                // then
                verify(auditLogRepository).save(auditLogCaptor.capture());
                AuditLog savedLog = auditLogCaptor.getValue();
                assertThat(savedLog.getIpAddress()).isEqualTo("172.16.0.1");
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }
}
