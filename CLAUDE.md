# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Backend (Spring Boot)
```bash
./gradlew build                                    # Build project
./gradlew build -x test                            # Build without tests
./gradlew test                                     # Run all tests
./gradlew test --tests "com.jay.auth.SomeTest"     # Run single test class
./gradlew bootRun                                  # Run application (requires MySQL, Redis)
./gradlew clean build                              # Clean and rebuild
```

### Frontend (React + Vite)
```bash
cd frontend
npm install          # Install dependencies
npm run dev          # Start dev server (port 3000, proxies /api → localhost:8080)
npm run build        # Production build
npm run lint         # Run ESLint
```

## Architecture

Spring Boot 3.5.10 authentication service with email and social login (Google, Kakao, Naver), plus a React 19 frontend.

**Tech Stack**: Java 17, Spring Security 6.x, MySQL (JPA/Hibernate), Redis, JWT (jjwt 0.12.6), BCrypt, AES-256, TOTP 2FA (dev.samstevens.totp), Thymeleaf (email templates), Vite, React Router, Axios.

### Security Filter Chain Order
1. `SecurityHeadersFilter` — XSS/clickjacking response headers
2. `RateLimitFilter` — IP-based rate limiting via Redis
3. `RequestLoggingFilter` — logs `METHOD URI - STATUS (Xms) [IP: ...]`
4. `JwtAuthenticationFilter` — validates Bearer token, checks Redis blacklist

CSRF disabled. Stateless sessions. Public endpoints: `/api/v1/auth/**`, `/api/v1/phone/**`, `/api/v1/health`, Swagger paths, OAuth2 paths. Admin endpoints (`/api/v1/admin/**`) require `ROLE_ADMIN`.

### JWT Structure
Claims: `userId`, `userUuid`, `channelCode`, `role`, `tokenType` (ACCESS/REFRESH), `jti`. Signed with HS256. Access token: 30 min. Refresh token: 14 days. Revoked tokens stored in Redis blacklist by `jti`.

### OAuth2 Flow
Google/Kakao use OIDC (`CustomOidcUserService`), Naver uses OAuth2 (`CustomOAuth2UserService`). `CustomOAuth2AuthorizationRequestResolver` overrides the `state` parameter with `link_state` for account linking flow. Success handler redirects to `${app.oauth2.redirect-uri}?accessToken=...&refreshToken=...&expiresIn=...`.

### Redis Key Namespaces

| Key Pattern | TTL | Purpose |
|---|---|---|
| `refresh:{userId}:{tokenId}` | 14 days | Refresh token storage |
| `session:{userId}:{tokenId}` | 14 days | Session info (Hash: device, browser, OS, IP) |
| `blacklist:{tokenId}` | Remaining token lifetime | Revoked access tokens |
| `trusted:{userId}:{deviceId}` | 30 days | Trusted devices (Hash) |
| `oauth2:link:{state}` | 10 min | OAuth2 account linking state |
| `login:email:{email}` / `login:ip:{ip}` | 15 min | Brute-force rate limit (5/email, 20/IP) |
| `rate:auth:{ip}` | 1 min | Auth endpoint rate limit (10 req/min) |
| `rate:user:{ip}` | 1 min | User endpoint rate limit (200 req/min) |
| `rate:api:{ip}` | 1 min | General rate limit (60 req/min) |

Spring `@Cacheable` caches: `userProfile` (5 min), `securityDashboard` (3 min), `geoip` (24 hr).

### Data Model
- `User` (tb_user): Base user info, 1:0..1 with SignInInfo, 1:N with Channel
- `UserSignInInfo` (tb_user_sign_in_info): Email login credentials (email users only)
- `UserChannel` (tb_user_channel): Login channels (EMAIL, GOOGLE, KAKAO, NAVER)
- `UserTwoFactor` (tb_user_two_factor): TOTP 2FA secret and backup codes
- `LoginHistory` (tb_login_history): Login attempts with device/location (no FK, references user_id)
- `PasswordHistory` (tb_password_history): Password reuse prevention
- `EmailVerification` (tb_email_verification): Email verification tokens (no FK, linked by encrypted email)
- `PhoneVerification` (tb_phone_verification): Phone verification tokens (no FK)
- `AuditLog` (tb_audit_log): Audit trail (no FK, nullable user_id)
- `SupportPost` (tb_support_post): Customer support posts with category/status/view count (no FK, references user_id)
- `SupportComment` (tb_support_comment): Comments on support posts with AI-generated flag (no FK)

### Key Design Decisions
- Email fields stored as: `*_enc` (original encrypted) + `*_lower_enc` (lowercase encrypted for search)
- Social login: only `tb_user` + `tb_user_channel` (no SignInInfo)
- Email login: `tb_user` + `tb_user_sign_in_info` + `tb_user_channel`
- One user can link multiple social accounts
- OAuth2 account linking uses cookie (`oauth2_link_state`) to pass state through OAuth2 flow
- Remember Me: localStorage (persistent) vs sessionStorage (session-only)
- `LoginHistory`, `AuditLog`, `SupportPost`, `SupportComment` use loose references (no FK) for performance
- GeoIP via `http://ip-api.com/json/{ip}`, cached 24hr in Redis
- Support posts have status workflow: OPEN → IN_PROGRESS → RESOLVED → CLOSED
- Support post categories: ACCOUNT, LOGIN, SECURITY, OTHER
- AI auto-reply is generated asynchronously when a support post is created

