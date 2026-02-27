# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소의 코드를 다룰 때 참고하는 가이드입니다.

## 빌드 명령어

### 백엔드 (Spring Boot)
```bash
./gradlew build                                    # 프로젝트 빌드
./gradlew build -x test                            # 테스트 제외 빌드
./gradlew test                                     # 전체 테스트 실행
./gradlew test --tests "com.jay.auth.SomeTest"     # 단일 테스트 클래스 실행
./gradlew bootRun                                  # 애플리케이션 실행 (MySQL, Redis 필요)
./gradlew clean build                              # 클린 후 재빌드
```

### 프론트엔드 (React + Vite)
```bash
cd frontend
npm install          # 의존성 설치
npm run dev          # 개발 서버 시작 (포트 3000, /api → localhost:8080 프록시)
npm run build        # 프로덕션 빌드
npm run lint         # ESLint 실행
```

## 아키텍처

이메일 및 소셜 로그인(Google, Kakao, Naver)을 지원하는 Spring Boot 3.5.10 인증 서비스와 React 19 프론트엔드.

**기술 스택**: Java 17, Spring Security 6.x, MySQL (JPA/Hibernate), Redis, JWT (jjwt 0.12.6), BCrypt, AES-256, TOTP 2FA (dev.samstevens.totp), WebAuthn/Passkey (webauthn4j-core 0.28.4), Micrometer + Prometheus + Grafana (모니터링), Thymeleaf (이메일 템플릿), Vite, React Router, Axios.

### 보안 필터 체인 순서
1. `SecurityHeadersFilter` — XSS/클릭재킹 방지 응답 헤더
2. `RateLimitFilter` — Redis 기반 IP별 요청 제한
3. `RequestLoggingFilter` — `METHOD URI - STATUS (Xms) [IP: ...]` 형식 로깅
4. `JwtAuthenticationFilter` — Bearer 토큰 검증, Redis 블랙리스트 확인

CSRF 비활성화. 무상태 세션. 공개 엔드포인트: `/api/v1/auth/**`, `/api/v1/phone/**`, `/api/v1/health`, Swagger 경로, OAuth2 경로. 관리자 엔드포인트(`/api/v1/admin/**`)는 `ROLE_ADMIN` 필요.

### JWT 구조
클레임: `userId`, `userUuid`, `channelCode`, `role`, `tokenType` (ACCESS/REFRESH), `jti`. HS256 서명. 액세스 토큰: 30분. 리프레시 토큰: 14일. 폐기된 토큰은 `jti` 기준으로 Redis 블랙리스트에 저장.

### OAuth2 플로우
Google/Kakao는 OIDC(`CustomOidcUserService`), Naver는 OAuth2(`CustomOAuth2UserService`) 사용. `CustomOAuth2AuthorizationRequestResolver`가 계정 연동 플로우에서 `state` 파라미터를 `link_state`로 오버라이드. 성공 핸들러는 `${app.oauth2.redirect-uri}?accessToken=...&refreshToken=...&expiresIn=...`로 리다이렉트.

### Redis 키 네임스페이스

| 키 패턴 | TTL | 용도 |
|---|---|---|
| `refresh:{userId}:{tokenId}` | 14일 | 리프레시 토큰 저장 |
| `session:{userId}:{tokenId}` | 14일 | 세션 정보 (Hash: 기기, 브라우저, OS, IP) |
| `blacklist:{tokenId}` | 토큰 잔여 수명 | 폐기된 액세스 토큰 |
| `trusted:{userId}:{deviceId}` | 30일 | 신뢰 기기 (Hash) |
| `oauth2:link:{state}` | 10분 | OAuth2 계정 연동 상태 |
| `login:email:{email}` / `login:ip:{ip}` | 15분 | 무차별 대입 방지 (이메일 5회/IP 20회) |
| `rate:auth:{ip}` | 1분 | 인증 엔드포인트 제한 (10회/분) |
| `rate:user:{ip}` | 1분 | 사용자 엔드포인트 제한 (200회/분) |
| `rate:api:{ip}` | 1분 | 일반 API 제한 (60회/분) |
| `passkey:challenge:register:{userId}` | 5분 | 패스키 등록 챌린지 |
| `passkey:challenge:login:{sessionId}` | 5분 | 패스키 인증 챌린지 |

Spring `@Cacheable` 캐시: `userProfile` (5분), `securityDashboard` (3분), `geoip` (24시간).

