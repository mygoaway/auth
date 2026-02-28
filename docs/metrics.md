# Auth Service 메트릭 가이드

## 개요

Micrometer + Prometheus + Grafana 스택으로 인증 서비스의 비즈니스 메트릭을 수집·시각화합니다.

- **수집**: Micrometer (`MeterRegistry`)
- **저장**: Prometheus — `http://localhost:9090`
- **시각화**: Grafana — `http://localhost:3001` (admin / admin)
- **노출 엔드포인트**: `GET /actuator/prometheus`

---

## 메트릭 목록

### Counter 메트릭

#### 1. `auth_login_attempts_total`

로그인 시도 수 (성공/실패).

| 태그 | 값 |
|---|---|
| `channel` | `EMAIL` \| `GOOGLE` \| `KAKAO` \| `NAVER` |
| `result` | `success` \| `fail` |

**호출 위치**

| 위치 | 메서드 | 조건 |
|---|---|---|
| `AuthService` | `recordLoginSuccess("EMAIL")` | 이메일 로그인 성공 |
| `AuthService` | `recordLoginFailure("EMAIL")` | 이메일 로그인 실패 |
| `OAuth2AuthenticationSuccessHandler` | `recordLoginSuccess(channel)` | 소셜 로그인 성공 |

---

#### 2. `auth_signup_total`

회원가입 수.

| 태그 | 값 |
|---|---|
| `channel` | `EMAIL` (현재 이메일 가입만 집계) |

**호출 위치**: `AuthService.signUp()` 완료 시

---

#### 3. `auth_token_issued_total`

JWT 토큰 발급 수.

| 태그 | 값 |
|---|---|
| `type` | `ACCESS` \| `REFRESH` |
| `channel` | `EMAIL` \| `GOOGLE` \| `KAKAO` \| `NAVER` |

> ACCESS와 REFRESH는 항상 쌍으로 발급됩니다. 대시보드에서는 `type="ACCESS"` 필터로 채널별 단일 라인만 표시합니다.

**호출 위치**

| 위치 | 메서드 | 조건 |
|---|---|---|
| `TokenService.issueTokens()` | `recordTokenIssued(type, channel)` | 이메일 로그인 토큰 발급 |
| `TokenService.issueTokensWithSession()` | `recordTokenIssued(type, channel)` | 소셜 로그인 / 세션 포함 토큰 발급 |

---

#### 4. `auth_token_refresh_total`

토큰 갱신 결과.

| 태그 | 값 |
|---|---|
| `result` | `success` \| `fail` |

**호출 위치**: `TokenService.refreshTokens()` — 검증 실패(3가지 경우) 시 `fail`, 갱신 완료 시 `success`

---

#### 5. `auth_logout_total`

로그아웃 수.

| 태그 | 값 |
|---|---|
| `type` | `single` (현재 기기) \| `all` (전체 기기) |

**호출 위치**: `TokenService.logout()`, `TokenService.logoutAll()`

---

#### 6. `auth_email_verification_total`

이메일 인증 코드 발송·성공·실패 수.

| 태그 | 값 |
|---|---|
| `type` | `SIGNUP` \| `PASSWORD_RESET` \| `EMAIL_CHANGE` |
| `action` | `sent` \| `success` \| `failure` |

> **주의**: `sent`/`success`/`failure` 세 action은 동일한 태그 키 집합을 가져야 합니다. 실패 원인(reason)은 태그 불일치를 피하기 위해 별도 메트릭 `auth_email_verification_failure_total`에 기록합니다.

**호출 위치**

| 메서드 | action | 조건 |
|---|---|---|
| `EmailVerificationService.sendVerificationCode()` | `sent` | 인증 코드 발송 완료 |
| `EmailVerificationService.verifyCode()` | `success` | 인증 성공 |
| `EmailVerificationService.verifyCode()` | `failure` | 인증 실패 (모든 사유 공통) |

---

#### 7. `auth_email_verification_failure_total`

이메일 인증 실패 원인별 상세 수.

| 태그 | 값 |
|---|---|
| `type` | `SIGNUP` \| `PASSWORD_RESET` \| `EMAIL_CHANGE` |
| `reason` | `not_found` \| `expired` \| `mismatch` |

| reason | 의미 |
|---|---|
| `not_found` | 해당 이메일·타입으로 진행 중인 인증 요청 없음 |
| `expired` | 인증 코드 만료 (기본 10분) |
| `mismatch` | 입력한 코드가 발급된 코드와 불일치 |

---

#### 8. `rate_limit_exceeded_total`

Rate Limit 초과 수. `AuthMetrics`가 아닌 `RateLimitFilter`에서 직접 `MeterRegistry`로 기록합니다.

| 태그 | 값 |
|---|---|
| `type` | `auth` \| `user` \| `api` |

