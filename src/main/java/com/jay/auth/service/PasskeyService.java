package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserPasskey;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.request.PasskeyAuthenticateRequest;
import com.jay.auth.dto.request.PasskeyRegisterRequest;
import com.jay.auth.dto.response.*;
import com.jay.auth.exception.PasskeyException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserPasskeyRepository;
import com.jay.auth.repository.UserRepository;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyService {

    private final UserPasskeyRepository userPasskeyRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EncryptionService encryptionService;
    private final StringRedisTemplate stringRedisTemplate;

    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final ObjectConverter objectConverter = new ObjectConverter();

    @Value("${app.webauthn.rp-id:localhost}")
    private String rpId;

    @Value("${app.webauthn.rp-name:Authly}")
    private String rpName;

    @Value("${app.webauthn.origin:http://localhost:3000}")
    private String origin;

    private static final long CHALLENGE_TIMEOUT_SECONDS = 300;
    private static final int MAX_PASSKEYS_PER_USER = 10;
    private static final String CHALLENGE_KEY_PREFIX = "passkey:challenge:";

    /**
     * 패스키 등록 옵션 생성 (Authenticated)
     */
    @Transactional(readOnly = true)
    public PasskeyRegistrationOptionsResponse generateRegistrationOptions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (userPasskeyRepository.countByUserId(userId) >= MAX_PASSKEYS_PER_USER) {
            throw PasskeyException.limitExceeded();
        }

        // Generate challenge
        byte[] challengeBytes = new byte[32];
        new SecureRandom().nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

        // Store challenge in Redis
        String redisKey = CHALLENGE_KEY_PREFIX + "register:" + userId;
        stringRedisTemplate.opsForValue().set(redisKey, challenge, CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Get user display name
        String displayName = user.getNicknameEnc() != null
                ? encryptionService.decryptNickname(user.getNicknameEnc())
                : "User";

        // Build exclude credentials (already registered passkeys)
        List<UserPasskey> existingPasskeys = userPasskeyRepository.findByUserId(userId);
        List<PasskeyRegistrationOptionsResponse.ExcludeCredential> excludeCredentials = existingPasskeys.stream()
                .map(p -> PasskeyRegistrationOptionsResponse.ExcludeCredential.builder()
                        .id(p.getCredentialId())
                        .type("public-key")
                        .build())
                .toList();

        return PasskeyRegistrationOptionsResponse.builder()
                .challenge(challenge)
                .rp(PasskeyRegistrationOptionsResponse.RpInfo.builder()
                        .id(rpId)
                        .name(rpName)
                        .build())
                .user(PasskeyRegistrationOptionsResponse.UserInfo.builder()
                        .id(Base64.getUrlEncoder().withoutPadding().encodeToString(
                                user.getUserUuid().getBytes()))
                        .name(displayName)
                        .displayName(displayName)
                        .build())
                .pubKeyCredParams(List.of(
                        PasskeyRegistrationOptionsResponse.PubKeyCredParam.builder()
                                .type("public-key")
                                .alg(-7)  // ES256
                                .build(),
                        PasskeyRegistrationOptionsResponse.PubKeyCredParam.builder()
                                .type("public-key")
                                .alg(-257)  // RS256
                                .build()
                ))
                .timeout(CHALLENGE_TIMEOUT_SECONDS * 1000)
                .attestation("none")
                .excludeCredentials(excludeCredentials)
                .build();
    }

    /**
     * 패스키 등록 검증 & credential 저장 (Authenticated)
     */
    @CacheEvict(value = "securityDashboard", key = "#userId")
    @Transactional
    public void verifyRegistration(Long userId, PasskeyRegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Retrieve and consume challenge from Redis
        String redisKey = CHALLENGE_KEY_PREFIX + "register:" + userId;
        String storedChallenge = stringRedisTemplate.opsForValue().getAndDelete(redisKey);
        if (storedChallenge == null) {
            throw PasskeyException.invalidChallenge();
        }

        if (userPasskeyRepository.countByUserId(userId) >= MAX_PASSKEYS_PER_USER) {
            throw PasskeyException.limitExceeded();
        }

        try {
            byte[] clientDataJSON = Base64.getUrlDecoder().decode(request.getClientDataJSON());
            byte[] attestationObject = Base64.getUrlDecoder().decode(request.getAttestationObject());

            Challenge challenge = new DefaultChallenge(Base64.getUrlDecoder().decode(storedChallenge));
            Origin originObj = new Origin(origin);
            ServerProperty serverProperty = new ServerProperty(originObj, rpId, challenge, null);

            RegistrationRequest registrationRequest = new RegistrationRequest(attestationObject, clientDataJSON);
            RegistrationParameters registrationParameters = new RegistrationParameters(
                    serverProperty, null, false, true);

            RegistrationData registrationData = webAuthnManager.parse(registrationRequest);
            webAuthnManager.validate(registrationData, registrationParameters);

            // Extract credential data
            AttestationObject attestationObj = registrationData.getAttestationObject();
            AttestedCredentialData credentialData = attestationObj.getAuthenticatorData().getAttestedCredentialData();

            // Serialize public key
            AttestedCredentialDataConverter converter = new AttestedCredentialDataConverter(objectConverter);
            byte[] publicKeyBytes = converter.convert(credentialData);

            // Check for duplicate credential
            String credentialId = request.getCredentialId();
            if (userPasskeyRepository.findByCredentialId(credentialId).isPresent()) {
                throw PasskeyException.alreadyRegistered();
            }

            // Save passkey
            UserPasskey passkey = UserPasskey.builder()
                    .user(user)
                    .credentialId(credentialId)
                    .publicKey(publicKeyBytes)
                    .signCount(attestationObj.getAuthenticatorData().getSignCount())
                    .transports(request.getTransports())
                    .deviceName(request.getDeviceName() != null ? request.getDeviceName() : "패스키")
                    .build();

            userPasskeyRepository.save(passkey);

            log.info("Passkey registered for user: {}, credentialId: {}", userId, credentialId);
        } catch (VerificationException e) {
            log.warn("Passkey registration validation failed for user: {}", userId, e);
            throw PasskeyException.registrationFailed();
        } catch (PasskeyException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey registration failed for user: {}", userId, e);
            throw PasskeyException.registrationFailed();
        }
    }

    /**
     * 패스키 인증 옵션 생성 (Public)
     */
    public PasskeyAuthenticationOptionsResponse generateAuthenticationOptions() {
        byte[] challengeBytes = new byte[32];
        new SecureRandom().nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

        // Store challenge in Redis with a random session key
        String sessionId = UUID.randomUUID().toString();
        String redisKey = CHALLENGE_KEY_PREFIX + "login:" + sessionId;
        stringRedisTemplate.opsForValue().set(redisKey, challenge, CHALLENGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        return PasskeyAuthenticationOptionsResponse.builder()
                .challenge(challenge)
                .timeout(CHALLENGE_TIMEOUT_SECONDS * 1000)
                .rpId(rpId)
                .allowCredentials(Collections.emptyList())
                .userVerification("preferred")
                .build();
    }

    /**
     * 패스키 인증 검증 & JWT 발급 (Public)
     */
    @Transactional
    public LoginResponse verifyAuthentication(PasskeyAuthenticateRequest request) {
        // Find passkey by credential ID
        UserPasskey passkey = userPasskeyRepository.findByCredentialIdWithUser(request.getCredentialId())
                .orElseThrow(PasskeyException::notFound);

        User user = passkey.getUser();

        // Find and consume challenge from Redis - try all login challenges
        String challenge = findAndConsumeLoginChallenge(request.getClientDataJSON());
        if (challenge == null) {
            throw PasskeyException.invalidChallenge();
        }

        try {
            byte[] clientDataJSON = Base64.getUrlDecoder().decode(request.getClientDataJSON());
            byte[] authenticatorData = Base64.getUrlDecoder().decode(request.getAuthenticatorData());
            byte[] signature = Base64.getUrlDecoder().decode(request.getSignature());
            byte[] credentialId = Base64.getUrlDecoder().decode(request.getCredentialId());

            Challenge challengeObj = new DefaultChallenge(Base64.getUrlDecoder().decode(challenge));
            Origin originObj = new Origin(origin);
            ServerProperty serverProperty = new ServerProperty(originObj, rpId, challengeObj, null);

            // Reconstruct attested credential data
            AttestedCredentialDataConverter converter = new AttestedCredentialDataConverter(objectConverter);
            AttestedCredentialData credentialData = converter.convert(passkey.getPublicKey());

            AuthenticatorImpl authenticator = new AuthenticatorImpl(
                    credentialData, null, passkey.getSignCount());

            AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                    credentialId, null, authenticatorData, clientDataJSON, null, signature);
            AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                    serverProperty, authenticator, null, false, true);

            AuthenticationData authenticationData = webAuthnManager.parse(authenticationRequest);
            webAuthnManager.validate(authenticationData, authenticationParameters);

            // Update sign count
            passkey.updateSignCount(authenticationData.getAuthenticatorData().getSignCount());
            passkey.recordUsage();

            // Issue tokens
            TokenResponse tokenResponse = tokenService.issueTokens(
                    user.getId(),
                    user.getUserUuid(),
                    ChannelCode.EMAIL,
                    user.getRole().name()
            );

            String nickname = user.getNicknameEnc() != null
                    ? encryptionService.decryptNickname(user.getNicknameEnc())
                    : null;
            String email = user.getEmailEnc() != null
                    ? encryptionService.decryptEmail(user.getEmailEnc())
                    : null;

            log.info("Passkey authentication successful for user: {}", user.getId());

            return LoginResponse.of(
                    user.getId(),
                    user.getUserUuid(),
                    email,
                    nickname,
                    tokenResponse
            );
        } catch (VerificationException e) {
            log.warn("Passkey authentication validation failed for credentialId: {}", request.getCredentialId(), e);
            throw PasskeyException.verificationFailed();
        } catch (PasskeyException e) {
            throw e;
        } catch (Exception e) {
            log.error("Passkey authentication failed for credentialId: {}", request.getCredentialId(), e);
            throw PasskeyException.verificationFailed();
        }
    }

    /**
     * 패스키 목록 조회
     */
    @Transactional(readOnly = true)
    public PasskeyListResponse listPasskeys(Long userId) {
        List<UserPasskey> passkeys = userPasskeyRepository.findByUserId(userId);
        return PasskeyListResponse.from(passkeys);
    }

    /**
     * 패스키 삭제
     */
    @CacheEvict(value = "securityDashboard", key = "#userId")
    @Transactional
    public void deletePasskey(Long userId, Long passkeyId) {
        UserPasskey passkey = userPasskeyRepository.findByIdAndUserId(passkeyId, userId)
                .orElseThrow(PasskeyException::notFound);

        userPasskeyRepository.delete(passkey);
        log.info("Passkey deleted for user: {}, passkeyId: {}", userId, passkeyId);
    }

    /**
     * 패스키 이름 변경
     */
    @Transactional
    public void renamePasskey(Long userId, Long passkeyId, String deviceName) {
        UserPasskey passkey = userPasskeyRepository.findByIdAndUserId(passkeyId, userId)
                .orElseThrow(PasskeyException::notFound);

        passkey.updateDeviceName(deviceName);
        log.info("Passkey renamed for user: {}, passkeyId: {}", userId, passkeyId);
    }

    /**
     * 사용자에게 패스키가 있는지 확인
     */
    @Transactional(readOnly = true)
    public boolean hasPasskeys(Long userId) {
        return userPasskeyRepository.existsByUserId(userId);
    }

    private String findAndConsumeLoginChallenge(String clientDataJSON) {
        // We need to extract the challenge from clientDataJSON and match it against stored challenges
        // Since we store challenges by sessionId, we scan for matching ones
        Set<String> keys = stringRedisTemplate.keys(CHALLENGE_KEY_PREFIX + "login:*");
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            String storedChallenge = stringRedisTemplate.opsForValue().get(key);
            if (storedChallenge != null) {
                stringRedisTemplate.delete(key);
                return storedChallenge;
            }
        }
        return null;
    }
}
