package com.jay.auth.service;

import com.jay.auth.domain.entity.UserSignInInfo;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.dto.request.ChangePasswordRequest;
import com.jay.auth.dto.request.ResetPasswordRequest;
import com.jay.auth.dto.response.RecoveryAccountsResponse;
import com.jay.auth.exception.AuthenticationException;
import com.jay.auth.exception.InvalidPasswordException;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.exception.PasswordPolicyException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserSignInInfoRepository;
import com.jay.auth.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserSignInInfoRepository userSignInInfoRepository;
    private final EncryptionService encryptionService;
    private final EmailVerificationService emailVerificationService;
    private final TokenService tokenService;
    private final PasswordUtil passwordUtil;
    private final SecurityNotificationService securityNotificationService;
    private final PasswordPolicyService passwordPolicyService;
    private final CacheManager cacheManager;

    @CacheEvict(value = "securityDashboard", key = "#userId")
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

        // 현재 비밀번호와 동일한지 확인
        if (passwordPolicyService.isSameAsCurrentPassword(request.getNewPassword(), signInInfo.getPasswordHash())) {
            throw PasswordPolicyException.sameAsCurrent();
        }

        // 이전 비밀번호 재사용 확인
        if (passwordPolicyService.isPasswordReused(userId, request.getNewPassword())) {
            throw PasswordPolicyException.reused();
        }

        // 현재 비밀번호를 이력에 저장
        passwordPolicyService.savePasswordHistory(signInInfo.getUser(), signInInfo.getPasswordHash());

        signInInfo.updatePassword(passwordUtil.encode(request.getNewPassword()));

        // 모든 세션 무효화 (비밀번호 변경 후 전체 로그아웃)
        tokenService.logoutAll(userId, null);

        // 비밀번호 변경 알림
        securityNotificationService.notifyPasswordChanged(userId);

        log.info("User {} changed password and logged out from all sessions", userId);
    }

    @Transactional(readOnly = true)
    public RecoveryAccountsResponse getAccountsByRecoveryEmail(String tokenId, String recoveryEmail) {
        // 복구 이메일 인증 확인
        if (!emailVerificationService.isVerifiedByTokenId(
                tokenId, recoveryEmail, VerificationType.PASSWORD_RESET)) {
            throw InvalidVerificationException.notVerified();
        }

        // 복구 이메일로 연결된 모든 로그인 정보 조회
        String recoveryEmailLowerEnc = encryptionService.encryptForSearch(recoveryEmail);
        List<UserSignInInfo> signInInfoList = userSignInInfoRepository
                .findAllByRecoveryEmailLowerEncWithUser(recoveryEmailLowerEnc);

        if (signInInfoList.isEmpty()) {
            throw UserNotFoundException.recoveryEmailNotFound();
        }

        List<RecoveryAccountsResponse.AccountInfo> accounts = signInInfoList.stream()
                .map(signInInfo -> {
                    String loginEmail = encryptionService.decryptEmail(signInInfo.getLoginEmailEnc());
                    String maskedEmail = maskEmail(loginEmail);
                    return new RecoveryAccountsResponse.AccountInfo(loginEmail, maskedEmail);
                })
                .toList();

        return RecoveryAccountsResponse.builder()
                .accounts(accounts)
                .build();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return local.charAt(0) + "***" + domain;
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

        // 로그인 이메일로 로그인 정보 조회
        String loginEmailLowerEnc = encryptionService.encryptForSearch(request.getLoginEmail());
        UserSignInInfo signInInfo = userSignInInfoRepository.findByLoginEmailLowerEncWithUser(loginEmailLowerEnc)
                .orElseThrow(UserNotFoundException::new);

        // 해당 계정의 복구 이메일이 요청의 복구 이메일과 일치하는지 검증
        String recoveryEmailLowerEnc = encryptionService.encryptForSearch(request.getRecoveryEmail());
        if (!recoveryEmailLowerEnc.equals(signInInfo.getUser().getRecoveryEmailLowerEnc())) {
            throw UserNotFoundException.recoveryEmailNotFound();
        }

        Long userId = signInInfo.getUser().getId();

        // 현재 비밀번호와 동일한지 확인
        if (passwordPolicyService.isSameAsCurrentPassword(request.getNewPassword(), signInInfo.getPasswordHash())) {
            throw PasswordPolicyException.sameAsCurrent();
        }

        // 이전 비밀번호 재사용 확인
        if (passwordPolicyService.isPasswordReused(userId, request.getNewPassword())) {
            throw PasswordPolicyException.reused();
        }

        // 현재 비밀번호를 이력에 저장
        passwordPolicyService.savePasswordHistory(signInInfo.getUser(), signInInfo.getPasswordHash());

        // 비밀번호 변경
        signInInfo.updatePassword(passwordUtil.encode(request.getNewPassword()));

        // 잠금 해제
        if (signInInfo.isLocked()) {
            signInInfo.unlock();
        }

        // 인증 기록 삭제
        emailVerificationService.deleteVerificationByTokenId(request.getTokenId());

        // 모든 세션 무효화
        tokenService.logoutAll(userId, null);

        // 비밀번호 변경 알림
        securityNotificationService.notifyPasswordChanged(userId);

        // 비밀번호 변경으로 보안 점수(PASSWORD_HEALTH) 갱신을 위해 캐시 무효화
        var cache = cacheManager.getCache("securityDashboard");
        if (cache != null) {
            cache.evict(userId);
        }

        log.info("User {} reset password via recovery email", userId);
    }
}
