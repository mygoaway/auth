package com.jay.auth.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupportPostServiceTest {

    @InjectMocks
    private SupportPostService supportPostService;

    @Mock
    private SupportPostRepository supportPostRepository;

    @Mock
    private SupportCommentRepository supportCommentRepository;

    @Mock
    private UserService userService;

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {

        @Test
        @DisplayName("일반 사용자 목록 조회 성공")
        void getPostsForUser() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            SupportPost post = createPost(1L, 1L, "테스트", "테스트 제목", "테스트 내용", PostCategory.ACCOUNT, false);
            Page<SupportPost> page = new PageImpl<>(List.of(post));

            given(supportPostRepository.findAllForUser(eq(1L), eq(null), eq(null), eq(pageable)))
                    .willReturn(page);

            // when
            Page<SupportPostListResponse> result = supportPostService.getPosts(1L, false, null, null, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("테스트 제목");
        }

        @Test
        @DisplayName("관리자 목록 조회 성공")
        void getPostsForAdmin() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<SupportPost> page = new PageImpl<>(List.of());

            given(supportPostRepository.findAllForAdmin(eq(null), eq(null), eq(pageable)))
                    .willReturn(page);

            // when
            Page<SupportPostListResponse> result = supportPostService.getPosts(1L, true, null, null, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
            verify(supportPostRepository).findAllForAdmin(eq(null), eq(null), eq(pageable));
        }
    }

    @Nested
    @DisplayName("게시글 상세 조회")
    class GetPost {

        @Test
        @DisplayName("공개 게시글 조회 성공")
        void getPublicPost() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findByPostIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

            // when
            SupportPostDetailResponse result = supportPostService.getPost(1L, 2L, false);

            // then
            assertThat(result.getTitle()).isEqualTo("제목");
        }

        @Test
        @DisplayName("비공개 게시글 - 작성자 조회 성공")
        void getPrivatePostByAuthor() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, true);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findByPostIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

            // when
            SupportPostDetailResponse result = supportPostService.getPost(1L, 1L, false);

            // then
            assertThat(result.getTitle()).isEqualTo("제목");
        }

        @Test
        @DisplayName("비공개 게시글 - 다른 사용자 접근 거부")
        void getPrivatePostByOtherUser() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, true);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            // when & then
            assertThatThrownBy(() -> supportPostService.getPost(1L, 2L, false))
                    .isInstanceOf(SupportPostAccessDeniedException.class);
        }

        @Test
        @DisplayName("비공개 게시글 - 관리자 조회 성공")
        void getPrivatePostByAdmin() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, true);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findByPostIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

            // when
            SupportPostDetailResponse result = supportPostService.getPost(1L, 2L, true);

            // then
            assertThat(result.getTitle()).isEqualTo("제목");
        }

        @Test
        @DisplayName("존재하지 않는 게시글 조회 실패")
        void getPostNotFound() {
            // given
            given(supportPostRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> supportPostService.getPost(999L, 1L, false))
                    .isInstanceOf(SupportPostNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("게시글 작성")
    class CreatePost {

        @Test
        @DisplayName("게시글 작성 성공")
        void createPostSuccess() {
            // given
            given(userService.getNickname(1L)).willReturn("테스트유저");
            given(supportPostRepository.save(any(SupportPost.class))).willAnswer(invocation -> {
                SupportPost p = invocation.getArgument(0);
                setField(p, "id", 1L);
                return p;
            });

            CreateSupportPostRequest request = new CreateSupportPostRequest();
            setField(request, "title", "테스트 제목");
            setField(request, "content", "테스트 내용");
            setField(request, "category", PostCategory.ACCOUNT);
            setField(request, "privatePost", false);

            // when
            SupportPostDetailResponse result = supportPostService.createPost(1L, request);

            // then
            assertThat(result.getTitle()).isEqualTo("테스트 제목");
            assertThat(result.getAuthorNickname()).isEqualTo("테스트유저");
            verify(supportPostRepository).save(any(SupportPost.class));
        }
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {

        @Test
        @DisplayName("작성자가 게시글 수정 성공")
        void updatePostSuccess() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "기존 제목", "기존 내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));
            given(supportCommentRepository.findByPostIdOrderByCreatedAtAsc(1L)).willReturn(List.of());

            UpdateSupportPostRequest request = new UpdateSupportPostRequest();
            setField(request, "title", "수정된 제목");
            setField(request, "content", "수정된 내용");
            setField(request, "category", PostCategory.SECURITY);
            setField(request, "privatePost", true);

            // when
            SupportPostDetailResponse result = supportPostService.updatePost(1L, 1L, request);

            // then
            assertThat(result.getTitle()).isEqualTo("수정된 제목");
        }

        @Test
        @DisplayName("대기중이 아닌 게시글 수정 시 실패")
        void updatePostNotOpen() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            setField(post, "status", PostStatus.IN_PROGRESS);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            UpdateSupportPostRequest request = new UpdateSupportPostRequest();
            setField(request, "title", "수정");
            setField(request, "content", "수정");
            setField(request, "category", PostCategory.ACCOUNT);

            // when & then
            assertThatThrownBy(() -> supportPostService.updatePost(1L, 1L, request))
                    .isInstanceOf(SupportPostNotModifiableException.class);
        }

        @Test
        @DisplayName("다른 사용자가 게시글 수정 시 접근 거부")
        void updatePostByOtherUser() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            UpdateSupportPostRequest request = new UpdateSupportPostRequest();
            setField(request, "title", "수정");
            setField(request, "content", "수정");
            setField(request, "category", PostCategory.ACCOUNT);

            // when & then
            assertThatThrownBy(() -> supportPostService.updatePost(1L, 2L, request))
                    .isInstanceOf(SupportPostAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("게시글 삭제")
    class DeletePost {

        @Test
        @DisplayName("작성자가 게시글 삭제 성공")
        void deletePostByAuthor() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            // when
            supportPostService.deletePost(1L, 1L, false);

            // then
            verify(supportCommentRepository).deleteByPostId(1L);
            verify(supportPostRepository).delete(post);
        }

        @Test
        @DisplayName("관리자가 게시글 삭제 성공")
        void deletePostByAdmin() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            // when
            supportPostService.deletePost(1L, 2L, true);

            // then
            verify(supportCommentRepository).deleteByPostId(1L);
            verify(supportPostRepository).delete(post);
        }

        @Test
        @DisplayName("다른 사용자가 게시글 삭제 시 접근 거부")
        void deletePostByOtherUser() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            // when & then
            assertThatThrownBy(() -> supportPostService.deletePost(1L, 2L, false))
                    .isInstanceOf(SupportPostAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("게시글 상태 변경")
    class UpdatePostStatus {

        @Test
        @DisplayName("상태 변경 성공")
        void updatePostStatusSuccess() {
            // given
            SupportPost post = createPost(1L, 1L, "작성자", "제목", "내용", PostCategory.ACCOUNT, false);
            given(supportPostRepository.findById(1L)).willReturn(Optional.of(post));

            // when
            supportPostService.updatePostStatus(1L, PostStatus.RESOLVED);

            // then
            assertThat(post.getStatus()).isEqualTo(PostStatus.RESOLVED);
        }
    }

    private SupportPost createPost(Long id, Long userId, String authorNickname,
                                    String title, String content,
                                    PostCategory category, boolean isPrivate) {
        SupportPost post = SupportPost.builder()
                .userId(userId)
                .authorNickname(authorNickname)
                .title(title)
                .content(content)
                .category(category)
                .isPrivate(isPrivate)
                .build();
        setField(post, "id", id);
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
