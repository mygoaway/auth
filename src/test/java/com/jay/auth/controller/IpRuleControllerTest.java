package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jay.auth.domain.enums.IpRuleType;
import com.jay.auth.dto.request.IpRuleCreateRequest;
import com.jay.auth.dto.response.IpRulePageResponse;
import com.jay.auth.dto.response.IpRuleResponse;
import com.jay.auth.exception.IpRuleNotFoundException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AuditLogService;
import com.jay.auth.service.IpAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = IpRuleController.class,
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
class IpRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private IpAccessService ipAccessService;

    @MockitoBean
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        UserPrincipal adminPrincipal = new UserPrincipal(1L, "admin-uuid", "ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("GET /api/v1/admin/ip-rules")
    class GetRules {

        @Test
        @DisplayName("IP 규칙 목록 조회 성공")
        void getRulesSuccess() throws Exception {
            IpRuleResponse rule = IpRuleResponse.builder()
                    .ruleId(1L)
                    .ipAddress("1.2.3.4")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("테스트 차단")
                    .isActive(true)
                    .createdAt("2026-01-01 00:00:00")
                    .build();

            Page<IpRuleResponse> page = new PageImpl<>(List.of(rule), PageRequest.of(0, 20), 1);
            given(ipAccessService.getRules(0, 20)).willReturn(page);

            mockMvc.perform(get("/api/v1/admin/ip-rules")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rules.length()").value(1))
                    .andExpect(jsonPath("$.rules[0].ipAddress").value("1.2.3.4"))
                    .andExpect(jsonPath("$.rules[0].ruleType").value("BLOCK"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("규칙이 없을 때 빈 목록을 반환한다")
        void getRulesEmpty() throws Exception {
            Page<IpRuleResponse> emptyPage = new PageImpl<>(
                    Collections.emptyList(), PageRequest.of(0, 20), 0);
            given(ipAccessService.getRules(0, 20)).willReturn(emptyPage);

            mockMvc.perform(get("/api/v1/admin/ip-rules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rules.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/ip-rules")
    class CreateRule {

        @Test
        @DisplayName("BLOCK 규칙 생성 성공 — 201 반환")
        void createBlockRuleSuccess() throws Exception {
            IpRuleCreateRequest request = new IpRuleCreateRequest();
            org.springframework.test.util.ReflectionTestUtils.setField(request, "ipAddress", "5.6.7.8");
            org.springframework.test.util.ReflectionTestUtils.setField(request, "ruleType", IpRuleType.BLOCK);
            org.springframework.test.util.ReflectionTestUtils.setField(request, "reason", "악성 IP");
            org.springframework.test.util.ReflectionTestUtils.setField(request, "expiredAt", null);

            IpRuleResponse response = IpRuleResponse.builder()
                    .ruleId(2L)
                    .ipAddress("5.6.7.8")
                    .ruleType(IpRuleType.BLOCK)
                    .reason("악성 IP")
                    .isActive(true)
                    .createdAt("2026-01-01 00:00:00")
                    .build();

            given(ipAccessService.createRule(any(), eq(1L))).willReturn(response);

            mockMvc.perform(post("/api/v1/admin/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.ruleId").value(2))
                    .andExpect(jsonPath("$.ipAddress").value("5.6.7.8"))
                    .andExpect(jsonPath("$.ruleType").value("BLOCK"));
        }

        @Test
        @DisplayName("ipAddress가 없으면 400 반환")
        void createRuleMissingIp() throws Exception {
            IpRuleCreateRequest request = new IpRuleCreateRequest();
            org.springframework.test.util.ReflectionTestUtils.setField(request, "ipAddress", "");
            org.springframework.test.util.ReflectionTestUtils.setField(request, "ruleType", IpRuleType.BLOCK);

            mockMvc.perform(post("/api/v1/admin/ip-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/ip-rules/{ruleId}")
    class DeleteRule {

        @Test
        @DisplayName("규칙 삭제 성공 — 204 반환")
        void deleteRuleSuccess() throws Exception {
            willDoNothing().given(ipAccessService).deleteRule(eq(1L), eq(1L));

            mockMvc.perform(delete("/api/v1/admin/ip-rules/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("존재하지 않는 규칙 삭제 시 404 반환")
        void deleteRuleNotFound() throws Exception {
            willThrow(new IpRuleNotFoundException())
                    .given(ipAccessService).deleteRule(eq(999L), anyLong());

            mockMvc.perform(delete("/api/v1/admin/ip-rules/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
