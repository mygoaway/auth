package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.dto.response.TwoFactorSetupResponse;
import com.jay.auth.dto.response.TwoFactorStatusResponse;
import com.jay.auth.exception.TwoFactorException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.TotpService;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TwoFactorController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        com.jay.auth.config.RateLimitFilter.class,
                        com.jay.auth.config.RequestLoggingFilter.class,
                        com.jay.auth.config.SecurityHeadersFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class TwoFactorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TotpService totpService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("GET /api/v1/2fa/status")
    class GetStatus {

        @Test
        @DisplayName("2FA 상태 조회 성공")
        void getStatusSuccess() throws Exception {
            // given
            TwoFactorStatusResponse response = TwoFactorStatusResponse.builder()
                    .enabled(true)
                    .remainingBackupCodes(5)
                    .lastUsedAt(LocalDateTime.now())
                    .build();

            given(totpService.getTwoFactorStatus(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/2fa/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.remainingBackupCodes").value(5));
        }

        @Test
        @DisplayName("2FA 미설정 상태 조회")
        void getStatusNotEnabled() throws Exception {
            // given
            TwoFactorStatusResponse response = TwoFactorStatusResponse.builder()
                    .enabled(false)
                    .remainingBackupCodes(0)
                    .build();

            given(totpService.getTwoFactorStatus(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/2fa/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.remainingBackupCodes").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/2fa/setup")
    class Setup {

        @Test
        @DisplayName("2FA 설정 시작 성공")
        void setupSuccess() throws Exception {
            // given
            TwoFactorSetupResponse response = TwoFactorSetupResponse.builder()
                    .secret("JBSWY3DPEHPK3PXP")
                    .qrCodeDataUrl("data:image/png;base64,test")
                    .build();

            given(totpService.setupTwoFactor(1L)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/2fa/setup"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.secret").value("JBSWY3DPEHPK3PXP"))
                    .andExpect(jsonPath("$.qrCodeDataUrl").value("data:image/png;base64,test"));
        }

        @Test
        @DisplayName("2FA 이미 활성화된 경우 에러")
        void setupAlreadyEnabled() throws Exception {
            // given
            given(totpService.setupTwoFactor(1L)).willThrow(TwoFactorException.alreadyEnabled());

            // when & then
            mockMvc.perform(post("/api/v1/2fa/setup"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/2fa/enable")
    class Enable {

        @Test
        @DisplayName("2FA 활성화 성공")
        void enableSuccess() throws Exception {
            // given
            List<String> backupCodes = List.of("11111111", "22222222", "33333333");
            given(totpService.enableTwoFactor(1L, "123456")).willReturn(backupCodes);

            String requestBody = """
                    {
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/enable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("2단계 인증이 활성화되었습니다"))
                    .andExpect(jsonPath("$.backupCodes").isArray())
                    .andExpect(jsonPath("$.backupCodes.length()").value(3));
        }

        @Test
        @DisplayName("잘못된 코드로 활성화 실패")
        void enableInvalidCode() throws Exception {
            // given
            given(totpService.enableTwoFactor(1L, "000000")).willThrow(TwoFactorException.invalidCode());

            String requestBody = """
                    {
                        "code": "000000"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/enable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/2fa/disable")
    class Disable {

        @Test
        @DisplayName("2FA 비활성화 성공")
        void disableSuccess() throws Exception {
            // given
            willDoNothing().given(totpService).disableTwoFactor(1L, "123456");

            String requestBody = """
                    {
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("2FA 미활성화 상태에서 비활성화 시도 시 에러")
        void disableNotEnabled() throws Exception {
            // given
            willThrow(TwoFactorException.notEnabled()).given(totpService).disableTwoFactor(anyLong(), anyString());

            String requestBody = """
                    {
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/disable")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/2fa/verify")
    class Verify {

        @Test
        @DisplayName("2FA 코드 검증 성공")
        void verifySuccess() throws Exception {
            // given
            given(totpService.verifyCode(1L, "123456")).willReturn(true);

            String requestBody = """
                    {
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true));
        }

        @Test
        @DisplayName("2FA 코드 검증 실패")
        void verifyFailed() throws Exception {
            // given
            given(totpService.verifyCode(1L, "000000")).willReturn(false);

            String requestBody = """
                    {
                        "code": "000000"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/2fa/backup-codes/regenerate")
    class RegenerateBackupCodes {

        @Test
        @DisplayName("백업 코드 재생성 성공")
        void regenerateSuccess() throws Exception {
            // given
            List<String> backupCodes = List.of("aaaa1111", "bbbb2222", "cccc3333");
            given(totpService.regenerateBackupCodes(1L, "123456")).willReturn(backupCodes);

            String requestBody = """
                    {
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/backup-codes/regenerate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("백업 코드가 재생성되었습니다"))
                    .andExpect(jsonPath("$.backupCodes").isArray())
                    .andExpect(jsonPath("$.backupCodes.length()").value(3));
        }

        @Test
        @DisplayName("2FA 미활성화 상태에서 백업 코드 재생성 시 에러")
        void regenerateNotEnabled() throws Exception {
            // given
            given(totpService.regenerateBackupCodes(anyLong(), anyString()))
                    .willThrow(TwoFactorException.notEnabled());

            String requestBody = """
                    {
                        "code": "123456"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/2fa/backup-codes/regenerate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }
}
