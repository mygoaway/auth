package com.jay.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_user_two_factor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserTwoFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * TOTP 비밀키 (암호화 저장)
     */
    @Column(name = "secret_enc", length = 512)
    private String secretEnc;

    /**
     * 2FA 활성화 여부
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /**
     * 백업 코드 (암호화 저장, JSON 배열)
     */
    @Column(name = "backup_codes_enc", length = 1024)
    private String backupCodesEnc;

    /**
     * 마지막 사용 시간
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
        this.secretEnc = null;
        this.backupCodesEnc = null;
    }

    public void updateSecret(String secretEnc) {
        this.secretEnc = secretEnc;
    }

    public void updateBackupCodes(String backupCodesEnc) {
        this.backupCodesEnc = backupCodesEnc;
    }

    public void recordUsage() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
