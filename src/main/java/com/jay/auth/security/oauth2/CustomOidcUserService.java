package com.jay.auth.security.oauth2;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.service.OAuth2UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final OAuth2UserService oAuth2UserService;

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

        User user = oAuth2UserService.processOAuth2User(channelCode, oAuth2UserInfo);

        log.info("OIDC user loaded: registrationId={}, userId={}", registrationId, user.getId());

        return new CustomOidcUser(
                user.getId(),
                user.getUserUuid(),
                channelCode,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                attributes,
                userNameAttributeName
        );
    }
}
