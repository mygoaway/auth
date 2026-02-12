package com.jay.auth.security.oauth2;

import com.jay.auth.controller.OAuth2LinkController;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.exception.AccountLinkingException;
import com.jay.auth.service.OAuth2LinkStateService;
import com.jay.auth.service.OAuth2UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomOidcUserServiceTest {

    @Mock
    private OAuth2UserService oAuth2UserService;

    @Mock
    private OAuth2LinkStateService oAuth2LinkStateService;

    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private ClientRegistration createGoogleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .build();
    }

    private OidcUserRequest createOidcUserRequest() {
        ClientRegistration clientRegistration = createGoogleClientRegistration();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token",
                Instant.now(), Instant.now().plusSeconds(3600));

        Map<String, Object> idTokenClaims = new HashMap<>();
        idTokenClaims.put("sub", "google-oidc-123");
        idTokenClaims.put("iss", "https://accounts.google.com");
        idTokenClaims.put("aud", java.util.Collections.singletonList("client-id"));
        idTokenClaims.put("iat", Instant.now());
        idTokenClaims.put("exp", Instant.now().plusSeconds(3600));

        OidcIdToken idToken = new OidcIdToken(
                "id-token-value", Instant.now(), Instant.now().plusSeconds(3600), idTokenClaims);

        return new OidcUserRequest(clientRegistration, accessToken, idToken);
    }

    private User createMockUser(Long id, String uuid, UserStatus status, UserRole role) {
        User user = User.builder().status(status).build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "userUuid", uuid);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    /**
     * OidcUserService.loadUser()가 실제 HTTP/JWKS 호출을 수행하므로,
     * 테스트용 서브클래스를 생성하여 super.loadUser()를 오버라이드합니다.
     */
    private CustomOidcUserService createTestableService(Map<String, Object> attributes, OidcIdToken idToken) {
        return new CustomOidcUserService(oAuth2UserService, oAuth2LinkStateService) {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
                String registrationId = userRequest.getClientRegistration().getRegistrationId();
                String userNameAttributeName = userRequest.getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName();

                // super.loadUser() 대신 직접 attributes 사용
                Map<String, Object> mergedAttributes = new HashMap<>(attributes);

                OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, mergedAttributes);
                ChannelCode channelCode = OAuth2UserInfoFactory.getChannelCode(registrationId);

                String linkState = getLinkStateFromCookie();
                Long linkUserId = linkState != null ? oAuth2LinkStateService.getLinkUserId(linkState) : null;
                boolean isLinkMode = linkUserId != null;

                User user;
                try {
                    if (isLinkMode) {
                        user = oAuth2UserService.processOAuth2UserForLinking(linkUserId, channelCode, oAuth2UserInfo);
                    } else {
                        user = oAuth2UserService.processOAuth2User(channelCode, oAuth2UserInfo);
                    }
                } catch (AccountLinkingException e) {
                    String errorMessage = "해당 소셜 계정은 이미 다른 계정에 연동되어 있습니다";
                    throw new OAuth2AuthenticationException(
                            new org.springframework.security.oauth2.core.OAuth2Error("account_linking_error", errorMessage, null),
                            errorMessage, e);
                } catch (IllegalStateException e) {
                    throw new OAuth2AuthenticationException(
                            new org.springframework.security.oauth2.core.OAuth2Error("account_error", e.getMessage(), null),
                            e.getMessage(), e);
                }

                boolean pendingDeletion = user.getStatus() == UserStatus.PENDING_DELETE;

                return new CustomOidcUser(
                        user.getId(),
                        user.getUserUuid(),
                        channelCode,
                        idToken,
                        null,
                        mergedAttributes,
                        userNameAttributeName,
                        user.getRole().name(),
                        isLinkMode,
                        pendingDeletion,
                        user.getDeletionRequestedAt()
                );
            }

            private String getLinkStateFromCookie() {
                try {
                    org.springframework.web.context.request.ServletRequestAttributes attrs =
                            (org.springframework.web.context.request.ServletRequestAttributes)
                                    RequestContextHolder.getRequestAttributes();
                    if (attrs == null) return null;
                    jakarta.servlet.http.HttpServletRequest request = attrs.getRequest();
                    Cookie[] cookies = request.getCookies();
                    if (cookies == null) return null;
                    return java.util.Arrays.stream(cookies)
                            .filter(c -> OAuth2LinkController.LINK_STATE_COOKIE_NAME.equals(c.getName()))
                            .map(Cookie::getValue)
                            .findFirst()
                            .orElse(null);
                } catch (Exception e) {
                    return null;
                }
            }
        };
    }

    @Nested
    @DisplayName("일반 OIDC 로그인 처리")
    class NormalOidcLogin {

        @Test
        @DisplayName("Google OIDC 로그인 시 CustomOidcUser가 반환되어야 한다")
        void shouldReturnCustomOidcUserForGoogleLogin() {
            // given
            OidcUserRequest userRequest = createOidcUserRequest();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-oidc-123");
            attributes.put("name", "Google User");
            attributes.put("email", "google@example.com");
            attributes.put("picture", "https://google.com/photo.jpg");

            User mockUser = createMockUser(1L, "uuid-google", UserStatus.ACTIVE, UserRole.USER);
            given(oAuth2UserService.processOAuth2User(eq(ChannelCode.GOOGLE), any(OAuth2UserInfo.class)))
                    .willReturn(mockUser);

            CustomOidcUserService testService = createTestableService(attributes, userRequest.getIdToken());

            // when
            OidcUser result = testService.loadUser(userRequest);

            // then
            assertThat(result).isInstanceOf(CustomOidcUser.class);
            CustomOidcUser customUser = (CustomOidcUser) result;
            assertThat(customUser.getUserId()).isEqualTo(1L);
            assertThat(customUser.getUserUuid()).isEqualTo("uuid-google");
            assertThat(customUser.getChannelCode()).isEqualTo(ChannelCode.GOOGLE);
            assertThat(customUser.isLinkMode()).isFalse();
            assertThat(customUser.isPendingDeletion()).isFalse();
            assertThat(customUser.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("탈퇴 예정 사용자의 OIDC 로그인 시 pendingDeletion이 true여야 한다")
        void shouldSetPendingDeletionForPendingDeleteUser() {
            // given
            OidcUserRequest userRequest = createOidcUserRequest();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-oidc-pending");
            attributes.put("name", "Pending User");
            attributes.put("email", "pending@example.com");

            User mockUser = createMockUser(2L, "uuid-pending", UserStatus.PENDING_DELETE, UserRole.USER);
            given(oAuth2UserService.processOAuth2User(eq(ChannelCode.GOOGLE), any(OAuth2UserInfo.class)))
                    .willReturn(mockUser);

            CustomOidcUserService testService = createTestableService(attributes, userRequest.getIdToken());

            // when
            OidcUser result = testService.loadUser(userRequest);

            // then
            CustomOidcUser customUser = (CustomOidcUser) result;
            assertThat(customUser.isPendingDeletion()).isTrue();
        }
    }

    @Nested
    @DisplayName("연동 모드 OIDC 처리")
    class LinkModeOidc {

        @Test
        @DisplayName("연동 모드에서 OIDC 로그인 시 계정이 연동되어야 한다")
        void shouldLinkAccountInLinkMode() {
            // given
            OidcUserRequest userRequest = createOidcUserRequest();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-oidc-link");
            attributes.put("name", "Link User");
            attributes.put("email", "link@example.com");

            mockRequest.setCookies(new Cookie(OAuth2LinkController.LINK_STATE_COOKIE_NAME, "oidc-link-state"));
            given(oAuth2LinkStateService.getLinkUserId("oidc-link-state")).willReturn(10L);

            User mockUser = createMockUser(10L, "uuid-linked", UserStatus.ACTIVE, UserRole.USER);
            given(oAuth2UserService.processOAuth2UserForLinking(eq(10L), eq(ChannelCode.GOOGLE), any(OAuth2UserInfo.class)))
                    .willReturn(mockUser);

            CustomOidcUserService testService = createTestableService(attributes, userRequest.getIdToken());

            // when
            OidcUser result = testService.loadUser(userRequest);

            // then
            CustomOidcUser customUser = (CustomOidcUser) result;
            assertThat(customUser.getUserId()).isEqualTo(10L);
            assertThat(customUser.isLinkMode()).isTrue();
            assertThat(customUser.getChannelCode()).isEqualTo(ChannelCode.GOOGLE);
        }

        @Test
        @DisplayName("연동 모드에서 중복 연동 시 OAuth2AuthenticationException이 발생해야 한다")
        void shouldThrowExceptionForDuplicateLinkInOidc() {
            // given
            OidcUserRequest userRequest = createOidcUserRequest();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-oidc-dup");
            attributes.put("name", "Dup User");

            mockRequest.setCookies(new Cookie(OAuth2LinkController.LINK_STATE_COOKIE_NAME, "oidc-dup-state"));
            given(oAuth2LinkStateService.getLinkUserId("oidc-dup-state")).willReturn(20L);
            given(oAuth2UserService.processOAuth2UserForLinking(eq(20L), eq(ChannelCode.GOOGLE), any(OAuth2UserInfo.class)))
                    .willThrow(AccountLinkingException.alreadyLinkedToAnotherUser());

            CustomOidcUserService testService = createTestableService(attributes, userRequest.getIdToken());

            // when & then
            assertThatThrownBy(() -> testService.loadUser(userRequest))
                    .isInstanceOf(OAuth2AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("비활성 계정 OIDC 처리")
    class InactiveAccountOidc {

        @Test
        @DisplayName("비활성 계정의 OIDC 로그인 시 OAuth2AuthenticationException이 발생해야 한다")
        void shouldThrowExceptionForInactiveAccount() {
            // given
            OidcUserRequest userRequest = createOidcUserRequest();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-inactive");
            attributes.put("name", "Inactive User");

            given(oAuth2UserService.processOAuth2User(eq(ChannelCode.GOOGLE), any(OAuth2UserInfo.class)))
                    .willThrow(new IllegalStateException("User account is not active"));

            CustomOidcUserService testService = createTestableService(attributes, userRequest.getIdToken());

            // when & then
            assertThatThrownBy(() -> testService.loadUser(userRequest))
                    .isInstanceOf(OAuth2AuthenticationException.class);
        }
    }

    @Nested
    @DisplayName("쿠키가 없는 경우")
    class NoCookies {

        @Test
        @DisplayName("쿠키가 없으면 일반 로그인 모드로 처리되어야 한다")
        void shouldTreatAsNormalLoginWhenNoCookies() {
            // given
            OidcUserRequest userRequest = createOidcUserRequest();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-no-cookie");
            attributes.put("name", "No Cookie User");
            attributes.put("email", "nocookie@example.com");

            User mockUser = createMockUser(5L, "uuid-nocookie", UserStatus.ACTIVE, UserRole.USER);
            given(oAuth2UserService.processOAuth2User(eq(ChannelCode.GOOGLE), any(OAuth2UserInfo.class)))
                    .willReturn(mockUser);

            CustomOidcUserService testService = createTestableService(attributes, userRequest.getIdToken());

            // when
            OidcUser result = testService.loadUser(userRequest);

            // then
            CustomOidcUser customUser = (CustomOidcUser) result;
            assertThat(customUser.getUserId()).isEqualTo(5L);
            assertThat(customUser.isLinkMode()).isFalse();
        }
    }
}
