package com.jay.auth.security.oauth2;

import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.service.LoginHistoryService;
import com.jay.auth.service.OAuth2LinkStateService;
import com.jay.auth.service.SecurityNotificationService;
import com.jay.auth.service.TokenService;
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

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Value("${app.oauth2.link-success-uri:http://localhost:3000/oauth2/link/success}")
    private String linkSuccessUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        try {
            Long userId;
            String userUuid;
            ChannelCode channelCode;
            boolean isLinkMode;

            if (authentication.getPrincipal() instanceof CustomOidcUser oidcUser) {
                userId = oidcUser.getUserId();
                userUuid = oidcUser.getUserUuid();
                channelCode = oidcUser.getChannelCode();
                isLinkMode = oidcUser.isLinkMode();
            } else if (authentication.getPrincipal() instanceof CustomOAuth2User oAuth2User) {
                userId = oAuth2User.getUserId();
                userUuid = oAuth2User.getUserUuid();
                channelCode = oAuth2User.getChannelCode();
                isLinkMode = oAuth2User.isLinkMode();
            } else {
                log.error("Unknown principal type: {}", authentication.getPrincipal().getClass());
                redirectWithError(request, response, "Unknown authentication type");
                return;
            }

            // Clean up link state if exists
            String state = request.getParameter("state");
            oAuth2LinkStateService.removeLinkState(state);

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
                        userId, userUuid, channelCode, sessionInfo);

                // Record login history
                loginHistoryService.recordLoginSuccess(userId, channelCode, request);

                // Send new device login notification
                securityNotificationService.notifyNewDeviceLogin(userId, sessionInfo);

                log.info("OAuth2 authentication success: userId={}, channelCode={}", userId, channelCode);

                String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                        .queryParam("accessToken", tokenResponse.getAccessToken())
                        .queryParam("refreshToken", tokenResponse.getRefreshToken())
                        .queryParam("expiresIn", tokenResponse.getExpiresIn())
                        .build().toUriString();

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
}
