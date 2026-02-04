package com.jay.auth.security.oauth2;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.service.OAuth2UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2UserService oAuth2UserService;

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

        User user = oAuth2UserService.processOAuth2User(channelCode, oAuth2UserInfo);

        log.info("OAuth2 user loaded: registrationId={}, userId={}", registrationId, user.getId());

        return new CustomOAuth2User(
                user.getId(),
                user.getUserUuid(),
                channelCode,
                oAuth2User.getAttributes(),
                userNameAttributeName
        );
    }
}
