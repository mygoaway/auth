package com.jay.auth.controller;

import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.OAuth2LinkStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Tag(name = "OAuth2 Link", description = "OAuth2 계정 연동 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2/link")
@RequiredArgsConstructor
public class OAuth2LinkController {

    private final OAuth2LinkStateService oAuth2LinkStateService;

    @Operation(summary = "OAuth2 연동 시작", description = "소셜 계정 연동을 위한 OAuth2 인증을 시작합니다")
    @GetMapping("/start/{provider}")
    public void startLink(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String provider,
            HttpServletResponse response) throws IOException {

        // Generate a unique state for this link request
        String state = UUID.randomUUID().toString();

        // Save the link state with user ID
        oAuth2LinkStateService.saveLinkState(state, userPrincipal.getUserId());

        // Redirect to OAuth2 authorization endpoint with link state
        String redirectUrl = String.format("/oauth2/authorization/%s?link_state=%s", provider, state);

        log.info("Starting OAuth2 link: userId={}, provider={}, state={}",
                userPrincipal.getUserId(), provider, state);

        response.sendRedirect(redirectUrl);
    }

    @Operation(summary = "OAuth2 연동 상태 확인", description = "연동을 위한 state 값을 생성합니다 (API 호출용)")
    @PostMapping("/prepare/{provider}")
    public ResponseEntity<Map<String, String>> prepareLink(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String provider) {

        // Generate a unique state for this link request
        String state = UUID.randomUUID().toString();

        // Save the link state with user ID
        oAuth2LinkStateService.saveLinkState(state, userPrincipal.getUserId());

        log.info("Prepared OAuth2 link: userId={}, provider={}, state={}",
                userPrincipal.getUserId(), provider, state);

        return ResponseEntity.ok(Map.of(
                "state", state,
                "authorizationUrl", String.format("/oauth2/authorization/%s?link_state=%s", provider, state)
        ));
    }
}
