package com.jay.auth.domain.entity;

import com.jay.auth.domain.enums.IpRuleType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_ip_access_rule", indexes = {
        @Index(name = "idx_ip_rule_ip", columnList = "ip_address"),
        @Index(name = "idx_ip_rule_type", columnList = "rule_type"),
        @Index(name = "idx_ip_rule_active", columnList = "is_active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IpAccessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 50)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 10)
    private IpRuleType ruleType;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public IpAccessRule(String ipAddress, IpRuleType ruleType, String reason,
                        Long createdBy, LocalDateTime expiredAt) {
        this.ipAddress = ipAddress;
        this.ruleType = ruleType;
        this.reason = reason;
        this.createdBy = createdBy;
        this.expiredAt = expiredAt;
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isExpired() {
        return expiredAt != null && LocalDateTime.now().isAfter(expiredAt);
    }
}
