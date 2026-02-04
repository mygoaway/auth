package com.jay.auth.domain.entity;

import com.jay.auth.domain.enums.VerificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_email_verification", indexes = {
        @Index(name = "idx_token", columnList = "token_id"),
        @Index(name = "idx_email_type", columnList = "email_lower_enc, verification_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @Column(name = "token_id", nullable = false, unique = true, length = 36)
    private String tokenId;

    @Column(name = "email_lower_enc", nullable = false, length = 512)
    private String emailLowerEnc;

    @Column(name = "verification_code", nullable = false, length = 10)
    private String verificationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 20)
    private VerificationType verificationType;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.tokenId == null) {
            this.tokenId = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Builder
    public EmailVerification(String emailLowerEnc, String verificationCode,
                             VerificationType verificationType, LocalDateTime expiresAt) {
        this.emailLowerEnc = emailLowerEnc;
        this.verificationCode = verificationCode;
        this.verificationType = verificationType;
        this.expiresAt = expiresAt;
        this.isVerified = false;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean verify(String code) {
        if (this.isVerified) {
            return false;
        }
        if (isExpired()) {
            return false;
        }
        if (!this.verificationCode.equals(code)) {
            return false;
        }
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        return true;
    }
}
