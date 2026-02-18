package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.exception.AccountLinkingException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.security.oauth2.OAuth2UserInfo;
import com.jay.auth.util.NicknameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final UserRepository userRepository;
    private final UserChannelRepository userChannelRepository;
    private final EncryptionService encryptionService;
    private final NicknameGenerator nicknameGenerator;
    private final CacheManager cacheManager;

    @Transactional
    public User processOAuth2User(ChannelCode channelCode, OAuth2UserInfo oAuth2UserInfo) {
        String channelKey = oAuth2UserInfo.getId();
        String email = oAuth2UserInfo.getEmail();
        String name = oAuth2UserInfo.getName();

        // 기존 채널로 가입된 사용자 조회
        Optional<UserChannel> existingChannel = userChannelRepository
                .findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey);

        if (existingChannel.isPresent()) {
            User user = existingChannel.get().getUser();
            // PENDING_DELETE는 허용 (로그인 후 유예 취소 가능), 그 외 비활성 상태는 거부
            if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING_DELETE) {
                throw new IllegalStateException("User account is not active");
            }
            // 채널 이메일 업데이트
            updateChannelEmail(existingChannel.get(), email);
            log.info("Existing user logged in via {}: userId={}, status={}", channelCode, user.getId(), user.getStatus());
            return user;
        }

        // 신규 사용자 생성
        return createNewUser(channelCode, channelKey, email, name);
    }

    /**
     * Process OAuth2 user for account linking mode.
     * Links the social account to an existing user instead of creating a new one.
     */
    @Transactional
    public User processOAuth2UserForLinking(Long userId, ChannelCode channelCode, OAuth2UserInfo oAuth2UserInfo) {
        String channelKey = oAuth2UserInfo.getId();
        String email = oAuth2UserInfo.getEmail();

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User account is not active");
        }

        // Check if this social account is already linked to another user
        Optional<UserChannel> existingChannel = userChannelRepository
                .findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey);

        if (existingChannel.isPresent()) {
            UserChannel channel = existingChannel.get();
            if (!channel.getUser().getId().equals(userId)) {
                throw AccountLinkingException.alreadyLinkedToAnotherUser();
            } else {
                // Already linked to current user, just update email
                updateChannelEmail(channel, email);
                log.info("Social account already linked to user: userId={}, channelCode={}", userId, channelCode);
                return user;
            }
        }

        // Check if user already has this channel type
        List<UserChannel> userChannels = userChannelRepository.findByUserIdAndChannelCode(userId, channelCode);
        if (!userChannels.isEmpty()) {
            throw AccountLinkingException.alreadyLinkedToCurrentUser();
        }

        // Create new channel for linking
        EncryptionService.EncryptedEmail encryptedEmail = null;
        if (email != null) {
            encryptedEmail = encryptionService.encryptEmail(email);
        }

        UserChannel newChannel = UserChannel.builder()
                .user(user)
                .channelCode(channelCode)
                .channelKey(channelKey)
                .channelEmailEnc(encryptedEmail != null ? encryptedEmail.encrypted() : null)
                .channelEmailLowerEnc(encryptedEmail != null ? encryptedEmail.encryptedLower() : null)
                .build();

        userChannelRepository.save(newChannel);
        user.addChannel(newChannel);
        userRepository.flush();

        // 트랜잭션 커밋 후 캐시 제거 (AOP 순서 문제 방지)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictUserProfileCache(userId);
                evictSecurityDashboardCache(userId);
            }
        });

        log.info("Social account linked to user: userId={}, channelCode={}, channelKey={}", userId, channelCode, channelKey);

        return user;
    }

    private User createNewUser(ChannelCode channelCode, String channelKey, String email, String name) {
        EncryptionService.EncryptedEmail encryptedEmail = null;
        if (email != null) {
            encryptedEmail = encryptionService.encryptEmail(email);
        }

        // 닉네임 자동 생성
        String nickname = nicknameGenerator.generate();
        String encryptedNickname = encryptionService.encryptNickname(nickname);

        // User 생성
        User user = User.builder()
                .emailEnc(encryptedEmail != null ? encryptedEmail.encrypted() : null)
                .emailLowerEnc(encryptedEmail != null ? encryptedEmail.encryptedLower() : null)
                .nicknameEnc(encryptedNickname)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);

        // UserChannel 생성
        UserChannel channel = UserChannel.builder()
                .user(user)
                .channelCode(channelCode)
                .channelKey(channelKey)
                .channelEmailEnc(encryptedEmail != null ? encryptedEmail.encrypted() : null)
                .channelEmailLowerEnc(encryptedEmail != null ? encryptedEmail.encryptedLower() : null)
                .build();

        userChannelRepository.save(channel);

        log.info("New user created via {}: userId={}, channelKey={}", channelCode, user.getId(), channelKey);

        return user;
    }

    private void updateChannelEmail(UserChannel channel, String email) {
        if (email == null) {
            return;
        }
        EncryptionService.EncryptedEmail encryptedEmail = encryptionService.encryptEmail(email);
        channel.updateChannelEmail(encryptedEmail.encrypted(), encryptedEmail.encryptedLower());
    }

    private void evictUserProfileCache(Long userId) {
        try {
            var cache = cacheManager.getCache("userProfile");
            if (cache != null) {
                cache.evict(userId);
                log.debug("Evicted userProfile cache for userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict userProfile cache: {}", e.getMessage());
        }
    }

    private void evictSecurityDashboardCache(Long userId) {
        try {
            var cache = cacheManager.getCache("securityDashboard");
            if (cache != null) {
                cache.evict(userId);
                log.debug("Evicted securityDashboard cache for userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict securityDashboard cache: {}", e.getMessage());
        }
    }
}
