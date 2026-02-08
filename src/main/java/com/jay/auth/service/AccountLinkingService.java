package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.dto.response.ChannelStatusResponse;
import com.jay.auth.exception.AccountLinkingException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLinkingService {

    private final UserRepository userRepository;
    private final UserChannelRepository userChannelRepository;
    private final UserSignInInfoRepository userSignInInfoRepository;
    private final EncryptionService encryptionService;
    private final PasswordUtil passwordUtil;
    private final SecurityNotificationService securityNotificationService;

    /**
     * Get all channels status for a user
     */
    @Transactional(readOnly = true)
    public ChannelStatusResponse getChannelsStatus(Long userId) {
        User user = userRepository.findByIdWithChannels(userId)
                .orElseThrow(UserNotFoundException::new);

        // Create a map of linked channels
        Map<ChannelCode, UserChannel> linkedChannels = user.getChannels().stream()
                .collect(Collectors.toMap(UserChannel::getChannelCode, Function.identity()));

        // Build response with all channel codes
        List<ChannelStatusResponse.ChannelStatus> channelStatuses = Arrays.stream(ChannelCode.values())
                .map(channelCode -> {
                    UserChannel channel = linkedChannels.get(channelCode);
                    boolean linked = channel != null;
                    String channelEmail = null;
                    if (linked && channel.getChannelEmailEnc() != null) {
                        channelEmail = encryptionService.decryptEmail(channel.getChannelEmailEnc());
                    }
                    return ChannelStatusResponse.ChannelStatus.builder()
                            .channelCode(channelCode.name())
                            .description(channelCode.getDescription())
                            .linked(linked)
                            .channelEmail(channelEmail)
                            .linkedAt(linked ? channel.getCreatedAt() : null)
                            .build();
                })
                .toList();

        return ChannelStatusResponse.builder()
                .channels(channelStatuses)
                .build();
    }

    /**
     * Link a social account to an existing user
     */
    @Transactional
    public void linkSocialAccount(Long userId, ChannelCode channelCode, String channelKey, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Check if this channelKey is already linked to another user
        Optional<UserChannel> existingChannel = userChannelRepository
                .findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey);

        if (existingChannel.isPresent()) {
            UserChannel channel = existingChannel.get();
            if (!channel.getUser().getId().equals(userId)) {
                throw AccountLinkingException.alreadyLinkedToAnotherUser();
            } else {
                throw AccountLinkingException.alreadyLinkedToCurrentUser();
            }
        }

        // Check if user already has this channel type linked
        List<UserChannel> userChannels = userChannelRepository.findByUserIdAndChannelCode(userId, channelCode);
        if (!userChannels.isEmpty()) {
            throw AccountLinkingException.alreadyLinkedToCurrentUser();
        }

        // Create channel
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

        // 연동 알림 발송
        securityNotificationService.notifyAccountLinked(userId, channelCode);

        log.info("Social account linked: userId={}, channelCode={}, channelKey={}", userId, channelCode, channelKey);
    }

    /**
     * Unlink a channel from a user
     */
    @Transactional
    public void unlinkChannel(Long userId, ChannelCode channelCode) {
        User user = userRepository.findByIdWithChannels(userId)
                .orElseThrow(UserNotFoundException::new);

        List<UserChannel> channels = user.getChannels();

        // Check if user has this channel
        Optional<UserChannel> channelToRemove = channels.stream()
                .filter(ch -> ch.getChannelCode() == channelCode)
                .findFirst();

        if (channelToRemove.isEmpty()) {
            throw AccountLinkingException.channelNotFound();
        }

        // Check if this is the last channel
        if (channels.size() <= 1) {
            throw AccountLinkingException.cannotUnlinkLastChannel();
        }

        // If unlinking EMAIL channel, also delete UserSignInInfo
        if (channelCode == ChannelCode.EMAIL) {
            userSignInInfoRepository.findByUserId(userId)
                    .ifPresent(userSignInInfoRepository::delete);
        }

        // Delete the channel
        userChannelRepository.delete(channelToRemove.get());

        // 연동 해제 알림 발송
        securityNotificationService.notifyAccountUnlinked(userId, channelCode);

        log.info("Channel unlinked: userId={}, channelCode={}", userId, channelCode);
    }

    /**
     * Check if a social account is available for linking (not used by another user)
     */
    @Transactional(readOnly = true)
    public boolean isSocialAccountAvailable(ChannelCode channelCode, String channelKey, Long currentUserId) {
        Optional<UserChannel> existingChannel = userChannelRepository
                .findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey);

        if (existingChannel.isEmpty()) {
            return true;
        }

        // Available only if it's already linked to the current user
        return existingChannel.get().getUser().getId().equals(currentUserId);
    }
}