### 데이터 모델
- `User` (tb_user): 기본 사용자 정보, SignInInfo와 1:0..1, Channel과 1:N 관계
- `UserSignInInfo` (tb_user_sign_in_info): 이메일 로그인 자격증명 (이메일 사용자만)
- `UserChannel` (tb_user_channel): 로그인 채널 (EMAIL, GOOGLE, KAKAO, NAVER)
- `UserTwoFactor` (tb_user_two_factor): TOTP 2FA 시크릿 및 백업 코드
- `UserPasskey` (tb_user_passkey): WebAuthn 패스키 자격증명 (credentialId, publicKey, signCount, transports, deviceName)
- `LoginHistory` (tb_login_history): 로그인 시도 이력 (기기/위치 포함, FK 없음, user_id 참조)
- `PasswordHistory` (tb_password_history): 비밀번호 재사용 방지
- `EmailVerification` (tb_email_verification): 이메일 인증 토큰 (FK 없음, 암호화된 이메일로 연결)
- `PhoneVerification` (tb_phone_verification): 휴대폰 인증 토큰 (FK 없음)
- `AuditLog` (tb_audit_log): 감사 추적 (FK 없음, nullable user_id)
- `SupportPost` (tb_support_post): 고객센터 게시글 (카테고리/상태/조회수, FK 없음, user_id 참조)
- `SupportComment` (tb_support_comment): 고객센터 댓글 (AI 생성 여부 플래그, FK 없음)

### 주요 설계 결정
- 이메일 필드 저장 방식: `*_enc` (원본 암호화) + `*_lower_enc` (소문자 암호화, 검색용)
- 닉네임 필드 저장 방식: `nickname_enc` (원본 암호화) + `nickname_lower_enc` (소문자 암호화, UNIQUE INDEX로 중복 방지)
- 닉네임 중복 방지: DB UNIQUE INDEX + 서비스 레이어 사전 검증 이중 구조. 본인 닉네임과 동일하거나 타인이 사용 중인 경우 `DUPLICATE_NICKNAME` (409). 회원가입 시 `NicknameGenerator.generateUnique()`로 충돌 시 UUID 8자 suffix 조합
- 소셜 로그인: `tb_user` + `tb_user_channel`만 사용 (SignInInfo 없음)
- 이메일 로그인: `tb_user` + `tb_user_sign_in_info` + `tb_user_channel`
- 하나의 사용자가 여러 소셜 계정 연동 가능
- OAuth2 계정 연동은 쿠키(`oauth2_link_state`)로 OAuth2 플로우를 통해 상태 전달
- 로그인 유지: localStorage (영구) vs sessionStorage (세션 한정)
- `LoginHistory`, `AuditLog`, `SupportPost`, `SupportComment`는 성능을 위해 느슨한 참조 사용 (FK 없음)
- GeoIP: `http://ip-api.com/json/{ip}` 사용, Redis에 24시간 캐시
- 고객센터 게시글 상태 워크플로우: OPEN → IN_PROGRESS → RESOLVED → CLOSED
- 고객센터 게시글 카테고리: ACCOUNT, LOGIN, SECURITY, OTHER
- AI 자동 답변은 고객센터 게시글 생성 시 비동기로 생성
- 패스키 (WebAuthn): webauthn4j-core의 `WebAuthnManager.createNonStrictWebAuthnManager()` 사용. 챌린지는 Redis에 5분 TTL로 저장. 사용자당 최대 10개 패스키. 로그인 시 discoverable credentials 사용 (allowCredentials 없음). 보안 대시보드에 패스키 요소 포함 (15점)

### API 응답 형식
에러 응답은 `ApiResponse<T>` 래퍼와 `@JsonInclude(NON_NULL)` 사용:
```json
{ "success": false, "error": { "code": "ERROR_CODE", "message": "..." }, "timestamp": "..." }
```
**성공 응답은 DTO를 직접 반환** (ApiResponse로 감싸지 않음). 전역 예외 핸들러(`GlobalExceptionHandler`)가 모든 에러를 래핑. `BusinessException`이 기본 예외 (`errorCode` + `httpStatus`). `RateLimitException`은 `Retry-After` 헤더 추가.

### API 엔드포인트

| 컨트롤러 | 기본 경로 | 인증 |
|---|---|---|
| `AuthController` | `/api/v1/auth` | 공개 |
| `EmailVerificationController` | `/api/v1/auth/email` | 공개 |
| `PhoneVerificationController` | `/api/v1/phone` | 공개 |
| `UserController` | `/api/v1/users` | 인증 필요 |
| `TwoFactorController` | `/api/v1/2fa` | 인증 필요 |
| `OAuth2LinkController` | `/api/v1/oauth2/link` | 인증 필요 |
| `SupportController` | `/api/v1/support` | 인증 필요 |
| `PasskeyController` | `/api/v1/passkey` (관리), `/api/v1/auth/passkey` (로그인) | 혼합 |
| `AdminController` | `/api/v1/admin` | ROLE_ADMIN |

### 비동기 및 스케줄링
- `@Async("asyncExecutor")`: `AuditLogService`, `LoginHistoryService`, `SecurityNotificationService`, `SupportAiReplyService`
- `AccountCleanupScheduler` (매일 새벽 3시): `PENDING_DELETE` 30일 초과 사용자 개인정보 삭제, 90일 미로그인 시 휴면 전환, 180일 초과 로그인 이력 정리
- `VerificationCleanupScheduler` (매시간): 만료된 이메일/휴대폰 인증 행 삭제

