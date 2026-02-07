package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.service.OAuth2LinkStateService;
import com.jay.auth.service.OAuth2UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final OAuth2UserService oAuth2UserService;
    private final OAuth2LinkStateService oAuth2LinkStateService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        // OIDC와 UserInfo의 attributes를 병합
        Map<String, Object> attributes = new HashMap<>(oidcUser.getAttributes());
        if (oidcUser.getUserInfo() != null) {
            attributes.putAll(oidcUser.getUserInfo().getClaims());
        }

        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, attributes);
        ChannelCode channelCode = OAuth2UserInfoFactory.getChannelCode(registrationId);

        // Check if this is a link mode request by reading the cookie
        String linkState = getLinkStateFromCookie();
        Long linkUserId = linkState != null ? oAuth2LinkStateService.getLinkUserId(linkState) : null;
        boolean isLinkMode = linkUserId != null;

        log.debug("OIDC loadUser: linkState={}, linkUserId={}, isLinkMode={}", linkState, linkUserId, isLinkMode);

        User user;
        if (isLinkMode) {
            // Link mode: link social account to existing user
            user = oAuth2UserService.processOAuth2UserForLinking(linkUserId, channelCode, oAuth2UserInfo);
            log.info("OIDC user linked: registrationId={}, userId={}", registrationId, user.getId());
        } else {
            // Normal mode: login or create new user
            user = oAuth2UserService.processOAuth2User(channelCode, oAuth2UserInfo);
            log.info("OIDC user loaded: registrationId={}, userId={}", registrationId, user.getId());
        }

        return new CustomOidcUser(
                user.getId(),
                user.getUserUuid(),
                channelCode,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                attributes,
                userNameAttributeName,
                isLinkMode
        );
    }

    private String getLinkStateFromCookie() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            return Arrays.stream(cookies)
                    .filter(c -> OAuth2LinkController.LINK_STATE_COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Failed to get link state from cookie", e);
            return null;
        }
    }
}
