package com.jay.auth.service;

import com.jay.auth.domain.entity.SupportComment;
import com.jay.auth.domain.entity.SupportPost;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.dto.request.CreateSupportCommentRequest;
import com.jay.auth.dto.response.SupportPostDetailResponse;
import com.jay.auth.exception.SupportPostAccessDeniedException;
import com.jay.auth.exception.SupportPostNotFoundException;
import com.jay.auth.repository.SupportCommentRepository;
import com.jay.auth.repository.SupportPostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupportCommentServiceTest {

    @InjectMocks
    private SupportCommentService supportCommentService;

    @Mock
    private SupportCommentRepository supportCommentRepository;

    @Mock
    private SupportPostRepository supportPostRepository;

    @Mock
    private UserService userService;

    @Nested
    @DisplayName("댓글 작성")
    class CreateComment {

        @Test
        @DisplayName("공개 게시글에 댓글 작성 성공")
        void createCommentOnPublicPost() {
            // given
            SupportPost post = createPost(1L, 1L, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(userService.getNickname(2L)).willReturn("댓글유저");
            given(supportCommentRepository.save(any(SupportComment.class))).willAnswer(invocation -> {
                SupportComment c = invocation.getArgument(0);
                setField(c, "id", 1L);
                return c;
            });

            CreateSupportCommentRequest request = new CreateSupportCommentRequest();
            setField(request, "content", "댓글 내용");

            // when
            SupportPostDetailResponse.CommentResponse result =
                    supportCommentService.createComment(1L, 2L, false, request);

            // then
            assertThat(result.getContent()).isEqualTo("댓글 내용");
            assertThat(result.getAuthorNickname()).isEqualTo("댓글유저");
            assertThat(result.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("관리자가 댓글 작성 시 isAdmin 플래그 설정")
        void createCommentByAdmin() {
            // given
            SupportPost post = createPost(1L, 1L, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(userService.getNickname(2L)).willReturn("관리자");
            given(supportCommentRepository.save(any(SupportComment.class))).willAnswer(invocation -> {
                SupportComment c = invocation.getArgument(0);
                setField(c, "id", 1L);
                return c;
            });

            CreateSupportCommentRequest request = new CreateSupportCommentRequest();
            setField(request, "content", "관리자 답변");

            // when
            SupportPostDetailResponse.CommentResponse result =
                    supportCommentService.createComment(1L, 2L, true, request);

            // then
            assertThat(result.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("비공개 게시글에 다른 사용자가 댓글 작성 시 접근 거부")
        void createCommentOnPrivatePostByOtherUser() {
            // given
            SupportPost post = createPost(1L, 1L, true);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            CreateSupportCommentRequest request = new CreateSupportCommentRequest();
            setField(request, "content", "댓글");

            // when & then
            assertThatThrownBy(() -> supportCommentService.createComment(1L, 2L, false, request))
                    .isInstanceOf(SupportPostAccessDeniedException.class);
        }

        @Test
        @DisplayName("존재하지 않는 게시글에 댓글 작성 실패")
        void createCommentOnNonExistentPost() {
            // given
            given(supportPostRepository.findById(999L)).willReturn(Optional.empty());

            CreateSupportCommentRequest request = new CreateSupportCommentRequest();
            setField(request, "content", "댓글");

            // when & then
            assertThatThrownBy(() -> supportCommentService.createComment(999L, 1L, false, request))
                    .isInstanceOf(SupportPostNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("댓글 삭제")
    class DeleteComment {

        @Test
        @DisplayName("댓글 작성자가 삭제 성공")
        void deleteCommentByAuthor() {
            // given
            SupportPost post = createPost(1L, 1L, false);
            SupportComment comment = createComment(1L, 1L, 2L, "댓글유저", "내용");
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findById(1L)).willReturn(Optional.of(comment));

            // when
            supportCommentService.deleteComment(1L, 1L, 2L, false);

            // then
            verify(supportCommentRepository).delete(comment);
        }

        @Test
        @DisplayName("관리자가 댓글 삭제 성공")
        void deleteCommentByAdmin() {
            // given
            SupportPost post = createPost(1L, 1L, false);
            SupportComment comment = createComment(1L, 1L, 2L, "댓글유저", "내용");
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findById(1L)).willReturn(Optional.of(comment));

            // when
            supportCommentService.deleteComment(1L, 1L, 3L, true);

            // then
            verify(supportCommentRepository).delete(comment);
        }

        @Test
        @DisplayName("다른 사용자가 댓글 삭제 시 접근 거부")
        void deleteCommentByOtherUser() {
            // given
            SupportPost post = createPost(1L, 1L, false);
            SupportComment comment = createComment(1L, 1L, 2L, "댓글유저", "내용");
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findById(1L)).willReturn(Optional.of(comment));

            // when & then
            assertThatThrownBy(() -> supportCommentService.deleteComment(1L, 1L, 3L, false))
                    .isInstanceOf(SupportPostAccessDeniedException.class);
        }
    }

    private SupportPost createPost(Long id, Long userId, boolean isPrivate) {
        SupportPost post = SupportPost.builder()
                .userId(userId)
                .authorNickname("작성자")
                .title("제목")
                .content("내용")
                .category(PostCategory.ACCOUNT)
                .isPrivate(isPrivate)
                .build();
        setField(post, "id", id);
        return post;
    }

    private SupportComment createComment(Long id, Long postId, Long userId,
                                          String authorNickname, String content) {
        SupportComment comment = SupportComment.builder()
                .postId(postId)
                .userId(userId)
                .authorNickname(authorNickname)
                .content(content)
                .isAdmin(false)
                .build();
        setField(comment, "id", id);
        return comment;
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
