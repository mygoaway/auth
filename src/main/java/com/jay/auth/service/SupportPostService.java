package com.jay.auth.service;

import com.jay.auth.domain.entity.SupportComment;
import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import com.jay.auth.dto.request.CreateSupportPostRequest;
import com.jay.auth.dto.request.UpdateSupportPostRequest;
import com.jay.auth.dto.response.SupportPostDetailResponse;
import com.jay.auth.dto.response.SupportPostListResponse;
import com.jay.auth.exception.SupportPostAccessDeniedException;
import com.jay.auth.exception.SupportPostNotModifiableException;
import com.jay.auth.exception.SupportPostNotFoundException;
import com.jay.auth.repository.SupportCommentRepository;
import com.jay.auth.repository.SupportPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportPostService {

    private final SupportPostRepository supportPostRepository;
    private final SupportCommentRepository supportCommentRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public Page<SupportPostListResponse> getPosts(Long userId, boolean isAdmin,
                                                   PostCategory category, PostStatus status,
                                                   Pageable pageable) {
        Page<SupportPost> posts;
        if (isAdmin) {
            posts = supportPostRepository.findAllForAdmin(category, status, pageable);
        } else {
            posts = supportPostRepository.findAllForUser(userId, category, status, pageable);
        }
        return posts.map(SupportPostListResponse::from);
    }

    @Transactional
    public SupportPostDetailResponse getPost(Long postId, Long userId, boolean isAdmin) {
        SupportPost post = supportPostRepository.findById(postId)
                .orElseThrow(SupportPostNotFoundException::new);

        // 비공개 글은 작성자 또는 관리자만 조회 가능
        if (post.isPrivate() && !post.getUserId().equals(userId) && !isAdmin) {
            throw new SupportPostAccessDeniedException();
        }

        post.incrementViewCount();

        List<SupportComment> comments = supportCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        return SupportPostDetailResponse.of(post, comments);
    }

    @Transactional
    public SupportPostDetailResponse createPost(Long userId, CreateSupportPostRequest request) {
        String nickname = userService.getNickname(userId);

        SupportPost post = SupportPost.builder()
                .userId(userId)
                .authorNickname(nickname)
                .title(request.getTitle())
                .content(request.getContent())
                .category(request.getCategory())
                .isPrivate(request.isPrivate())
                .build();

        supportPostRepository.save(post);

        log.info("Support post created: postId={}, userId={}", post.getId(), userId);

        return SupportPostDetailResponse.of(post, List.of());
    }

    @Transactional
    public SupportPostDetailResponse updatePost(Long postId, Long userId, UpdateSupportPostRequest request) {
        SupportPost post = supportPostRepository.findById(postId)
                .orElseThrow(SupportPostNotFoundException::new);

        if (!post.getUserId().equals(userId)) {
            throw new SupportPostAccessDeniedException();
        }

        if (post.getStatus() != PostStatus.OPEN) {
            throw new SupportPostNotModifiableException();
        }

        post.update(request.getTitle(), request.getContent(), request.getCategory(), request.isPrivate());

        List<SupportComment> comments = supportCommentRepository.findByPostIdOrderByCreatedAtAsc(postId);

        log.info("Support post updated: postId={}, userId={}", postId, userId);

        return SupportPostDetailResponse.of(post, comments);
    }

    @Transactional
    public void deletePost(Long postId, Long userId, boolean isAdmin) {
        SupportPost post = supportPostRepository.findById(postId)
                .orElseThrow(SupportPostNotFoundException::new);

        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new SupportPostAccessDeniedException();
        }

        if (post.getStatus() != PostStatus.OPEN) {
            throw new SupportPostNotModifiableException();
        }

        supportCommentRepository.deleteByPostId(postId);
        supportPostRepository.delete(post);

        log.info("Support post deleted: postId={}, userId={}, isAdmin={}", postId, userId, isAdmin);
    }

    @Transactional
    public void updatePostStatus(Long postId, PostStatus status) {
        SupportPost post = supportPostRepository.findById(postId)
                .orElseThrow(SupportPostNotFoundException::new);

        post.updateStatus(status);

        log.info("Support post status updated: postId={}, status={}", postId, status);
    }
}
