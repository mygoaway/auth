package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.enums.UserStatus;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.UpdatePhoneRequest;
import com.jay.auth.dto.request.UpdateProfileRequest;
import com.jay.auth.dto.request.UpdateRecoveryEmailRequest;
import com.jay.auth.dto.response.UserProfileResponse;
import com.jay.auth.exception.DuplicateNicknameException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final TokenService tokenService;
    private final PhoneVerificationService phoneVerificationService;
    private final EmailVerificationService emailVerificationService;
    private final AuditLogService auditLogService;

    @Cacheable(value = "userProfile", key = "#userId")
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
                .userId(user.getId())
                .userUuid(user.getUserUuid())
                .email(email)
                .recoveryEmail(recoveryEmail)
                .phone(phone)
                .nickname(nickname)
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .channels(channels)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @CacheEvict(value = "userProfile", key = "#userId")
    @Transactional
    public void updateNickname(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String nicknameLowerEnc = encryptionService.encryptNicknameLower(request.getNickname());

        // 본인 현재 닉네임과 동일한지 확인
        if (nicknameLowerEnc.equals(user.getNicknameLowerEnc())) {
            throw new DuplicateNicknameException();
        }

        // 타인 중복 확인
        if (userRepository.existsByNicknameLowerEnc(nicknameLowerEnc)) {
            throw new DuplicateNicknameException();
        }

        String encryptedNickname = encryptionService.encryptNickname(request.getNickname());
        user.updateNickname(encryptedNickname, nicknameLowerEnc);
        auditLogService.log(userId, "NICKNAME_CHANGE", "USER");

        log.info("User {} updated nickname", userId);
    }

    @CacheEvict(value = "userProfile", key = "#userId")
    @Transactional
    public void updatePhone(Long userId, UpdatePhoneRequest request) {
        // tokenId 유효성 검증
        if (!phoneVerificationService.isValidTokenId(request.getTokenId())) {
            throw new InvalidVerificationException("핸드폰 인증이 완료되지 않았습니다");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String encryptedPhone = encryptionService.encryptPhone(request.getPhone());
        user.updatePhone(encryptedPhone);

        // 인증 레코드 삭제
        phoneVerificationService.deleteVerificationByTokenId(request.getTokenId());
        auditLogService.log(userId, "PHONE_CHANGE", "USER");

        log.info("User {} updated phone", userId);
    }

    @CacheEvict(value = {"userProfile", "securityDashboard"}, key = "#userId")
    @Transactional
    public void updateRecoveryEmail(Long userId, UpdateRecoveryEmailRequest request) {
        // tokenId로 이메일 인증 완료 여부 확인
        if (!emailVerificationService.isVerifiedByTokenId(
                request.getTokenId(), request.getRecoveryEmail(), VerificationType.EMAIL_CHANGE)) {
            throw InvalidVerificationException.notVerified();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        EncryptionService.EncryptedEmail encryptedEmail =
                encryptionService.encryptEmail(request.getRecoveryEmail());
        user.updateRecoveryEmail(encryptedEmail.encrypted(), encryptedEmail.encryptedLower());

        // 인증 기록 삭제
        emailVerificationService.deleteVerificationByTokenId(request.getTokenId());
        auditLogService.log(userId, "RECOVERY_EMAIL_CHANGE", "USER");

        log.info("User {} updated recovery email", userId);
    }

    @Transactional(readOnly = true)
    public String getNickname(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return user.getNicknameEnc() != null
                ? encryptionService.decryptNickname(user.getNicknameEnc()) : "익명";
    }

    /**
     * 복구 이메일로 등록된 사용자 존재 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean existsByRecoveryEmail(String recoveryEmail) {
        String emailLowerEnc = encryptionService.encryptForSearch(recoveryEmail);
        return userRepository.existsByRecoveryEmailLowerEnc(emailLowerEnc);
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

    /**
     * 회원 탈퇴 요청 (30일 유예 기간)
     * - User status를 PENDING_DELETE로 변경
     * - 모든 토큰 무효화
     * - 30일 후 배치에서 실제 데이터 삭제
     */
    @CacheEvict(value = {"userProfile", "securityDashboard"}, key = "#userId")
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        user.requestDeletion();

        // 모든 토큰 무효화
        tokenService.logoutAll(userId, null);
        auditLogService.log(userId, "ACCOUNT_DELETE_REQUEST", "USER");

        log.info("User {} requested deletion (30 day grace period)", userId);
    }

    /**
     * 탈퇴 유예 취소 (계정 복구)
     */
    @CacheEvict(value = {"userProfile", "securityDashboard"}, key = "#userId")
    @Transactional
    public void cancelDeletion(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.PENDING_DELETE) {
            throw new IllegalStateException("탈퇴 유예 상태가 아닙니다");
        }

        user.cancelDeletion();

        log.info("User {} cancelled deletion request", userId);
    }
}
