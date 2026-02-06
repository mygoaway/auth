package com.jay.auth.domain.entity;

import com.jay.auth.domain.enums.ChannelCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_login_history", indexes = {
        @Index(name = "idx_login_history_user", columnList = "user_id"),
        @Index(name = "idx_login_history_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_code", nullable = false, length = 20)
    private ChannelCode channelCode;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "is_success", nullable = false)
    private Boolean isSuccess = true;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Builder
    public LoginHistory(Long userId, ChannelCode channelCode, String ipAddress, String userAgent,
                        String deviceType, String browser, String os, String location,
                        Boolean isSuccess, String failureReason) {
        this.userId = userId;
        this.channelCode = channelCode;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceType = deviceType;
        this.browser = browser;
        this.os = os;
        this.location = location;
        this.isSuccess = isSuccess != null ? isSuccess : true;
        this.failureReason = failureReason;
    }
}
