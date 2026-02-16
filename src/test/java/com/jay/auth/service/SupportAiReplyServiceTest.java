package com.jay.auth.service;

import com.jay.auth.domain.entity.SupportComment;
import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import com.jay.auth.repository.SupportCommentRepository;
import com.jay.auth.repository.SupportPostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportAiReplyServiceTest {

    @InjectMocks
    private SupportAiReplyService supportAiReplyService;

    @Mock
    private AiReplyService aiReplyService;

    @Mock
    private SupportCommentRepository supportCommentRepository;

    @Mock
    private SupportPostRepository supportPostRepository;

    @Nested
    @DisplayName("AI 자동응답 생성 및 저장")
    class GenerateAndSaveReply {

        @Test
        @DisplayName("AI 응답 생성 및 댓글 저장 성공")
        void generateAndSaveReplySuccess() {
            // given
            Long postId = 1L;
            SupportPost post = createPost(postId, PostStatus.OPEN);
            given(aiReplyService.generateReply("제목", "내용", "ACCOUNT")).willReturn("AI 응답입니다");
            given(supportPostRepository.findById(postId)).willReturn(Optional.of(post));

            // when
            supportAiReplyService.generateAndSaveReply(postId, "제목", "내용", "ACCOUNT");

            // then
            ArgumentCaptor<SupportComment> captor = ArgumentCaptor.forClass(SupportComment.class);
            verify(supportCommentRepository).save(captor.capture());

            SupportComment saved = captor.getValue();
            assertThat(saved.getPostId()).isEqualTo(postId);
            assertThat(saved.getUserId()).isEqualTo(0L);
            assertThat(saved.getContent()).isEqualTo("AI 응답입니다");
            assertThat(saved.isAdmin()).isTrue();
            assertThat(saved.isAiGenerated()).isTrue();
            assertThat(saved.getAuthorNickname()).isEqualTo("AI 상담원");
        }

        @Test
        @DisplayName("게시글 상태가 OPEN이면 IN_PROGRESS로 변경")
        void changeStatusToInProgress() {
            // given
            Long postId = 1L;
            SupportPost post = createPost(postId, PostStatus.OPEN);
            given(aiReplyService.generateReply(any(), any(), any())).willReturn("응답");
            given(supportPostRepository.findById(postId)).willReturn(Optional.of(post));

            // when
            supportAiReplyService.generateAndSaveReply(postId, "제목", "내용", "ACCOUNT");

            // then
            assertThat(post.getStatus()).isEqualTo(PostStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("게시글 상태가 OPEN이 아니면 상태 변경하지 않음")
        void doNotChangeStatusIfNotOpen() {
            // given
            Long postId = 1L;
            SupportPost post = createPost(postId, PostStatus.IN_PROGRESS);
            given(aiReplyService.generateReply(any(), any(), any())).willReturn("응답");
            given(supportPostRepository.findById(postId)).willReturn(Optional.of(post));

            // when
            supportAiReplyService.generateAndSaveReply(postId, "제목", "내용", "ACCOUNT");

            // then
            assertThat(post.getStatus()).isEqualTo(PostStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("AI 서비스 실패 시 예외를 던지지 않음")
        void handleAiServiceFailure() {
            // given
            Long postId = 1L;
            given(aiReplyService.generateReply(any(), any(), any())).willThrow(new RuntimeException("API error"));

            // when & then (no exception thrown)
            supportAiReplyService.generateAndSaveReply(postId, "제목", "내용", "ACCOUNT");

            verify(supportCommentRepository, never()).save(any());
        }

        @Test
        @DisplayName("게시글이 존재하지 않아도 댓글은 저장됨")
        void saveCommentEvenIfPostNotFound() {
            // given
            Long postId = 999L;
            given(aiReplyService.generateReply(any(), any(), any())).willReturn("응답");
            given(supportPostRepository.findById(postId)).willReturn(Optional.empty());

            // when
            supportAiReplyService.generateAndSaveReply(postId, "제목", "내용", "ACCOUNT");

            // then
            verify(supportCommentRepository).save(any(SupportComment.class));
        }
    }

    private SupportPost createPost(Long id, PostStatus status) {
        SupportPost post = SupportPost.builder()
                .userId(1L)
                .authorNickname("작성자")
                .title("제목")
                .content("내용")
                .category(PostCategory.ACCOUNT)
                .isPrivate(false)
                .build();
        setField(post, "id", id);
        setField(post, "status", status);
        return post;
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
