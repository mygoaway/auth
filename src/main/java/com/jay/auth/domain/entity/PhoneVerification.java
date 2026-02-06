package com.jay.auth.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_phone_verification", indexes = {
        @Index(name = "idx_phone_token", columnList = "token_id"),
        @Index(name = "idx_phone_lower_enc", columnList = "phone_lower_enc")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @Column(name = "token_id", nullable = false, unique = true, length = 36)
    private String tokenId;

    @Column(name = "phone_enc", nullable = false, length = 512)
    private String phoneEnc;

    @Column(name = "phone_lower_enc", nullable = false, length = 512)
    private String phoneLowerEnc;

    @Column(name = "verification_code", nullable = false, length = 10)
    private String verificationCode;

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
    public PhoneVerification(String phoneEnc, String phoneLowerEnc, String verificationCode, LocalDateTime expiresAt) {
        this.phoneEnc = phoneEnc;
        this.phoneLowerEnc = phoneLowerEnc;
        this.verificationCode = verificationCode;
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
