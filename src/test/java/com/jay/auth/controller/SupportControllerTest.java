package com.jay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import com.jay.auth.dto.response.SupportPostDetailResponse;
import com.jay.auth.dto.response.SupportPostListResponse;
import com.jay.auth.exception.SupportPostNotFoundException;
import com.jay.auth.security.JwtAuthenticationFilter;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.SupportCommentService;
import com.jay.auth.service.SupportPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = SupportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        JwtAuthenticationFilter.class,
                        com.jay.auth.config.RateLimitFilter.class,
                        com.jay.auth.config.RequestLoggingFilter.class,
                        com.jay.auth.config.SecurityHeadersFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SupportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SupportPostService supportPostService;

    @MockitoBean
    private SupportCommentService supportCommentService;

    @BeforeEach
    void setUp() {
        UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {

        @Test
        @DisplayName("GET /api/v1/support/posts - 목록 조회 성공")
        void getPostsSuccess() throws Exception {
            // given
            SupportPostListResponse response = SupportPostListResponse.builder()
                    .id(1L)
                    .title("테스트 제목")
                    .authorNickname("작성자")
                    .category(PostCategory.ACCOUNT)
                    .status(PostStatus.OPEN)
                    .isPrivate(false)
                    .viewCount(0)
                    .commentCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            given(supportPostService.getPosts(eq(1L), eq(false), eq(null), eq(null), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(response)));

            // when & then
            mockMvc.perform(get("/api/v1/support/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("테스트 제목"))
                    .andExpect(jsonPath("$.content[0].authorNickname").value("작성자"));
        }
    }

    @Nested
    @DisplayName("게시글 상세 조회")
    class GetPost {

        @Test
        @DisplayName("GET /api/v1/support/posts/{postId} - 상세 조회 성공")
        void getPostSuccess() throws Exception {
            // given
            SupportPostDetailResponse response = SupportPostDetailResponse.builder()
                    .id(1L)
                    .userId(1L)
                    .title("테스트 제목")
                    .content("테스트 내용")
                    .authorNickname("작성자")
                    .category(PostCategory.ACCOUNT)
                    .status(PostStatus.OPEN)
                    .isPrivate(false)
                    .viewCount(1)
                    .commentCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .comments(List.of())
                    .build();

            given(supportPostService.getPost(eq(1L), eq(1L), eq(false))).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/support/posts/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("테스트 제목"))
                    .andExpect(jsonPath("$.content").value("테스트 내용"));
        }

        @Test
        @DisplayName("GET /api/v1/support/posts/{postId} - 존재하지 않는 게시글 404")
        void getPostNotFound() throws Exception {
            // given
            given(supportPostService.getPost(eq(999L), eq(1L), eq(false)))
                    .willThrow(new SupportPostNotFoundException());

            // when & then
            mockMvc.perform(get("/api/v1/support/posts/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("게시글 작성")
    class CreatePost {

        @Test
        @DisplayName("POST /api/v1/support/posts - 게시글 작성 성공")
        void createPostSuccess() throws Exception {
            // given
            SupportPostDetailResponse response = SupportPostDetailResponse.builder()
                    .id(1L)
                    .userId(1L)
                    .title("새 게시글")
                    .content("게시글 내용")
                    .authorNickname("작성자")
                    .category(PostCategory.LOGIN)
                    .status(PostStatus.OPEN)
                    .isPrivate(false)
                    .viewCount(0)
                    .commentCount(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .comments(List.of())
                    .build();

            given(supportPostService.createPost(eq(1L), any())).willReturn(response);

            String requestBody = """
                    {
                        "title": "새 게시글",
                        "content": "게시글 내용",
                        "category": "LOGIN",
                        "isPrivate": false
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/support/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("새 게시글"));
        }

        @Test
        @DisplayName("POST /api/v1/support/posts - 제목 없이 작성 시 400")
        void createPostWithoutTitle() throws Exception {
            String requestBody = """
                    {
                        "content": "내용만 있음",
                        "category": "ACCOUNT"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/support/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("게시글 삭제")
    class DeletePost {

        @Test
        @DisplayName("DELETE /api/v1/support/posts/{postId} - 삭제 성공")
        void deletePostSuccess() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/support/posts/1"))
                    .andExpect(status().isNoContent());

            verify(supportPostService).deletePost(eq(1L), eq(1L), eq(false));
        }
    }

    @Nested
    @DisplayName("댓글 작성")
    class CreateComment {

        @Test
        @DisplayName("POST /api/v1/support/posts/{postId}/comments - 댓글 작성 성공")
        void createCommentSuccess() throws Exception {
            // given
            SupportPostDetailResponse.CommentResponse response = SupportPostDetailResponse.CommentResponse.builder()
                    .id(1L)
                    .userId(1L)
                    .authorNickname("댓글유저")
                    .content("댓글 내용")
                    .isAdmin(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            given(supportCommentService.createComment(eq(1L), eq(1L), eq(false), any()))
                    .willReturn(response);

            String requestBody = """
                    {
                        "content": "댓글 내용"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/support/posts/1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("댓글 내용"))
                    .andExpect(jsonPath("$.authorNickname").value("댓글유저"));
        }
    }

    @Nested
    @DisplayName("댓글 삭제")
    class DeleteComment {

        @Test
        @DisplayName("DELETE /api/v1/support/posts/{postId}/comments/{commentId} - 삭제 성공")
        void deleteCommentSuccess() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/support/posts/1/comments/1"))
                    .andExpect(status().isNoContent());

            verify(supportCommentService).deleteComment(eq(1L), eq(1L), eq(1L), eq(false));
        }
    }
}
