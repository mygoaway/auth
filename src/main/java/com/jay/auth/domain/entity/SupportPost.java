package com.jay.auth.domain.entity;

import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tb_support_post", indexes = {
        @Index(name = "idx_support_post_user_id", columnList = "user_id"),
        @Index(name = "idx_support_post_status", columnList = "status"),
        @Index(name = "idx_support_post_created_at", columnList = "created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "author_nickname", nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Builder
    public SupportPost(Long userId, String authorNickname, String title, String content,
                       PostCategory category, boolean isPrivate) {
        this.userId = userId;
        this.authorNickname = authorNickname;
        this.title = title;
        this.content = content;
        this.category = category;
        this.isPrivate = isPrivate;
        this.status = PostStatus.OPEN;
        this.viewCount = 0;
        this.commentCount = 0;
    }

    public void update(String title, String content, PostCategory category, boolean isPrivate) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.isPrivate = isPrivate;
    }

    public void updateStatus(PostStatus status) {
        this.status = status;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }
}
