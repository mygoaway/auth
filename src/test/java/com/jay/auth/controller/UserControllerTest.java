package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private com.jay.auth.service.AccountLinkingService accountLinkingService;

    @MockitoBean
    private com.jay.auth.service.LoginHistoryService loginHistoryService;

    @MockitoBean
    private com.jay.auth.service.TokenService tokenService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("GET /api/v1/users/profile - 프로필 조회 성공")
    void getProfileSuccess() throws Exception {
        // given
        UserProfileResponse response = UserProfileResponse.builder()
                .userUuid("uuid-1234")
                .email("test@email.com")
                .nickname("테스트")
                .status("ACTIVE")
                .channels(List.of(
                        UserProfileResponse.ChannelInfo.builder()
                                .channelCode("EMAIL")
                                .channelEmail("test@email.com")
                                .linkedAt(LocalDateTime.now())
                                .build()
                ))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(userService.getProfile(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userUuid").value("uuid-1234"))
                .andExpect(jsonPath("$.email").value("test@email.com"))
                .andExpect(jsonPath("$.nickname").value("테스트"))
                .andExpect(jsonPath("$.channels[0].channelCode").value("EMAIL"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/profile/nickname - 닉네임 변경 성공")
    void updateNicknameSuccess() throws Exception {
        // given
        String requestBody = """
                {
                    "nickname": "새닉네임"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/users/profile/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userService).updateNickname(eq(1L), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/profile/phone - 핸드폰 번호 변경 성공")
    void updatePhoneSuccess() throws Exception {
        // given
        String requestBody = """
                {
                    "phone": "010-1234-5678",
                    "tokenId": "token-123"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/users/profile/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userService).updatePhone(eq(1L), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/users/profile/recovery-email - 복구 이메일 변경 성공")
    void updateRecoveryEmailSuccess() throws Exception {
        // given
        String requestBody = """
                {
                    "recoveryEmail": "recovery@email.com",
                    "tokenId": "token-123"
                }
                """;

        // when & then
        mockMvc.perform(patch("/api/v1/users/profile/recovery-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userService).updateRecoveryEmail(eq(1L), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - 회원 탈퇴 성공")
    void deleteAccountSuccess() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(1L);
    }
}
