package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.dto.response.*;
import com.jay.auth.exception.PasskeyException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.PasskeyService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PasskeyController.class,
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
class PasskeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PasskeyService passkeyService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("POST /api/v1/passkey/register/options")
    class GetRegistrationOptions {

        @Test
        @DisplayName("패스키 등록 옵션 생성 성공")
        void getRegistrationOptionsSuccess() throws Exception {
            // given
            PasskeyRegistrationOptionsResponse response = PasskeyRegistrationOptionsResponse.builder()
                    .challenge("test-challenge")
                    .rp(PasskeyRegistrationOptionsResponse.RpInfo.builder()
                            .id("localhost")
                            .name("Authly")
                            .build())
                    .user(PasskeyRegistrationOptionsResponse.UserInfo.builder()
                            .id("dXVpZC0xMjM0")
                            .name("testuser")
                            .displayName("testuser")
                            .build())
                    .pubKeyCredParams(List.of(
                            PasskeyRegistrationOptionsResponse.PubKeyCredParam.builder()
                                    .type("public-key").alg(-7).build(),
                            PasskeyRegistrationOptionsResponse.PubKeyCredParam.builder()
                                    .type("public-key").alg(-257).build()
                    ))
                    .timeout(300000L)
                    .attestation("none")
                    .excludeCredentials(List.of())
                    .build();

            given(passkeyService.generateRegistrationOptions(1L)).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/passkey/register/options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.challenge").value("test-challenge"))
                    .andExpect(jsonPath("$.rp.id").value("localhost"))
                    .andExpect(jsonPath("$.rp.name").value("Authly"))
                    .andExpect(jsonPath("$.user.name").value("testuser"))
                    .andExpect(jsonPath("$.pubKeyCredParams.length()").value(2))
                    .andExpect(jsonPath("$.attestation").value("none"));
        }

        @Test
        @DisplayName("패스키 한도 초과 시 에러")
        void getRegistrationOptionsLimitExceeded() throws Exception {
            // given
            given(passkeyService.generateRegistrationOptions(1L))
                    .willThrow(PasskeyException.limitExceeded());

            // when & then
            mockMvc.perform(post("/api/v1/passkey/register/options"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/passkey/register/verify")
    class VerifyRegistration {

        @Test
        @DisplayName("패스키 등록 검증 성공")
        void verifyRegistrationSuccess() throws Exception {
            // given
            willDoNothing().given(passkeyService).verifyRegistration(eq(1L), any());

            String requestBody = """
                    {
                        "credentialId": "test-credential-id",
                        "attestationObject": "dGVzdC1hdHRlc3RhdGlvbg",
                        "clientDataJSON": "dGVzdC1jbGllbnQtZGF0YQ",
                        "transports": "internal",
                        "deviceName": "MacBook"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/passkey/register/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("패스키가 등록되었습니다"));
        }

        @Test
        @DisplayName("패스키 등록 검증 실패")
        void verifyRegistrationFailed() throws Exception {
            // given
            willThrow(PasskeyException.registrationFailed())
                    .given(passkeyService).verifyRegistration(eq(1L), any());

            String requestBody = """
                    {
                        "credentialId": "test-credential-id",
                        "attestationObject": "dGVzdC1hdHRlc3RhdGlvbg",
                        "clientDataJSON": "dGVzdC1jbGllbnQtZGF0YQ"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/passkey/register/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/passkey/login/options")
    class GetAuthenticationOptions {

        @Test
        @DisplayName("패스키 인증 옵션 생성 성공")
        void getAuthenticationOptionsSuccess() throws Exception {
            // given
            PasskeyAuthenticationOptionsResponse response = PasskeyAuthenticationOptionsResponse.builder()
                    .challenge("auth-challenge")
                    .timeout(300000L)
                    .rpId("localhost")
                    .allowCredentials(List.of())
                    .userVerification("preferred")
                    .build();

            given(passkeyService.generateAuthenticationOptions()).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/passkey/login/options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.challenge").value("auth-challenge"))
                    .andExpect(jsonPath("$.rpId").value("localhost"))
                    .andExpect(jsonPath("$.userVerification").value("preferred"))
                    .andExpect(jsonPath("$.allowCredentials").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/passkey/login/verify")
    class VerifyAuthentication {

        @Test
        @DisplayName("패스키 인증 성공")
        void verifyAuthenticationSuccess() throws Exception {
            // given
            TokenResponse tokenResponse = TokenResponse.builder()
                    .accessToken("access-token")
                    .refreshToken("refresh-token")
                    .expiresIn(1800000L)
                    .build();

            LoginResponse loginResponse = LoginResponse.of(
                    1L, "uuid-1234", "test@example.com", "testuser", tokenResponse);

            given(passkeyService.verifyAuthentication(any())).willReturn(loginResponse);

            String requestBody = """
                    {
                        "credentialId": "test-credential-id",
                        "authenticatorData": "dGVzdC1hdXRoLWRhdGE",
                        "clientDataJSON": "dGVzdC1jbGllbnQtZGF0YQ",
                        "signature": "dGVzdC1zaWduYXR1cmU"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/passkey/login/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.userUuid").value("uuid-1234"))
                    .andExpect(jsonPath("$.token.accessToken").value("access-token"));
        }

        @Test
        @DisplayName("패스키 인증 실패 - 존재하지 않는 패스키")
        void verifyAuthenticationNotFound() throws Exception {
            // given
            given(passkeyService.verifyAuthentication(any()))
                    .willThrow(PasskeyException.notFound());

            String requestBody = """
                    {
                        "credentialId": "unknown-credential",
                        "authenticatorData": "dGVzdC1hdXRoLWRhdGE",
                        "clientDataJSON": "dGVzdC1jbGllbnQtZGF0YQ",
                        "signature": "dGVzdC1zaWduYXR1cmU"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/auth/passkey/login/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/passkey/list")
    class ListPasskeys {

        @Test
        @DisplayName("패스키 목록 조회 성공")
        void listPasskeysSuccess() throws Exception {
            // given
            PasskeyListResponse response = PasskeyListResponse.builder()
                    .passkeys(List.of(
                            PasskeyListResponse.PasskeyInfo.builder()
                                    .id(1L)
                                    .deviceName("MacBook")
                                    .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                                    .build(),
                            PasskeyListResponse.PasskeyInfo.builder()
                                    .id(2L)
                                    .deviceName("iPhone")
                                    .createdAt(LocalDateTime.of(2026, 1, 2, 0, 0))
                                    .build()
                    ))
                    .build();

            given(passkeyService.listPasskeys(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/passkey/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passkeys.length()").value(2))
                    .andExpect(jsonPath("$.passkeys[0].deviceName").value("MacBook"))
                    .andExpect(jsonPath("$.passkeys[1].deviceName").value("iPhone"));
        }

        @Test
        @DisplayName("패스키 빈 목록 조회")
        void listPasskeysEmpty() throws Exception {
            // given
            PasskeyListResponse response = PasskeyListResponse.builder()
                    .passkeys(List.of())
                    .build();

            given(passkeyService.listPasskeys(1L)).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/passkey/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passkeys").isEmpty());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/passkey/{id}")
    class RenamePasskey {

        @Test
        @DisplayName("패스키 이름 변경 성공")
        void renamePasskeySuccess() throws Exception {
            // given
            willDoNothing().given(passkeyService).renamePasskey(1L, 10L, "새 이름");

            String requestBody = """
                    {
                        "deviceName": "새 이름"
                    }
                    """;

            // when & then
            mockMvc.perform(patch("/api/v1/passkey/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("패스키 이름이 변경되었습니다"));
        }

        @Test
        @DisplayName("존재하지 않는 패스키 이름 변경 시 에러")
        void renamePasskeyNotFound() throws Exception {
            // given
            willThrow(PasskeyException.notFound())
                    .given(passkeyService).renamePasskey(eq(1L), eq(999L), anyString());

            String requestBody = """
                    {
                        "deviceName": "새 이름"
                    }
                    """;

            // when & then
            mockMvc.perform(patch("/api/v1/passkey/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/passkey/{id}")
    class DeletePasskey {

        @Test
        @DisplayName("패스키 삭제 성공")
        void deletePasskeySuccess() throws Exception {
            // given
            willDoNothing().given(passkeyService).deletePasskey(1L, 10L);

            // when & then
            mockMvc.perform(delete("/api/v1/passkey/10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("존재하지 않는 패스키 삭제 시 에러")
        void deletePasskeyNotFound() throws Exception {
            // given
            willThrow(PasskeyException.notFound())
                    .given(passkeyService).deletePasskey(1L, 999L);

            // when & then
            mockMvc.perform(delete("/api/v1/passkey/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
