package com.jay.auth.service;

import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserChannel;
import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.ChannelCode;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.EmailLoginRequest;
import com.jay.auth.dto.request.EmailSignUpRequest;
import com.jay.auth.dto.response.LoginResponse;
import com.jay.auth.dto.response.LoginResult;
import com.jay.auth.dto.response.SignUpResponse;
import com.jay.auth.dto.response.TokenResponse;
import com.jay.auth.security.TokenStore;
import com.jay.auth.exception.AuthenticationException;
import com.jay.auth.exception.DuplicateEmailException;
import com.jay.auth.exception.InvalidPasswordException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.UserChannelRepository;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.service.metrics.AuthMetrics;
import com.jay.auth.service.metrics.AuthTimed;
import com.jay.auth.util.NicknameGenerator;
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
    private final PasswordPolicyService passwordPolicyService;
    private final TotpService totpService;
    private final NicknameGenerator nicknameGenerator;
    private final AuthMetrics authMetrics;

    /**
     * 이메일 회원가입
     */
    @AuthTimed(operation = "email_signup")
    @Transactional
    public SignUpResponse signUpWithEmail(EmailSignUpRequest request) {
        String tokenId = request.getTokenId();
        String email = request.getEmail();
        String password = request.getPassword();
        String nickname = nicknameGenerator.generateUnique(
                nick -> userRepository.existsByNicknameLowerEnc(encryptionService.encryptNicknameLower(nick))
        );

        // 1. 비밀번호 정책 검증
        if (!passwordUtil.isValidPassword(password)) {
            throw new InvalidPasswordException();
        }

        // 2. tokenId + 이메일로 인증 완료 여부 확인
        if (!emailVerificationService.isVerifiedByTokenId(tokenId, email, VerificationType.SIGNUP)) {
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
                .nicknameLowerEnc(encryptionService.encryptNicknameLower(nickname))
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
        emailVerificationService.deleteVerificationByTokenId(tokenId);

        // 8. 토큰 발급
        TokenResponse tokenResponse = tokenService.issueTokens(
                user.getId(),
                user.getUserUuid(),
                ChannelCode.EMAIL,
                user.getRole().name()
        );

        log.info("User signed up with email: {}, userId: {}", email, user.getId());
        authMetrics.recordSignUp("EMAIL");

        return SignUpResponse.of(
                user.getUserUuid(),
                email,
                nickname,
                tokenResponse
        );
    }

    /**
     * 이메일 로그인 (세션 정보 없이)
     */
    @Transactional
    public LoginResponse loginWithEmail(EmailLoginRequest request) {
        LoginResult result = authenticateWithEmail(request);

        // 토큰 발급 (세션 정보 없음)
        TokenResponse tokenResponse = tokenService.issueTokens(
                result.getUserId(),
                result.getUserUuid(),
                ChannelCode.EMAIL,
                result.getRole()
        );

        return LoginResponse.of(
                result.getUserId(),
                result.getUserUuid(),
                result.getEmail(),
                result.getNickname(),
                tokenResponse
        );
    }

    /**
     * 이메일 로그인 (세션 정보 포함)
     */
    @Transactional
    public LoginResponse loginWithEmail(EmailLoginRequest request, TokenStore.SessionInfo sessionInfo) {
        LoginResult result = authenticateWithEmail(request);

        // 토큰 발급 (세션 정보 포함)
        TokenResponse tokenResponse = tokenService.issueTokensWithSession(
                result.getUserId(),
                result.getUserUuid(),
                ChannelCode.EMAIL,
                result.getRole(),
                sessionInfo
        );

        // 비밀번호 만료 확인
        UserSignInInfo signInInfo = userSignInInfoRepository.findByUserId(result.getUserId())
                .orElse(null);
        boolean passwordExpired = signInInfo != null && passwordPolicyService.isPasswordExpired(signInInfo);
        Integer daysUntilExpiration = signInInfo != null ?
                passwordPolicyService.getDaysUntilExpiration(signInInfo) : null;

        // 2FA 필요 여부 확인
        boolean twoFactorRequired = totpService.isTwoFactorRequired(result.getUserId());

        return LoginResponse.of(
                result.getUserId(),
                result.getUserUuid(),
                result.getEmail(),
                result.getNickname(),
                tokenResponse,
                passwordExpired,
                daysUntilExpiration,
                twoFactorRequired,
                result.isPendingDeletion(),
                result.getDeletionRequestedAt()
        );
    }

    /**
     * 이메일 인증 (토큰 발급 없이)
     */
    @AuthTimed(operation = "email_authenticate")
    @Transactional
    public LoginResult authenticateWithEmail(EmailLoginRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        // 1. 이메일로 로그인 정보 조회
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        UserSignInInfo signInInfo = userSignInInfoRepository.findByLoginEmailLowerEncWithUser(emailLowerEnc)
                .orElseThrow(AuthenticationException::invalidCredentials);

        User user = signInInfo.getUser();

        // 2. 계정 상태 확인 (PENDING_DELETE는 허용, 로그인 후 유예 취소 가능)
        boolean pendingDeletion = user.getStatus() == com.jay.auth.domain.enums.UserStatus.PENDING_DELETE;
        if (user.getStatus() != com.jay.auth.domain.enums.UserStatus.ACTIVE && !pendingDeletion) {
            throw AuthenticationException.accountNotActive();
        }

        // 3. 계정 잠금 확인
        if (signInInfo.isLocked()) {
            throw AuthenticationException.accountLocked();
        }

        // 4. 비밀번호 검증
        if (!passwordUtil.matches(password, signInInfo.getPasswordHash())) {
            signInInfo.recordLoginFailure();
            authMetrics.recordLoginFailure("EMAIL");
            throw AuthenticationException.invalidCredentials();
        }

        // 5. 로그인 성공 처리
        signInInfo.recordLoginSuccess();
        authMetrics.recordLoginSuccess("EMAIL");

        // 6. 닉네임 복호화
        String nickname = encryptionService.decryptNickname(user.getNicknameEnc());

        log.info("User authenticated with email: {}, userId: {}, pendingDeletion: {}", email, user.getId(), pendingDeletion);

        return LoginResult.of(
                user.getId(),
                user.getUserUuid(),
                email,
                nickname,
                ChannelCode.EMAIL,
                user.getRole().name(),
                pendingDeletion,
                user.getDeletionRequestedAt()
        );
    }

    /**
     * 이메일로 사용자 ID 조회 (로그인 실패 이력 기록용)
     */
    @Transactional(readOnly = true)
    public Long findUserIdByEmail(String email) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        return userSignInInfoRepository.findByLoginEmailLowerEncWithUser(emailLowerEnc)
                .map(signInInfo -> signInInfo.getUser().getId())
                .orElse(null);
    }

    /**
     * 이메일 중복 체크
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        return userSignInInfoRepository.existsByLoginEmailLowerEnc(emailLowerEnc);
    }

    /**
     * 소셜 로그인 채널 이메일 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isSocialChannelEmail(String email) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        return userChannelRepository.existsByChannelEmailLowerEncAndChannelCodeNot(emailLowerEnc, ChannelCode.EMAIL);
    }
}
