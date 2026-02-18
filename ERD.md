# Database ERD

## 테이블 관계도

```
┌─────────────────────────────────────┐
│            tb_user (User)           │
├─────────────────────────────────────┤
│ PK  user_id          BIGINT AI     │
│     user_uuid        VARCHAR(36) UQ│
│     email_enc        VARCHAR(512)  │
│     email_lower_enc  VARCHAR(512)  │ ← IDX
│     recovery_email_enc      VARCHAR(512)  │
│     recovery_email_lower_enc VARCHAR(512) │
│     phone_enc        VARCHAR(512)  │
│     nickname_enc     VARCHAR(512)  │
│     nickname_lower_enc VARCHAR(512)│ ← UQ IDX
│     status           VARCHAR(20) NN│ ← ACTIVE/LOCKED/PENDING_DELETE
│     role             VARCHAR(20) NN│ ← USER/ADMIN
│     email_updated_at    DATETIME   │
│     phone_updated_at    DATETIME   │
│     nickname_updated_at DATETIME   │
│     deletion_requested_at DATETIME │
│     created_at       DATETIME NN   │
│     updated_at       DATETIME NN   │
└──────────┬──────┬──────┬───────────┘
           │      │      │
           │1    1│     1│
           │      │      │
           │      │      │0..1
           │      │  ┌───┴──────────────────────────────────┐
           │      │  │  tb_user_two_factor (UserTwoFactor)  │
           │      │  ├──────────────────────────────────────┤
           │      │  │ PK  id              BIGINT AI        │
           │      │  │ FK  user_id         BIGINT NN UQ     │ ← 1:1
           │      │  │     secret_enc      VARCHAR(512)     │
           │      │  │     is_enabled      BOOLEAN NN       │
           │      │  │     backup_codes_enc VARCHAR(1024)   │
           │      │  │     last_used_at    DATETIME         │
           │      │  │     created_at      DATETIME NN      │
           │      │  │     updated_at      DATETIME NN      │
           │      │  └──────────────────────────────────────┘
           │      │
           │     0..1
           │  ┌───┴──────────────────────────────────────────┐
           │  │   tb_user_sign_in_info (UserSignInInfo)      │
           │  ├──────────────────────────────────────────────┤
           │  │ PK  sign_in_id           BIGINT AI           │
           │  │ FK  user_id              BIGINT NN UQ        │ ← 1:1
           │  │     login_email_enc      VARCHAR(512) NN     │
           │  │     login_email_lower_enc VARCHAR(512) NN    │ ← UQ IDX
           │  │     password_hash        VARCHAR(255) NN     │
           │  │     password_updated_at  DATETIME            │
           │  │     last_login_at        DATETIME            │
           │  │     login_fail_count     INT NN              │
           │  │     locked_until         DATETIME            │
           │  │     created_at           DATETIME NN         │
           │  │     updated_at           DATETIME NN         │
           │  └──────────────────────────────────────────────┘
           │
          1│
           │
           │ *
┌──────────┴──────────────────────────────────┐
│       tb_user_channel (UserChannel)         │
├─────────────────────────────────────────────┤
│ PK  user_channel_id    BIGINT AI            │
│ FK  user_id            BIGINT NN            │ ← 1:N
│     channel_code       VARCHAR(20) NN       │ ← EMAIL/GOOGLE/KAKAO/NAVER/FACEBOOK
│     channel_key        VARCHAR(255) NN      │
│     channel_email_enc       VARCHAR(512)    │
│     channel_email_lower_enc VARCHAR(512)    │
│     created_at         DATETIME NN          │
│     updated_at         DATETIME NN          │
├─────────────────────────────────────────────┤
│ UQ (channel_code, channel_key)              │
└─────────────────────────────────────────────┘


┌───────────────────────────────────────┐     ┌──────────────────────────────────────┐
│ tb_password_history (PasswordHistory) │     │    tb_login_history (LoginHistory)    │
├───────────────────────────────────────┤     ├──────────────────────────────────────┤
│ PK  history_id     BIGINT AI          │     │ PK  history_id      BIGINT AI        │
│ FK  user_id        BIGINT NN          │     │     user_id         BIGINT NN        │ ← IDX (FK 없음)
│     password_hash  VARCHAR(255) NN    │     │     channel_code    VARCHAR(20) NN   │
│     changed_at     DATETIME NN        │     │     ip_address      VARCHAR(50)      │
│     created_at     DATETIME NN        │     │     user_agent      VARCHAR(512)     │
│     updated_at     DATETIME NN        │     │     device_type     VARCHAR(50)      │
├───────────────────────────────────────┤     │     browser         VARCHAR(100)     │
│ tb_user.user_id ──1:N── user_id       │     │     os              VARCHAR(100)     │
└───────────────────────────────────────┘     │     location        VARCHAR(200)     │
                                              │     is_success      BOOLEAN NN       │
                                              │     failure_reason  VARCHAR(200)     │
                                              │     created_at      DATETIME NN      │
                                              ├──────────────────────────────────────┤
                                              │ tb_user.user_id ──1:N── user_id      │
                                              └──────────────────────────────────────┘


┌──────────────────────────────────────────┐     ┌──────────────────────────────────────┐
│ tb_email_verification (EmailVerification) │     │ tb_phone_verification (PhoneVerif.)  │
├──────────────────────────────────────────┤     ├──────────────────────────────────────┤
│ PK  verification_id   BIGINT AI          │     │ PK  verification_id  BIGINT AI       │
│     token_id          VARCHAR(36) UQ     │     │     token_id         VARCHAR(36) UQ  │
│     email_lower_enc   VARCHAR(512) NN    │     │     phone_enc        VARCHAR(512) NN │
│     verification_code VARCHAR(10) NN     │     │     phone_lower_enc  VARCHAR(512) NN │
│     verification_type VARCHAR(20) NN     │     │     verification_code VARCHAR(10) NN │
│     is_verified       BOOLEAN NN         │     │     is_verified      BOOLEAN NN      │
│     expires_at        DATETIME NN        │     │     expires_at       DATETIME NN     │
│     verified_at       DATETIME           │     │     verified_at      DATETIME        │
│     created_at        DATETIME NN        │     │     created_at       DATETIME NN     │
├──────────────────────────────────────────┤     ├──────────────────────────────────────┤
│ IDX (email_lower_enc, verification_type) │     │ IDX (phone_lower_enc)                │
│ * User와 직접 FK 관계 없음 (이메일로 연결) │     │ * User와 직접 FK 관계 없음            │
└──────────────────────────────────────────┘     └──────────────────────────────────────┘


┌──────────────────────────────────────┐
│       tb_audit_log (AuditLog)        │
├──────────────────────────────────────┤
│ PK  audit_id       BIGINT AI         │
│     user_id        BIGINT            │ ← IDX (FK 없음, nullable)
│     action         VARCHAR(50) NN    │ ← IDX
│     target         VARCHAR(100)      │
│     detail         VARCHAR(500)      │
│     ip_address     VARCHAR(50)       │
│     user_agent     VARCHAR(512)      │
│     is_success     BOOLEAN NN        │
│     created_at     DATETIME NN       │ ← IDX
└──────────────────────────────────────┘
```

