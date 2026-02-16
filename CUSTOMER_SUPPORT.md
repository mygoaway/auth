# 고객센터 (Customer Support Board) 상세 문서

## 목차

1. [개요](#1-개요)
2. [데이터 모델](#2-데이터-모델)
3. [Repository 계층](#3-repository-계층)
4. [Service 계층](#4-service-계층)
5. [Controller 계층](#5-controller-계층)
6. [DTO 정의](#6-dto-정의)
7. [예외 처리](#7-예외-처리)
8. [보안 설정 및 접근 제어](#8-보안-설정-및-접근-제어)
9. [관리자 대시보드 연동](#9-관리자-대시보드-연동)
10. [프론트엔드 구현](#10-프론트엔드-구현)
11. [테스트 코드](#11-테스트-코드)
12. [전체 플로우 시퀀스](#12-전체-플로우-시퀀스)
13. [알려진 제한사항 및 설계 참고사항](#13-알려진-제한사항-및-설계-참고사항)
14. [파일 맵](#14-파일-맵)

---

## 1. 개요

고객센터는 인증 서비스 사용자들이 문의 게시글을 작성하고, 관리자가 답변/상태 관리를 할 수 있는 게시판 기능입니다. 비공개 글 설정, 카테고리 분류, 상태 관리, 댓글 기능을 지원합니다.

### 주요 기능

| 기능 | 설명 |
|---|---|
| 게시글 목록 조회 | 카테고리/상태 필터링, 페이지네이션 (10개/페이지) |
| 게시글 상세 조회 | 본문 + 댓글 목록, 조회수 자동 증가 |
| 게시글 작성 | 제목, 내용, 카테고리, 비공개 여부 설정 |
| 게시글 수정 | 작성자만, OPEN 상태에서만 가능 |
| 게시글 삭제 | 작성자 또는 관리자, OPEN 상태에서만 가능 |
| 댓글 작성 | 인증된 사용자 누구나 (비공개 글은 작성자/관리자만) |
| 댓글 삭제 | 댓글 작성자 또는 관리자 |
| 상태 변경 | 관리자만 (OPEN → IN_PROGRESS → RESOLVED → CLOSED) |
| 비공개 글 | 작성자와 관리자만 열람 가능 |
| 관리자 통계 | 상태별 게시글 수, 오늘 등록 수 |

### 카테고리 (PostCategory)

| 값 | 한국어 |
|---|---|
| `ACCOUNT` | 계정 |
| `LOGIN` | 로그인 |
| `SECURITY` | 보안 |
| `OTHER` | 기타 |

### 상태 (PostStatus)

| 값 | 한국어 | 설명 |
|---|---|---|
| `OPEN` | 대기중 | 초기 상태, 수정/삭제 가능 |
| `IN_PROGRESS` | 처리중 | 관리자가 확인 중, 수정/삭제 불가 |
| `RESOLVED` | 해결됨 | 처리 완료 |
| `CLOSED` | 종료 | 최종 종료 |

---

## 2. 데이터 모델

### 2.1 엔티티: `SupportPost`

**파일**: `src/main/java/com/jay/auth/domain/entity/SupportPost.java`
**테이블**: `tb_support_post`

```java
@Entity
@Table(name = "tb_support_post", indexes = {
    @Index(name = "idx_support_post_user_id", columnList = "user_id"),
    @Index(name = "idx_support_post_status", columnList = "status"),
    @Index(name = "idx_support_post_created_at", columnList = "created_at")
})
public class SupportPost extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "author_nickname", nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;
}
```

### 테이블 스키마

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | 기본 키 |
| `user_id` | `BIGINT` | NOT NULL | 작성자 ID (FK 없음, loose reference) |
| `author_nickname` | `VARCHAR(100)` | NOT NULL | 작성 시점 닉네임 스냅샷 |
| `title` | `VARCHAR(200)` | NOT NULL | 게시글 제목 |
| `content` | `TEXT` | NOT NULL | 게시글 본문 |
| `category` | `VARCHAR(20)` | NOT NULL | 카테고리 (ACCOUNT/LOGIN/SECURITY/OTHER) |
| `status` | `VARCHAR(20)` | NOT NULL | 상태 (OPEN/IN_PROGRESS/RESOLVED/CLOSED) |
| `is_private` | `BOOLEAN` | NOT NULL | 비공개 여부 |
| `view_count` | `INT` | NOT NULL, 기본값 `0` | 조회수 |
| `comment_count` | `INT` | NOT NULL, 기본값 `0` | 댓글 수 (비정규화된 카운터) |
| `created_at` | `DATETIME` | NOT NULL | BaseEntity 제공 |
| `updated_at` | `DATETIME` | NOT NULL | BaseEntity 제공 |

### 인덱스

| 인덱스명 | 컬럼 | 용도 |
|---|---|---|
| `idx_support_post_user_id` | `user_id` | 사용자별 게시글 조회 |
| `idx_support_post_status` | `status` | 상태별 필터링 |
| `idx_support_post_created_at` | `created_at` | 최신순 정렬 |

### 엔티티 도메인 메서드

| 메서드 | 동작 |
|---|---|
| `update(title, content, category, isPrivate)` | 수정 가능한 필드 업데이트 |
| `updateStatus(PostStatus)` | 상태 변경 |
| `incrementViewCount()` | 조회수 +1 |
| `incrementCommentCount()` | 댓글 수 +1 |
| `decrementCommentCount()` | 댓글 수 -1 (최소 0 보장) |

### 2.2 엔티티: `SupportComment`

**파일**: `src/main/java/com/jay/auth/domain/entity/SupportComment.java`
**테이블**: `tb_support_comment`

```java
@Entity
@Table(name = "tb_support_comment", indexes = {
    @Index(name = "idx_support_comment_post_id", columnList = "post_id"),
    @Index(name = "idx_support_comment_user_id", columnList = "user_id")
})
public class SupportComment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "author_nickname", nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;
}
```

### 테이블 스키마

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | 기본 키 |
| `post_id` | `BIGINT` | NOT NULL | 게시글 ID (FK 없음) |
| `user_id` | `BIGINT` | NOT NULL | 작성자 ID (FK 없음) |
| `author_nickname` | `VARCHAR(100)` | NOT NULL | 작성 시점 닉네임 스냅샷 |
| `content` | `TEXT` | NOT NULL | 댓글 내용 |
| `is_admin` | `BOOLEAN` | NOT NULL | 관리자 작성 여부 |
| `created_at` | `DATETIME` | NOT NULL | BaseEntity 제공 |
| `updated_at` | `DATETIME` | NOT NULL | BaseEntity 제공 |

### 관계 설명

- `SupportPost`와 `SupportComment`는 **JPA 연관관계 없음** (loose reference)
- `postId`, `userId`를 통해 논리적으로 연결
- FK 제약조건 미설정 — `LoginHistory`, `AuditLog`과 동일한 패턴
- 댓글 수는 `SupportPost.commentCount`에 비정규화하여 저장 (COUNT 서브쿼리 대신 프로그래밍적 업데이트)

---

## 3. Repository 계층

### 3.1 SupportPostRepository

**파일**: `src/main/java/com/jay/auth/repository/SupportPostRepository.java`

```java
public interface SupportPostRepository extends JpaRepository<SupportPost, Long> {

    long countByStatus(PostStatus status);

    long countByCreatedAtAfter(LocalDateTime dateTime);

    @Query("SELECT p FROM SupportPost p WHERE " +
           "(p.isPrivate = false OR p.userId = :userId) AND " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    Page<SupportPost> findAllForUser(@Param("userId") Long userId,
                                     @Param("category") PostCategory category,
                                     @Param("status") PostStatus status,
                                     Pageable pageable);

    @Query("SELECT p FROM SupportPost p WHERE " +
           "(:category IS NULL OR p.category = :category) AND " +
           "(:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    Page<SupportPost> findAllForAdmin(@Param("category") PostCategory category,
                                      @Param("status") PostStatus status,
                                      Pageable pageable);
}
```

| 메서드 | 용도 | 호출처 |
|---|---|---|
| `countByStatus` | 상태별 게시글 수 집계 | `AdminService.getSupportStats()` |
| `countByCreatedAtAfter` | 오늘 등록 게시글 수 | `AdminService.getSupportStats()` |
| `findAllForUser` | 일반 사용자 목록 조회 (공개글 + 자신의 비공개글) | `SupportPostService.getPosts()` |
| `findAllForAdmin` | 관리자 목록 조회 (전체 게시글) | `SupportPostService.getPosts()` |

**쿼리 설계**:
- `(:param IS NULL OR ...)` 패턴으로 카테고리/상태 필터를 선택적으로 적용
- 별도의 쿼리 변형 없이 하나의 JPQL로 모든 필터 조합을 처리
- 일반 사용자 쿼리는 `p.isPrivate = false OR p.userId = :userId` 조건으로 비공개 글 접근을 제한

### 3.2 SupportCommentRepository

**파일**: `src/main/java/com/jay/auth/repository/SupportCommentRepository.java`

```java
public interface SupportCommentRepository extends JpaRepository<SupportComment, Long> {

    List<SupportComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    void deleteByPostId(Long postId);
}
```

| 메서드 | 용도 | 호출처 |
|---|---|---|
| `findByPostIdOrderByCreatedAtAsc` | 게시글의 댓글 목록 (시간순) | `SupportPostService.getPost()` |
| `deleteByPostId` | 게시글 삭제 시 댓글 일괄 삭제 | `SupportPostService.deletePost()` |

---

## 4. Service 계층

### 4.1 SupportPostService

**파일**: `src/main/java/com/jay/auth/service/SupportPostService.java`

**의존성**:
```java
private final SupportPostRepository supportPostRepository;
private final SupportCommentRepository supportCommentRepository;
private final UserService userService;
```

---

#### 4.1.1 getPosts(Long userId, boolean isAdmin, PostCategory category, PostStatus status, Pageable pageable) → Page\<SupportPostListResponse\>

**게시글 목록 조회**

```
@Transactional(readOnly = true)
```

**동작 순서:**

1. `isAdmin`이면 `findAllForAdmin(category, status, pageable)` 호출 (모든 게시글 표시)
2. 일반 사용자면 `findAllForUser(userId, category, status, pageable)` 호출 (공개글 + 자신의 비공개글만)
3. 각 `SupportPost` 엔티티를 `SupportPostListResponse.from(post)`로 변환하여 반환

---

#### 4.1.2 getPost(Long postId, Long userId, boolean isAdmin) → SupportPostDetailResponse

**게시글 상세 조회**

```
@Transactional
```

**동작 순서:**

1. `supportPostRepository.findById(postId)` → 없으면 `SupportPostNotFoundException`
2. 비공개 글 접근 검증: 비공개이고 작성자가 아니고 관리자가 아니면 `SupportPostAccessDeniedException`
3. `post.incrementViewCount()` — 조회수 증가 (트랜잭션 내 dirty-checked write)
4. `supportCommentRepository.findByPostIdOrderByCreatedAtAsc(postId)` — 댓글 목록 조회
5. `SupportPostDetailResponse.of(post, comments)` 반환

---

#### 4.1.3 createPost(Long userId, CreateSupportPostRequest request) → SupportPostDetailResponse

**게시글 작성**

```
@Transactional
```

**동작 순서:**

1. `userService.getNickname(userId)` — 현재 닉네임 스냅샷
2. `SupportPost.builder()` 로 엔티티 생성 (status는 항상 `OPEN`)
3. `supportPostRepository.save(post)` — DB 저장
4. `SupportPostDetailResponse.of(post, List.of())` 반환 (새 게시글이므로 댓글 없음)
5. 로그 기록: `info`

---

#### 4.1.4 updatePost(Long postId, Long userId, UpdateSupportPostRequest request) → SupportPostDetailResponse

**게시글 수정**

```
@Transactional
```

**동작 순서:**

1. `findById(postId)` → 없으면 `SupportPostNotFoundException`
2. `post.getUserId() != userId` → `SupportPostAccessDeniedException` (작성자만 수정 가능)
3. `post.getStatus() != PostStatus.OPEN` → `SupportPostNotModifiableException` (OPEN 상태에서만 수정 가능)
4. `post.update(title, content, category, isPrivate)` — 필드 업데이트
5. 댓글 목록 조회 후 `SupportPostDetailResponse.of(post, comments)` 반환
6. 로그 기록: `info`

---

#### 4.1.5 deletePost(Long postId, Long userId, boolean isAdmin) → void

**게시글 삭제**

```
@Transactional
```

**동작 순서:**

1. `findById(postId)` → 없으면 `SupportPostNotFoundException`
2. 작성자가 아니고 관리자도 아니면 → `SupportPostAccessDeniedException`
3. `post.getStatus() != PostStatus.OPEN` → `SupportPostNotModifiableException` (작성자, 관리자 모두 OPEN 상태에서만 삭제 가능)
4. `supportCommentRepository.deleteByPostId(postId)` — 관련 댓글 일괄 삭제
5. `supportPostRepository.delete(post)` — 게시글 삭제
6. 로그 기록: `info`

---

#### 4.1.6 updatePostStatus(Long postId, PostStatus status) → void

**게시글 상태 변경 (관리자 전용)**

```
@Transactional
```

**동작 순서:**

1. `findById(postId)` → 없으면 `SupportPostNotFoundException`
2. `post.updateStatus(status)` — 상태 업데이트
3. 로그 기록: `info`

> 관리자 권한 검증은 Controller 계층에서 수행합니다.

---

### 4.2 SupportCommentService

**파일**: `src/main/java/com/jay/auth/service/SupportCommentService.java`

**의존성**:
```java
private final SupportCommentRepository supportCommentRepository;
private final SupportPostRepository supportPostRepository;
private final UserService userService;
```

---

#### 4.2.1 createComment(Long postId, Long userId, boolean isAdmin, CreateSupportCommentRequest request) → SupportPostDetailResponse.CommentResponse

**댓글 작성**

```
@Transactional
```

**동작 순서:**

1. `supportPostRepository.findById(postId)` → 없으면 `SupportPostNotFoundException`
2. 비공개 글이고 작성자가 아니고 관리자가 아니면 → `SupportPostAccessDeniedException`
3. `userService.getNickname(userId)` — 닉네임 스냅샷
4. `SupportComment.builder()` 로 엔티티 생성 (`isAdmin` 플래그 설정)
5. `supportCommentRepository.save(comment)` — DB 저장
6. `post.incrementCommentCount()` — 게시글의 댓글 수 증가
7. `SupportPostDetailResponse.CommentResponse.from(comment)` 반환
8. 로그 기록: `info`

---

#### 4.2.2 deleteComment(Long postId, Long commentId, Long userId, boolean isAdmin) → void

**댓글 삭제**

```
@Transactional
```

**동작 순서:**

1. `supportPostRepository.findById(postId)` → 없으면 `SupportPostNotFoundException`
2. `supportCommentRepository.findById(commentId)` → 없으면 `SupportPostNotFoundException` (동일 예외 재사용)
3. 댓글 작성자가 아니고 관리자도 아니면 → `SupportPostAccessDeniedException`
4. `supportCommentRepository.delete(comment)` — 댓글 삭제
5. `post.decrementCommentCount()` — 게시글의 댓글 수 감소
6. 로그 기록: `info`

> **참고**: 댓글 삭제에는 게시글 상태 제한이 없습니다. OPEN이 아닌 게시글의 댓글도 삭제할 수 있습니다.

---

## 5. Controller 계층

### SupportController

**파일**: `src/main/java/com/jay/auth/controller/SupportController.java`
**Base Path**: `/api/v1/support`
**Swagger Tag**: `Support` / `고객센터 API`
**인증**: 모든 엔드포인트에서 JWT Bearer 토큰 필요 (`@AuthenticationPrincipal UserPrincipal`)

관리자 판별은 `userPrincipal.getRole().equals("ADMIN")`으로 인라인 수행합니다.

---

#### GET /api/v1/support/posts

게시글 목록을 조회합니다.

- **인증**: 필수 (Bearer Token)
- **Query Parameters**:
  - `category` (선택): `ACCOUNT` / `LOGIN` / `SECURITY` / `OTHER`
  - `status` (선택): `OPEN` / `IN_PROGRESS` / `RESOLVED` / `CLOSED`
  - `page` (선택, 기본값 0): 페이지 번호
  - `size` (선택, 기본값 10): 페이지 크기
- **Response**: `200 OK`

```json
{
  "content": [
    {
      "id": 1,
      "title": "로그인이 안됩니다",
      "authorNickname": "사용자1",
      "category": "LOGIN",
      "status": "OPEN",
      "isPrivate": false,
      "viewCount": 5,
      "commentCount": 2,
      "createdAt": "2026-02-15T10:30:00"
    }
  ],
  "totalPages": 3,
  "totalElements": 25,
  "number": 0,
  "size": 10
}
```

- 일반 사용자: 공개 게시글 + 자신의 비공개 게시글만 표시
- 관리자: 모든 게시글 표시

---

#### GET /api/v1/support/posts/{postId}

게시글 상세를 조회합니다.

- **인증**: 필수 (Bearer Token)
- **Response**: `200 OK`

```json
{
  "id": 1,
  "userId": 42,
  "title": "로그인이 안됩니다",
  "content": "Google 계정으로 로그인 시 오류가 발생합니다.",
  "authorNickname": "사용자1",
  "category": "LOGIN",
  "status": "OPEN",
  "isPrivate": false,
  "viewCount": 6,
  "commentCount": 2,
  "createdAt": "2026-02-15T10:30:00",
  "updatedAt": "2026-02-15T10:30:00",
  "isAuthor": true,
  "comments": [
    {
      "id": 1,
      "userId": 1,
      "authorNickname": "관리자",
      "content": "확인 중입니다.",
      "isAdmin": true,
      "createdAt": "2026-02-15T11:00:00",
      "isAuthor": false
    }
  ]
}
```

- `isAuthor` 필드는 Controller에서 현재 사용자와 비교하여 설정
- 조회 시 `viewCount` 자동 증가
- **Error**: `403` (비공개 글 접근 불가), `404` (게시글 없음)

---

#### POST /api/v1/support/posts

게시글을 작성합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**:

```json
{
  "title": "로그인이 안됩니다",
  "content": "Google 계정으로 로그인 시 오류가 발생합니다.",
  "category": "LOGIN",
  "isPrivate": false
}
```

- **Validation**:
  - `title`: `@NotBlank`, `@Size(max=200)`
  - `content`: `@NotBlank`
  - `category`: `@NotNull` (PostCategory enum)
  - `isPrivate`: boolean
- **Response**: `200 OK` — 생성된 게시글 상세 반환

---

#### PUT /api/v1/support/posts/{postId}

게시글을 수정합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**: `CreateSupportPostRequest`와 동일 구조
- **Response**: `200 OK` — 수정된 게시글 상세 반환
- **Error**: `400` (OPEN 상태가 아님), `403` (작성자가 아님), `404` (게시글 없음)

---

#### DELETE /api/v1/support/posts/{postId}

게시글을 삭제합니다.

- **인증**: 필수 (Bearer Token)
- **Response**: `204 No Content`
- **권한**: 작성자 또는 관리자
- **제한**: OPEN 상태에서만 삭제 가능
- **Error**: `400` (OPEN 상태가 아님), `403` (권한 없음), `404` (게시글 없음)

---

#### POST /api/v1/support/posts/{postId}/comments

댓글을 작성합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**:

```json
{
  "content": "확인 중입니다."
}
```

- **Validation**: `content`: `@NotBlank(message = "댓글 내용은 필수입니다")`
- **Response**: `200 OK`

```json
{
  "id": 1,
  "userId": 1,
  "authorNickname": "관리자",
  "content": "확인 중입니다.",
  "isAdmin": true,
  "createdAt": "2026-02-15T11:00:00",
  "isAuthor": false
}
```

- 관리자가 작성한 댓글은 `isAdmin: true` 플래그 설정
- 비공개 글에는 작성자와 관리자만 댓글 작성 가능
- **Error**: `403` (비공개 글 접근 불가), `404` (게시글 없음)

---

#### DELETE /api/v1/support/posts/{postId}/comments/{commentId}

댓글을 삭제합니다.

- **인증**: 필수 (Bearer Token)
- **Response**: `204 No Content`
- **권한**: 댓글 작성자 또는 관리자
- **Error**: `403` (권한 없음), `404` (게시글 또는 댓글 없음)

---

#### PATCH /api/v1/support/posts/{postId}/status

게시글 상태를 변경합니다.

- **인증**: 필수 (Bearer Token)
- **권한**: 관리자 전용 (Controller에서 인라인 검증)
- **Request Body**:

```json
{
  "status": "IN_PROGRESS"
}
```

- **Validation**: `status`: `@NotNull` (PostStatus enum)
- **Response**: `200 OK` (빈 body)
- **Error**: `403` (관리자가 아님), `404` (게시글 없음)

---

## 6. DTO 정의

### 요청 DTO

#### CreateSupportPostRequest

**파일**: `src/main/java/com/jay/auth/dto/request/CreateSupportPostRequest.java`

```java
public class CreateSupportPostRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private PostCategory category;

    @JsonProperty("isPrivate")
    private boolean privatePost;
}
```

#### UpdateSupportPostRequest

**파일**: `src/main/java/com/jay/auth/dto/request/UpdateSupportPostRequest.java`

`CreateSupportPostRequest`와 동일한 구조 (별도의 클래스).

#### CreateSupportCommentRequest

**파일**: `src/main/java/com/jay/auth/dto/request/CreateSupportCommentRequest.java`

```java
public class CreateSupportCommentRequest {
    @NotBlank(message = "댓글 내용은 필수입니다")
    private String content;
}
```

#### UpdatePostStatusRequest

**파일**: `src/main/java/com/jay/auth/dto/request/UpdatePostStatusRequest.java`

```java
public class UpdatePostStatusRequest {
    @NotNull
    private PostStatus status;
}
```

### 응답 DTO

#### SupportPostListResponse

**파일**: `src/main/java/com/jay/auth/dto/response/SupportPostListResponse.java`

```java
public class SupportPostListResponse {
    private Long id;
    private String title;
    private String authorNickname;
    private PostCategory category;
    private PostStatus status;
    @JsonProperty("isPrivate")
    private boolean isPrivate;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;
}
```

정적 팩토리: `SupportPostListResponse.from(SupportPost post)`

#### SupportPostDetailResponse

**파일**: `src/main/java/com/jay/auth/dto/response/SupportPostDetailResponse.java`

```java
public class SupportPostDetailResponse {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String authorNickname;
    private PostCategory category;
    private PostStatus status;
    @JsonProperty("isPrivate")
    private boolean isPrivate;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponse> comments;
    @Setter
    @JsonProperty("isAuthor")
    private boolean isAuthor;          // Controller에서 설정

    @Data
    @Builder
    public static class CommentResponse {
        private Long id;
        private Long userId;
        private String authorNickname;
        private String content;
        private boolean isAdmin;
        private LocalDateTime createdAt;
        @Setter
        @JsonProperty("isAuthor")
        private boolean isAuthor;      // Controller에서 설정
    }
}
```

정적 팩토리: `SupportPostDetailResponse.of(SupportPost post, List<SupportComment> comments)`

- `isAuthor` 필드는 `@Setter`를 통해 Controller에서 현재 사용자와 비교 후 설정
- 서비스 계층에 UserPrincipal을 전달하지 않는 설계

#### AdminSupportStatsResponse

**파일**: `src/main/java/com/jay/auth/dto/response/AdminSupportStatsResponse.java`

```java
public class AdminSupportStatsResponse {
    private long totalPosts;
    private long openPosts;
    private long inProgressPosts;
    private long resolvedPosts;
    private long closedPosts;
    private long todayPosts;
}
```

---

## 7. 예외 처리

### 전용 예외 클래스

모든 예외는 `BusinessException`을 상속합니다.

| 예외 클래스 | 에러 코드 | HTTP 상태 | 메시지 |
|---|---|---|---|
| `SupportPostNotFoundException` | `SUPPORT001` | `404 Not Found` | 게시글을 찾을 수 없습니다 |
| `SupportPostAccessDeniedException` | `SUPPORT002` | `403 Forbidden` | 해당 게시글에 접근할 수 없습니다 |
| `SupportPostNotModifiableException` | `SUPPORT003` | `400 Bad Request` | 대기중 상태의 게시글만 수정할 수 있습니다 |

### GlobalExceptionHandler에서의 처리

`BusinessException` 범용 핸들러에 의해 자동 처리됩니다.

**에러 응답 형식:**
```json
{
  "success": false,
  "error": {
    "code": "SUPPORT001",
    "message": "게시글을 찾을 수 없습니다"
  },
  "timestamp": "2026-02-15T10:30:00"
}
```

> **참고**: `SupportPostNotFoundException`은 댓글이 없는 경우에도 재사용됩니다 (별도의 `CommentNotFoundException` 없음).
> `SupportPostNotModifiableException`의 메시지는 "수정"만 언급하지만, 삭제 차단에도 사용됩니다.

---

## 8. 보안 설정 및 접근 제어

### Spring Security 설정

**파일**: `src/main/java/com/jay/auth/config/SecurityConfig.java`

- `/api/v1/support/**`는 `PUBLIC_ENDPOINTS`에 포함되어 있지 **않음**
- `anyRequest().authenticated()` 규칙에 의해 JWT 인증 필수
- 관리자 통계 (`/api/v1/admin/stats/support`)는 `/api/v1/admin/**` 경로에 포함되어 `ROLE_ADMIN` 필요

### Rate Limiting

| 경로 | Rate Limit 키 | 제한 |
|---|---|---|
| `/api/v1/support/**` | `rate:user:{ip}` | 200 req/min |

고객센터 전용 Rate Limit은 없으며, 일반 사용자 API와 동일한 제한이 적용됩니다.

### 접근 제어 매트릭스

| 동작 | 일반 사용자 | 작성자 | 관리자 |
|---|---|---|---|
| 목록 조회 | 공개글 + 자신의 비공개글 | 좌동 | 전체 |
| 상세 조회 | 공개글만 | 공개글 + 자신의 비공개글 | 전체 |
| 게시글 작성 | O | - | O |
| 게시글 수정 | X | OPEN 상태만 | X |
| 게시글 삭제 | X | OPEN 상태만 | OPEN 상태만 |
| 댓글 작성 | 공개글에만 | 공개글 + 자신의 비공개글 | 전체 |
| 댓글 삭제 | X | 자신의 댓글만 | 전체 |
| 상태 변경 | X | X | O |

### 필터 체인

```
요청 → SecurityHeadersFilter → RateLimitFilter → RequestLoggingFilter → JwtAuthenticationFilter
                                                                            ↓
                                                                    Bearer 토큰 검증
                                                                    Redis 블랙리스트 확인
                                                                            ↓
                                                                    SupportController
```

---

## 9. 관리자 대시보드 연동

### AdminService.getSupportStats()

**파일**: `src/main/java/com/jay/auth/service/AdminService.java`

```
@Transactional(readOnly = true)
```

```java
public AdminSupportStatsResponse getSupportStats() {
    long totalPosts = supportPostRepository.count();
    long openPosts = supportPostRepository.countByStatus(PostStatus.OPEN);
    long inProgressPosts = supportPostRepository.countByStatus(PostStatus.IN_PROGRESS);
    long resolvedPosts = supportPostRepository.countByStatus(PostStatus.RESOLVED);
    long closedPosts = supportPostRepository.countByStatus(PostStatus.CLOSED);

    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    long todayPosts = supportPostRepository.countByCreatedAtAfter(todayStart);

    return AdminSupportStatsResponse.builder()
            .totalPosts(totalPosts)
            .openPosts(openPosts)
            .inProgressPosts(inProgressPosts)
            .resolvedPosts(resolvedPosts)
            .closedPosts(closedPosts)
            .todayPosts(todayPosts)
            .build();
}
```

- 5회의 `countByStatus` 쿼리 + 1회의 `countByCreatedAtAfter` 쿼리로 통계 집계
- 관리자 대시보드 개요(Overview) 탭에서 "미처리 문의" = `openPosts` 표시 (빨간색 강조)
- 고객센터 탭에서 전체 통계 카드 표시

### AdminController

**파일**: `src/main/java/com/jay/auth/controller/AdminController.java`

```
GET /api/v1/admin/stats/support → AdminSupportStatsResponse
```

- `ROLE_ADMIN` 필수 (Spring Security 경로 수준 보호)

---

## 10. 프론트엔드 구현

### 10.1 API 클라이언트

**파일**: `frontend/src/api/support.js`

```javascript
export const supportApi = {
  getPosts: (params) => client.get('/support/posts', { params }),
  getPost: (postId) => client.get(`/support/posts/${postId}`),
  createPost: (data) => client.post('/support/posts', data),
  updatePost: (postId, data) => client.put(`/support/posts/${postId}`, data),
  deletePost: (postId) => client.delete(`/support/posts/${postId}`),
  createComment: (postId, content) => client.post(`/support/posts/${postId}/comments`, { content }),
  deleteComment: (postId, commentId) => client.delete(`/support/posts/${postId}/comments/${commentId}`),
  updatePostStatus: (postId, status) => client.patch(`/support/posts/${postId}/status`, { status }),
};
```

- Axios 클라이언트 사용 (`http://localhost:8080/api/v1` 기본 URL)
- 인증 토큰은 Axios 인터셉터에서 자동 주입

### 10.2 관리자 통계 API

**파일**: `frontend/src/api/admin.js`

```javascript
getSupportStats: () => client.get('/admin/stats/support'),
```

### 10.3 라우팅

**파일**: `frontend/src/App.jsx`

```jsx
<Route path="/support" element={<PrivateRoute><SupportBoardPage /></PrivateRoute>} />
<Route path="/support/:postId" element={<PrivateRoute><SupportPostDetailPage /></PrivateRoute>} />
```

- 인증된 사용자만 접근 가능 (`PrivateRoute`)
- `DashboardPage`에 "고객센터" 네비게이션 버튼 배치

### 10.4 게시글 목록 페이지 (SupportBoardPage)

**파일**: `frontend/src/pages/SupportBoardPage.jsx`

**상태 변수:**
```javascript
const [posts, setPosts] = useState([]);
const [totalPages, setTotalPages] = useState(0);
const [page, setPage] = useState(0);
const [category, setCategory] = useState('');
const [status, setStatus] = useState('');
const [loading, setLoading] = useState(true);
const [showWriteModal, setShowWriteModal] = useState(false);
const [form, setForm] = useState({ title: '', content: '', category: 'ACCOUNT', isPrivate: false });
const [error, setError] = useState('');
const [submitLoading, setSubmitLoading] = useState(false);
```

**동작:**
- `page`, `category`, `status` 변경 시 `loadPosts()` 자동 호출
- 카테고리 드롭다운: 전체 카테고리 / 계정 / 로그인 / 보안 / 기타
- 상태 드롭다운: 전체 상태 / 대기중 / 처리중 / 해결됨 / 종료
- 게시글 행 클릭 시 `/support/:id` 로 이동
- 각 행에 비공개 배지, 카테고리 배지, 제목, 작성자, 날짜, 조회수, 댓글 수, 상태 배지 표시

**글쓰기 모달:**

```
┌────────────────────────────────────┐
│     새 글 작성                      │
│                                    │
│  카테고리: [ACCOUNT ▼]             │
│                                    │
│  제목:                             │
│  ┌──────────────────────────────┐  │
│  │                              │  │
│  └──────────────────────────────┘  │
│                                    │
│  내용:                             │
│  ┌──────────────────────────────┐  │
│  │                              │  │
│  │                              │  │
│  │                              │  │
│  └──────────────────────────────┘  │
│                                    │
│  ☐ 비공개로 작성                    │
│                                    │
│  [등록] [취소]                      │
└────────────────────────────────────┘
```

**페이지네이션:**
이전 / 페이지 번호 버튼 / 다음 (비활성화 처리 포함)

### 10.5 게시글 상세 페이지 (SupportPostDetailPage)

**파일**: `frontend/src/pages/SupportPostDetailPage.jsx`

**상태 변수:**
```javascript
const [post, setPost] = useState(null);
const [loading, setLoading] = useState(true);
const [comment, setComment] = useState('');
const [commentLoading, setCommentLoading] = useState(false);
const [error, setError] = useState('');
const [showEditModal, setShowEditModal] = useState(false);
const [editForm, setEditForm] = useState({ title: '', content: '', category: 'ACCOUNT', isPrivate: false });
const [editLoading, setEditLoading] = useState(false);
```

**관리자/작성자 판별:**
```javascript
const isAdmin = user?.role === 'ADMIN';
const isAuthor = post?.isAuthor;  // 백엔드 응답의 isAuthor 플래그 사용
```

**게시글 표시:**

```
┌──────────────────────────────────────────────────────┐
│  [LOGIN] [OPEN]                                      │
│                                                      │
│  로그인이 안됩니다                                     │
│                                                      │
│  작성자: 사용자1 · 2026-02-15 · 조회 6                 │
│                                                      │
│  Google 계정으로 로그인 시 오류가 발생합니다.             │
│  어떤 에러 메시지가 표시됩니다.                          │
│                                                      │
│  [수정] [삭제]        ← isAuthor && status=OPEN 일 때  │
│                                                      │
│  상태: [IN_PROGRESS ▼]     ← 관리자에게만 표시          │
└──────────────────────────────────────────────────────┘
```

**수정 모달:** 글쓰기 모달과 동일한 구조, 현재 값으로 미리 채워짐

**댓글 섹션:**

```
┌──────────────────────────────────────────────────────┐
│  댓글 2                                              │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ 관리자 [관리자]        2026-02-15 11:00  [삭제]  │  │
│  │ 확인 중입니다.                                  │  │  ← 관리자 댓글: 보라색 배경
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │ 사용자1               2026-02-15 11:30  [삭제]  │  │
│  │ 추가 정보 첨부합니다.                            │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │ 댓글을 입력하세요...                          │    │
│  └──────────────────────────────────────────────┘    │
│  [댓글 등록]                                         │
└──────────────────────────────────────────────────────┘
```

- 관리자 댓글: 보라색 그라데이션 배경 + "관리자" 배지
- 삭제 버튼: 댓글 작성자 또는 관리자에게만 표시

### 10.6 관리자 페이지 (AdminPage)

**파일**: `frontend/src/pages/AdminPage.jsx`

관리자 대시보드의 "고객센터" 탭:

```
┌──────────────────────────────────────────────────────┐
│  [개요] [사용자] [로그인] [고객센터]                      │
│                                                      │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌────┐│
│  │전체   │ │대기중 │ │처리중 │ │해결됨 │ │종료   │ │오늘 ││
│  │25    │ │ 10   │ │ 5    │ │ 8    │ │ 2    │ │ 3  ││
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └────┘│
│     ↑ 대기중은 빨간색 강조                              │
│                                                      │
│  [고객센터 게시판으로 이동 →]                             │
└──────────────────────────────────────────────────────┘
```

개요(Overview) 탭에서도 "미처리 문의" 카드로 `openPosts` 수를 빨간색으로 표시합니다.

### 10.7 CSS 스타일링

**파일**: `frontend/src/styles/global.css`

고객센터 전용 CSS 클래스 (약 350줄):

| 클래스 패턴 | 용도 |
|---|---|
| `.support-header`, `.support-filters` | 상단 바 레이아웃 |
| `.support-select` | 카테고리/상태 필터 드롭다운 |
| `.support-post-list`, `.support-post-item` | 목록 호버/보더 |
| `.support-category-badge` | 카테고리 색상 배지 |
| `.support-status-badge` | 상태 색상 배지 |
| `.support-private-badge` | 비공개 배지 |
| `.support-pagination`, `.support-page-btn` | 페이지네이션 |
| `.support-detail-*` | 상세 페이지 헤더/본문/액션 |
| `.support-comments`, `.support-comment` | 댓글 목록 |
| `.support-comment.admin` | 관리자 댓글 보라색 그라데이션 |
| `.support-admin-badge` | "관리자" 배지 |
| `.support-comment-form`, `.support-textarea` | 댓글 입력 |
| `.support-checkbox-label` | 비공개 체크박스 |

반응형 지원: `@media (max-width: 768px)` 오버라이드

---

## 11. 테스트 코드

### 11.1 서비스 테스트 (SupportPostServiceTest)

**파일**: `src/test/java/com/jay/auth/service/SupportPostServiceTest.java`
**방식**: `@ExtendWith(MockitoExtension.class)` — Spring 컨텍스트 없는 순수 단위 테스트

| 테스트 그룹 (@Nested) | 테스트 케이스 | 검증 내용 |
|---|---|---|
| **목록 조회 (GetPosts)** | `regularUserCallsFindAllForUser` | 일반 사용자는 `findAllForUser` 호출 |
| | `adminCallsFindAllForAdmin` | 관리자는 `findAllForAdmin` 호출 |
| **상세 조회 (GetPost)** | `publicPostAccessibleByAnyUser` | 공개 게시글 조회 성공 |
| | `privatePostAccessibleByAuthor` | 비공개 글 작성자 조회 성공 |
| | `privatePostBlockedForNonAuthorNonAdmin` | 비공개 글 비작성자 접근 거부 |
| | `privatePostAccessibleByAdmin` | 관리자 비공개 글 조회 성공 |
| | `nonExistentPostThrowsNotFound` | 존재하지 않는 게시글 → `SupportPostNotFoundException` |
| **게시글 작성 (CreatePost)** | `createPostSuccess` | 닉네임 조회, 저장, 응답 반환 |
| **게시글 수정 (UpdatePost)** | `updatePostSuccess` | 작성자가 OPEN 게시글 수정 성공 |
| | `nonOpenPostThrowsNotModifiable` | OPEN 아닌 게시글 → `SupportPostNotModifiableException` |
| | `differentUserThrowsAccessDenied` | 비작성자 수정 → `SupportPostAccessDeniedException` |
| **게시글 삭제 (DeletePost)** | `authorDeletesOpenPost` | 작성자 삭제 성공 (댓글도 삭제) |
| | `adminDeletesOpenPost` | 관리자 삭제 성공 |
| | `nonOpenPostThrowsNotModifiable` | OPEN 아닌 게시글 삭제 → `SupportPostNotModifiableException` |
| | `differentUserThrowsAccessDenied` | 비작성자/비관리자 삭제 → `SupportPostAccessDeniedException` |
| **상태 변경 (UpdatePostStatus)** | `updateStatusSuccess` | 상태 변경 정상 동작 |

**테스트 헬퍼:**
```java
private SupportPost createPost(Long id, Long userId, String authorNickname,
                               String title, String content, PostCategory category,
                               boolean isPrivate) {
    SupportPost post = SupportPost.builder()...build();
    setField(post, "id", id);  // 리플렉션으로 ID 설정
    return post;
}
```

### 11.2 서비스 테스트 (SupportCommentServiceTest)

**파일**: `src/test/java/com/jay/auth/service/SupportCommentServiceTest.java`
**방식**: `@ExtendWith(MockitoExtension.class)`

| 테스트 그룹 (@Nested) | 테스트 케이스 | 검증 내용 |
|---|---|---|
| **댓글 작성 (CreateComment)** | `publicPostCommentSuccess` | 공개 글에 댓글 작성 성공 |
| | `adminCommentHasIsAdminFlag` | 관리자 댓글에 `isAdmin=true` 설정 |
| | `privatePostNonAuthorNonAdminBlocked` | 비공개 글 비작성자 댓글 → `SupportPostAccessDeniedException` |
| | `nonExistentPostThrowsNotFound` | 존재하지 않는 게시글 → `SupportPostNotFoundException` |
| **댓글 삭제 (DeleteComment)** | `commentAuthorDeletesSuccess` | 댓글 작성자 삭제 성공 |
| | `adminDeletesSuccess` | 관리자 삭제 성공 |
| | `differentUserThrowsAccessDenied` | 비작성자/비관리자 삭제 → `SupportPostAccessDeniedException` |

### 11.3 컨트롤러 테스트 (SupportControllerTest)

**파일**: `src/test/java/com/jay/auth/controller/SupportControllerTest.java`
**방식**: `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)`

**보안 필터 제외:**
```java
@WebMvcTest(
    controllers = SupportController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            JwtAuthenticationFilter.class,
            RateLimitFilter.class,
            RequestLoggingFilter.class,
            SecurityHeadersFilter.class
        }
    )
)
```

**인증 세팅:**
```java
@BeforeEach
void setUp() {
    UserPrincipal userPrincipal = new UserPrincipal(1L, "uuid-1234", "USER");
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

| 테스트 케이스 | HTTP 상태 | 검증 내용 |
|---|---|---|
| `GET /api/v1/support/posts` | 200 | content[0].title, authorNickname |
| `GET /api/v1/support/posts/1` | 200 | title, content |
| `GET /api/v1/support/posts/999` | 404 | `SupportPostNotFoundException` |
| `POST /api/v1/support/posts` (정상) | 200 | 생성된 게시글 반환 |
| `POST /api/v1/support/posts` (제목 없음) | 400 | 유효성 검증 실패 |
| `DELETE /api/v1/support/posts/1` | 204 | `deletePost(1L, 1L, false)` 호출 확인 |
| `POST /api/v1/support/posts/1/comments` | 200 | content, authorNickname |
| `DELETE /api/v1/support/posts/1/comments/1` | 204 | `deleteComment(1L, 1L, 1L, false)` 호출 확인 |

### 11.4 관리자 테스트

**파일**: `src/test/java/com/jay/auth/service/AdminServiceTest.java` — `GetSupportStats` 중첩 클래스

- `count()` → 100, 각 `countByStatus()` → 특정 값, `countByCreatedAtAfter()` → 3
- 응답 필드 값 검증

**파일**: `src/test/java/com/jay/auth/controller/AdminControllerTest.java` — `GetSupportStats` 중첩 클래스

- `GET /api/v1/admin/stats/support` → 200, `totalPosts`, `openPosts`, `inProgressPosts`, `resolvedPosts` 검증

---

## 12. 전체 플로우 시퀀스

### 12.1 게시글 작성 플로우

```
사용자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │ [고객센터 → "글쓰기" 클릭]        │                                │
     │ [제목/내용/카테고리/비공개 입력]    │                                │
     │                                  │                                │
     │──── POST /support/posts ───────→│                                │
     │     Authorization: Bearer ...   │                                │
     │     { title, content,           │                                │
     │       category, isPrivate }     │                                │
     │                                  │── UserService.getNickname() ──→│
     │                                  │←── 닉네임 반환 ─────────────── │
     │                                  │── SupportPost 생성             │
     │                                  │   (status=OPEN, viewCount=0)  │
     │                                  │── save() ───────────────────→ │
     │                                  │                                │
     │←── SupportPostDetailResponse ──│                                │
     │                                  │                                │
     │ [상세 페이지로 이동]              │                                │
```

### 12.2 게시글 조회 및 댓글 작성 플로우

```
사용자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │──── GET /support/posts/1 ──────→│                                │
     │     Authorization: Bearer ...   │                                │
     │                                  │── findById(1) ────────────── → │
     │                                  │←── SupportPost 반환 ────────── │
     │                                  │── 비공개 접근 검증               │
     │                                  │── incrementViewCount() ──────→ │
     │                                  │── findByPostIdOrderByCreatedAtAsc ──→│
     │                                  │←── 댓글 목록 반환 ──────────── │
     │                                  │── isAuthor 설정 (Controller)   │
     │                                  │                                │
     │←── SupportPostDetailResponse ──│                                │
     │     (isAuthor: true/false)      │                                │
     │                                  │                                │
     │ [게시글 + 댓글 표시]              │                                │
     │ [댓글 입력 후 "등록" 클릭]        │                                │
     │                                  │                                │
     │── POST /support/posts/1/comments →│                               │
     │     { "content": "답변 감사합니다" }│                              │
     │                                  │── findById(1) → 존재 확인      │
     │                                  │── 비공개 접근 검증               │
     │                                  │── getNickname() ──────────── → │
     │                                  │── SupportComment 생성          │
     │                                  │   (isAdmin = role 기반)        │
     │                                  │── save() ───────────────────→ │
     │                                  │── post.incrementCommentCount()→│
     │                                  │                                │
     │←── CommentResponse ────────────│                                │
     │                                  │                                │
     │ [새 댓글 표시, 게시글 다시 로드]   │                                │
```

### 12.3 관리자 상태 변경 플로우

```
관리자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │ [상세 페이지에서 상태 드롭다운 변경] │                              │
     │                                  │                                │
     │── PATCH /support/posts/1/status →│                                │
     │     Authorization: Bearer ...   │                                │
     │     { "status": "IN_PROGRESS" } │                                │
     │                                  │── 관리자 권한 검증 (인라인)      │
     │                                  │── findById(1) ────────────── → │
     │                                  │←── SupportPost 반환 ────────── │
     │                                  │── post.updateStatus(IN_PROGRESS)│
     │                                  │── dirty-check → UPDATE ──────→│
     │                                  │                                │
     │←── 200 OK ────────────────────│                                │
     │                                  │                                │
     │ [게시글 다시 로드 → 상태 반영]     │                                │
     │ [수정/삭제 버튼 비활성화]          │                                │
```

### 12.4 게시글 삭제 플로우

```
사용자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │ ["삭제" 클릭 → confirm() 확인]    │                                │
     │                                  │                                │
     │── DELETE /support/posts/1 ─────→│                                │
     │     Authorization: Bearer ...   │                                │
     │                                  │── findById(1) ────────────── → │
     │                                  │── 작성자/관리자 권한 검증        │
     │                                  │── OPEN 상태 확인                │
     │                                  │── deleteByPostId(1) ─────── → │ ← 댓글 일괄 삭제
     │                                  │── delete(post) ──────────── → │ ← 게시글 삭제
     │                                  │                                │
     │←── 204 No Content ────────────│                                │
     │                                  │                                │
     │ [/support 목록 페이지로 이동]      │                                │
```

---

## 13. 알려진 제한사항 및 설계 참고사항

### 13.1 보안 관련

| 항목 | 설명 |
|---|---|
| **관리자 권한 검증 방식** | `SupportController`에서 `userPrincipal.getRole().equals("ADMIN")`으로 인라인 검증합니다. Spring Security의 `@PreAuthorize` 또는 `hasRole`을 사용하지 않습니다. |
| **전용 Rate Limit 없음** | 게시글 작성/댓글 작성에 전용 Rate Limit이 없습니다. 일반 사용자 API Rate Limit (200 req/min)만 적용됩니다. |
| **이벤트 알림 없음** | 게시글 작성/댓글 작성/상태 변경 시 이메일 알림이나 `SecurityNotificationService` 호출이 없습니다. |
| **AuditLog 미연동** | 고객센터 관련 동작이 `AuditLog`에 기록되지 않습니다. |

### 13.2 기능 관련

| 항목 | 설명 |
|---|---|
| **댓글 수정 불가** | 댓글은 작성(write-once)과 삭제만 가능하며 수정 기능이 없습니다. |
| **상태 전이 검증 없음** | 관리자가 임의의 상태로 변경할 수 있습니다 (예: `CLOSED` → `OPEN`). 순방향 전이 제한이 없습니다. |
| **예외 재사용** | 댓글이 존재하지 않을 때 `SupportPostNotFoundException`이 재사용됩니다 (별도의 `CommentNotFoundException` 없음). |
| **예외 메시지 불일치** | `SupportPostNotModifiableException`의 메시지는 "수정"만 언급하지만, 삭제 차단에도 사용됩니다. |
| **닉네임 스냅샷** | 게시글/댓글 작성 시점의 닉네임이 저장되므로, 사용자가 닉네임을 변경해도 기존 게시글/댓글의 닉네임은 변경되지 않습니다. |
| **조회수 중복 카운트** | 동일 사용자가 반복 조회해도 조회수가 계속 증가합니다 (IP/세션 기반 중복 방지 없음). |
| **Cascade 미설정** | `SupportPost` 삭제 시 댓글 자동 cascade가 되지 않으므로 서비스 계층에서 명시적으로 `deleteByPostId`를 호출합니다. |
| **댓글 수 비정규화** | `commentCount`가 비정규화되어 있어 수동 동기화가 필요합니다. 직접 DB를 수정하면 불일치가 발생할 수 있습니다. |

### 13.3 스케줄링 및 비동기

고객센터 기능에는 **스케줄링 작업이나 `@Async` 연산이 없습니다**:
- `AccountCleanupScheduler`와 `VerificationCleanupScheduler`는 고객센터 테이블을 처리하지 않습니다
- 사용자 계정이 삭제되어도 해당 사용자의 게시글/댓글은 그대로 유지됩니다 (orphan 데이터)

### 13.4 FK 미사용

`SupportPost`와 `SupportComment`는 JPA 연관관계 및 FK 제약조건 없이 loose reference를 사용합니다. 이는 프로젝트의 `LoginHistory`, `AuditLog`과 동일한 패턴입니다.

---

## 14. 파일 맵

### 백엔드

```
src/main/java/com/jay/auth/
├── controller/
│   ├── SupportController.java            # 고객센터 API 엔드포인트 (8개)
│   └── AdminController.java              # 관리자 통계 (GET /admin/stats/support)
├── service/
│   ├── SupportPostService.java           # 게시글 비즈니스 로직 (6개 메서드)
│   ├── SupportCommentService.java        # 댓글 비즈니스 로직 (2개 메서드)
│   └── AdminService.java                 # 고객센터 통계 조회 (getSupportStats)
├── domain/
│   ├── entity/
│   │   ├── SupportPost.java              # 게시글 엔티티 (tb_support_post)
│   │   └── SupportComment.java           # 댓글 엔티티 (tb_support_comment)
│   └── enums/
│       ├── PostCategory.java             # 카테고리 열거형 (ACCOUNT/LOGIN/SECURITY/OTHER)
│       └── PostStatus.java               # 상태 열거형 (OPEN/IN_PROGRESS/RESOLVED/CLOSED)
├── repository/
│   ├── SupportPostRepository.java        # 게시글 JPA Repository
│   └── SupportCommentRepository.java     # 댓글 JPA Repository
├── dto/
│   ├── request/
│   │   ├── CreateSupportPostRequest.java     # 게시글 작성 요청
│   │   ├── UpdateSupportPostRequest.java     # 게시글 수정 요청
│   │   ├── CreateSupportCommentRequest.java  # 댓글 작성 요청
│   │   └── UpdatePostStatusRequest.java      # 상태 변경 요청
│   └── response/
│       ├── SupportPostListResponse.java      # 목록 응답
│       ├── SupportPostDetailResponse.java    # 상세 응답 (+ CommentResponse 내부 클래스)
│       └── AdminSupportStatsResponse.java    # 관리자 통계 응답
└── exception/
    ├── SupportPostNotFoundException.java      # SUPPORT001 - 404
    ├── SupportPostAccessDeniedException.java  # SUPPORT002 - 403
    └── SupportPostNotModifiableException.java # SUPPORT003 - 400

src/test/java/com/jay/auth/
├── service/
│   ├── SupportPostServiceTest.java       # 게시글 서비스 단위 테스트 (11개)
│   ├── SupportCommentServiceTest.java    # 댓글 서비스 단위 테스트 (7개)
│   └── AdminServiceTest.java            # 관리자 통계 테스트 (GetSupportStats)
└── controller/
    ├── SupportControllerTest.java        # 컨트롤러 테스트 (8개)
    └── AdminControllerTest.java          # 관리자 통계 컨트롤러 테스트
```

### 프론트엔드

```
frontend/src/
├── api/
│   ├── support.js                        # supportApi 객체 (8개 메서드)
│   └── admin.js                          # getSupportStats (관리자 통계)
├── pages/
│   ├── SupportBoardPage.jsx              # 게시글 목록 + 글쓰기 모달
│   ├── SupportPostDetailPage.jsx         # 게시글 상세 + 댓글 + 수정 모달
│   ├── AdminPage.jsx                     # 관리자 대시보드 (고객센터 탭)
│   └── DashboardPage.jsx                 # "고객센터" 네비게이션 버튼
├── styles/
│   └── global.css                        # support-* CSS 클래스 (~350줄)
└── App.jsx                               # /support, /support/:postId 라우팅
```
