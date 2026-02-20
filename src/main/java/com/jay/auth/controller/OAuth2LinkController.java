package com.jay.auth.controller;

import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.OAuth2LinkStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "OAuth2 Link", description = "OAuth2 계정 연동 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2/link")
@RequiredArgsConstructor
public class OAuth2LinkController {

    public static final String LINK_STATE_COOKIE_NAME = "oauth2_link_state";
    private static final int COOKIE_MAX_AGE = 300; // 5 minutes

    private final OAuth2LinkStateService oAuth2LinkStateService;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @Operation(summary = "OAuth2 연동 상태 확인", description = "연동을 위한 state 값을 생성합니다 (API 호출용)")
    @PostMapping("/prepare/{provider}")
    public ResponseEntity<Map<String, String>> prepareLink(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String provider,
            HttpServletResponse response) {

        // Generate a unique state for this link request
        String state = UUID.randomUUID().toString();

        // Save the link state with user ID
        oAuth2LinkStateService.saveLinkState(state, userPrincipal.getUserId());

        // Set cookie with link state
        addLinkStateCookie(response, state);

        log.info("Prepared OAuth2 link: userId={}, provider={}, state={}",
                userPrincipal.getUserId(), provider, state);

        return ResponseEntity.ok(Map.of(
                "state", state,
                "authorizationUrl", String.format("/oauth2/authorization/%s?link_state=%s", provider, state)
        ));
    }

    private void addLinkStateCookie(HttpServletResponse response, String state) {
        Cookie cookie = new Cookie(LINK_STATE_COOKIE_NAME, state);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setSecure(secureCookie);
        response.addCookie(cookie);
    }
}
