package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.security.oauth2.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final UserRepository userRepository;
    private final UserChannelRepository userChannelRepository;
    private final EncryptionService encryptionService;

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
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new IllegalStateException("User account is not active");
            }
            // 채널 이메일 업데이트
            updateChannelEmail(existingChannel.get(), email);
            log.info("Existing user logged in via {}: userId={}", channelCode, user.getId());
            return user;
        }

        // 신규 사용자 생성
        return createNewUser(channelCode, channelKey, email, name);
    }

    private User createNewUser(ChannelCode channelCode, String channelKey, String email, String name) {
        EncryptionService.EncryptedEmail encryptedEmail = null;
        if (email != null) {
            encryptedEmail = encryptionService.encryptEmail(email);
        }

        String encryptedNickname = null;
        if (name != null) {
            encryptedNickname = encryptionService.encryptNickname(name);
        }

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
}
