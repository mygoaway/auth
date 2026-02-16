package com.jay.auth.controller;

import com.jay.auth.domain.enums.PostCategory;
import com.jay.auth.domain.enums.PostStatus;
import com.jay.auth.dto.request.CreateSupportCommentRequest;
import com.jay.auth.dto.request.CreateSupportPostRequest;
import com.jay.auth.dto.request.UpdatePostStatusRequest;
import com.jay.auth.dto.request.UpdateSupportPostRequest;
import com.jay.auth.dto.response.SupportPostDetailResponse;
import com.jay.auth.dto.response.SupportPostListResponse;
import com.jay.auth.security.UserPrincipal;
import com.jay.auth.service.SupportCommentService;
import com.jay.auth.service.SupportPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Support", description = "고객센터 API")
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportPostService supportPostService;
    private final SupportCommentService supportCommentService;

    @Operation(summary = "게시글 목록 조회", description = "고객센터 게시글 목록을 페이징 조회합니다")
    @GetMapping("/posts")
    public ResponseEntity<Page<SupportPostListResponse>> getPosts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) PostStatus status,
            @PageableDefault(size = 10) Pageable pageable) {

        boolean isAdmin = "ADMIN".equals(userPrincipal.getRole());
        Page<SupportPostListResponse> posts = supportPostService.getPosts(
                userPrincipal.getUserId(), isAdmin, category, status, pageable);

        return ResponseEntity.ok(posts);
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 상세 정보와 댓글을 조회합니다")
    @GetMapping("/posts/{postId}")
    public ResponseEntity<SupportPostDetailResponse> getPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {

        boolean isAdmin = "ADMIN".equals(userPrincipal.getRole());
        SupportPostDetailResponse response = supportPostService.getPost(
                postId, userPrincipal.getUserId(), isAdmin);
        Long currentUserId = userPrincipal.getUserId();
        response.setAuthor(response.getUserId() != null
                && response.getUserId().equals(currentUserId));
        if (response.getComments() != null) {
            response.getComments().forEach(c ->
                    c.setAuthor(c.getUserId() != null && c.getUserId().equals(currentUserId)));
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "게시글 작성", description = "고객센터 게시글을 작성합니다")
    @PostMapping("/posts")
    public ResponseEntity<SupportPostDetailResponse> createPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateSupportPostRequest request) {

        SupportPostDetailResponse response = supportPostService.createPost(
                userPrincipal.getUserId(), request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "게시글 수정", description = "본인이 작성한 게시글을 수정합니다")
    @PutMapping("/posts/{postId}")
    public ResponseEntity<SupportPostDetailResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId,
            @Valid @RequestBody UpdateSupportPostRequest request) {

        SupportPostDetailResponse response = supportPostService.updatePost(
                postId, userPrincipal.getUserId(), request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다 (작성자 또는 관리자)")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId) {

        boolean isAdmin = "ADMIN".equals(userPrincipal.getRole());
        supportPostService.deletePost(postId, userPrincipal.getUserId(), isAdmin);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<SupportPostDetailResponse.CommentResponse> createComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId,
            @Valid @RequestBody CreateSupportCommentRequest request) {

        boolean isAdmin = "ADMIN".equals(userPrincipal.getRole());
        SupportPostDetailResponse.CommentResponse response = supportCommentService.createComment(
                postId, userPrincipal.getUserId(), isAdmin, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다 (작성자 또는 관리자)")
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId,
            @PathVariable Long commentId) {

        boolean isAdmin = "ADMIN".equals(userPrincipal.getRole());
        supportCommentService.deleteComment(postId, commentId, userPrincipal.getUserId(), isAdmin);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "게시글 상태 변경", description = "관리자가 게시글 상태를 변경합니다")
    @PatchMapping("/posts/{postId}/status")
    public ResponseEntity<Void> updatePostStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostStatusRequest request) {

        if (!"ADMIN".equals(userPrincipal.getRole())) {
            throw new com.jay.auth.exception.SupportPostAccessDeniedException();
        }

        supportPostService.updatePostStatus(postId, request.getStatus());

        return ResponseEntity.ok().build();
    }
}
