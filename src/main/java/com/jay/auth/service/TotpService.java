package com.jay.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.entity.User;
import com.jay.auth.domain.entity.UserTwoFactor;
import com.jay.auth.dto.response.TwoFactorSetupResponse;
import com.jay.auth.dto.response.TwoFactorStatusResponse;
import com.jay.auth.exception.TwoFactorException;
import com.jay.auth.exception.UserNotFoundException;
import com.jay.auth.repository.UserRepository;
import com.jay.auth.repository.UserTwoFactorRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private final UserRepository userRepository;
    private final UserTwoFactorRepository userTwoFactorRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    private static final String ISSUER = "AuthService";
    private static final int BACKUP_CODE_COUNT = 8;
    private static final int BACKUP_CODE_LENGTH = 8;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
            new DefaultCodeGenerator(), new SystemTimeProvider());

    /**
     * 2FA 설정 시작 (QR 코드 생성)
     */
    @Transactional
    public TwoFactorSetupResponse setupTwoFactor(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // Generate secret
        String secret = secretGenerator.generate();

        // Get or create UserTwoFactor
        UserTwoFactor twoFactor = userTwoFactorRepository.findByUserId(userId)
                .orElseGet(() -> UserTwoFactor.builder()
                        .user(user)
                        .build());

        // Store encrypted secret (not yet enabled)
        String encryptedSecret = encryptionService.encrypt(secret);
        twoFactor.updateSecret(encryptedSecret);
        userTwoFactorRepository.save(twoFactor);

        // Get user email for QR code
        String email = user.getEmailEnc() != null
                ? encryptionService.decryptEmail(user.getEmailEnc())
                : "user@" + userId;

        // Generate QR code
        String qrCodeDataUrl = generateQrCodeDataUrl(secret, email);

        log.info("2FA setup initiated for user: {}", userId);

        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .qrCodeDataUrl(qrCodeDataUrl)
                .build();
    }

    /**
     * 2FA 활성화 (코드 확인 후)
     */
    @CacheEvict(value = "securityDashboard", key = "#userId")
    @Transactional
    public List<String> enableTwoFactor(Long userId, String code) {
        UserTwoFactor twoFactor = userTwoFactorRepository.findByUserId(userId)
                .orElseThrow(TwoFactorException::notSetup);

        if (twoFactor.isEnabled()) {
            throw TwoFactorException.alreadyEnabled();
        }

        if (twoFactor.getSecretEnc() == null) {
            throw TwoFactorException.notSetup();
        }

        // Verify code
        String secret = encryptionService.decrypt(twoFactor.getSecretEnc());
        if (!codeVerifier.isValidCode(secret, code)) {
            throw TwoFactorException.invalidCode();
        }

        // Generate backup codes
        List<String> backupCodes = generateBackupCodes();
        String backupCodesJson = serializeBackupCodes(backupCodes);
        String encryptedBackupCodes = encryptionService.encrypt(backupCodesJson);

        // Enable 2FA
        twoFactor.enable();
        twoFactor.updateBackupCodes(encryptedBackupCodes);
        twoFactor.recordUsage();

        log.info("2FA enabled for user: {}", userId);

        return backupCodes;
    }

    /**
     * 2FA 비활성화
     */
    @CacheEvict(value = "securityDashboard", key = "#userId")
    @Transactional
    public void disableTwoFactor(Long userId, String code) {
        UserTwoFactor twoFactor = userTwoFactorRepository.findByUserId(userId)
                .orElseThrow(TwoFactorException::notSetup);

        if (!twoFactor.isEnabled()) {
            throw TwoFactorException.notEnabled();
        }

        // Verify code
        String secret = encryptionService.decrypt(twoFactor.getSecretEnc());
        if (!codeVerifier.isValidCode(secret, code)) {
            throw TwoFactorException.invalidCode();
        }

        // Disable 2FA
        twoFactor.disable();

        log.info("2FA disabled for user: {}", userId);
    }

    /**
     * TOTP 코드 검증
     */
    @Transactional
    public boolean verifyCode(Long userId, String code) {
        UserTwoFactor twoFactor = userTwoFactorRepository.findByUserId(userId)
                .orElse(null);

        if (twoFactor == null || !twoFactor.isEnabled()) {
            return true; // 2FA not enabled, allow
        }

        String secret = encryptionService.decrypt(twoFactor.getSecretEnc());

        // Try TOTP code first
        if (codeVerifier.isValidCode(secret, code)) {
            twoFactor.recordUsage();
            return true;
        }

        // Try backup code
        if (verifyAndConsumeBackupCode(twoFactor, code)) {
            twoFactor.recordUsage();
            return true;
        }

        return false;
    }

    /**
     * 2FA 상태 조회
     */
    @Transactional(readOnly = true)
    public TwoFactorStatusResponse getTwoFactorStatus(Long userId) {
        UserTwoFactor twoFactor = userTwoFactorRepository.findByUserId(userId)
                .orElse(null);

        boolean enabled = twoFactor != null && twoFactor.isEnabled();
        int remainingBackupCodes = 0;

        if (enabled && twoFactor.getBackupCodesEnc() != null) {
            List<String> backupCodes = deserializeBackupCodes(
                    encryptionService.decrypt(twoFactor.getBackupCodesEnc()));
            remainingBackupCodes = backupCodes.size();
        }

        return TwoFactorStatusResponse.builder()
                .enabled(enabled)
                .remainingBackupCodes(remainingBackupCodes)
                .lastUsedAt(enabled ? twoFactor.getLastUsedAt() : null)
                .build();
    }

    /**
     * 백업 코드 재생성
     */
    @Transactional
    public List<String> regenerateBackupCodes(Long userId, String code) {
        UserTwoFactor twoFactor = userTwoFactorRepository.findByUserId(userId)
                .orElseThrow(TwoFactorException::notSetup);

        if (!twoFactor.isEnabled()) {
            throw TwoFactorException.notEnabled();
        }

        // Verify code
        String secret = encryptionService.decrypt(twoFactor.getSecretEnc());
        if (!codeVerifier.isValidCode(secret, code)) {
            throw TwoFactorException.invalidCode();
        }

        // Generate new backup codes
        List<String> backupCodes = generateBackupCodes();
        String backupCodesJson = serializeBackupCodes(backupCodes);
        String encryptedBackupCodes = encryptionService.encrypt(backupCodesJson);

        twoFactor.updateBackupCodes(encryptedBackupCodes);

        log.info("Backup codes regenerated for user: {}", userId);

        return backupCodes;
    }

    /**
     * 사용자에게 2FA가 필요한지 확인
     */
    @Transactional(readOnly = true)
    public boolean isTwoFactorRequired(Long userId) {
        return userTwoFactorRepository.existsByUserIdAndEnabled(userId, true);
    }

    private String generateQrCodeDataUrl(String secret, String email) {
        QrData qrData = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            QrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] qrCodeBytes = qrGenerator.generate(qrData);
            String base64 = Base64.getEncoder().encodeToString(qrCodeBytes);
            return "data:image/png;base64," + base64;
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                code.append(random.nextInt(10));
            }
            codes.add(code.toString());
        }

        return codes;
    }

    private boolean verifyAndConsumeBackupCode(UserTwoFactor twoFactor, String code) {
        if (twoFactor.getBackupCodesEnc() == null) {
            return false;
        }

        List<String> backupCodes = deserializeBackupCodes(
                encryptionService.decrypt(twoFactor.getBackupCodesEnc()));

        if (backupCodes.contains(code)) {
            backupCodes.remove(code);
            String backupCodesJson = serializeBackupCodes(backupCodes);
            twoFactor.updateBackupCodes(encryptionService.encrypt(backupCodesJson));
            log.info("Backup code consumed for user: {}, remaining: {}", twoFactor.getUser().getId(), backupCodes.size());
            return true;
        }

        return false;
    }

    private String serializeBackupCodes(List<String> codes) {
        try {
            return objectMapper.writeValueAsString(codes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize backup codes", e);
        }
    }

    private List<String> deserializeBackupCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
