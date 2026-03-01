package com.jay.auth.service;

import com.jay.auth.domain.entity.EmailVerification;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.EmailVerificationRepository;
import com.jay.auth.service.metrics.AuthMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 고위험 로그인 감지 시 이메일 재인증을 처리하는 서비스.
 *
 * <p>판단 기준:
 * <ul>
 *   <li>신뢰 기기가 아닌 새 기기에서 로그인</li>
 *   <li>2FA 미설정 계정의 새 환경 로그인</li>
 * </ul>
 *
 * <p>재인증이 필요하면 이메일로 6자리 코드를 발송하고 tokenId를 반환한다.
 * 클라이언트는 이 tokenId를 {@code POST /api/v1/auth/email/verify-login}에 전달해
 * 정규 토큰을 발급받아야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLoginVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailSender emailSender;
    private final EncryptionService encryptionService;
    private final TrustedDeviceService trustedDeviceService;
    private final TotpService totpService;
    private final MeterRegistry meterRegistry;
    private final AuthMetrics authMetrics;

    private static final int CODE_LENGTH        = 6;
    private static final int EXPIRATION_MINUTES = 10;

    /**
     * 로그인 직후 재인증이 필요한지 판단한다.
     *
     * @param userId   로그인한 사용자 ID
     * @param deviceId 요청 기기 핑거프린트 (null 가능)
     * @return true이면 재인증 필요
     */
    public boolean isVerificationRequired(Long userId, String deviceId) {
        // 2FA 활성화된 계정은 이미 2FA로 보호됨 → 재인증 불필요
        if (totpService.isTwoFactorRequired(userId)) {
            return false;
        }

        // 신뢰 기기이면 재인증 불필요
        if (deviceId != null && trustedDeviceService.isTrustedDevice(userId, deviceId)) {
            return false;
        }

        // 신뢰 기기가 아닌 새 기기에서 로그인 → 재인증 필요
        return true;
    }

    /**
     * 이메일로 재인증 코드를 발송하고 tokenId를 반환한다.
     *
     * @param email 사용자 이메일 (평문)
     * @return tokenId (재인증 확인 시 필요)
     */
    @Transactional
    public String sendVerificationCode(String email) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);

        // 기존 POST_LOGIN_VERIFICATION 미인증 요청 삭제
        emailVerificationRepository.deleteByEmailAndType(emailLowerEnc, VerificationType.POST_LOGIN_VERIFICATION);

        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        EmailVerification verification = EmailVerification.builder()
                .emailLowerEnc(emailLowerEnc)
                .verificationCode(code)
                .verificationType(VerificationType.POST_LOGIN_VERIFICATION)
                .expiresAt(expiresAt)
                .build();

        EmailVerification saved = emailVerificationRepository.save(verification);
        emailSender.sendPostLoginVerificationCode(email, code);

        Counter.builder("post_login_verification_sent_total")
                .register(meterRegistry)
                .increment();

        log.info("Post-login verification code sent: email={}", email);
        return saved.getTokenId();
    }

    /**
     * 재인증 코드를 검증하고 성공 시 tokenId를 반환한다.
     */
    @Transactional
    public String verifyCode(String email, String code) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);

        EmailVerification verification = emailVerificationRepository
                .findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(
                        emailLowerEnc, VerificationType.POST_LOGIN_VERIFICATION)
                .orElseThrow(() -> {
                    authMetrics.recordEmailVerificationFailure(
                            VerificationType.POST_LOGIN_VERIFICATION.name(), "not_found");
                    return InvalidVerificationException.codeNotFound();
                });

        if (verification.isExpired()) {
            authMetrics.recordEmailVerificationFailure(
                    VerificationType.POST_LOGIN_VERIFICATION.name(), "expired");
            throw InvalidVerificationException.codeExpired();
        }

        if (!verification.verify(code)) {
            authMetrics.recordEmailVerificationFailure(
                    VerificationType.POST_LOGIN_VERIFICATION.name(), "mismatch");
            throw InvalidVerificationException.codeMismatch();
        }

        emailVerificationRepository.save(verification);

        Counter.builder("post_login_verification_success_total")
                .register(meterRegistry)
                .increment();

        authMetrics.recordEmailVerificationSuccess(VerificationType.POST_LOGIN_VERIFICATION.name());
        log.info("Post-login verification successful: email={}", email);

        return verification.getTokenId();
    }

    /**
     * tokenId로 재인증 완료 여부를 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean isVerifiedByTokenId(String tokenId, String email) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        return emailVerificationRepository.findByTokenId(tokenId)
                .filter(v -> v.getEmailLowerEnc().equals(emailLowerEnc))
                .filter(v -> v.getVerificationType() == VerificationType.POST_LOGIN_VERIFICATION)
                .filter(v -> v.getIsVerified())
                .filter(v -> !v.isExpired())
                .isPresent();
    }

    /**
     * 재인증 완료 후 레코드를 삭제한다.
     */
    @Transactional
    public void deleteVerificationByTokenId(String tokenId) {
        emailVerificationRepository.findByTokenId(tokenId)
                .ifPresent(emailVerificationRepository::delete);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
