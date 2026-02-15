# 2단계 인증 (Two-Factor Authentication) 상세 문서

## 목차

1. [개요](#1-개요)
2. [기술 스택 및 의존성](#2-기술-스택-및-의존성)
3. [데이터 모델](#3-데이터-모델)
4. [Repository 계층](#4-repository-계층)
5. [Service 계층 (TotpService)](#5-service-계층-totpservice)
6. [Controller 계층 (TwoFactorController)](#6-controller-계층-twofactorcontroller)
7. [DTO 정의](#7-dto-정의)
8. [예외 처리](#8-예외-처리)
9. [로그인 플로우 통합](#9-로그인-플로우-통합)
10. [보안 설정 및 접근 제어](#10-보안-설정-및-접근-제어)
11. [보안 대시보드 연동](#11-보안-대시보드-연동)
12. [계정 삭제 시 정리](#12-계정-삭제-시-정리)
13. [프론트엔드 구현](#13-프론트엔드-구현)
14. [테스트 코드](#14-테스트-코드)
15. [전체 플로우 시퀀스](#15-전체-플로우-시퀀스)
16. [알려진 제한사항 및 설계 참고사항](#16-알려진-제한사항-및-설계-참고사항)
17. [파일 맵](#17-파일-맵)

---

## 1. 개요

본 프로젝트의 2단계 인증(2FA)은 **TOTP (Time-based One-Time Password)** 방식을 사용합니다. 사용자는 Google Authenticator, Microsoft Authenticator 등 TOTP 호환 인증 앱을 통해 30초마다 갱신되는 6자리 일회용 코드를 생성하고, 로그인 시 이를 추가로 입력하여 본인 인증을 수행합니다.

이메일 로그인 사용자뿐만 아니라 소셜 로그인(Google, Kakao, Naver, Facebook) 사용자도 2FA를 설정할 수 있습니다.

### 주요 기능

| 기능 | 설명 |
|---|---|
| 2FA 설정 (Setup) | QR 코드 생성 및 TOTP 시크릿 발급 |
| 2FA 활성화 (Enable) | TOTP 코드 확인 후 활성화, 백업 코드 8개 발급 |
| 2FA 비활성화 (Disable) | TOTP 코드 확인 후 비활성화 |
| 로그인 시 2FA 검증 (Verify) | TOTP 코드 또는 백업 코드로 검증 |
| 백업 코드 재발급 (Regenerate) | 기존 코드 무효화 후 새 백업 코드 8개 발급 |
| 2FA 상태 조회 (Status) | 활성화 여부, 잔여 백업 코드 수, 마지막 사용 시간 조회 |

---

## 2. 기술 스택 및 의존성

### TOTP 라이브러리

```groovy
// build.gradle
implementation 'dev.samstevens.totp:totp:1.7.1'
```

이 라이브러리는 다음 기능을 제공합니다:

| 클래스 | 역할 |
|---|---|
| `DefaultSecretGenerator` | Base32 인코딩된 TOTP 시크릿 생성 |
| `DefaultCodeGenerator` | TOTP 코드 생성기 (SHA1, 6자리, 30초 주기) |
| `DefaultCodeVerifier` | TOTP 코드 검증기 (시간 허용 범위 내 유효성 검사) |
| `SystemTimeProvider` | 시스템 시간 기반 타임스탬프 제공 |
| `ZxingPngQrGenerator` | QR 코드 PNG 이미지 생성 (ZXing 라이브러리 기반) |
| `QrData.Builder` | `otpauth://totp/...` URI 빌더 |

### TOTP 설정값

| 항목 | 값 | 설명 |
|---|---|---|
| Algorithm | SHA1 | HMAC 해시 알고리즘 |
| Digits | 6 | 생성되는 코드 자릿수 |
| Period | 30초 | 코드 갱신 주기 |
| Issuer | `"AuthService"` | 인증 앱에 표시되는 서비스 이름 |

### 암호화

- **TOTP 시크릿**: AES-256 암호화 후 DB 저장 (`EncryptionService.encrypt()`)
- **백업 코드**: JSON 배열로 직렬화 후 AES-256 암호화하여 DB 저장
- 복호화: `EncryptionService.decrypt()`

---

## 3. 데이터 모델

### 엔티티: `UserTwoFactor`

**파일**: `src/main/java/com/jay/auth/domain/entity/UserTwoFactor.java`
**테이블**: `tb_user_two_factor`

```java
@Entity
@Table(name = "tb_user_two_factor")
public class UserTwoFactor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "secret_enc", length = 512)
    private String secretEnc;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "backup_codes_enc", length = 1024)
    private String backupCodesEnc;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 테이블 스키마

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | 기본 키 |
| `user_id` | `BIGINT` | FK → `tb_user.id`, UNIQUE, NOT NULL | 사용자 ID (1:1 관계) |
| `secret_enc` | `VARCHAR(512)` | NULL 허용 | AES-256 암호화된 TOTP 시크릿 |
| `is_enabled` | `BOOLEAN` | NOT NULL, 기본값 `false` | 2FA 활성화 상태 |
| `backup_codes_enc` | `VARCHAR(1024)` | NULL 허용 | AES-256 암호화된 백업 코드 JSON 배열 |
| `last_used_at` | `DATETIME` | NULL 허용 | 마지막 2FA 인증 시간 |
| `created_at` | `DATETIME` | NOT NULL | 레코드 생성 시간 |
| `updated_at` | `DATETIME` | NOT NULL | 레코드 수정 시간 |

### 엔티티 도메인 메서드

| 메서드 | 동작 |
|---|---|
| `enable()` | `enabled = true` |
| `disable()` | `enabled = false`, `secretEnc = null`, `backupCodesEnc = null` |
| `updateSecret(String)` | 암호화된 시크릿 교체 |
| `updateBackupCodes(String)` | 암호화된 백업 코드 교체 |
| `recordUsage()` | `lastUsedAt = LocalDateTime.now()` |

### 관계 설명

- `User` ↔ `UserTwoFactor`: **1:0..1 관계** (사용자가 2FA 설정을 시작하기 전에는 레코드가 존재하지 않음)
- `FetchType.LAZY`: 지연 로딩 (User 조회 시 자동으로 로드되지 않음)
- Cascade 미설정: `User` 삭제 시 자동 cascade 되지 않으므로 `AccountCleanupScheduler`에서 명시적으로 삭제

---

## 4. Repository 계층

**파일**: `src/main/java/com/jay/auth/repository/UserTwoFactorRepository.java`

```java
public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactor, Long> {

    Optional<UserTwoFactor> findByUserId(Long userId);

    @Query("SELECT t FROM UserTwoFactor t JOIN FETCH t.user WHERE t.user.id = :userId")
    Optional<UserTwoFactor> findByUserIdWithUser(@Param("userId") Long userId);

    boolean existsByUserIdAndEnabled(Long userId, boolean enabled);

    void deleteByUserId(Long userId);
}
```

| 메서드 | 용도 | 호출처 |
|---|---|---|
| `findByUserId` | 대부분의 서비스 메서드에서 2FA 레코드 조회 | `TotpService` 전체 |
| `findByUserIdWithUser` | User JOIN FETCH (N+1 방지용, 현재 미사용) | 향후 확장용 |
| `existsByUserIdAndEnabled` | 로그인 시 2FA 필요 여부 빠른 확인 (단일 쿼리) | `TotpService.isTwoFactorRequired()` |
| `deleteByUserId` | 계정 삭제 시 2FA 데이터 정리 | `AccountCleanupScheduler` |

---

## 5. Service 계층 (TotpService)

**파일**: `src/main/java/com/jay/auth/service/TotpService.java`

### 의존성

```java
private final UserRepository userRepository;
private final UserTwoFactorRepository userTwoFactorRepository;
private final EncryptionService encryptionService;
private final ObjectMapper objectMapper;

// 인스턴스 레벨 필드 (Spring Bean이 아님)
private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
        new DefaultCodeGenerator(), new SystemTimeProvider());
```

> `CodeVerifier`가 Spring Bean이 아니라 인스턴스 필드로 선언되어 있어, 테스트 시 리플렉션(`setField`)으로 mock을 주입합니다.

### 상수

```java
private static final String ISSUER = "AuthService";
private static final int BACKUP_CODE_COUNT = 8;   // 생성되는 백업 코드 개수
private static final int BACKUP_CODE_LENGTH = 8;   // 각 백업 코드 자릿수
```

---

### 5.1 setupTwoFactor(Long userId) → TwoFactorSetupResponse

**2FA 설정 시작 - QR 코드 및 시크릿 생성**

```
@Transactional
```

**동작 순서:**

1. `UserRepository`에서 사용자 조회 → 없으면 `UserNotFoundException`
2. `SecretGenerator.generate()`로 새 TOTP 시크릿 생성 (Base32 인코딩 문자열)
3. `UserTwoFactor` 레코드 조회 또는 새로 생성 (`findByUserId` → `orElseGet`)
4. 시크릿을 `EncryptionService.encrypt()`로 암호화 후 DB 저장
5. 사용자 이메일을 복호화하여 QR 라벨로 사용 (이메일 없으면 `"user@{userId}"`)
6. `ZxingPngQrGenerator`로 QR 코드 PNG 생성 → Base64 인코딩
7. `TwoFactorSetupResponse { secret, qrCodeDataUrl }` 반환

**QR 코드 URI 형식:**
```
otpauth://totp/{email}?secret={secret}&issuer=AuthService&algorithm=SHA1&digits=6&period=30
```

**특이사항:**
- 이미 2FA가 활성화된 상태에서도 setup 호출이 가능합니다 (기존 시크릿을 덮어씁니다)
- 단, 이후 `enableTwoFactor()`를 호출하면 `alreadyEnabled()` 예외가 발생합니다
- setup만으로는 2FA가 활성화되지 않습니다 (`enabled = false` 유지)

---

### 5.2 enableTwoFactor(Long userId, String code) → List\<String\>

**2FA 활성화 - 코드 확인 후 백업 코드 발급**

```
@Transactional
```

**동작 순서:**

1. `UserTwoFactor` 조회 → 없으면 `TwoFactorException.notSetup()`
2. 이미 활성화 상태면 `TwoFactorException.alreadyEnabled()`
3. `secretEnc`가 null이면 `TwoFactorException.notSetup()`
4. 시크릿 복호화 → `CodeVerifier.isValidCode(secret, code)`로 TOTP 코드 검증
5. 검증 실패 시 `TwoFactorException.invalidCode()`
6. 백업 코드 8개 생성 (`SecureRandom`으로 8자리 숫자 × 8개)
7. 백업 코드를 JSON 배열로 직렬화 → AES-256 암호화 → DB 저장
8. `twoFactor.enable()` / `twoFactor.recordUsage()` 호출
9. 평문 백업 코드 리스트 반환 (이때 한 번만 사용자에게 보여줌)

**백업 코드 생성 로직:**
```java
private List<String> generateBackupCodes() {
    List<String> codes = new ArrayList<>();
    SecureRandom random = new SecureRandom();
    for (int i = 0; i < 8; i++) {      // 8개
        StringBuilder code = new StringBuilder();
        for (int j = 0; j < 8; j++) {  // 8자리
            code.append(random.nextInt(10));  // 0-9
        }
        codes.add(code.toString());
    }
    return codes;
}
```

- 결과 예시: `["48291037", "19374826", "73829104", ...]`
- 각 코드: 8자리 순수 숫자 (10^8 = 1억 가지 경우의 수)
- 중복 검사 미수행 (확률적으로 무시 가능)

---

### 5.3 disableTwoFactor(Long userId, String code) → void

**2FA 비활성화**

```
@Transactional
```

**동작 순서:**

1. `UserTwoFactor` 조회 → 없으면 `TwoFactorException.notSetup()`
2. 비활성화 상태면 `TwoFactorException.notEnabled()`
3. 시크릿 복호화 → TOTP 코드 검증 → 실패 시 `TwoFactorException.invalidCode()`
4. `twoFactor.disable()` 호출

**disable() 동작:**
```java
public void disable() {
    this.enabled = false;
    this.secretEnc = null;        // 시크릿 완전 삭제
    this.backupCodesEnc = null;   // 백업 코드 완전 삭제
}
```

- 비활성화 시 시크릿과 백업 코드가 DB에서 null로 설정됩니다
- `UserTwoFactor` 레코드 자체는 삭제되지 않고 유지됩니다 (재설정 시 재사용)

---

### 5.4 verifyCode(Long userId, String code) → boolean

**로그인 시 2FA 코드 검증**

```
@Transactional
```

**동작 순서:**

1. `UserTwoFactor` 조회 → null이거나 비활성화 → **`true` 반환** (2FA 미적용, 통과)
2. 시크릿 복호화
3. **TOTP 코드 우선 검증**: `codeVerifier.isValidCode(secret, code)`
   - 성공: `recordUsage()`, `true` 반환
4. **백업 코드 검증** (TOTP 실패 시): `verifyAndConsumeBackupCode(twoFactor, code)`
   - 성공: 해당 코드 소비(제거), 남은 코드 재암호화 저장, `recordUsage()`, `true` 반환
5. 모두 실패: `false` 반환 (예외 발생하지 않음)

**백업 코드 소비 로직:**
```java
private boolean verifyAndConsumeBackupCode(UserTwoFactor twoFactor, String code) {
    if (twoFactor.getBackupCodesEnc() == null) return false;

    List<String> backupCodes = deserializeBackupCodes(
            encryptionService.decrypt(twoFactor.getBackupCodesEnc()));

    if (backupCodes.contains(code)) {       // 단순 문자열 비교
        backupCodes.remove(code);            // 일회용 사용 후 제거
        String backupCodesJson = serializeBackupCodes(backupCodes);
        twoFactor.updateBackupCodes(encryptionService.encrypt(backupCodesJson));
        return true;
    }
    return false;
}
```

---

### 5.5 getTwoFactorStatus(Long userId) → TwoFactorStatusResponse

**2FA 상태 조회**

```
@Transactional(readOnly = true)
```

**동작 순서:**

1. `UserTwoFactor` 조회
2. 레코드 없음 또는 비활성화 → `{ enabled: false, remainingBackupCodes: 0, lastUsedAt: null }`
3. 활성화 상태 → 백업 코드 복호화 및 역직렬화하여 잔여 개수 계산
4. `{ enabled: true, remainingBackupCodes: N, lastUsedAt: ... }` 반환

---

### 5.6 regenerateBackupCodes(Long userId, String code) → List\<String\>

**백업 코드 재발급**

```
@Transactional
```

**동작 순서:**

1. `UserTwoFactor` 조회 → 없으면 `TwoFactorException.notSetup()`
2. 비활성화 상태면 `TwoFactorException.notEnabled()`
3. TOTP 코드 검증 → 실패 시 `TwoFactorException.invalidCode()`
4. 새 백업 코드 8개 생성 → 직렬화 → 암호화 → `backupCodesEnc` 덮어쓰기
5. 새 백업 코드 평문 리스트 반환

**중요**: 기존 백업 코드는 **즉시 모두 무효화**됩니다.

---

### 5.7 isTwoFactorRequired(Long userId) → boolean

**2FA 필요 여부 확인 (로그인 시 사용)**

```
@Transactional(readOnly = true)
```

```java
return userTwoFactorRepository.existsByUserIdAndEnabled(userId, true);
```

- 단일 SQL `EXISTS` 쿼리로 빠르게 확인
- `AuthService.loginWithEmail()`에서 호출

---

## 6. Controller 계층 (TwoFactorController)

**파일**: `src/main/java/com/jay/auth/controller/TwoFactorController.java`
**Base Path**: `/api/v1/2fa`
**Swagger Tag**: `Two-Factor Auth` / `2단계 인증 API`
**인증**: 모든 엔드포인트에서 JWT Bearer 토큰 필요 (`@AuthenticationPrincipal UserPrincipal`)

### API 엔드포인트 상세

#### GET /api/v1/2fa/status

2FA 상태를 조회합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**: 없음
- **Response**: `200 OK`

```json
{
  "enabled": true,
  "remainingBackupCodes": 5,
  "lastUsedAt": "2026-02-10T14:30:00"
}
```

---

#### POST /api/v1/2fa/setup

2FA 설정을 시작하고 QR 코드와 시크릿을 반환합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**: 없음
- **Response**: `200 OK`

```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgoAAAA..."
}
```

- `secret`: 인증 앱에 수동 입력할 수 있는 Base32 시크릿
- `qrCodeDataUrl`: HTML `<img src="...">` 태그에 직접 사용 가능한 Data URL

---

#### POST /api/v1/2fa/enable

TOTP 코드를 확인한 후 2FA를 활성화합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**:

```json
{
  "code": "123456"
}
```

- **Validation**: `@NotBlank`, `@Pattern("^[0-9]{6,8}$")`
- **Response**: `200 OK`

```json
{
  "message": "2단계 인증이 활성화되었습니다",
  "backupCodes": ["48291037", "19374826", "73829104", "58201947", "83920174", "29481037", "61839274", "40291837"]
}
```

- **Error**: `400` (2FA_NOT_SETUP, 2FA_ALREADY_ENABLED, 2FA_INVALID_CODE)

---

#### POST /api/v1/2fa/disable

TOTP 코드를 확인한 후 2FA를 비활성화합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**:

```json
{
  "code": "123456"
}
```

- **Response**: `200 OK` (빈 body)
- **Error**: `400` (2FA_NOT_SETUP, 2FA_NOT_ENABLED, 2FA_INVALID_CODE)

---

#### POST /api/v1/2fa/verify

로그인 시 2FA 코드를 검증합니다. TOTP 코드와 백업 코드 모두 지원합니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**:

```json
{
  "code": "123456"
}
```

- **Response**: `200 OK`

```json
{
  "valid": true
}
```

또는

```json
{
  "valid": false
}
```

- **참고**: 검증 실패 시에도 HTTP 200을 반환하고 `valid: false`로 응답합니다 (예외를 던지지 않음)

---

#### POST /api/v1/2fa/backup-codes/regenerate

새로운 백업 코드를 생성합니다. 기존 백업 코드는 즉시 무효화됩니다.

- **인증**: 필수 (Bearer Token)
- **Request Body**:

```json
{
  "code": "123456"
}
```

- **Response**: `200 OK`

```json
{
  "message": "백업 코드가 재생성되었습니다",
  "backupCodes": ["12345678", "87654321", "11223344", "55667788", "99001122", "33445566", "77889900", "44556677"]
}
```

- **Error**: `400` (2FA_NOT_SETUP, 2FA_NOT_ENABLED, 2FA_INVALID_CODE)

---

## 7. DTO 정의

### TwoFactorVerifyRequest (요청)

**파일**: `src/main/java/com/jay/auth/dto/request/TwoFactorVerifyRequest.java`

```java
public class TwoFactorVerifyRequest {
    @NotBlank(message = "인증 코드를 입력해주세요")
    @Pattern(regexp = "^[0-9]{6,8}$", message = "인증 코드는 6~8자리 숫자여야 합니다")
    private String code;
}
```

- 6자리: TOTP 코드
- 8자리: 백업 코드
- 모든 2FA 엔드포인트(enable, disable, verify, regenerate)에서 동일하게 사용

### TwoFactorSetupResponse (응답)

**파일**: `src/main/java/com/jay/auth/dto/response/TwoFactorSetupResponse.java`

```java
public class TwoFactorSetupResponse {
    private String secret;         // 평문 TOTP 시크릿 (Base32)
    private String qrCodeDataUrl;  // "data:image/png;base64,..." QR 코드 이미지
}
```

### TwoFactorStatusResponse (응답)

**파일**: `src/main/java/com/jay/auth/dto/response/TwoFactorStatusResponse.java`

```java
public class TwoFactorStatusResponse {
    private boolean enabled;           // 2FA 활성화 여부
    private int remainingBackupCodes;  // 잔여 백업 코드 수 (0~8)
    private LocalDateTime lastUsedAt;  // 마지막 2FA 인증 시간 (nullable)
}
```

### LoginResponse 내 2FA 필드

**파일**: `src/main/java/com/jay/auth/dto/response/LoginResponse.java`

```java
public class LoginResponse {
    // ... 기타 필드 ...
    private boolean twoFactorRequired;  // 2FA가 필요한 사용자인지 여부
}
```

---

## 8. 예외 처리

### TwoFactorException

**파일**: `src/main/java/com/jay/auth/exception/TwoFactorException.java`

`BusinessException`을 상속하는 전용 예외 클래스입니다.

| 팩토리 메서드 | 에러 코드 | HTTP 상태 | 메시지 |
|---|---|---|---|
| `notSetup()` | `2FA_NOT_SETUP` | `400 Bad Request` | 2단계 인증이 설정되지 않았습니다 |
| `notEnabled()` | `2FA_NOT_ENABLED` | `400 Bad Request` | 2단계 인증이 활성화되지 않았습니다 |
| `alreadyEnabled()` | `2FA_ALREADY_ENABLED` | `400 Bad Request` | 2단계 인증이 이미 활성화되어 있습니다 |
| `invalidCode()` | `2FA_INVALID_CODE` | `400 Bad Request` | 잘못된 인증 코드입니다 |
| `required()` | `2FA_REQUIRED` | `403 Forbidden` | 2단계 인증이 필요합니다 |

### GlobalExceptionHandler에서의 처리

**파일**: `src/main/java/com/jay/auth/exception/GlobalExceptionHandler.java`

```java
@ExceptionHandler(TwoFactorException.class)
public ResponseEntity<ApiResponse<Void>> handleTwoFactorException(TwoFactorException e) {
    log.warn("Two factor exception: {} - {}", e.getErrorCode(), e.getMessage());
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
}
```

**에러 응답 형식:**
```json
{
  "success": false,
  "error": {
    "code": "2FA_INVALID_CODE",
    "message": "잘못된 인증 코드입니다"
  },
  "timestamp": "2026-02-15T10:30:00"
}
```

> **참고**: `GlobalExceptionHandler`의 전용 핸들러는 HTTP 상태를 항상 `400`으로 반환합니다. `required()`의 `403` 상태는 `BusinessException` 범용 핸들러를 통해 처리될 때만 반영됩니다.

---

## 9. 로그인 플로우 통합

### AuthService에서의 2FA 확인

**파일**: `src/main/java/com/jay/auth/service/AuthService.java`

```java
// loginWithEmail 메서드 내부
boolean twoFactorRequired = totpService.isTwoFactorRequired(result.getUserId());

return LoginResponse.of(
    result.getUserId(), result.getUserUuid(), email, result.getNickname(),
    tokenResponse, passwordExpired, daysUntilExpiration,
    twoFactorRequired,
    result.isPendingDeletion(), result.getDeletionRequestedAt()
);
```

### 핵심 동작

1. 이메일/비밀번호 인증이 **성공**하면 JWT 토큰이 **즉시 발급**됩니다
2. `twoFactorRequired` 플래그가 응답에 포함됩니다
3. **2FA 검증은 프론트엔드에서 제어**합니다:
   - `twoFactorRequired: true` → 토큰을 메모리에만 보관, 2FA 화면 표시
   - `twoFactorRequired: false` → 토큰을 스토리지에 저장, 대시보드 이동

### 토큰 발급 시점 다이어그램

```
사용자 → [이메일/비밀번호] → 서버
서버 → JWT 발급 (accessToken + refreshToken)
서버 → { token: {...}, twoFactorRequired: true }

                    ┌─ twoFactorRequired: false ─→ 스토리지 저장 → 대시보드
프론트엔드 분기 ─────┤
                    └─ twoFactorRequired: true  ─→ 메모리 보관 → 2FA 화면
                                                    │
                                                    ├─ POST /2fa/verify (code) → { valid: true }
                                                    │   → 스토리지 저장 → 대시보드
                                                    │
                                                    └─ POST /2fa/verify (code) → { valid: false }
                                                        → 에러 메시지 표시
```

---

## 10. 보안 설정 및 접근 제어

### SecurityConfig 내 접근 규칙

**파일**: `src/main/java/com/jay/auth/config/SecurityConfig.java`

- `/api/v1/2fa/**`는 `PUBLIC_ENDPOINTS`에 포함되어 있지 **않습니다**
- `anyRequest().authenticated()` 규칙에 의해 JWT 인증이 필수입니다
- 요청 시 `Authorization: Bearer {accessToken}` 헤더가 필요합니다

### Rate Limiting

**파일**: `src/main/java/com/jay/auth/config/RateLimitFilter.java`

| 경로 | Rate Limit 키 | 제한 |
|---|---|---|
| `/api/v1/2fa/**` | `rate:user:{ip}` | 200 req/min |

- 2FA 전용 Rate Limit은 없으며, 일반 사용자 API와 동일한 제한이 적용됩니다
- `rate:user:{ip}` → Redis에 1분 TTL로 저장

### 필터 체인 적용 순서

```
요청 → SecurityHeadersFilter → RateLimitFilter → RequestLoggingFilter → JwtAuthenticationFilter
                                                                            ↓
                                                                    Bearer 토큰 검증
                                                                    Redis 블랙리스트 확인
                                                                            ↓
                                                                    TwoFactorController
```

---

## 11. 보안 대시보드 연동

### SecurityDashboardService

**파일**: `src/main/java/com/jay/auth/service/SecurityDashboardService.java`

2FA는 보안 점수에서 **가장 높은 배점(30점/100점)**을 차지합니다.

```java
// calculateSecurityFactors 메서드 내부
TwoFactorStatusResponse twoFactorStatus = totpService.getTwoFactorStatus(userId);
factors.add(SecurityFactor.builder()
    .name("2FA_ENABLED")
    .description("2단계 인증")
    .score(twoFactorStatus.isEnabled() ? 30 : 0)
    .maxScore(30)
    .enabled(twoFactorStatus.isEnabled())
    .build());
```

### 보안 점수 구성

| 항목 | 배점 | 2FA 활성화 시 | 2FA 비활성화 시 |
|---|---|---|---|
| 2FA_ENABLED | 30점 | 30점 | 0점 |
| PASSWORD_HEALTH | 25점 | - | - |
| RECOVERY_EMAIL | 15점 | - | - |
| SOCIAL_LINKED | 15점 | - | - |
| LOGIN_MONITORING | 15점 | - | - |
| **합계** | **100점** | | |

### 추천 메시지

2FA가 비활성화된 경우:
> "2단계 인증을 활성화하여 계정 보안을 강화하세요."

### 캐싱

- `@Cacheable(value = "securityDashboard", key = "#userId")` — 3분 TTL
- 2FA 상태 변경 후 최대 3분간 대시보드에 이전 점수가 표시될 수 있습니다

---

## 12. 계정 삭제 시 정리

### AccountCleanupScheduler

**파일**: `src/main/java/com/jay/auth/service/AccountCleanupScheduler.java`

- **실행 시점**: 매일 새벽 3시 (`@Scheduled(cron = "0 0 3 * * *")`)
- **대상**: `PENDING_DELETE` 상태가 30일 이상 된 사용자

```java
// PII 삭제 프로세스 내부
userTwoFactorRepository.deleteByUserId(userId);  // 2FA 데이터 삭제
passwordHistoryRepository.deleteByUserId(userId);
loginHistoryRepository.deleteByUserId(userId);
```

- `UserTwoFactor`에 cascade가 설정되어 있지 않으므로 명시적으로 `deleteByUserId`를 호출합니다
- 이 삭제는 `User` 엔티티의 cascade 삭제(채널, 로그인 정보) 이전에 수행됩니다

---

## 13. 프론트엔드 구현

### 13.1 API 클라이언트

**파일**: `frontend/src/api/auth.js`

```javascript
export const twoFactorApi = {
  getStatus: ()             => client.get('/2fa/status'),
  setup: ()                 => client.post('/2fa/setup'),
  enable: (code)            => client.post('/2fa/enable', { code }),
  disable: (code)           => client.post('/2fa/disable', { code }),
  verify: (code)            => client.post('/2fa/verify', { code }),
  regenerateBackupCodes: (code) => client.post('/2fa/backup-codes/regenerate', { code }),
};
```

- Axios 클라이언트 사용 (`http://localhost:8080/api/v1` 기본 URL)
- 인증 토큰은 Axios 인터셉터에서 자동 주입

### 13.2 AuthContext (상태 관리)

**파일**: `frontend/src/contexts/AuthContext.jsx`

#### login 함수 (2FA 분기 처리)

```javascript
const login = async (email, password, rememberMe = false) => {
  const response = await authApi.login(email, password);
  const data = response.data;

  // 2FA가 필요한 경우
  if (data.twoFactorRequired) {
    return {
      twoFactorRequired: true,
      token: data.token,       // 메모리에만 보관
      rememberMe,
      pendingDeletion: data.pendingDeletion,
      deletionRequestedAt: data.deletionRequestedAt
    };
  }

  // 일반 로그인
  const storage = rememberMe ? localStorage : sessionStorage;
  storage.setItem('accessToken', data.token.accessToken);
  storage.setItem('refreshToken', data.token.refreshToken);
  await loadProfile();
  return { ...data };
};
```

#### complete2FALogin 함수 (2FA 검증 후 호출)

```javascript
const complete2FALogin = async (loginData) => {
  const { token, rememberMe } = loginData;
  const storage = rememberMe ? localStorage : sessionStorage;
  storage.setItem('accessToken', token.accessToken);
  storage.setItem('refreshToken', token.refreshToken);
  await loadProfile();
};
```

### 13.3 로그인 페이지 (2FA 화면)

**파일**: `frontend/src/pages/LoginPage.jsx`

로그인 페이지는 3단계 상태 머신으로 동작합니다:

```
1. 로그인 방법 선택 (소셜 / 이메일)
        ↓
2. 이메일 로그인 폼 (이메일 / 비밀번호 / 로그인 유지)
        ↓
3. 2FA 인증 화면 (requires2FA === true 일 때)
```

#### 2FA 인증 화면 동작

**상태 변수:**
```javascript
const [requires2FA, setRequires2FA] = useState(false);
const [twoFactorCode, setTwoFactorCode] = useState('');
const [tempLoginData, setTempLoginData] = useState(null);
```

**입력 필드:**
- 6자리 숫자만 입력 가능 (`e.target.value.replace(/\D/g, '').slice(0, 6)`)
- 큰 폰트, 문자 간격 확대 (OTP 스타일)
- 제출 버튼: `twoFactorCode.length !== 6` 일 때 비활성화

**제출 처리:**
```javascript
const handle2FASubmit = async (e) => {
  e.preventDefault();
  setLoading(true);
  setError('');
  try {
    await twoFactorApi.verify(twoFactorCode);     // POST /2fa/verify
    if (complete2FALogin) {
      await complete2FALogin(tempLoginData);        // 토큰 스토리지 저장
    }
    navigate('/dashboard');
  } catch (err) {
    setError(err.response?.data?.error?.message || '2FA 인증에 실패했습니다');
  } finally {
    setLoading(false);
  }
};
```

**"다른 방법으로 로그인" 링크:**
- `requires2FA = false`로 리셋
- `twoFactorCode` 초기화
- 로그인 폼으로 돌아감

### 13.4 대시보드 페이지 (2FA 관리)

**파일**: `frontend/src/pages/DashboardPage.jsx`

보안 설정 탭에서 4가지 모달을 통해 2FA를 관리합니다.

#### 모달 구성

| 모달 키 | 트리거 | 용도 |
|---|---|---|
| `'2fa-setup'` | "설정" 버튼 (2FA 비활성화 시) | QR 코드 표시 + 코드 입력으로 활성화 |
| `'2fa-backup'` | 활성화/재발급 성공 시 자동 전환 | 백업 코드 표시 (확인 버튼 클릭 필수) |
| `'2fa-disable'` | "비활성화" 버튼 | 코드 입력으로 비활성화 |
| `'2fa-regenerate'` | "백업코드 재발급" 버튼 | 경고 표시 + 코드 입력으로 재발급 |

#### 2FA 설정 모달 (`2fa-setup`)

```
┌────────────────────────────────┐
│     2단계 인증 설정              │
│                                │
│  인증 앱으로 아래 QR 코드를      │
│  스캔하세요.                    │
│                                │
│      ┌──────────────┐          │
│      │   QR CODE    │ (200px)  │
│      └──────────────┘          │
│                                │
│  수동 입력 키:                  │
│  ┌─────────────────────┐       │
│  │ JBSWY3DPEHPK3PXP   │ mono  │
│  └─────────────────────┘       │
│                                │
│  인증 코드 입력:                │
│  ┌──────────────┐              │
│  │ _ _ _ _ _ _  │ (6자리)      │
│  └──────────────┘              │
│                                │
│  [활성화] (6자리 입력 시 활성화)  │
│  [취소]                        │
└────────────────────────────────┘
```

#### 백업 코드 모달 (`2fa-backup`)

```
┌────────────────────────────────────┐
│     백업 코드                       │
│                                    │
│  ✓ 2단계 인증이 활성화되었습니다!     │
│                                    │
│  아래 백업 코드를 안전한 곳에        │
│  저장하세요. 인증 앱에 접근할 수      │
│  없을 때 사용할 수 있습니다.        │
│                                    │
│  ┌──────────┬──────────┐           │
│  │ 48291037 │ 19374826 │           │
│  │ 73829104 │ 58201947 │           │
│  │ 83920174 │ 29481037 │           │
│  │ 61839274 │ 40291837 │ (2열 그리드)│
│  └──────────┴──────────┘           │
│                                    │
│  ⚠ 각 백업 코드는 한 번만           │
│    사용할 수 있습니다.              │
│                                    │
│  [확인]                            │
└────────────────────────────────────┘
```

- 배경 클릭으로 닫을 수 없음 (확인 버튼 클릭 필수)
- 경고 텍스트 빨간색 표시

#### 2FA 비활성화 모달 (`2fa-disable`)

```
┌────────────────────────────────────┐
│     2단계 인증 비활성화              │
│                                    │
│  ⚠ 2단계 인증을 비활성화하면        │
│    계정 보안이 약화됩니다.          │
│                                    │
│  2단계 인증을 비활성화하려면         │
│  현재 인증 코드를 입력하세요.        │
│                                    │
│  ┌──────────────┐                  │
│  │ _ _ _ _ _ _  │ (6자리)          │
│  └──────────────┘                  │
│                                    │
│  [비활성화] [취소]                  │
└────────────────────────────────────┘
```

#### 백업 코드 재발급 모달 (`2fa-regenerate`)

```
┌────────────────────────────────────────┐
│     백업 코드 재발급                    │
│                                        │
│  ⚠ 주의: 기존 백업 코드는 모두          │
│    무효화됩니다.                        │
│    새로운 백업 코드가 발급되면 기존       │
│    코드는 더 이상 사용할 수 없습니다.    │
│                                        │
│  백업 코드를 재발급하려면 현재           │
│  인증 코드를 입력하세요.                │
│                                        │
│  ┌──────────────┐                      │
│  │ _ _ _ _ _ _  │ (6자리)              │
│  └──────────────┘                      │
│                                        │
│  [재발급] [취소]                        │
└────────────────────────────────────────┘
```

#### 보안 설정 섹션 표시

```
┌──────────────────────────────────────────────┐
│  2단계 인증 (2FA)                             │
│                                              │
│  [활성화됨] (초록색 태그)   백업 코드 5개 남음  │
│                                              │
│  [백업코드 재발급]  [비활성화]                  │
└──────────────────────────────────────────────┘

또는

┌──────────────────────────────────────────────┐
│  2단계 인증 (2FA)                             │
│                                              │
│  [비활성화됨] (회색 태그)                      │
│                                              │
│  [설정]                                      │
└──────────────────────────────────────────────┘
```

---

## 14. 테스트 코드

### 14.1 서비스 테스트 (TotpServiceTest)

**파일**: `src/test/java/com/jay/auth/service/TotpServiceTest.java`
**방식**: `@ExtendWith(MockitoExtension.class)` — Spring 컨텍스트 없는 순수 단위 테스트

| 테스트 그룹 (@Nested) | 테스트 케이스 | 검증 내용 |
|---|---|---|
| **2FA 설정 (setupTwoFactor)** | `setupTwoFactorSuccess` | QR 코드 URL 반환, save 호출 확인 |
| | `setupTwoFactorFailsUserNotFound` | `UserNotFoundException` 발생 |
| | `setupTwoFactorOverwritesExisting` | 기존 레코드에 새 시크릿 덮어쓰기 |
| **2FA 활성화 (enableTwoFactor)** | `enableTwoFactorSuccess` | 백업 코드 8개 반환 |
| | `enableTwoFactorFailsWithInvalidCode` | `TwoFactorException` 발생 |
| | `enableTwoFactorFailsWhenNotSetup` | `TwoFactorException` 발생 |
| | `enableTwoFactorFailsWhenAlreadyEnabled` | `TwoFactorException` 발생 |
| **2FA 비활성화 (disableTwoFactor)** | `disableTwoFactorSuccess` | `enabled = false` 확인 |
| | `disableTwoFactorFailsWithInvalidCode` | `TwoFactorException` 발생 |
| **코드 검증 (verifyCode)** | `verifyCodeWithValidTotpCode` | `true` 반환 |
| | `verifyCodeWithBackupCode` | 백업 코드 소비 후 `true` 반환 |
| | `verifyCodeWhenTwoFactorNotEnabled` | 2FA 미설정 시 `true` 반환 |
| **상태 조회 (getTwoFactorStatus)** | `getTwoFactorStatusEnabled` | `enabled: true`, 잔여 코드 수 |
| | `getTwoFactorStatusDisabled` | `enabled: false`, 코드 0개 |
| **백업 코드 재생성 (regenerateBackupCodes)** | `regenerateBackupCodesSuccess` | 새 백업 코드 8개 반환 |
| **2FA 필요 여부 (isTwoFactorRequired)** | `isTwoFactorRequiredTrue` | `true` 반환 |
| | `isTwoFactorRequiredFalse` | `false` 반환 |

**특이 테스트 기법:**

`CodeVerifier`가 Spring Bean이 아닌 인스턴스 필드이므로, 리플렉션으로 mock을 주입합니다:

```java
CodeVerifier mockCodeVerifier = Mockito.mock(CodeVerifier.class);
setField(totpService, "codeVerifier", mockCodeVerifier);
given(mockCodeVerifier.isValidCode("plain_secret", "123456")).willReturn(true);
```

`setField` 헬퍼:
```java
private void setField(Object target, String fieldName, Object value) {
    java.lang.reflect.Field field = findField(target.getClass(), fieldName);
    field.setAccessible(true);
    field.set(target, value);
}
```

### 14.2 컨트롤러 테스트 (TwoFactorControllerTest)

**파일**: `src/test/java/com/jay/auth/controller/TwoFactorControllerTest.java`
**방식**: `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)`

**보안 필터 제외:**
```java
@WebMvcTest(
    controllers = TwoFactorController.class,
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

| 테스트 그룹 | 테스트 케이스 | HTTP 상태 | 검증 내용 |
|---|---|---|---|
| **GET /status** | 활성화 상태 조회 | 200 | `enabled: true`, `remainingBackupCodes: 5` |
| | 미설정 상태 조회 | 200 | `enabled: false`, `remainingBackupCodes: 0` |
| **POST /setup** | 설정 성공 | 200 | `secret`, `qrCodeDataUrl` 반환 |
| | 이미 활성화 → 에러 | 400 | - |
| **POST /enable** | 활성화 성공 | 200 | `message`, `backupCodes[]` 반환 |
| | 잘못된 코드 | 400 | - |
| **POST /disable** | 비활성화 성공 | 200 | 빈 body |
| | 미활성화 상태 | 400 | - |
| **POST /verify** | 검증 성공 | 200 | `valid: true` |
| | 검증 실패 | 200 | `valid: false` (400이 아님!) |
| **POST /backup-codes/regenerate** | 재생성 성공 | 200 | `message`, `backupCodes[]` 반환 |
| | 미활성화 상태 | 400 | - |

### 14.3 예외 핸들러 테스트

**파일**: `src/test/java/com/jay/auth/exception/GlobalExceptionHandlerTest.java`

`TwoFactorException` 관련 테스트:
- `notSetup()` → 400, `success: false`
- `required()` → 400, `success: false`
- `invalidCode()` → 400, `success: false`

---

## 15. 전체 플로우 시퀀스

### 15.1 2FA 최초 설정 플로우

```
사용자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │ [보안 설정 → "설정" 클릭]          │                                │
     │                                  │                                │
     │──── POST /2fa/setup ────────────→│                                │
     │     (Authorization: Bearer ...)  │                                │
     │                                  │── User 조회 ─────────────────→│
     │                                  │←─ User 반환 ─────────────────│
     │                                  │── TOTP 시크릿 생성              │
     │                                  │── 시크릿 AES-256 암호화         │
     │                                  │── UserTwoFactor 저장 ────────→│
     │                                  │   (enabled=false)              │
     │                                  │── QR 코드 PNG 생성              │
     │                                  │── Base64 인코딩                 │
     │                                  │                                │
     │←── { secret, qrCodeDataUrl } ───│                                │
     │                                  │                                │
     │ [QR 코드 표시 + 수동 입력 키]      │                                │
     │ [사용자가 인증 앱에 등록]           │                                │
     │ [인증 앱에서 6자리 코드 확인]       │                                │
     │                                  │                                │
     │──── POST /2fa/enable ───────────→│                                │
     │     { "code": "482910" }         │                                │
     │                                  │── UserTwoFactor 조회 ─────────→│
     │                                  │←─ (enabled=false, secret) ────│
     │                                  │── 시크릿 복호화                  │
     │                                  │── TOTP 코드 검증 ✓              │
     │                                  │── 백업 코드 8개 생성             │
     │                                  │── 백업 코드 JSON → AES-256 암호화│
     │                                  │── UserTwoFactor 업데이트 ──────→│
     │                                  │   (enabled=true, backupCodes)  │
     │                                  │                                │
     │←── { message, backupCodes[] } ──│                                │
     │                                  │                                │
     │ [백업 코드 모달 표시]              │                                │
     │ [사용자가 백업 코드 저장]           │                                │
     │ ["확인" 클릭]                     │                                │
```

### 15.2 2FA가 설정된 사용자의 로그인 플로우

```
사용자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │──── POST /auth/email/login ────→│                                │
     │     { email, password }          │                                │
     │                                  │── 이메일/비밀번호 검증            │
     │                                  │── isTwoFactorRequired(userId) ─→│
     │                                  │←──── true ────────────────────│
     │                                  │── JWT 토큰 발급                 │
     │                                  │                                │
     │←── { token, twoFactorRequired: true } ──│                        │
     │                                  │                                │
     │ [토큰을 메모리(state)에 보관]       │                                │
     │ [2FA 인증 화면 표시]              │                                │
     │ [인증 앱에서 6자리 코드 확인]       │                                │
     │                                  │                                │
     │──── POST /2fa/verify ───────────→│                                │
     │     Authorization: Bearer ...    │                                │
     │     { "code": "591037" }         │                                │
     │                                  │── UserTwoFactor 조회 ─────────→│
     │                                  │── 시크릿 복호화                  │
     │                                  │── TOTP 코드 검증 ✓              │
     │                                  │── recordUsage() ──────────────→│
     │                                  │                                │
     │←── { "valid": true } ───────────│                                │
     │                                  │                                │
     │ [토큰을 스토리지에 저장]            │                                │
     │   localStorage (로그인 유지)      │                                │
     │   sessionStorage (세션만)         │                                │
     │ [프로필 로드]                     │                                │
     │ [대시보드로 이동]                  │                                │
```

### 15.3 백업 코드로 로그인하는 플로우

```
사용자(프론트엔드)                    서버(백엔드)                          DB
     │                                  │                                │
     │ [... 로그인 후 2FA 화면 ...]       │                                │
     │ [인증 앱 접근 불가 → 백업 코드 사용] │                               │
     │                                  │                                │
     │──── POST /2fa/verify ───────────→│                                │
     │     { "code": "48291037" }       │  (8자리 백업 코드)              │
     │                                  │── UserTwoFactor 조회 ─────────→│
     │                                  │── 시크릿 복호화                  │
     │                                  │── TOTP 코드 검증 ✗ (실패)       │
     │                                  │── 백업 코드 복호화               │
     │                                  │── 백업 코드 목록에서 검색 ✓       │
     │                                  │── 사용된 코드 제거               │
     │                                  │── 남은 코드 재암호화 → DB 저장 ─→│
     │                                  │── recordUsage() ──────────────→│
     │                                  │                                │
     │←── { "valid": true } ───────────│                                │
```

> **주의**: 프론트엔드 입력 필드의 `maxLength=6` 제한으로 인해 8자리 백업 코드를 입력할 수 없습니다. (알려진 제한사항)

---

## 16. 알려진 제한사항 및 설계 참고사항

### 16.1 보안 관련

| 항목 | 설명 |
|---|---|
| **토큰 선발급** | 2FA 검증 전에 JWT 토큰이 발급됩니다. 2FA 강제는 프론트엔드 책임입니다. `twoFactorRequired` 플래그를 무시하고 토큰을 직접 저장하면 2FA를 우회할 수 있습니다. |
| **2FA 전용 Rate Limit 없음** | `/api/v1/2fa/verify`에 전용 brute-force 방지가 없습니다. 일반 사용자 API Rate Limit (200 req/min)만 적용됩니다. 로그인의 5회/이메일, 20회/IP 제한과 대조됩니다. |
| **백업 코드 비교 방식** | `List.contains(code)`로 단순 문자열 비교합니다. 타이밍 안전(constant-time) 비교를 사용하지 않습니다. TOTP 코드는 라이브러리가 처리합니다. |
| **2FA 이벤트 알림 없음** | 2FA 활성화/비활성화/백업 코드 소비 시 이메일 알림이나 `SecurityNotificationService` 호출이 없습니다. |

### 16.2 기능 관련

| 항목 | 설명 |
|---|---|
| **프론트엔드 입력 제한** | 백엔드는 6~8자리를 허용하지만, 프론트엔드 모든 입력 필드가 `maxLength={6}`으로 제한되어 8자리 백업 코드를 입력할 수 없습니다. |
| **Setup 재호출 가능** | 이미 2FA가 활성화된 상태에서 `/2fa/setup`을 호출하면 시크릿이 덮어쓰기됩니다. 이후 `/2fa/enable`은 `alreadyEnabled()` 예외를 발생시켜, 새 시크릿이 저장되었지만 활성화할 수 없는 상태가 됩니다. |
| **보안 점수 캐싱** | 보안 대시보드 점수가 3분간 캐싱되므로, 2FA 상태 변경이 즉시 반영되지 않을 수 있습니다. |
| **Cascade 미설정** | `User` 삭제 시 `UserTwoFactor`가 자동 cascade 되지 않으므로 `AccountCleanupScheduler`에서 명시적 삭제가 필요합니다. |
| **백업 코드 중복** | 8자리 숫자 8개를 생성할 때 중복 검사를 수행하지 않습니다 (확률적으로 무시 가능). |

### 16.3 Redis 사용

2FA 자체에서는 Redis를 사용하지 않습니다:
- TOTP 시크릿 → MySQL (`secret_enc`)
- 백업 코드 → MySQL (`backup_codes_enc`)
- 2FA 세션 상태 → 없음 (별도의 pending 상태 패턴 없음)

Rate Limit만 Redis를 통해 적용됩니다:
- `rate:user:{ip}` — 1분 TTL, 200 req/min

---

## 17. 파일 맵

### 백엔드

```
src/main/java/com/jay/auth/
├── controller/
│   └── TwoFactorController.java          # 2FA API 엔드포인트 (6개)
├── service/
│   └── TotpService.java                   # 2FA 비즈니스 로직
├── domain/entity/
│   └── UserTwoFactor.java                 # 2FA 엔티티 (tb_user_two_factor)
├── repository/
│   └── UserTwoFactorRepository.java       # 2FA JPA Repository
├── dto/
│   ├── request/
│   │   └── TwoFactorVerifyRequest.java    # 코드 입력 요청 DTO
│   └── response/
│       ├── TwoFactorSetupResponse.java    # 설정 응답 (시크릿 + QR)
│       ├── TwoFactorStatusResponse.java   # 상태 응답
│       └── LoginResponse.java             # 로그인 응답 (twoFactorRequired 포함)
├── exception/
│   ├── TwoFactorException.java            # 2FA 전용 예외
│   └── GlobalExceptionHandler.java        # 2FA 예외 핸들러 포함
├── config/
│   ├── SecurityConfig.java                # 2FA 경로 인증 규칙
│   └── RateLimitFilter.java               # 2FA 경로 Rate Limit
└── service/
    ├── AuthService.java                   # 로그인 시 isTwoFactorRequired 호출
    ├── SecurityDashboardService.java      # 보안 점수에 2FA 반영 (30점)
    └── AccountCleanupScheduler.java       # 계정 삭제 시 2FA 데이터 정리

src/test/java/com/jay/auth/
├── service/
│   └── TotpServiceTest.java              # 서비스 단위 테스트 (15개)
├── controller/
│   └── TwoFactorControllerTest.java      # 컨트롤러 테스트 (12개)
└── exception/
    └── GlobalExceptionHandlerTest.java   # 2FA 예외 핸들러 테스트 (3개)
```

### 프론트엔드

```
frontend/src/
├── api/
│   └── auth.js                           # twoFactorApi 객체 (6개 메서드)
├── contexts/
│   └── AuthContext.jsx                   # login(), complete2FALogin()
└── pages/
    ├── LoginPage.jsx                     # 2FA 인증 화면 (3번째 단계)
    └── DashboardPage.jsx                 # 2FA 관리 모달 4종
```
