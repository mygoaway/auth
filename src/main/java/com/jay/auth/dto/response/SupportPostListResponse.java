package com.jay.auth.dto.response;

import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SupportPostListResponse {

    private Long id;
    private String title;
    private String authorNickname;
    private PostCategory category;
    private PostStatus status;
    private boolean isPrivate;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;

    public static SupportPostListResponse from(SupportPost post) {
        return SupportPostListResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .authorNickname(post.getAuthorNickname())
                .category(post.getCategory())
                .status(post.getStatus())
                .isPrivate(post.isPrivate())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
