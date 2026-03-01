package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.response.VerificationResponse;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AuthService;
import com.jay.auth.service.EmailVerificationService;
import com.jay.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = EmailVerificationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        com.jay.auth.config.RateLimitFilter.class,
                        com.jay.auth.config.RequestLoggingFilter.class,
                        com.jay.auth.config.SecurityHeadersFilter.class,
                        com.jay.auth.config.RequestIdFilter.class,
                        com.jay.auth.config.IpAccessFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class EmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private com.jay.auth.service.PostLoginVerificationService postLoginVerificationService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/email/send-verification")
    class SendVerification {

        @Test
        @DisplayName("회원가입 인증코드 발송 성공")
        void sendVerificationForSignupSuccess() throws Exception {
            // given
            given(authService.isEmailExists("test@email.com")).willReturn(false);
            given(emailVerificationService.sendVerificationCode("test@email.com", VerificationType.SIGNUP))
                    .willReturn("token-123");
            given(emailVerificationService.getExpirationMinutes()).willReturn(5);

            String requestBody = """
                    {
                        "email": "test@email.com",
                        "type": "SIGNUP"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenId").value("token-123"))
                    .andExpect(jsonPath("$.message").value("인증 코드가 발송되었습니다"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(300));
        }

        @Test
        @DisplayName("이미 가입된 이메일로 회원가입 인증코드 발송 시 409")
        void sendVerificationForSignupDuplicateEmail() throws Exception {
            // given
            given(authService.isEmailExists("test@email.com")).willReturn(true);

            String requestBody = """
                    {
                        "email": "test@email.com",
                        "type": "SIGNUP"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("비밀번호 재설정 인증코드 발송 성공")
        void sendVerificationForPasswordResetSuccess() throws Exception {
            // given
            given(userService.existsByRecoveryEmail("recovery@email.com")).willReturn(true);
            given(emailVerificationService.sendVerificationCode("recovery@email.com", VerificationType.PASSWORD_RESET))
                    .willReturn("token-456");
            given(emailVerificationService.getExpirationMinutes()).willReturn(5);

            String requestBody = """
                    {
                        "email": "recovery@email.com",
                        "type": "PASSWORD_RESET"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenId").value("token-456"));
        }

        @Test
        @DisplayName("복구 이메일 미등록 시 비밀번호 재설정 인증코드 발송 실패")
        void sendVerificationForPasswordResetEmailNotFound() throws Exception {
            // given
            given(userService.existsByRecoveryEmail("unknown@email.com")).willReturn(false);

            String requestBody = """
                    {
                        "email": "unknown@email.com",
                        "type": "PASSWORD_RESET"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("이메일 변경 인증코드 발송 성공")
        void sendVerificationForEmailChangeSuccess() throws Exception {
            // given
            given(emailVerificationService.sendVerificationCode("new@email.com", VerificationType.EMAIL_CHANGE))
                    .willReturn("token-789");
            given(emailVerificationService.getExpirationMinutes()).willReturn(5);

            String requestBody = """
                    {
                        "email": "new@email.com",
                        "type": "EMAIL_CHANGE"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenId").value("token-789"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/email/verify")
    class VerifyEmail {

        @Test
        @DisplayName("이메일 인증 코드 확인 성공")
        void verifyEmailSuccess() throws Exception {
            // given
            given(emailVerificationService.verifyCode("test@email.com", "123456", VerificationType.SIGNUP))
                    .willReturn("verified-token");

            String requestBody = """
                    {
                        "email": "test@email.com",
                        "code": "123456",
                        "type": "SIGNUP"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenId").value("verified-token"))
                    .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다"));
        }

        @Test
        @DisplayName("잘못된 인증 코드로 확인 실패")
        void verifyEmailInvalidCode() throws Exception {
            // given
            given(emailVerificationService.verifyCode("test@email.com", "000000", VerificationType.SIGNUP))
                    .willThrow(new IllegalArgumentException("인증 코드가 일치하지 않습니다"));

            String requestBody = """
                    {
                        "email": "test@email.com",
                        "code": "000000",
                        "type": "SIGNUP"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
