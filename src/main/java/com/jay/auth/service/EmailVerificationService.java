package com.jay.auth.service;

import com.jay.auth.domain.entity.EmailVerification;
import com.jay.auth.domain.enums.VerificationType;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.EmailVerificationRepository;
import com.jay.auth.service.metrics.AuthMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailSender emailSender;
    private final EncryptionService encryptionService;
    private final AuthMetrics authMetrics;

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 10;

    /**
     * 인증 코드 생성 및 발송
     */
    @Transactional
    public String sendVerificationCode(String email, VerificationType type) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);

        // 기존 미인증 요청 삭제
        emailVerificationRepository.deleteByEmailAndType(emailLowerEnc, type);

        // 인증 코드 생성
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        // 저장
        EmailVerification verification = EmailVerification.builder()
                .emailLowerEnc(emailLowerEnc)
                .verificationCode(code)
                .verificationType(type)
                .expiresAt(expiresAt)
                .build();

        emailVerificationRepository.save(verification);

        // 이메일 발송
        emailSender.sendVerificationCode(email, code);

        authMetrics.recordEmailVerificationSent(type.name());
        log.info("Verification code sent to: {}, type: {}", email, type);

        return verification.getTokenId();
    }

    /**
     * 인증 코드 확인
     * @return tokenId 인증 완료된 토큰 ID
     */
    @Transactional
    public String verifyCode(String email, String code, VerificationType type) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);

        EmailVerification verification = emailVerificationRepository
                .findByEmailLowerEncAndVerificationTypeAndIsVerifiedFalse(emailLowerEnc, type)
                .orElseThrow(() -> {
                    authMetrics.recordEmailVerificationFailure(type.name(), "not_found");
                    return InvalidVerificationException.codeNotFound();
                });

        if (verification.isExpired()) {
            authMetrics.recordEmailVerificationFailure(type.name(), "expired");
            throw InvalidVerificationException.codeExpired();
        }

        if (!verification.verify(code)) {
            authMetrics.recordEmailVerificationFailure(type.name(), "mismatch");
            throw InvalidVerificationException.codeMismatch();
        }

        emailVerificationRepository.save(verification);

        authMetrics.recordEmailVerificationSuccess(type.name());
        log.info("Email verified: {}, type: {}", email, type);

        return verification.getTokenId();
    }

    /**
     * 이메일 인증 완료 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isVerified(String email, VerificationType type) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        LocalDateTime now = LocalDateTime.now();

        return emailVerificationRepository
                .findVerifiedAndNotExpired(emailLowerEnc, type, now)
                .isPresent();
    }

    /**
     * tokenId와 이메일로 인증 완료 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isVerifiedByTokenId(String tokenId, String email, VerificationType type) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);

        return emailVerificationRepository.findByTokenId(tokenId)
                .filter(v -> v.getEmailLowerEnc().equals(emailLowerEnc))
                .filter(v -> v.getVerificationType() == type)
                .filter(v -> v.getIsVerified())
                .filter(v -> !v.isExpired())
                .isPresent();
    }

    /**
     * 인증 완료된 레코드 삭제 (회원가입 완료 후)
     */
    @Transactional
    public void deleteVerification(String email, VerificationType type) {
        String emailLowerEnc = encryptionService.encryptForSearch(email);
        emailVerificationRepository.deleteByEmailAndType(emailLowerEnc, type);
    }

    /**
     * tokenId로 인증 레코드 삭제
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

    public int getExpirationMinutes() {
        return EXPIRATION_MINUTES;
    }
}