| type | 경로 | 제한 |
|---|---|---|
| `auth` | `/api/v1/auth/**` | 10회/분 |
| `user` | `/api/v1/users/**` | 200회/분 |
| `api` | 기타 | 60회/분 |

---

### Timer 메트릭

#### 9. `auth_operation_duration_seconds`

인증 서비스 주요 작업의 실행 시간. Prometheus에서 `_count`, `_sum`, `_max`, `_bucket` suffix로 노출됩니다.

| 태그 | 값 |
|---|---|
| `operation` | 아래 표 참고 |
| `channel` | `EMAIL` \| `GOOGLE` \| `KAKAO` \| `NAVER` \| `UNKNOWN` |
| `success` | `true` \| `false` |

**operation 값 목록**

| operation | 설명 | 측정 방식 |
|---|---|---|
| `email_signup` | 이메일 회원가입 | AOP `@AuthTimed` |
| `email_authenticate` | 이메일 인증 (로그인) | AOP `@AuthTimed` |
| `password_change` | 비밀번호 변경 | AOP `@AuthTimed` |
| `password_reset` | 비밀번호 재설정 | AOP `@AuthTimed` |
| `recovery_accounts_lookup` | 복구 이메일로 계정 조회 | AOP `@AuthTimed` |
| `totp_verify` | TOTP 코드 검증 | AOP `@AuthTimed` |
| `passkey_register` | 패스키 등록 검증 | AOP `@AuthTimed` |
| `passkey_authenticate` | 패스키 인증 검증 | AOP `@AuthTimed` |
| `oauth2_login` | OAuth2/OIDC 사용자 처리 (IDP별) | AOP `@AuthTimed` |
| `oauth2_link` | OAuth2 계정 연동 처리 | AOP `@AuthTimed` |
| `oauth2_handler` | OAuth2 성공 핸들러 전체 흐름 | `Timer.Sample` 직접 측정 |

**대표 PromQL**

```promql
# 작업별 평균 실행시간 (5분 기준)
rate(auth_operation_duration_seconds_sum{application="auth-service"}[5m])
/ rate(auth_operation_duration_seconds_count{application="auth-service"}[5m])

# IDP별 로그인 p95 응답시간
histogram_quantile(0.95,
  rate(auth_operation_duration_seconds_bucket{
    application="auth-service",
    operation=~"oauth2_login|oauth2_handler|email_authenticate"
  }[5m])
)

# 작업 성공률 (%)
100 * sum by (operation) (
  rate(auth_operation_duration_seconds_count{application="auth-service", success="true"}[5m])
) / sum by (operation) (
  rate(auth_operation_duration_seconds_count{application="auth-service"}[5m])
)
```

---

#### 10. `http_server_requests_custom`

HTTP 요청 처리 시간 타이머. `RequestLoggingFilter`에서 직접 `MeterRegistry`로 기록합니다.

| 태그 | 값 |
|---|---|
| `method` | `GET` \| `POST` \| `PUT` \| `DELETE` 등 |
| `uri` | 정규화된 URI 패턴 |
| `status` | HTTP 상태 코드 (`200`, `401`, `429` 등) |

---

### Gauge 메트릭

Gauge는 Counter와 달리 값이 증가하거나 감소할 수 있는 지표에 사용합니다. **위치**: `AuthGaugeMetrics.java`

#### 11. `auth_active_sessions`

현재 Redis에 존재하는 활성 세션 수.

| 업데이트 방식 | 시점 |
|---|---|
| `+1` (실시간) | `TokenService.issueTokens()` / `issueTokensWithSession()` — 로그인·토큰 발급 |
| `-1` (실시간) | `TokenService.logout()` / `logoutAll()` — 로그아웃 |
| 보정 (60초 주기) | Redis `session:*` 키 수를 실값으로 덮어씀 (드리프트 방지) |

> 앱 재시작 직후 또는 Redis에 직접 개입한 경우 AtomicLong 값이 실제와 다를 수 있으나, 60초 내 자동 보정됩니다.

---

#### 12. `auth_registered_passkeys`

DB에 등록된 패스키 총 수.

| 업데이트 방식 | 시점 |
|---|---|
| `+1` (실시간) | `PasskeyService.verifyRegistration()` — 패스키 등록 성공 |
| `-1` (실시간) | `PasskeyService.deletePasskey()` — 패스키 삭제 |
| 보정 (60초 주기) | `UserPasskeyRepository.count()` 실값으로 덮어씀 |

---

#### 13. `auth_locked_accounts`

현재 잠금 상태(`lockedUntil > now`)인 계정 수.

| 업데이트 방식 | 주기 |
|---|---|
| DB 집계 쿼리 | 2분마다 갱신 |

