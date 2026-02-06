package com.jay.auth.service;

import com.jay.auth.domain.entity.PhoneVerification;
import com.jay.auth.exception.InvalidVerificationException;
import com.jay.auth.repository.PhoneVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final EncryptionService encryptionService;

    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 3;

    /**
     * 인증 코드 생성 및 발송 (실제 SMS는 로그로 대체)
     */
    @Transactional
    public String sendVerificationCode(String phone) {
        String phoneEnc = encryptionService.encryptPhone(phone);
        String phoneLowerEnc = encryptionService.encryptForSearch(phone);

        // 기존 미인증 요청 삭제
        phoneVerificationRepository.deleteByPhoneLowerEnc(phoneLowerEnc);

        // 인증 코드 생성
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES);

        // 저장
        PhoneVerification verification = PhoneVerification.builder()
                .phoneEnc(phoneEnc)
                .phoneLowerEnc(phoneLowerEnc)
                .verificationCode(code)
                .expiresAt(expiresAt)
                .build();

        phoneVerificationRepository.save(verification);

        // 실제 SMS 발송 대신 로그로 대체
        log.info("========================================");
        log.info("SMS Verification Code for {}: {}", phone, code);
        log.info("========================================");

        log.info("Verification code sent to phone: {}", phone);

        return verification.getTokenId();
    }

    /**
     * 인증 코드 확인
     * @return tokenId (인증 성공 시)
     */
    @Transactional
    public String verifyCode(String phone, String code) {
        String phoneLowerEnc = encryptionService.encryptForSearch(phone);

        PhoneVerification verification = phoneVerificationRepository
                .findByPhoneLowerEncAndIsVerifiedFalse(phoneLowerEnc)
                .orElseThrow(InvalidVerificationException::codeNotFound);

        if (verification.isExpired()) {
            throw InvalidVerificationException.codeExpired();
        }

        if (!verification.verify(code)) {
            throw InvalidVerificationException.codeMismatch();
        }

        phoneVerificationRepository.save(verification);

        log.info("Phone verified: {}", phone);

        return verification.getTokenId();
    }

    /**
     * tokenId 유효성 검증
     */
    @Transactional(readOnly = true)
    public boolean isValidTokenId(String tokenId) {
        LocalDateTime now = LocalDateTime.now();
        return phoneVerificationRepository.findByTokenIdAndVerifiedTrue(tokenId, now).isPresent();
    }

    /**
     * tokenId로 인증 레코드 삭제
     */
    @Transactional
    public void deleteVerificationByTokenId(String tokenId) {
        phoneVerificationRepository.findByTokenId(tokenId)
                .ifPresent(phoneVerificationRepository::delete);
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
