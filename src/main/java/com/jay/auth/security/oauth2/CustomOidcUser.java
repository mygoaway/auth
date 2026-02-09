package com.jay.auth.security.oauth2;

import com.jay.auth.domain.enums.ChannelCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomOidcUser implements OidcUser {

    private final Long userId;
    private final String userUuid;
    private final ChannelCode channelCode;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String nameAttributeKey;
    private final String role;
    private final boolean linkMode;
    private final boolean pendingDeletion;
    private final LocalDateTime deletionRequestedAt;

    public CustomOidcUser(Long userId, String userUuid, ChannelCode channelCode,
                          OidcIdToken idToken, OidcUserInfo userInfo,
                          Map<String, Object> attributes, String nameAttributeKey) {
        this(userId, userUuid, channelCode, idToken, userInfo, attributes, nameAttributeKey, "USER", false, false, null);
    }

    public CustomOidcUser(Long userId, String userUuid, ChannelCode channelCode,
                          OidcIdToken idToken, OidcUserInfo userInfo,
                          Map<String, Object> attributes, String nameAttributeKey, boolean linkMode) {
        this(userId, userUuid, channelCode, idToken, userInfo, attributes, nameAttributeKey, "USER", linkMode, false, null);
    }

    public CustomOidcUser(Long userId, String userUuid, ChannelCode channelCode,
                          OidcIdToken idToken, OidcUserInfo userInfo,
                          Map<String, Object> attributes, String nameAttributeKey, String role,
                          boolean linkMode, boolean pendingDeletion, LocalDateTime deletionRequestedAt) {
        this.userId = userId;
        this.userUuid = userUuid;
        this.channelCode = channelCode;
        this.idToken = idToken;
        this.userInfo = userInfo;
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        this.role = role != null ? role : "USER";
        this.linkMode = linkMode;
        this.pendingDeletion = pendingDeletion;
        this.deletionRequestedAt = deletionRequestedAt;
    }

    @Override
    public Map<String, Object> getClaims() {
        return attributes;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return String.valueOf(attributes.get(nameAttributeKey));
    }
}
