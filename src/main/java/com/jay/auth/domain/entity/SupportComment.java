package com.jay.auth.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tb_support_comment", indexes = {
        @Index(name = "idx_support_comment_post_id", columnList = "post_id"),
        @Index(name = "idx_support_comment_user_id", columnList = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "author_nickname", nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    @Column(name = "is_ai_generated", nullable = false)
    private boolean isAiGenerated;

    @Builder
    public SupportComment(Long postId, Long userId, String authorNickname, String content, boolean isAdmin, boolean isAiGenerated) {
        this.postId = postId;
        this.userId = userId;
        this.authorNickname = authorNickname;
        this.content = content;
        this.isAdmin = isAdmin;
        this.isAiGenerated = isAiGenerated;
    }
}
