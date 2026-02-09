package com.jay.auth.domain.entity;

import com.jay.auth.domain.enums.UserRole;
import com.jay.auth.domain.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_user", indexes = {
        @Index(name = "idx_email_lower", columnList = "email_lower_enc")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "user_uuid", nullable = false, unique = true, length = 36)
    private String userUuid;

    @Column(name = "email_enc", length = 512)
    private String emailEnc;

    @Column(name = "email_lower_enc", length = 512)
    private String emailLowerEnc;

    @Column(name = "recovery_email_enc", length = 512)
    private String recoveryEmailEnc;

    @Column(name = "recovery_email_lower_enc", length = 512)
    private String recoveryEmailLowerEnc;

    @Column(name = "phone_enc", length = 512)
    private String phoneEnc;

    @Column(name = "nickname_enc", length = 512)
    private String nicknameEnc;

    @Column(name = "email_updated_at")
    private LocalDateTime emailUpdatedAt;

    @Column(name = "phone_updated_at")
    private LocalDateTime phoneUpdatedAt;

    @Column(name = "nickname_updated_at")
    private LocalDateTime nicknameUpdatedAt;

    @Column(name = "deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserSignInInfo signInInfo;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserChannel> channels = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.userUuid == null) {
            this.userUuid = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
        if (this.role == null) {
            this.role = UserRole.USER;
        }
    }

    @Builder
    public User(String emailEnc, String emailLowerEnc, String recoveryEmailEnc,
                String recoveryEmailLowerEnc, String phoneEnc, String nicknameEnc,
                UserStatus status) {
        this.emailEnc = emailEnc;
        this.emailLowerEnc = emailLowerEnc;
        this.recoveryEmailEnc = recoveryEmailEnc;
        this.recoveryEmailLowerEnc = recoveryEmailLowerEnc;
        this.phoneEnc = phoneEnc;
        this.nicknameEnc = nicknameEnc;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    public void updateEmail(String emailEnc, String emailLowerEnc) {
        this.emailEnc = emailEnc;
        this.emailLowerEnc = emailLowerEnc;
        this.emailUpdatedAt = LocalDateTime.now();
    }

    public void updateRecoveryEmail(String recoveryEmailEnc, String recoveryEmailLowerEnc) {
        this.recoveryEmailEnc = recoveryEmailEnc;
        this.recoveryEmailLowerEnc = recoveryEmailLowerEnc;
    }

    public void updatePhone(String phoneEnc) {
        this.phoneEnc = phoneEnc;
        this.phoneUpdatedAt = LocalDateTime.now();
    }

    public void updateNickname(String nicknameEnc) {
        this.nicknameEnc = nicknameEnc;
        this.nicknameUpdatedAt = LocalDateTime.now();
    }

    public void updateStatus(UserStatus status) {
        this.status = status;
    }

    public void updateRole(UserRole role) {
        this.role = role;
    }

    public void requestDeletion() {
        this.status = UserStatus.PENDING_DELETE;
        this.deletionRequestedAt = LocalDateTime.now();
    }

    public void cancelDeletion() {
        this.status = UserStatus.ACTIVE;
        this.deletionRequestedAt = null;
    }

    public void setSignInInfo(UserSignInInfo signInInfo) {
        this.signInInfo = signInInfo;
    }

    public void addChannel(UserChannel channel) {
        this.channels.add(channel);
    }

    public void removeChannel(UserChannel channel) {
        this.channels.remove(channel);
    }
}
