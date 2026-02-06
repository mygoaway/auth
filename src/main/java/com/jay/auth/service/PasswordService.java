package com.jay.auth.service;

import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.ChangePasswordRequest;
import com.jay.auth.dto.request.ResetPasswordRequest;
import com.jay.auth.exception.AuthenticationException;
import com.jay.auth.exception.InvalidPasswordException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserSignInInfoRepository userSignInInfoRepository;
    private final EncryptionService encryptionService;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final PasswordUtil passwordUtil;

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserSignInInfo signInInfo = userSignInInfoRepository.findByUserId(userId)
                .orElseThrow(UserNotFoundException::new);

        // 현재 비밀번호 확인
        if (!passwordUtil.matches(request.getCurrentPassword(), signInInfo.getPasswordHash())) {
            throw AuthenticationException.invalidCredentials();
        }

        // 새 비밀번호 정책 검증
        if (!passwordUtil.isValidPassword(request.getNewPassword())) {
            throw new InvalidPasswordException();
        }

        signInInfo.updatePassword(passwordUtil.encode(request.getNewPassword()));

        // 모든 세션 무효화 (비밀번호 변경 후 전체 로그아웃)
        tokenService.logoutAll(userId, null);

        log.info("User {} changed password and logged out from all sessions", userId);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 복구 이메일 인증 확인
        if (!emailVerificationService.isVerifiedByTokenId(
                request.getTokenId(), request.getRecoveryEmail(), VerificationType.PASSWORD_RESET)) {
            throw InvalidVerificationException.notVerified();
        }

        // 새 비밀번호 정책 검증
        if (!passwordUtil.isValidPassword(request.getNewPassword())) {
            throw new InvalidPasswordException();
        }

        // 복구 이메일로 로그인 정보 조회
        String recoveryEmailLowerEnc = encryptionService.encryptForSearch(request.getRecoveryEmail());
        UserSignInInfo signInInfo = userSignInInfoRepository.findByRecoveryEmailLowerEncWithUser(recoveryEmailLowerEnc)
                .orElseThrow(UserNotFoundException::recoveryEmailNotFound);

        // 비밀번호 변경
        signInInfo.updatePassword(passwordUtil.encode(request.getNewPassword()));

        // 잠금 해제
        if (signInInfo.isLocked()) {
            signInInfo.unlock();
        }

        // 인증 기록 삭제
        emailVerificationService.deleteVerificationByTokenId(request.getTokenId());

        // 모든 세션 무효화
        tokenService.logoutAll(signInInfo.getUser().getId(), null);

        log.info("User {} reset password via recovery email", signInInfo.getUser().getId());
    }
}
