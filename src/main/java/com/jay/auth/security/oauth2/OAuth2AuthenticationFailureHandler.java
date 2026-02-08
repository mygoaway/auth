package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Value("${app.oauth2.link-success-uri:http://localhost:3000/oauth2/link/success}")
    private String linkSuccessUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        log.error("OAuth2 authentication failed: {}", exception.getMessage());

        // Check if this was a link mode request
        boolean isLinkMode = hasLinkStateCookie(request);

        // Clear link state cookie
        clearLinkStateCookie(response);

        String baseUri = isLinkMode ? linkSuccessUri : redirectUri;
        String targetUrl = UriComponentsBuilder.fromUriString(baseUri)
                .queryParam("error", URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8))
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private boolean hasLinkStateCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        return Arrays.stream(cookies)
                .anyMatch(c -> OAuth2LinkController.LINK_STATE_COOKIE_NAME.equals(c.getName()));
    }

    private void clearLinkStateCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(OAuth2LinkController.LINK_STATE_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