> 잠금은 로그인 5회 실패 시 설정되고 30분 후 자동 만료됩니다. 브루트포스 공격 감지에 활용할 수 있습니다.

---

#### 14. `auth_2fa_enabled_users`

2FA(TOTP)를 활성화한 사용자 수.

| 업데이트 방식 | 주기 |
|---|---|
| DB 집계 쿼리 | 5분마다 갱신 |

---

## AuthMetrics 클래스

**위치**: `src/main/java/com/jay/auth/service/metrics/AuthMetrics.java`

모든 비즈니스 Counter/Timer 기록을 중앙화한 Spring `@Component`. `Counter.builder(...).register(registry)` 패턴은 **멱등적**으로 동일 이름+태그 조합을 중복 등록해도 기존 카운터를 재사용합니다.

```java
// Counter
void recordLoginSuccess(String channel)
void recordLoginFailure(String channel)
void recordSignUp(String channel)
void recordTokenIssued(String type, String channel)
void recordTokenRefreshSuccess()
void recordTokenRefreshFailure()
void recordLogout(String type)
void recordRateLimitedLogin(String by)       // 현재 미사용 (RateLimitFilter가 직접 기록)
void recordEmailVerificationSent(String type)
void recordEmailVerificationSuccess(String type)
void recordEmailVerificationFailure(String type, String reason)

// Timer (직접 측정이 필요한 경우)
void recordOperationDuration(String operation, String channel, boolean success, Duration duration)
Timer.Sample startSample()
void stopSample(Timer.Sample sample, String operation, String channel, boolean success)
```

---

## @AuthTimed 어노테이션

**위치**: `src/main/java/com/jay/auth/service/metrics/AuthTimed.java`
**구현체**: `src/main/java/com/jay/auth/service/metrics/AuthTimedAspect.java`

AOP 기반 실행시간 자동 측정 어노테이션. 메서드에 붙이면 `auth_operation_duration_seconds` 메트릭이 자동 기록됩니다.

```java
@AuthTimed(operation = "my_operation")
public void someMethod() { ... }

// channel 태그를 파라미터에서 자동 추출
@AuthTimed(operation = "oauth2_login", channelParam = "channelCode")
public void processOAuth2User(ChannelCode channelCode, ...) { ... }
```

**channel 태그 추출 우선순위**

1. `channelParam`에 지정된 이름의 파라미터 탐색
2. `ChannelCode` 타입의 파라미터 자동 감지
3. `"channelCode"` 또는 `"channel"` 이름의 `String` 파라미터
4. 해당 없으면 `"UNKNOWN"`

> **주의**: `@AuthTimed`는 Spring AOP 프록시를 통해 동작합니다. `@Component` 등 Spring 빈으로 등록된 클래스에만 적용됩니다. 같은 클래스 내 자기 호출(`this.method()`)에는 적용되지 않습니다.

---

## AuthGaugeMetrics 클래스

**위치**: `src/main/java/com/jay/auth/service/metrics/AuthGaugeMetrics.java`

Gauge 메트릭 관리 전용 `@Component`. 실시간 이벤트 기반 업데이트(AtomicLong)와 주기적 DB/Redis 동기화(@Scheduled)를 혼합 사용합니다.

```java
// 세션 게이지 — TokenService에서 호출
void incrementActiveSessions()
void decrementActiveSessions()
void decrementActiveSessions(long count)

// 패스키 게이지 — PasskeyService에서 호출
void incrementRegisteredPasskeys()
void decrementRegisteredPasskeys()
```

---

## Grafana 대시보드

**대시보드 UID**: `auth-service`
**파일**: `monitoring/grafana/dashboards/auth-service.json`

### 섹션 구성

#### Business Metrics

| 패널 | 타입 | 메트릭 | 설명 |
|---|---|---|---|
| Login Attempts (by channel & result) | timeseries | `auth_login_attempts_total` | 채널·결과별 로그인 시도 추이 |
| JWT Tokens Issued | timeseries | `auth_token_issued_total{type="ACCESS"}` | 채널별 토큰 발급 추이 |
| Rate Limit Exceeded (total) | stat | `rate_limit_exceeded_total` | 엔드포인트 타입별 Rate Limit 초과 |
| Token Refresh (success / fail) | stat | `auth_token_refresh_total` | 토큰 갱신 성공/실패 |
| Logouts (single / all) | stat | `auth_logout_total` | 단일/전체 로그아웃 수 |

#### Cache

| 패널 | 타입 | 메트릭 | 설명 |
|---|---|---|---|
| Cache Hits vs Misses | timeseries | `cache_gets_total` | 캐시 히트/미스 추이 |
| Cache Hit Rate (%) | gauge | `cache_gets_total` | 캐시 히트율 (5분 기준) |

#### IDP (Login Channel)

