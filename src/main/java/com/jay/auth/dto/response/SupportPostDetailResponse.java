package com.jay.auth.dto.response;

import com.jay.auth.domain.entity.SupportComment;
import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SupportPostDetailResponse {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String authorNickname;
    private PostCategory category;
    private PostStatus status;
    private boolean isPrivate;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponse> comments;

    public static SupportPostDetailResponse of(SupportPost post, List<SupportComment> comments) {
        return SupportPostDetailResponse.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .title(post.getTitle())
                .content(post.getContent())
                .authorNickname(post.getAuthorNickname())
                .category(post.getCategory())
                .status(post.getStatus())
                .isPrivate(post.isPrivate())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .comments(comments.stream().map(CommentResponse::from).toList())
                .build();
    }

    @Getter
    @Builder
    public static class CommentResponse {

        private Long id;
        private Long userId;
        private String authorNickname;
        private String content;
        private boolean isAdmin;
        private LocalDateTime createdAt;

        public static CommentResponse from(SupportComment comment) {
            return CommentResponse.builder()
                    .id(comment.getId())
                    .userId(comment.getUserId())
                    .authorNickname(comment.getAuthorNickname())
                    .content(comment.getContent())
                    .isAdmin(comment.isAdmin())
                    .createdAt(comment.getCreatedAt())
                    .build();
        }
    }
}
