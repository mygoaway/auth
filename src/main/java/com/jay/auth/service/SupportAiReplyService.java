package com.jay.auth.service;

import com.jay.auth.domain.entity.SupportComment;
import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostStatus;
import com.jay.auth.repository.SupportCommentRepository;
import com.jay.auth.repository.SupportPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportAiReplyService {

    private final AiReplyService aiReplyService;
    private final SupportCommentRepository supportCommentRepository;
    private final SupportPostRepository supportPostRepository;

    @Async("asyncExecutor")
    @Transactional
    public void generateAndSaveReply(Long postId, String title, String content, String category) {
        try {
            log.info("AI reply generation started for postId={}", postId);

            String reply = aiReplyService.generateReply(title, content, category);

            SupportComment comment = SupportComment.builder()
                    .postId(postId)
                    .userId(0L)
                    .authorNickname("AI 상담원")
                    .content(reply)
                    .isAdmin(true)
                    .isAiGenerated(true)
                    .build();

            supportCommentRepository.save(comment);

            supportPostRepository.findById(postId).ifPresent(post -> {
                if (post.getStatus() == PostStatus.OPEN) {
                    post.updateStatus(PostStatus.IN_PROGRESS);
                    post.incrementCommentCount();
                }
            });

            log.info("AI reply saved for postId={}", postId);
        } catch (Exception e) {
            log.error("Failed to generate AI reply for postId={}: {}", postId, e.getMessage(), e);
        }
    }
}