| 패널 | 타입 | 메트릭 | 설명 |
|---|---|---|---|
| Login Success by IDP | piechart | `auth_login_attempts_total{result="success"}` | 채널별 성공 로그인 누적 분포 |
| Login Failure by IDP | piechart | `auth_login_attempts_total{result="fail"}` | 채널별 실패 로그인 누적 분포 |

#### Email / Phone Verification

| 패널 | 타입 | 메트릭 | 설명 |
|---|---|---|---|
| 이메일 인증 발송 수 (타입별) | stat | `auth_email_verification_total{action="sent"}` | 타입별 인증 코드 발송 누적 |
| 이메일 인증 성공/실패 (타입별) | stat | `auth_email_verification_total{action=~"success\|failure"}` | 타입·결과별 누적 |
| 이메일 인증 실패 원인 분포 | piechart | `auth_email_verification_failure_total` | 실패 원인별 누적 분포 |
| 이메일 인증 성공률 (%) | stat | 성공 / (성공 + 실패) × 100 | 전체 인증 성공률 |

#### Operation Latency (Timer)

| 패널 | 타입 | 메트릭 | 설명 |
|---|---|---|---|
| IDP별 로그인 소요시간 (평균, p95) | timeseries | `auth_operation_duration_seconds` | IDP별 평균 및 p95 응답시간 추이 |
| 인증 작업별 p95 응답시간 | timeseries | `auth_operation_duration_seconds` | 전체 operation p95 추이 |
| 작업 성공률 (최근 5분) | stat | `auth_operation_duration_seconds` | operation별 성공 비율 (%) |
| 인증 작업별 평균 소요시간 | stat | `auth_operation_duration_seconds` | operation별 평균 실행시간 |

#### System State (Gauge)

| 패널 | 타입 | 메트릭 | 설명 |
|---|---|---|---|
| 활성 세션 수 | timeseries | `auth_active_sessions` | 현재 활성 세션 수 추이 |
| 잠금 계정 수 (현재) | timeseries | `auth_locked_accounts` | 잠금 계정 수 추이 (브루트포스 감지) |
| 등록된 패스키 총 수 | stat | `auth_registered_passkeys` | 전체 등록 패스키 현재값 |
| 2FA 활성화 사용자 수 | stat | `auth_2fa_enabled_users` | 2FA 활성화 사용자 현재값 |
| 현재 활성 세션 | stat | `auth_active_sessions` | 활성 세션 현재값 |

### 대시보드 업데이트 방법

파일 수정 후 Grafana API로 직접 push합니다 (파일 프로비저닝 자동 반영 안 됨).

```bash
curl -s -u admin:admin \
  -X POST http://localhost:3001/api/dashboards/db \
  -H 'Content-Type: application/json' \
  -d "{\"dashboard\": $(cat monitoring/grafana/dashboards/auth-service.json), \"overwrite\": true}"
```

---

## 새 메트릭 추가 방법

### Counter 추가

1. `AuthMetrics`에 메서드 추가 — `Counter.builder("메트릭명").tag(...).register(registry).increment()` 패턴
2. 호출 위치에서 주입 — `AuthMetrics authMetrics` 필드 추가 (`@RequiredArgsConstructor` 활용)
3. 테스트 업데이트 — `@Mock AuthMetrics authMetrics` 추가 및 `verify(authMetrics).메서드명(...)` 검증

### Timer 추가

**AOP 방식 (권장)**

```java
// 서비스 메서드에 어노테이션만 추가
@AuthTimed(operation = "my_operation")
public void myMethod() { ... }
```

**직접 측정 방식** (콜백·필터 등 AOP 적용 불가 시)

```java
Timer.Sample sample = authMetrics.startSample();
boolean success = false;
try {
    doWork();
    success = true;
} finally {
    authMetrics.stopSample(sample, "my_operation", "UNKNOWN", success);
}
```

### Gauge 추가

1. `AuthGaugeMetrics`에 `AtomicLong` 필드 및 `Gauge.builder(...).register(registry)` 등록 (생성자에서)
2. 증감 이벤트가 발생하는 서비스에 `AuthGaugeMetrics` 주입 후 호출
3. 주기적 정확도 보정이 필요한 경우 `@Scheduled` 메서드 추가
4. 해당 서비스 테스트에 `@Mock AuthGaugeMetrics authGaugeMetrics` 추가

### 공통

4. **대시보드 패널 추가** — `auth-service.json` 수정 후 위 `curl` 명령으로 push

> **태그셋 일관성 주의**: 동일 메트릭명의 모든 시리즈는 동일한 태그 키 집합을 가져야 합니다. 상황에 따라 태그 유무가 달라지면 Prometheus 직렬화 오류가 발생합니다. 이런 경우 별도 메트릭으로 분리하세요 (`auth_email_verification_failure_total` 참고).
