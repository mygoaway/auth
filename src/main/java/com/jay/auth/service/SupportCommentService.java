package com.jay.auth.service;

import com.jay.auth.domain.entity.SupportComment;
import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.dto.request.CreateSupportCommentRequest;
import com.jay.auth.dto.response.SupportPostDetailResponse;
import com.jay.auth.exception.SupportPostAccessDeniedException;
import com.jay.auth.exception.SupportPostNotFoundException;
import com.jay.auth.repository.SupportCommentRepository;
import com.jay.auth.repository.SupportPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportCommentService {

    private final SupportCommentRepository supportCommentRepository;
    private final SupportPostRepository supportPostRepository;
    private final UserService userService;

    @Transactional
    public SupportPostDetailResponse.CommentResponse createComment(Long postId, Long userId, boolean isAdmin,
                                                                     CreateSupportCommentRequest request) {
        SupportPost post = supportPostRepository.findById(postId)
                .orElseThrow(SupportPostNotFoundException::new);

        // 비공개 글은 작성자 또는 관리자만 댓글 작성 가능
        if (post.isPrivate() && !post.getUserId().equals(userId) && !isAdmin) {
            throw new SupportPostAccessDeniedException();
        }

        String nickname = userService.getNickname(userId);

        SupportComment comment = SupportComment.builder()
                .postId(postId)
                .userId(userId)
                .authorNickname(nickname)
                .content(request.getContent())
                .isAdmin(isAdmin)
                .build();

        supportCommentRepository.save(comment);
        post.incrementCommentCount();

        log.info("Support comment created: commentId={}, postId={}, userId={}", comment.getId(), postId, userId);

        return SupportPostDetailResponse.CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, Long userId, boolean isAdmin) {
        SupportPost post = supportPostRepository.findById(postId)
                .orElseThrow(SupportPostNotFoundException::new);

        SupportComment comment = supportCommentRepository.findById(commentId)
                .orElseThrow(() -> new SupportPostNotFoundException());

        // 댓글 작성자 또는 관리자만 삭제 가능
        if (!comment.getUserId().equals(userId) && !isAdmin) {
            throw new SupportPostAccessDeniedException();
        }

        supportCommentRepository.delete(comment);
        post.decrementCommentCount();

        log.info("Support comment deleted: commentId={}, postId={}, userId={}", commentId, postId, userId);
    }
}
