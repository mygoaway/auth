package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.OAuth2LinkStateService;
import com.jay.auth.service.SecurityNotificationService;
import com.jay.auth.service.TokenService;
import com.jay.auth.service.metrics.AuthMetrics;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenService tokenService;
    private final OAuth2LinkStateService oAuth2LinkStateService;
    private final LoginHistoryService loginHistoryService;
    private final SecurityNotificationService securityNotificationService;
    private final AuthMetrics authMetrics;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Value("${app.oauth2.link-success-uri:http://localhost:3000/oauth2/link/success}")
    private String linkSuccessUri;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        try {
            Long userId;
            String userUuid;
            ChannelCode channelCode;
            String role;
            boolean isLinkMode;
            boolean pendingDeletion;
            String deletionRequestedAt;

            if (authentication.getPrincipal() instanceof CustomOidcUser oidcUser) {
                userId = oidcUser.getUserId();
                userUuid = oidcUser.getUserUuid();
                channelCode = oidcUser.getChannelCode();
                role = oidcUser.getRole();
                isLinkMode = oidcUser.isLinkMode();
                pendingDeletion = oidcUser.isPendingDeletion();
                deletionRequestedAt = oidcUser.getDeletionRequestedAt() != null
                        ? oidcUser.getDeletionRequestedAt().toString() : null;
            } else if (authentication.getPrincipal() instanceof CustomOAuth2User oAuth2User) {
                userId = oAuth2User.getUserId();
                userUuid = oAuth2User.getUserUuid();
                channelCode = oAuth2User.getChannelCode();
                role = oAuth2User.getRole();
                isLinkMode = oAuth2User.isLinkMode();
                pendingDeletion = oAuth2User.isPendingDeletion();
                deletionRequestedAt = oAuth2User.getDeletionRequestedAt() != null
                        ? oAuth2User.getDeletionRequestedAt().toString() : null;
            } else {
                log.error("Unknown principal type: {}", authentication.getPrincipal().getClass());
                redirectWithError(request, response, "Unknown authentication type");
                return;
            }

            // Clean up link state if exists
            String state = request.getParameter("state");
            oAuth2LinkStateService.removeLinkState(state);

            // Clear link state cookie
            clearLinkStateCookie(response);

            if (isLinkMode) {
                // Link mode: don't issue new tokens, just redirect to success page
                log.info("OAuth2 account linking success: userId={}, channelCode={}", userId, channelCode);

                String targetUrl = UriComponentsBuilder.fromUriString(linkSuccessUri)
                        .queryParam("channelCode", channelCode.name())
                        .queryParam("success", "true")
                        .build().toUriString();

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
            } else {
                // Normal login mode: extract session info and issue tokens
                var sessionInfo = loginHistoryService.extractSessionInfo(request);
                TokenResponse tokenResponse = tokenService.issueTokensWithSession(
                        userId, userUuid, channelCode, role, sessionInfo);

                // Record login history
                loginHistoryService.recordLoginSuccess(userId, channelCode, request);

                // Record login metric
                authMetrics.recordLoginSuccess(channelCode.name());

                // Send new device login notification
                securityNotificationService.notifyNewDeviceLogin(userId, sessionInfo);

                log.info("OAuth2 authentication success: userId={}, channelCode={}, pendingDeletion={}",
                        userId, channelCode, pendingDeletion);

                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(redirectUri)
                        .queryParam("accessToken", tokenResponse.getAccessToken())
                        .queryParam("refreshToken", tokenResponse.getRefreshToken())
                        .queryParam("expiresIn", tokenResponse.getExpiresIn());

                if (pendingDeletion) {
                    uriBuilder.queryParam("pendingDeletion", "true");
                    if (deletionRequestedAt != null) {
                        uriBuilder.queryParam("deletionRequestedAt", deletionRequestedAt);
                    }
                }

                String targetUrl = uriBuilder.build().toUriString();

                getRedirectStrategy().sendRedirect(request, response, targetUrl);
            }
        } catch (Exception e) {
            log.error("OAuth2 authentication success handler failed", e);
            redirectWithError(request, response, e.getMessage());
        }
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response, String errorMessage) throws IOException {
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", URLEncoder.encode(errorMessage != null ? errorMessage : "Unknown error", StandardCharsets.UTF_8))
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void clearLinkStateCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(OAuth2LinkController.LINK_STATE_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
