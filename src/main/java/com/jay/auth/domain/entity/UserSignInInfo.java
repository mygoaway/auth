package com.jay.auth.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_user_sign_in_info", indexes = {
        @Index(name = "idx_login_email_lower", columnList = "login_email_lower_enc", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSignInInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sign_in_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "login_email_enc", nullable = false, length = 512)
    private String loginEmailEnc;

    @Column(name = "login_email_lower_enc", nullable = false, length = 512)
    private String loginEmailLowerEnc;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "login_fail_count", nullable = false)
    private Integer loginFailCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Builder
    public UserSignInInfo(User user, String loginEmailEnc, String loginEmailLowerEnc,
                          String passwordHash) {
        this.user = user;
        this.loginEmailEnc = loginEmailEnc;
        this.loginEmailLowerEnc = loginEmailLowerEnc;
        this.passwordHash = passwordHash;
        this.loginFailCount = 0;
        user.setSignInInfo(this);
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = LocalDateTime.now();
    }

    public void recordLoginSuccess() {
        this.lastLoginAt = LocalDateTime.now();
        this.loginFailCount = 0;
        this.lockedUntil = null;
    }

    public void recordLoginFailure() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    public boolean isLocked() {
        return this.lockedUntil != null && LocalDateTime.now().isBefore(this.lockedUntil);
    }

    public void unlock() {
        this.loginFailCount = 0;
        this.lockedUntil = null;
    }
}
