package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.exception.AccountLinkingException;
import com.jay.auth.service.OAuth2LinkStateService;
import com.jay.auth.service.OAuth2UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2UserService oAuth2UserService;
    private final OAuth2LinkStateService oAuth2LinkStateService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, oAuth2User.getAttributes());

        ChannelCode channelCode = OAuth2UserInfoFactory.getChannelCode(registrationId);

        // Check if this is a link mode request by reading the cookie
        String linkState = getLinkStateFromCookie();
        Long linkUserId = linkState != null ? oAuth2LinkStateService.getLinkUserId(linkState) : null;
        boolean isLinkMode = linkUserId != null;

        log.debug("OAuth2 loadUser: linkState={}, linkUserId={}, isLinkMode={}", linkState, linkUserId, isLinkMode);

        User user;
        try {
            if (isLinkMode) {
                // Link mode: link social account to existing user
                user = oAuth2UserService.processOAuth2UserForLinking(linkUserId, channelCode, oAuth2UserInfo);
                log.info("OAuth2 user linked: registrationId={}, userId={}", registrationId, user.getId());
            } else {
                // Normal mode: login or create new user
                user = oAuth2UserService.processOAuth2User(channelCode, oAuth2UserInfo);
                log.info("OAuth2 user loaded: registrationId={}, userId={}", registrationId, user.getId());
            }
        } catch (AccountLinkingException e) {
            log.warn("OAuth2 account linking failed: {}", e.getMessage());
            String errorMessage = "해당 소셜 계정은 이미 다른 계정에 연동되어 있습니다";
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_linking_error", errorMessage, null),
                    errorMessage, e);
        } catch (IllegalStateException e) {
            log.warn("OAuth2 authentication failed: {}", e.getMessage());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_error", e.getMessage(), null),
                    e.getMessage(), e);
        }

        boolean pendingDeletion = user.getStatus() == UserStatus.PENDING_DELETE;

        return new CustomOAuth2User(
                user.getId(),
                user.getUserUuid(),
                channelCode,
                oAuth2User.getAttributes(),
                userNameAttributeName,
                isLinkMode,
                pendingDeletion,
                user.getDeletionRequestedAt()
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
