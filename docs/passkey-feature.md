# Passkey (WebAuthn) 기능 상세

## 개요

WebAuthn/Passkey 기반 패스워드리스 인증을 지원합니다. 사용자는 생체인식(지문, Face ID)이나 기기 잠금(PIN)을 통해 비밀번호 없이 안전하게 로그인할 수 있습니다.

### 주요 특징
- 피싱 방어: origin 검증으로 피싱 사이트에서 사용 불가
- 패스워드리스: 비밀번호 입력 불필요
- 크로스 디바이스: 여러 기기에 패스키 등록 가능 (최대 10개)
- Discoverable Credentials: 사용자명 입력 없이 로그인 가능

## 기술 스택

- **라이브러리**: webauthn4j-core 0.28.4.RELEASE
- **챌린지 저장**: Redis (TTL 5분)
- **인증 모드**: Non-strict (attestation 검증 완화 - 패스키 용도에 적합)

## API 엔드포인트

### 등록 (Authenticated)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/passkey/register/options` | 등록 옵션(챌린지) 생성 |
| POST | `/api/v1/passkey/register/verify` | 등록 검증 및 credential 저장 |

### 인증 (Public)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/passkey/login/options` | 인증 옵션(챌린지) 생성 |
| POST | `/api/v1/auth/passkey/login/verify` | 인증 검증 및 JWT 발급 |

### 관리 (Authenticated)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/passkey/list` | 등록된 패스키 목록 조회 |
| PATCH | `/api/v1/passkey/{id}` | 패스키 이름 변경 |
| DELETE | `/api/v1/passkey/{id}` | 패스키 삭제 |

## 데이터 모델

### tb_user_passkey

| Column | Type | Description |
|---|---|---|
| id | BIGINT (PK) | Auto-increment |
| user_id | BIGINT (FK) | User 참조 |
| credential_id | VARCHAR(512) | WebAuthn credential ID (Base64URL, unique) |
| public_key | BLOB | 직렬화된 AttestedCredentialData |
| sign_count | BIGINT | Authenticator sign counter (복제 감지용) |
| transports | VARCHAR(255) | 전송 방식 (internal, usb, ble, nfc) |
| device_name | VARCHAR(100) | 사용자 지정 패스키 이름 |
| last_used_at | DATETIME | 마지막 사용 시각 |
| created_at | DATETIME | 등록 시각 |
| updated_at | DATETIME | 수정 시각 |

## 플로우

### 패스키 등록 플로우

```
[Frontend]                    [Backend]                    [Redis]
    |                             |                           |
    |-- POST register/options --> |                           |
    |                             |-- save challenge -------> |
    |                             |                           |
    |<-- options (challenge, rp,  |                           |
    |    user, pubKeyCredParams)  |                           |
    |                             |                           |
    |-- navigator.credentials     |                           |
    |   .create(options)          |                           |
    |                             |                           |
    |-- POST register/verify -->  |                           |
    |   (attestationObject,       |-- get & delete challenge  |
    |    clientDataJSON,          |<--------------------------|
    |    credentialId)            |                           |
    |                             |-- validate attestation    |
    |                             |-- save UserPasskey (DB)   |
    |<-- success                  |                           |
```

### 패스키 로그인 플로우

```
[Frontend]                    [Backend]                    [Redis]
    |                             |                           |
    |-- POST login/options -----> |                           |
    |                             |-- save challenge -------> |
    |                             |   (key: login:{sessionId})|
    |<-- options (challenge,      |                           |
    |    rpId, allowCredentials)  |                           |
    |                             |                           |
    |-- navigator.credentials     |                           |
    |   .get(options)             |                           |
    |                             |                           |
    |-- POST login/verify ------> |                           |
    |   (credentialId,            |-- find & consume challenge|
    |    authenticatorData,       |<--------------------------|
    |    clientDataJSON,          |                           |
    |    signature)               |-- find passkey by         |
    |                             |   credentialId (DB)       |
    |                             |-- validate assertion      |
    |                             |-- update signCount        |
    |                             |-- issue JWT tokens        |
    |<-- LoginResponse            |                           |
    |   (accessToken,             |                           |
    |    refreshToken, user info) |                           |
```

## Redis Key 패턴

| Key | TTL | Description |
|---|---|---|
| `passkey:challenge:register:{userId}` | 5분 | 등록용 챌린지 (사용자별) |
| `passkey:challenge:login:{sessionId}` | 5분 | 인증용 챌린지 (세션별, UUID) |

## 보안 고려사항

1. **챌린지 일회성**: Redis에서 조회 시 즉시 삭제 (replay attack 방지)
2. **Sign Count 검증**: authenticator의 sign count를 추적하여 credential 복제 감지
3. **Origin 검증**: WebAuthn4J가 origin을 자동 검증하여 피싱 방지
4. **RP ID 검증**: rpId가 요청 도메인과 일치하는지 검증
5. **등록 한도**: 사용자당 최대 10개 패스키 (DoS 방지)
6. **Credential ID 중복 검사**: 동일 credential의 중복 등록 방지

## 프론트엔드 통합

### 브라우저 호환성 확인
```javascript
if (window.PublicKeyCredential) {
  // WebAuthn 지원
}
```

### 패스키 등록 (보안 설정 탭)
- 대시보드 보안 탭에서 "패스키 관리" → "새 패스키 등록"
- `navigator.credentials.create()` 호출
- 기기 이름 지정 가능 (기본값: "패스키")

### 패스키 로그인 (로그인 페이지)
- 로그인 페이지 상단에 "패스키로 로그인" 버튼
- `navigator.credentials.get()` 호출 (discoverable credentials)
- 성공 시 JWT 토큰 수령 → 자동 로그인

## 설정

### 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `WEBAUTHN_RP_ID` | `localhost` | Relying Party ID (도메인) |
| `WEBAUTHN_RP_NAME` | `Authly` | Relying Party 표시 이름 |
| `WEBAUTHN_ORIGIN` | `http://localhost:3000` | 허용 origin |

### 프로덕션 설정 예시
```yaml
app:
  webauthn:
    rp-id: example.com
    rp-name: Authly
    origin: https://example.com
```

## 보안 대시보드 연동

- **보안 점수**: 패스키 등록 시 15점 추가 (PASSKEY_REGISTERED 팩터)
- **권장사항**: 패스키 미등록 시 "패스키를 등록하면 비밀번호 없이 안전하게 로그인할 수 있습니다" 표시
- **알림**: 패스키 등록/삭제 시 SecurityNotificationService를 통해 알림
- **캐시**: 패스키 등록/삭제 시 securityDashboard 캐시 무효화 (@CacheEvict)
