package com.jay.auth.domain.entity;

import com.jay.auth.domain.enums.ChannelCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tb_user_channel", indexes = {
        @Index(name = "idx_channel_unique", columnList = "channel_code, channel_key", unique = true),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserChannel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_channel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_code", nullable = false, length = 20)
    private ChannelCode channelCode;

    @Column(name = "channel_key", nullable = false, length = 255)
    private String channelKey;

    @Column(name = "channel_email_enc", length = 512)
    private String channelEmailEnc;

    @Column(name = "channel_email_lower_enc", length = 512)
    private String channelEmailLowerEnc;

    @Builder
    public UserChannel(User user, ChannelCode channelCode, String channelKey,
                       String channelEmailEnc, String channelEmailLowerEnc) {
        this.user = user;
        this.channelCode = channelCode;
        this.channelKey = channelKey;
        this.channelEmailEnc = channelEmailEnc;
        this.channelEmailLowerEnc = channelEmailLowerEnc;
        user.addChannel(this);
    }

    public void updateChannelEmail(String channelEmailEnc, String channelEmailLowerEnc) {
        this.channelEmailEnc = channelEmailEnc;
        this.channelEmailLowerEnc = channelEmailLowerEnc;
    }
}