### API Response Format
Error responses use `ApiResponse<T>` wrapper with `@JsonInclude(NON_NULL)`:
```json
{ "success": false, "error": { "code": "ERROR_CODE", "message": "..." }, "timestamp": "..." }
```
**Successful responses return the DTO directly** (not wrapped in ApiResponse). Global exception handler (`GlobalExceptionHandler`) wraps all errors. `BusinessException` is the base exception with `errorCode` + `httpStatus`. `RateLimitException` adds `Retry-After` header.

### API Endpoints

| Controller | Base Path | Auth |
|---|---|---|
| `AuthController` | `/api/v1/auth` | Public |
| `EmailVerificationController` | `/api/v1/auth/email` | Public |
| `PhoneVerificationController` | `/api/v1/phone` | Public |
| `UserController` | `/api/v1/users` | Authenticated |
| `TwoFactorController` | `/api/v1/2fa` | Authenticated |
| `OAuth2LinkController` | `/api/v1/oauth2/link` | Authenticated |
| `SupportController` | `/api/v1/support` | Authenticated |
| `AdminController` | `/api/v1/admin` | ROLE_ADMIN |

### Async & Scheduling
- `@Async("asyncExecutor")`: `AuditLogService`, `LoginHistoryService`, `SecurityNotificationService`, `SupportAiReplyService`
- `AccountCleanupScheduler` (3AM daily): PII removal for users in `PENDING_DELETE` >30 days, dormant conversion after 90 days no login, login history cleanup >180 days
- `VerificationCleanupScheduler` (hourly): Deletes expired email/phone verification rows

### External Services
- **SMS**: `CoolSmsSender` (real) / `LogSmsSender` (dev stub) — switched via `${SMS_PROVIDER:log}`
- **Email**: `SmtpEmailSender` (real) / `EmailSenderImpl` (dev stub) — switched via `${EMAIL_PROVIDER:log}`. Templates: `verification-code.html`, `security-alert.html` (Thymeleaf)
- **GeoIP**: `ip-api.com` free tier
- **AI Reply**: `ClaudeAiReplyService` (real) / `LogAiReplyService` (dev stub) — switched via `${AI_PROVIDER:log}`. Uses Anthropic Messages API (RestTemplate). Default model: `claude-sonnet-4-5-20250929`

### Frontend Proxy (Vite)
Dev server on port 3000 proxies `/api`, `/oauth2/authorization`, `/login/oauth2` to `http://localhost:8080`. Axios client uses `http://localhost:8080/api/v1` as base URL (absolute, not proxied). Token auto-refresh interceptor on 401.

## Testing

- **Service tests**: `@ExtendWith(MockitoExtension.class)` — pure unit tests with `@InjectMocks`/`@Mock`, no Spring context
- **Controller tests**: `@WebMvcTest` with `@AutoConfigureMockMvc(addFilters = false)` — excludes security/rate-limit filters via `@ComponentScan.Filter`, uses `@MockitoBean` (Spring Boot 3.4+ API)
- **Integration tests**: `@SpringBootTest @ActiveProfiles("test") @Import(TestConfig.class)`
- **TestConfig**: Provides `@Primary` mock beans for `RedisConnectionFactory` and `RedisTemplate` so tests don't need Redis
- **Test DB**: H2 in-memory (via `application-test.yml`)
- **JaCoCo**: Coverage excludes `dto/**`, `domain/enums/**`, `config/**`, `AuthApplication.class`
- **Conventions**: `@Nested` + `@DisplayName` (Korean) for grouping. Private `setField()` reflective helper for setting entity IDs in tests.

## Environment Variables

```bash
# Required
DB_USERNAME, DB_PASSWORD           # MySQL credentials
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
JWT_SECRET                         # Min 32 chars for HS256
ENCRYPTION_SECRET_KEY              # AES-256 key (32 chars)

# Optional (with defaults)
CORS_ALLOWED_ORIGINS               # default: http://localhost:3000
SMS_PROVIDER                       # log (default) or coolsms
EMAIL_PROVIDER                     # log (default) or smtp
OAUTH2_REDIRECT_URI                # default: http://localhost:3000/oauth2/callback
DB_HOST, DB_PORT                   # used in dev/prod profiles
MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD
COOLSMS_API_KEY, COOLSMS_API_SECRET, COOLSMS_SENDER
AI_PROVIDER                        # log (default) or claude
CLAUDE_API_KEY                     # Anthropic API key (required when AI_PROVIDER=claude)
CLAUDE_MODEL                       # default: claude-sonnet-4-5-20250929
CLAUDE_MAX_TOKENS                  # default: 1024
```

## Profiles

| Setting | `local` | `dev` | `prod` |
|---|---|---|---|
| DB DDL | `update` | `validate` | `none` |
| Show SQL | yes | no | no |
| HikariCP pool | 5 | 10 | 20 |
| Swagger | enabled | enabled | disabled |
| Log level | DEBUG | INFO | WARN |
| Mail | localhost:1025 (MailHog) | SMTP+STARTTLS | SMTP+STARTTLS |
| Logging output | console | console | rolling files (text + JSON, 50MB/30days/1GB cap) |
