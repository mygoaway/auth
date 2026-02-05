package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.dto.request.UpdatePhoneRequest;
import com.jay.auth.dto.request.UpdateProfileRequest;
import com.jay.auth.dto.request.UpdateRecoveryEmailRequest;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findByIdWithChannels(userId)
                .orElseThrow(UserNotFoundException::new);

        String email = user.getEmailEnc() != null
                ? encryptionService.decryptEmail(user.getEmailEnc()) : null;
        String recoveryEmail = user.getRecoveryEmailEnc() != null
                ? encryptionService.decryptEmail(user.getRecoveryEmailEnc()) : null;
        String phone = user.getPhoneEnc() != null
                ? encryptionService.decryptPhone(user.getPhoneEnc()) : null;
        String nickname = user.getNicknameEnc() != null
                ? encryptionService.decryptNickname(user.getNicknameEnc()) : null;

        List<UserProfileResponse.ChannelInfo> channels = user.getChannels().stream()
                .map(this::toChannelInfo)
                .toList();

        return UserProfileResponse.builder()
                .userUuid(user.getUserUuid())
                .email(email)
                .recoveryEmail(recoveryEmail)
                .phone(phone)
                .nickname(nickname)
                .status(user.getStatus().name())
                .channels(channels)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public void updateNickname(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String encryptedNickname = encryptionService.encryptNickname(request.getNickname());
        user.updateNickname(encryptedNickname);

        log.info("User {} updated nickname", userId);
    }

    @Transactional
    public void updatePhone(Long userId, UpdatePhoneRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String encryptedPhone = encryptionService.encryptPhone(request.getPhone());
        user.updatePhone(encryptedPhone);

        log.info("User {} updated phone", userId);
    }

    @Transactional
    public void updateRecoveryEmail(Long userId, UpdateRecoveryEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        EncryptionService.EncryptedEmail encryptedEmail =
                encryptionService.encryptEmail(request.getRecoveryEmail());
        user.updateRecoveryEmail(encryptedEmail.encrypted(), encryptedEmail.encryptedLower());

        log.info("User {} updated recovery email", userId);
    }

    private UserProfileResponse.ChannelInfo toChannelInfo(UserChannel channel) {
        String channelEmail = channel.getChannelEmailEnc() != null
                ? encryptionService.decryptEmail(channel.getChannelEmailEnc()) : null;

        return UserProfileResponse.ChannelInfo.builder()
                .channelCode(channel.getChannelCode().name())
                .channelEmail(channelEmail)
                .linkedAt(channel.getCreatedAt())
                .build();
    }
}
