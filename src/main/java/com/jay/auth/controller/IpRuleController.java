package com.jay.auth.controller;

import com.jay.auth.dto.request.IpRuleCreateRequest;
import com.jay.auth.dto.response.IpRulePageResponse;
import com.jay.auth.dto.response.IpRuleResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.AuditLogService;
import com.jay.auth.service.IpAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - IP Rules", description = "IP 접근 규칙 관리 API")
@RestController
@RequestMapping("/api/v1/admin/ip-rules")
@RequiredArgsConstructor
public class IpRuleController {

    private final IpAccessService ipAccessService;
    private final AuditLogService auditLogService;

    @Operation(summary = "IP 규칙 목록 조회", description = "등록된 IP 허용/차단 규칙 목록을 페이징하여 조회합니다")
    @GetMapping
    public ResponseEntity<IpRulePageResponse> getRules(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_IP_RULE_LIST", "IP_RULE");
        Page<IpRuleResponse> result = ipAccessService.getRules(page, size);
        IpRulePageResponse response = IpRulePageResponse.builder()
                .rules(result.getContent())
                .currentPage(result.getNumber())
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "IP 규칙 생성", description = "IP 허용 또는 차단 규칙을 생성합니다. 동일 IP에 동일 타입 활성 규칙이 있으면 대체됩니다.")
    @PostMapping
    public ResponseEntity<IpRuleResponse> createRule(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @Valid @RequestBody IpRuleCreateRequest request) {
        IpRuleResponse response = ipAccessService.createRule(request, adminPrincipal.getUserId());
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_IP_RULE_CREATE", "IP_RULE",
                "ip=" + request.getIpAddress() + ", type=" + request.getRuleType(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "IP 규칙 삭제", description = "등록된 IP 규칙을 비활성화합니다")
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @PathVariable Long ruleId) {
        ipAccessService.deleteRule(ruleId, adminPrincipal.getUserId());
        auditLogService.log(adminPrincipal.getUserId(), "ADMIN_IP_RULE_DELETE", "IP_RULE",
                "ruleId=" + ruleId, true);
        return ResponseEntity.noContent().build();
    }
}