## 관계 요약

| 관계 | 유형 | 설명 |
|------|------|------|
| `tb_user` ↔ `tb_user_sign_in_info` | **1:0..1** | 이메일 가입 사용자만 생성됨 |
| `tb_user` ↔ `tb_user_two_factor` | **1:0..1** | 2FA 설정 시 생성됨 |
| `tb_user` → `tb_user_channel` | **1:N** | 한 사용자가 여러 채널(EMAIL, GOOGLE 등) 보유 가능 |
| `tb_user` → `tb_password_history` | **1:N** | 비밀번호 재사용 방지용 이력 |
| `tb_user` → `tb_login_history` | **1:N** | 로그인 시도 이력 (FK 없이 user_id 컬럼으로 참조) |
| `tb_user` → `tb_audit_log` | **1:N** | 감사 로그 (FK 없이 user_id 컬럼으로 참조, nullable) |
| `tb_email_verification` | **독립** | User와 직접 FK 없음, 이메일 암호화 값으로 논리적 연결 |
| `tb_phone_verification` | **독립** | User와 직접 FK 없음, 전화번호 암호화 값으로 논리적 연결 |

## 설계 특징

- **암호화**: 이메일, 전화번호, 닉네임 등 개인정보는 AES-256으로 암호화 저장 (`*_enc` 컬럼)
- **검색용 컬럼**: 검색이 필요한 필드는 소문자 변환 후 암호화한 `*_lower_enc` 컬럼을 별도 유지
- **닉네임 유일성**: `nickname_lower_enc`에 UNIQUE INDEX 적용 — NULL은 제약 미적용(기존 사용자 호환), 닉네임 설정/변경 시 중복 409 반환
- **소셜 로그인**: `tb_user` + `tb_user_channel`만 사용 (SignInInfo 없음)
- **이메일 로그인**: `tb_user` + `tb_user_sign_in_info` + `tb_user_channel` 모두 사용
- **계정 연동**: 한 사용자가 여러 소셜 계정을 연결 가능 (채널별 unique key)
- **감사/이력**: `tb_login_history`, `tb_audit_log`는 FK 없이 느슨한 참조로 성능 최적화