### 모니터링
- **커스텀 메트릭**: `AuthMetrics` (`service/metrics/AuthMetrics.java`) — 로그인, 회원가입, JWT 발급, 토큰 갱신, 로그아웃, 이메일 인증 카운터 중앙화
- **RateLimitFilter**: `MeterRegistry` 직접 주입 → `rate_limit_exceeded_total{type}` 기록
- **RequestLoggingFilter**: `MeterRegistry` 직접 주입 → `http_server_requests_custom{method,uri,status}` 타이머 기록
- **노출 엔드포인트**: `/actuator/prometheus` (Prometheus 스크래핑 대상)
- **Docker**: `monitoring` 프로필로 Prometheus(9090) + Grafana(3001) 실행. 설정: `monitoring/prometheus.yml`, `monitoring/grafana/`

### 외부 서비스
- **SMS**: `CoolSmsSender` (실제) / `LogSmsSender` (개발용 스텁) — `${SMS_PROVIDER:log}`로 전환
- **이메일**: `SmtpEmailSender` (실제) / `EmailSenderImpl` (개발용 스텁) — `${EMAIL_PROVIDER:log}`로 전환. 템플릿: `verification-code.html`, `security-alert.html` (Thymeleaf)
- **GeoIP**: `ip-api.com` 무료 티어
- **AI 답변**: `ClaudeAiReplyService` (실제) / `LogAiReplyService` (개발용 스텁) — `${AI_PROVIDER:log}`로 전환. Anthropic Messages API (RestTemplate) 사용. 기본 모델: `claude-sonnet-4-5-20250929`

### 프론트엔드 프록시 (Vite)
개발 서버(포트 3000)가 `/api`, `/oauth2/authorization`, `/login/oauth2`를 `http://localhost:8080`으로 프록시. Axios 클라이언트는 `http://localhost:8080/api/v1`을 기본 URL로 사용 (절대 경로, 프록시 미사용). 401 응답 시 토큰 자동 갱신 인터셉터.

## 테스트

- **서비스 테스트**: `@ExtendWith(MockitoExtension.class)` — `@InjectMocks`/`@Mock` 사용 순수 단위 테스트, Spring 컨텍스트 없음
- **컨트롤러 테스트**: `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` — `@ComponentScan.Filter`로 보안/요청제한 필터 제외, `@MockitoBean` 사용 (Spring Boot 3.4+ API)
- **통합 테스트**: `@SpringBootTest @ActiveProfiles("test") @Import(TestConfig.class)`
- **TestConfig**: `RedisConnectionFactory`, `RedisTemplate`, `StringRedisTemplate`의 `@Primary` 모의 빈 제공 (테스트 시 Redis 불필요)
- **테스트 DB**: H2 인메모리 (`application-test.yml`)
- **JaCoCo**: 커버리지 제외 대상 — `dto/**`, `domain/enums/**`, `config/**`, `AuthApplication.class`
- **컨벤션**: `@Nested` + `@DisplayName` (한국어) 그룹핑. 테스트에서 엔티티 ID 설정용 `setField()` 리플렉션 헬퍼 사용.

## 환경 변수

```bash
# 필수
DB_USERNAME, DB_PASSWORD           # MySQL 자격증명
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
JWT_SECRET                         # HS256용 최소 32자
ENCRYPTION_SECRET_KEY              # AES-256 키 (32자)

# 선택 (기본값 있음)
CORS_ALLOWED_ORIGINS               # 기본값: http://localhost:3000
SMS_PROVIDER                       # log (기본) 또는 coolsms
EMAIL_PROVIDER                     # log (기본) 또는 smtp
OAUTH2_REDIRECT_URI                # 기본값: http://localhost:3000/oauth2/callback
DB_HOST, DB_PORT                   # dev/prod 프로필에서 사용
MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD
COOLSMS_API_KEY, COOLSMS_API_SECRET, COOLSMS_SENDER
WEBAUTHN_RP_ID                     # 기본값: localhost
WEBAUTHN_RP_NAME                   # 기본값: Authly
WEBAUTHN_ORIGIN                    # 기본값: http://localhost:3000
AI_PROVIDER                        # log (기본) 또는 claude
CLAUDE_API_KEY                     # Anthropic API 키 (AI_PROVIDER=claude일 때 필수)
CLAUDE_MODEL                       # 기본값: claude-sonnet-4-5-20250929
CLAUDE_MAX_TOKENS                  # 기본값: 1024
```

## 프로필

| 설정 | `local` | `dev` | `prod` |
|---|---|---|---|
| DB DDL | `update` | `validate` | `none` |
| SQL 출력 | 예 | 아니오 | 아니오 |
| HikariCP 풀 | 5 | 10 | 20 |
| Swagger | 활성화 | 활성화 | 비활성화 |
| 로그 레벨 | DEBUG | INFO | WARN |
| 메일 | localhost:1025 (MailHog) | SMTP+STARTTLS | SMTP+STARTTLS |
| 로깅 출력 | 콘솔 | 콘솔 | 롤링 파일 (텍스트 + JSON, 50MB/30일/1GB 제한) |

## 모니터링 관련 참고

메트릭 전체 목록, AuthMetrics API, Grafana 대시보드 구성, 새 메트릭 추가 방법은 `docs/metrics.md` 참고.
Docker 프로필 및 설정 파일 위치는 `docs/infrastructure.md` 참고.
