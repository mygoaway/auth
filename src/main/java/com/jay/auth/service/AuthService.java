package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.exception.InvalidPasswordException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSignInInfoRepository userSignInInfoRepository;
    private final UserChannelRepository userChannelRepository;
    private final EncryptionService encryptionService;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final PasswordUtil passwordUtil;

    /**
     * 이메일 회원가입
     */
    @Transactional
    public SignUpResponse signUpWithEmail(EmailSignUpRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();
        String nickname = request.getNickname();

        // 1. 비밀번호 정책 검증
        if (!passwordUtil.isValidPassword(password)) {
            throw new InvalidPasswordException();
        }

        // 2. 이메일 인증 완료 여부 확인
        if (!emailVerificationService.isVerified(email, VerificationType.SIGNUP)) {
            throw InvalidVerificationException.notVerified();
        }

        // 3. 이메일 중복 체크
        EncryptionService.EncryptedEmail encryptedEmail = encryptionService.encryptEmail(email);
        if (userSignInInfoRepository.existsByLoginEmailLowerEnc(encryptedEmail.encryptedLower())) {
            throw new DuplicateEmailException();
        }

        // 4. User 생성
        User user = User.builder()
                .emailEnc(encryptedEmail.encrypted())
                .emailLowerEnc(encryptedEmail.encryptedLower())
                .nicknameEnc(encryptionService.encryptNickname(nickname))
                .build();

        userRepository.save(user);

        // 5. UserSignInInfo 생성
        UserSignInInfo signInInfo = UserSignInInfo.builder()
                .user(user)
                .loginEmailEnc(encryptedEmail.encrypted())
                .loginEmailLowerEnc(encryptedEmail.encryptedLower())
                .passwordHash(passwordUtil.encode(password))
                .build();

        userSignInInfoRepository.save(signInInfo);

        // 6. UserChannel 생성 (EMAIL)
        UserChannel channel = UserChannel.builder()
                .user(user)
                .channelCode(ChannelCode.EMAIL)
                .channelKey(String.valueOf(user.getId()))
                .channelEmailEnc(encryptedEmail.encrypted())
                .channelEmailLowerEnc(encryptedEmail.encryptedLower())
                .build();

        userChannelRepository.save(channel);

        // 7. 인증 기록 삭제
        emailVerificationService.deleteVerification(email, VerificationType.SIGNUP);

        // 8. 토큰 발급
        TokenResponse tokenResponse = tokenService.issueTokens(
                user.getId(),
                user.getUserUuid(),
                ChannelCode.EMAIL
        );

        log.info("User signed up with email: {}, userId: {}", email, user.getId());

        return SignUpResponse.of(
                user.getUserUuid(),
                email,
                nickname,
                tokenResponse
        );
    }

    /**
     * 이메일 중복 체크
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        return userSignInInfoRepository.existsByLoginEmailLowerEnc(emailLowerEnc);
    }
}
