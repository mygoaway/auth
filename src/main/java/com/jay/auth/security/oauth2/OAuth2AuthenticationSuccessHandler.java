package com.jay.auth.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.TokenResponse;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        Long userId;
        String userUuid;
        ChannelCode channelCode;

        if (authentication.getPrincipal() instanceof CustomOidcUser oidcUser) {
            userId = oidcUser.getUserId();
            userUuid = oidcUser.getUserUuid();
            channelCode = oidcUser.getChannelCode();
        } else if (authentication.getPrincipal() instanceof CustomOAuth2User oAuth2User) {
            userId = oAuth2User.getUserId();
            userUuid = oAuth2User.getUserUuid();
            channelCode = oAuth2User.getChannelCode();
        } else {
            log.error("Unknown principal type: {}", authentication.getPrincipal().getClass());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        TokenResponse tokenResponse = tokenService.issueTokens(userId, userUuid, channelCode);

        log.info("OAuth2 authentication success: userId={}, channelCode={}", userId, channelCode);

        // Redirect with tokens as query parameters
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", tokenResponse.getAccessToken())
                .queryParam("refreshToken", tokenResponse.getRefreshToken())
                .queryParam("expiresIn", tokenResponse.getExpiresIn())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
