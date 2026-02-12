package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.OAuth2LinkStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = OAuth2LinkController.class,
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
class OAuth2LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OAuth2LinkStateService oAuth2LinkStateService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("POST /api/v1/oauth2/link/prepare/{provider}")
    class PrepareLink {

        @Test
        @DisplayName("OAuth2 연동 준비 성공")
        void prepareLinkSuccess() throws Exception {
            // given
            willDoNothing().given(oAuth2LinkStateService).saveLinkState(anyString(), anyLong());

            // when & then
            mockMvc.perform(post("/api/v1/oauth2/link/prepare/google"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").isNotEmpty())
                    .andExpect(jsonPath("$.authorizationUrl").value(
                            org.hamcrest.Matchers.containsString("/oauth2/authorization/google")))
                    .andExpect(cookie().exists("oauth2_link_state"));
        }

        @Test
        @DisplayName("Kakao 연동 준비 성공")
        void prepareLinkKakaoSuccess() throws Exception {
            // given
            willDoNothing().given(oAuth2LinkStateService).saveLinkState(anyString(), anyLong());

            // when & then
            mockMvc.perform(post("/api/v1/oauth2/link/prepare/kakao"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").isNotEmpty())
                    .andExpect(jsonPath("$.authorizationUrl").value(
                            org.hamcrest.Matchers.containsString("/oauth2/authorization/kakao")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/oauth2/link/start/{provider}")
    class StartLink {

        @Test
        @DisplayName("OAuth2 연동 시작 - 리다이렉트")
        void startLinkRedirect() throws Exception {
            // given
            willDoNothing().given(oAuth2LinkStateService).saveLinkState(anyString(), anyLong());

            // when & then
            mockMvc.perform(get("/api/v1/oauth2/link/start/google"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("/oauth2/authorization/google?link_state=*"))
                    .andExpect(cookie().exists("oauth2_link_state"));
        }

        @Test
        @DisplayName("Naver 연동 시작 - 리다이렉트")
        void startLinkNaverRedirect() throws Exception {
            // given
            willDoNothing().given(oAuth2LinkStateService).saveLinkState(anyString(), anyLong());

            // when & then
            mockMvc.perform(get("/api/v1/oauth2/link/start/naver"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("/oauth2/authorization/naver?link_state=*"))
                    .andExpect(cookie().exists("oauth2_link_state"));
        }
    }
}
