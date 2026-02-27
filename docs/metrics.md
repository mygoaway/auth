# Auth Service 메트릭 가이드

## 개요

Micrometer + Prometheus + Grafana 스택으로 인증 서비스의 비즈니스 메트릭을 수집·시각화합니다.

- **수집**: Micrometer (`MeterRegistry`)
- **저장**: Prometheus — `http://localhost:9090`
- **시각화**: Grafana — `http://localhost:3001` (admin / admin)
- **노출 엔드포인트**: `GET /actuator/prometheus`

---

## 메트릭 목록

### 1. `auth_login_attempts_total`

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

### 2. `auth_signup_total`

회원가입 수.

| 태그 | 값 |
|---|---|
| `channel` | `EMAIL` (현재 이메일 가입만 집계) |

**호출 위치**: `AuthService.signUp()` 완료 시

---

### 3. `auth_token_issued_total`

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

### 4. `auth_token_refresh_total`

토큰 갱신 결과.

| 태그 | 값 |
|---|---|
| `result` | `success` \| `fail` |

**호출 위치**: `TokenService.refreshTokens()` — 검증 실패(3가지 경우) 시 `fail`, 갱신 완료 시 `success`

---

### 5. `auth_logout_total`

로그아웃 수.

| 태그 | 값 |
|---|---|
| `type` | `single` (현재 기기) \| `all` (전체 기기) |

**호출 위치**: `TokenService.logout()`, `TokenService.logoutAll()`

---

### 6. `auth_email_verification_total`

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

### 7. `auth_email_verification_failure_total`

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

### 8. `rate_limit_exceeded_total`

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

### 9. `http_server_requests_custom`

HTTP 요청 처리 시간 타이머. `RequestLoggingFilter`에서 직접 `MeterRegistry`로 기록합니다.

| 태그 | 값 |
|---|---|
| `method` | `GET` \| `POST` \| `PUT` \| `DELETE` 등 |
| `uri` | 정규화된 URI 패턴 |
| `status` | HTTP 상태 코드 (`200`, `401`, `429` 등) |

---

## AuthMetrics 클래스

**위치**: `src/main/java/com/jay/auth/service/metrics/AuthMetrics.java`

모든 비즈니스 메트릭 기록을 중앙화한 Spring `@Component`. `Counter.builder(...).register(registry)` 패턴은 **멱등적**으로 동일 이름+태그 조합을 중복 등록해도 기존 카운터를 재사용합니다.

```java
@Component
public class AuthMetrics {

    private final MeterRegistry registry;

    // 메서드 목록
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
}
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

1. **`AuthMetrics`에 메서드 추가** — `Counter.builder("메트릭명").tag(...).register(registry).increment()` 패턴 사용
2. **호출 위치에서 주입** — 해당 서비스/핸들러에 `AuthMetrics authMetrics` 필드 추가 (`@RequiredArgsConstructor` 활용)
3. **테스트 업데이트** — 해당 서비스 테스트에 `@Mock AuthMetrics authMetrics` 추가 및 `verify(authMetrics).메서드명(...)` 검증
4. **대시보드 패널 추가** — `auth-service.json` 수정 후 위 `curl` 명령으로 push

> **태그셋 일관성 주의**: 동일 메트릭명의 모든 시리즈는 동일한 태그 키 집합을 가져야 합니다. 상황에 따라 태그 유무가 달라지면 Prometheus 직렬화 오류가 발생합니다. 이런 경우 별도 메트릭으로 분리하세요 (`auth_email_verification_failure_total` 참고).
