package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.service.PhoneVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PhoneVerificationController.class,
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
class PhoneVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PhoneVerificationService phoneVerificationService;

    @Nested
    @DisplayName("POST /api/v1/phone/send-verification")
    class SendVerification {

        @Test
        @DisplayName("인증 코드 발송 성공")
        void sendVerificationSuccess() throws Exception {
            // given
            given(phoneVerificationService.sendVerificationCode("01012345678")).willReturn("token-id-123");
            given(phoneVerificationService.getExpirationMinutes()).willReturn(3);

            String requestBody = """
                    {
                        "phone": "01012345678"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenId").value("token-id-123"))
                    .andExpect(jsonPath("$.message").value("인증 코드가 발송되었습니다"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(180));
        }

        @Test
        @DisplayName("잘못된 핸드폰 번호 형식 시 400")
        void sendVerificationInvalidPhone() throws Exception {
            String requestBody = """
                    {
                        "phone": "12345"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("핸드폰 번호 누락 시 400")
        void sendVerificationEmptyPhone() throws Exception {
            String requestBody = """
                    {
                        "phone": ""
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/send-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/phone/verify")
    class VerifyPhone {

        @Test
        @DisplayName("인증 코드 확인 성공")
        void verifyPhoneSuccess() throws Exception {
            // given
            given(phoneVerificationService.verifyCode("01012345678", "123456")).willReturn("verified-token-id");

            String requestBody = """
                    {
                        "phone": "01012345678",
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenId").value("verified-token-id"))
                    .andExpect(jsonPath("$.message").value("핸드폰 인증이 완료되었습니다"));
        }

        @Test
        @DisplayName("인증 코드 불일치 시 400")
        void verifyPhoneCodeMismatch() throws Exception {
            // given
            given(phoneVerificationService.verifyCode(anyString(), anyString()))
                    .willThrow(InvalidVerificationException.codeMismatch());

            String requestBody = """
                    {
                        "phone": "01012345678",
                        "code": "000000"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 코드 만료 시 400")
        void verifyPhoneCodeExpired() throws Exception {
            // given
            given(phoneVerificationService.verifyCode(anyString(), anyString()))
                    .willThrow(InvalidVerificationException.codeExpired());

            String requestBody = """
                    {
                        "phone": "01012345678",
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 검증 실패 - 인증 코드 누락 시 400")
        void verifyPhoneValidationError() throws Exception {
            String requestBody = """
                    {
                        "phone": "01012345678",
                        "code": ""
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/phone/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
