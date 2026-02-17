# 소셜 로그인 연동 플로우 (카카오 / 네이버 / 구글)

## 목차

1. [개요](#1-개요)
2. [회원가입 (소셜 로그인으로 신규 가입)](#2-회원가입-소셜-로그인으로-신규-가입)
3. [로그인 (기존 소셜 계정으로 로그인)](#3-로그인-기존-소셜-계정으로-로그인)
4. [계정 연동 (기존 계정에 소셜 계정 추가)](#4-계정-연동-기존-계정에-소셜-계정-추가)
5. [프로바이더별 설정](#5-프로바이더별-설정)
6. [Redis 키 구조](#6-redis-키-구조)
7. [에러 처리](#7-에러-처리)
8. [주요 파일 목록](#8-주요-파일-목록)

---

## 1. 개요

### 지원 프로바이더

| 프로바이더 | 프로토콜 | 처리 서비스 |
|---|---|---|
| Google | OIDC | `CustomOidcUserService` |
| Kakao | OIDC | `CustomOidcUserService` |
| Naver | OAuth2 | `CustomOAuth2UserService` |

### 핵심 컴포넌트

| 컴포넌트 | 역할 |
|---|---|
| `CustomOAuth2AuthorizationRequestResolver` | 계정 연동 시 `link_state` 파라미터를 OAuth2 state에 주입 |
| `CustomOAuth2UserService` | OAuth2 프로바이더(네이버) 사용자 정보 로딩 |
| `CustomOidcUserService` | OIDC 프로바이더(구글, 카카오) 사용자 정보 로딩 |
| `OAuth2UserService` | 사용자 생성/연동 비즈니스 로직 |
| `OAuth2AuthenticationSuccessHandler` | 인증 성공 후 토큰 발급 및 리다이렉트 |
| `OAuth2AuthenticationFailureHandler` | 인증 실패 시 에러 리다이렉트 |
| `OAuth2LinkController` | 계정 연동 준비 API |
| `OAuth2LinkStateService` | Redis 기반 연동 상태 관리 |

### DB 테이블 구조

```
tb_user (1) ──── (0..1) tb_user_sign_in_info   ← 이메일 로그인 사용자만
  │
  └──── (1..N) tb_user_channel                  ← 로그인 채널 (EMAIL, GOOGLE, KAKAO, NAVER)
                  UNIQUE(channel_code, channel_key)
```

- **소셜 로그인**: `tb_user` + `tb_user_channel`
- **이메일 로그인**: `tb_user` + `tb_user_sign_in_info` + `tb_user_channel`
- 한 사용자가 여러 소셜 계정을 연동할 수 있음 (채널 코드당 1개)

---

## 2. 회원가입 (소셜 로그인으로 신규 가입)

소셜 계정으로 처음 로그인하면 자동으로 회원가입이 진행됩니다.

### 시퀀스 다이어그램

```
┌──────────┐     ┌──────────┐     ┌──────────────┐     ┌──────────┐     ┌───────┐
│ Frontend │     │  Spring  │     │ OAuth2       │     │ Backend  │     │  DB   │
│          │     │ Security │     │ Provider     │     │ Service  │     │       │
└────┬─────┘     └────┬─────┘     └──────┬───────┘     └────┬─────┘     └───┬───┘
     │                │                   │                  │               │
     │ ① 소셜 로그인 버튼 클릭             │                  │               │
     │ GET /oauth2/authorization/{provider}                  │               │
     │───────────────>│                   │                  │               │
     │                │                   │                  │               │
     │                │ ② 인가 URL 리다이렉트                 │               │
     │                │──────────────────>│                  │               │
     │                │                   │                  │               │
     │                │   ③ 사용자 동의    │                  │               │
     │<───────────────────────────────────│                  │               │
     │ (동의 화면)     │                   │                  │               │
     │───────────────────────────────────>│                  │               │
     │                │                   │                  │               │
     │                │ ④ code + state 콜백                  │               │
     │                │<──────────────────│                  │               │
     │                │                   │                  │               │
     │                │ ⑤ code → token 교환, 사용자 정보 요청 │               │
     │                │──────────────────>│                  │               │
     │                │<──────────────────│                  │               │
     │                │                   │                  │               │
     │                │ ⑥ CustomOAuth2/OidcUserService.loadUser()            │
     │                │──────────────────────────────────────>               │
     │                │                   │                  │               │
     │                │                   │  ⑦ UserChannel 조회             │
     │                │                   │                  │──────────────>│
     │                │                   │                  │<──────────────│
     │                │                   │                  │ (없음 = 신규)  │
     │                │                   │                  │               │
     │                │                   │  ⑧ User + UserChannel 생성      │
     │                │                   │                  │──────────────>│
     │                │                   │                  │<──────────────│
     │                │                   │                  │               │
     │                │ ⑨ JWT 토큰 발급 + Redis 세션 저장     │               │
     │                │──────────────────────────────────────>               │
     │                │                   │                  │               │
     │ ⑩ 리다이렉트: /oauth2/callback?accessToken=...&refreshToken=...      │
     │<───────────────│                   │                  │               │
     │                │                   │                  │               │
     │ ⑪ 토큰 저장 → 프로필 로드 → /dashboard 이동           │               │
     │                │                   │                  │               │
```

### 상세 단계

#### ① 프론트엔드: 소셜 로그인 버튼 클릭

```javascript
// LoginPage.jsx
const handleSocialLogin = (provider) => {
  window.location.href = `${OAUTH2_BASE_URL}/oauth2/authorization/${provider}`;
};
// provider: "google" | "kakao" | "naver"
```

Vite 프록시 설정에 의해 `/oauth2/authorization` 경로는 백엔드(localhost:8080)로 전달됩니다.

#### ② ~ ⑤ Spring Security OAuth2 표준 플로우

Spring Security가 자동으로 처리하는 표준 OAuth2 플로우:

1. 프로바이더 인가 URL로 리다이렉트
2. 사용자가 동의 화면에서 승인
3. 프로바이더가 `code` + `state`를 콜백 URL로 전달
4. Spring Security가 `code`를 `access_token`으로 교환하고 사용자 정보 요청

#### ⑥ 사용자 정보 추출

프로바이더별 사용자 정보 파싱:

| 필드 | Google (OIDC) | Kakao (OIDC) | Naver (OAuth2) |
|---|---|---|---|
| ID | `sub` | `sub` | `response.id` |
| Email | `email` | `email` | `response.email` |
| Name | `name` | `nickname` | `response.name` |
| Picture | `picture` | `picture` | `response.profile_image` |

#### ⑦ ~ ⑧ 신규 사용자 생성

```java
// OAuth2UserService.processOAuth2User()
1. UserChannel 조회: findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey)
2. 결과 없음 → 신규 사용자 생성:
   - User 생성: UUID 발급, 이메일 암호화(AES-256), 닉네임 자동생성, status=ACTIVE, role=USER
   - UserChannel 생성: channel_code, channel_key, 채널 이메일 암호화
   - DB 저장
```

**DB 변경사항:**

| 테이블 | 동작 | 주요 필드 |
|---|---|---|
| `tb_user` | INSERT | user_uuid, email_enc, email_lower_enc, nickname_enc, status=ACTIVE |
| `tb_user_channel` | INSERT | user_id(FK), channel_code, channel_key, channel_email_enc |

#### ⑨ JWT 토큰 발급

```java
// TokenService.issueTokensWithSession()
- Access Token: 30분 TTL, HS256 서명
- Refresh Token: 14일 TTL
- Redis 저장: refresh:{userId}:{jti}, session:{userId}:{jti}
```

**JWT Claims:**
```json
{
  "userId": 123,
  "userUuid": "550e8400-...",
  "channelCode": "GOOGLE",
  "role": "USER",
  "tokenType": "ACCESS",
  "jti": "unique-token-id"
}
```

#### ⑩ 프론트엔드 콜백 리다이렉트

```
→ http://localhost:3000/oauth2/callback?accessToken={jwt}&refreshToken={jwt}&expiresIn=1800
```

#### ⑪ 프론트엔드 토큰 처리

```javascript
// OAuth2CallbackPage.jsx → AuthContext.jsx
1. URL 파라미터에서 accessToken, refreshToken 추출
2. 토큰 저장 (Remember Me 설정에 따라):
   - true: localStorage (브라우저 종료 후에도 유지)
   - false: sessionStorage (탭 종료 시 삭제)
3. /api/v1/users/profile 호출하여 사용자 정보 로드
4. /dashboard로 리다이렉트
```

---

## 3. 로그인 (기존 소셜 계정으로 로그인)

기존에 소셜 로그인으로 가입한 사용자가 다시 로그인하는 플로우입니다.

### 플로우

회원가입과 동일한 경로를 거치되, **⑦ ~ ⑧ 단계만 다릅니다:**

```
⑦ UserChannel 조회: findByChannelCodeAndChannelKeyWithUser(channelCode, channelKey)
⑧ 결과 있음 → 기존 User 반환 (신규 생성 없음)
```

이후 토큰 발급(⑨)부터는 회원가입과 동일합니다.

### 사용자 상태별 처리

| 상태 | 처리 |
|---|---|
| `ACTIVE` | 정상 로그인 |
| `PENDING_DELETE` | 로그인 허용 (30일 유예 기간 내 복구 가능). 프론트엔드에서 삭제 대기 안내 다이얼로그 표시 |
| `INACTIVE` | 로그인 차단, 예외 발생 |

### 로그인 부가 처리

토큰 발급과 함께 다음 작업이 비동기로 실행됩니다:

1. **로그인 이력 기록** (`LoginHistoryService`): `tb_login_history`에 디바이스/위치 정보 저장
2. **보안 알림 발송** (`SecurityNotificationService`): 새로운 디바이스 감지 시 이메일 알림

---

## 4. 계정 연동 (기존 계정에 소셜 계정 추가)

이미 로그인된 사용자가 추가 소셜 계정을 연동하는 플로우입니다. 예: 이메일로 가입한 사용자가 구글 계정 연동.

### 시퀀스 다이어그램

```
┌──────────┐     ┌──────────┐     ┌──────────────┐     ┌──────────┐     ┌───────┐
│ Frontend │     │  Backend │     │ OAuth2       │     │ Backend  │     │ Redis │
│(인증 상태)│     │Controller│     │ Provider     │     │ Service  │     │       │
└────┬─────┘     └────┬─────┘     └──────┬───────┘     └────┬─────┘     └───┬───┘
     │                │                   │                  │               │
     │ ① 연동하기 버튼 클릭                │                  │               │
     │ POST /api/v1/oauth2/link/prepare/{provider}           │               │
     │ (Authorization: Bearer {JWT})      │                  │               │
     │───────────────>│                   │                  │               │
     │                │                   │                  │               │
     │                │ ② link_state 생성 + Redis 저장 + 쿠키 설정            │
     │                │──────────────────────────────────────────────────────>│
     │                │                   │                  │               │
     │ ③ { authorizationUrl } 응답        │                  │               │
     │<───────────────│                   │                  │               │
     │                │                   │                  │               │
     │ ④ 리다이렉트: /oauth2/authorization/{provider}?link_state={state}     │
     │───────────────>│                   │                  │               │
     │                │                   │                  │               │
     │                │ ⑤ state를 link_state로 교체 후 인가 URL 리다이렉트    │
     │                │──────────────────>│                  │               │
     │                │                   │                  │               │
     │                │   ⑥ 사용자 동의    │                  │               │
     │<───────────────────────────────────│                  │               │
     │───────────────────────────────────>│                  │               │
     │                │                   │                  │               │
     │                │ ⑦ code + state(=link_state) 콜백     │               │
     │                │<──────────────────│                  │               │
     │                │                   │                  │               │
     │                │ ⑧ 사용자 정보 로딩 │                  │               │
     │                │──────────────────>│                  │               │
     │                │<──────────────────│                  │               │
     │                │                   │                  │               │
     │                │ ⑨ link 모드 감지 (쿠키 → Redis 조회)  │               │
     │                │──────────────────────────────────────────────────────>│
     │                │                   │                  │               │
     │                │ ⑩ processOAuth2UserForLinking()      │               │
     │                │──────────────────────────────────────>               │
     │                │                   │                  │               │
     │                │ ⑪ 토큰 발급 없음, link_state 정리     │               │
     │                │──────────────────────────────────────────────────────>│
     │                │                   │                  │               │
     │ ⑫ 리다이렉트: /oauth2/link/success?channelCode={code}&success=true   │
     │<───────────────│                   │                  │               │
     │                │                   │                  │               │
     │ ⑬ 성공 메시지 표시 → 프로필 새로고침 → /dashboard 이동 │               │
     │                │                   │                  │               │
```

### 상세 단계

#### ① 연동하기 버튼 클릭

```javascript
// DashboardPage.jsx
const handleLinkChannel = async (provider) => {
  const response = await oauth2Api.prepareLink(provider);
  const { authorizationUrl } = response.data;
  window.location.href = `${OAUTH2_BASE_URL}${authorizationUrl}`;
};
```

#### ② Link State 준비

```java
// OAuth2LinkController.prepareLink()
1. JWT에서 userId 추출 (@AuthenticationPrincipal UserPrincipal)
2. state = UUID.randomUUID().toString()
3. Redis 저장: oauth2:link:{state} = userId (TTL: 10분)
4. 쿠키 설정: oauth2_link_state = state (maxAge: 5분, HttpOnly)
5. 응답: { state, authorizationUrl: "/oauth2/authorization/{provider}?link_state={state}" }
```

#### ③ ~ ⑤ OAuth2 인가 요청 (link_state 주입)

```java
// CustomOAuth2AuthorizationRequestResolver.resolve()
1. 요청 URL에서 link_state 파라미터 확인
2. link_state가 있으면: OAuth2 state 파라미터를 link_state 값으로 교체
   → OAuth2AuthorizationRequest.from(request).state(linkState).build()
3. 프로바이더 인가 URL로 리다이렉트 (state = link_state)
```

**왜 state를 교체하는가?**
- OAuth2 state는 프로바이더에게 불투명(opaque)한 값
- 프로바이더가 콜백 시 동일한 state를 반환하므로, link_state를 주입하면 콜백에서 연동 모드를 식별할 수 있음

#### ⑥ ~ ⑧ 사용자 동의 및 정보 수신

일반 로그인과 동일한 OAuth2 표준 플로우입니다.

#### ⑨ Link 모드 감지

```java
// CustomOAuth2UserService / CustomOidcUserService - loadUser()
1. 쿠키에서 oauth2_link_state 값 읽기
2. Redis 조회: oauth2:link:{linkState} → userId
3. userId가 존재하면 isLinkMode = true
```

#### ⑩ 계정 연동 처리

```java
// OAuth2UserService.processOAuth2UserForLinking(userId, channelCode, oAuth2UserInfo)
1. User 조회 (userId) → status가 ACTIVE인지 확인
2. 해당 소셜 계정이 이미 다른 사용자에게 연동되어 있는지 확인
   → 있으면: AccountLinkingException ("이미 다른 계정에 연동된 소셜 계정")
3. 현재 사용자에게 이미 같은 채널이 연동되어 있는지 확인
   → 있으면: AccountLinkingException ("이미 연동된 채널")
4. 새 UserChannel 생성 및 저장
5. userProfile 캐시 제거 (다음 조회 시 갱신)
6. User 반환
```

**DB 변경사항:**

| 테이블 | 동작 | 주요 필드 |
|---|---|---|
| `tb_user_channel` | INSERT | 기존 user_id(FK), 새 channel_code, 새 channel_key |

#### ⑪ 성공 처리 (토큰 발급 없음)

```java
// OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
// isLinkMode = true인 경우:
1. 새로운 토큰 발급하지 않음 (이미 인증된 상태)
2. Redis에서 link_state 삭제
3. oauth2_link_state 쿠키 삭제
4. 리다이렉트: /oauth2/link/success?channelCode={GOOGLE|KAKAO|NAVER}&success=true
```

#### ⑫ ~ ⑬ 프론트엔드 연동 완료 처리

```javascript
// OAuth2LinkCallbackPage.jsx
1. URL 파라미터에서 success, channelCode 추출
2. 성공 메시지 표시 ("Google 계정이 연동되었습니다")
3. loadProfile() 호출하여 프로필 갱신
4. 2초 후 /dashboard?tab=channels 로 리다이렉트
```

---

## 5. 프로바이더별 설정

### Google (OIDC)

```yaml
# application-local.yml
spring.security.oauth2.client.registration.google:
  client-id: {GOOGLE_CLIENT_ID}
  client-secret: {GOOGLE_CLIENT_SECRET}
  scope: openid, profile, email
# OIDC Discovery 자동 사용 (별도 provider 설정 불필요)
```

- 프로토콜: OIDC (OpenID Connect)
- 처리: `CustomOidcUserService`
- ID Token에서 사용자 정보 추출

### Kakao (OIDC)

```yaml
spring.security.oauth2.client.registration.kakao:
  client-id: {KAKAO_CLIENT_ID}
  client-secret: {KAKAO_CLIENT_SECRET}
  authorization-grant-type: authorization_code
  client-authentication-method: client_secret_post
  redirect-uri: http://localhost:8080/login/oauth2/code/kakao
  scope: openid, profile_nickname, profile_image, account_email

spring.security.oauth2.client.provider.kakao:
  issuer-uri: https://kauth.kakao.com
  authorization-uri: https://kauth.kakao.com/oauth/authorize
  token-uri: https://kauth.kakao.com/oauth/token
  user-info-uri: https://kapi.kakao.com/v2/user/me
  user-name-attribute: sub
```

- 프로토콜: OIDC
- 처리: `CustomOidcUserService`
- 특이사항: `client-authentication-method: client_secret_post` (카카오 요구사항)

### Naver (OAuth2)

```yaml
spring.security.oauth2.client.registration.naver:
  client-id: {NAVER_CLIENT_ID}
  client-secret: {NAVER_CLIENT_SECRET}
  authorization-grant-type: authorization_code
  redirect-uri: http://localhost:8080/login/oauth2/code/naver
  scope: profile, email

spring.security.oauth2.client.provider.naver:
  authorization-uri: https://nid.naver.com/oauth2.0/authorize
  token-uri: https://nid.naver.com/oauth2.0/token
  user-info-uri: https://openapi.naver.com/v1/nid/me
  user-name-attribute: response
```

- 프로토콜: OAuth2 (OIDC 미지원)
- 처리: `CustomOAuth2UserService`
- 특이사항: 사용자 정보가 `response` 객체 안에 중첩됨

### 프록시 설정 (Vite Dev Server)

```javascript
// vite.config.js - 개발 환경에서 백엔드로 프록시
'/api'                   → http://localhost:8080
'/oauth2/authorization'  → http://localhost:8080
'/login/oauth2'          → http://localhost:8080
```

---

## 6. Redis 키 구조

### 소셜 로그인/연동에서 사용하는 키

| 키 패턴 | 값 | TTL | 용도 |
|---|---|---|---|
| `oauth2:link:{state}` | userId | 10분 | 계정 연동 상태 (연동 플로우에서만 사용) |
| `refresh:{userId}:{tokenId}` | 토큰 데이터 | 14일 | Refresh Token 저장 |
| `session:{userId}:{tokenId}` | Hash (device, browser, OS, IP) | 14일 | 세션 정보 |
| `blacklist:{tokenId}` | - | 토큰 잔여 수명 | 폐기된 Access Token |

### 연동 상태 생명주기

```
[연동 준비 API 호출]
  → Redis SET oauth2:link:{state} = userId (TTL: 10분)
  → Cookie SET oauth2_link_state = state (maxAge: 5분)

[OAuth2 콜백 처리]
  → Cookie GET oauth2_link_state
  → Redis GET oauth2:link:{state} → userId (link 모드 확인)

[연동 성공]
  → Redis DEL oauth2:link:{state}
  → Cookie DEL oauth2_link_state
```

---

## 7. 에러 처리

### OAuth2 실패 핸들러

```java
// OAuth2AuthenticationFailureHandler
1. 쿠키에서 link 모드 여부 확인
2. 쿠키 삭제
3. 리다이렉트:
   - 연동 실패: /oauth2/link/success?error={메시지}
   - 로그인 실패: /oauth2/callback?error={메시지}
```

### 계정 연동 에러 케이스

| 상황 | 에러 메시지 | 처리 |
|---|---|---|
| 소셜 계정이 다른 사용자에게 이미 연동됨 | "해당 소셜 계정은 이미 다른 계정에 연동되어 있습니다" | `AccountLinkingException` |
| 현재 사용자에게 이미 같은 채널 연동됨 | "이미 연동된 채널입니다" | `AccountLinkingException` |
| 사용자 계정이 비활성 상태 | "User account is not active" | `IllegalStateException` |
| OAuth2 프로바이더 인증 실패 | Spring Security 기본 메시지 | `OAuth2AuthenticationException` |

### 프론트엔드 에러 처리

```javascript
// OAuth2CallbackPage.jsx / OAuth2LinkCallbackPage.jsx
1. URL 파라미터에서 error 확인
2. 에러 메시지 표시
3. 일정 시간 후 적절한 페이지로 리다이렉트
   - 로그인 실패: /login
   - 연동 실패: /dashboard
```

---

## 8. 주요 파일 목록

### Backend

| 파일 | 경로 | 역할 |
|---|---|---|
| `SecurityConfig` | `config/SecurityConfig.java` | OAuth2 로그인 설정 및 Security Filter Chain |
| `CustomOAuth2UserService` | `security/oauth2/CustomOAuth2UserService.java` | OAuth2 사용자 정보 로딩 (Naver) |
| `CustomOidcUserService` | `security/oauth2/CustomOidcUserService.java` | OIDC 사용자 정보 로딩 (Google, Kakao) |
| `CustomOAuth2AuthorizationRequestResolver` | `security/oauth2/CustomOAuth2AuthorizationRequestResolver.java` | link_state → OAuth2 state 주입 |
| `OAuth2AuthenticationSuccessHandler` | `security/oauth2/OAuth2AuthenticationSuccessHandler.java` | 인증 성공 처리 (토큰 발급/연동 완료) |
| `OAuth2AuthenticationFailureHandler` | `security/oauth2/OAuth2AuthenticationFailureHandler.java` | 인증 실패 에러 리다이렉트 |
| `OAuth2UserService` | `service/OAuth2UserService.java` | 사용자 생성/연동 비즈니스 로직 |
| `OAuth2LinkStateService` | `service/OAuth2LinkStateService.java` | Redis 연동 상태 관리 |
| `OAuth2LinkController` | `controller/OAuth2LinkController.java` | 계정 연동 준비 API |
| `OAuth2UserInfoFactory` | `security/oauth2/user/OAuth2UserInfoFactory.java` | 프로바이더별 사용자 정보 파싱 팩토리 |
| `GoogleOAuth2UserInfo` | `security/oauth2/user/GoogleOAuth2UserInfo.java` | Google 사용자 정보 |
| `KakaoOAuth2UserInfo` | `security/oauth2/user/KakaoOAuth2UserInfo.java` | Kakao 사용자 정보 |
| `NaverOAuth2UserInfo` | `security/oauth2/user/NaverOAuth2UserInfo.java` | Naver 사용자 정보 |
| `UserChannel` | `domain/entity/UserChannel.java` | 사용자-채널 관계 엔티티 |
| `UserChannelRepository` | `repository/UserChannelRepository.java` | 채널 DB 조회 |

### Frontend

| 파일 | 경로 | 역할 |
|---|---|---|
| `LoginPage` | `pages/LoginPage.jsx` | 소셜 로그인 버튼 |
| `OAuth2CallbackPage` | `pages/OAuth2CallbackPage.jsx` | 로그인 콜백 처리 |
| `OAuth2LinkCallbackPage` | `pages/OAuth2LinkCallbackPage.jsx` | 연동 콜백 처리 |
| `DashboardPage` | `pages/DashboardPage.jsx` | 연동 관리 UI |
| `AuthContext` | `contexts/AuthContext.jsx` | 토큰 저장 및 인증 상태 관리 |
| `vite.config.js` | `vite.config.js` | 프록시 설정 |

### 설정

| 파일 | 역할 |
|---|---|
| `application-local.yml` | 로컬 환경 OAuth2 클라이언트 설정 |
| `application-dev.yml` | 개발 환경 설정 |
| `application-prod.yml` | 운영 환경 설정 |
