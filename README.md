# Authly

이메일, 소셜 로그인, 패스키를 지원하는 풀스택 인증 서비스입니다.

Spring Boot 백엔드와 React 프론트엔드로 구성되며, JWT 기반 인증, 2단계 인증(TOTP), WebAuthn/Passkey, OAuth2 소셜 로그인 등 다양한 인증 방식을 제공합니다.

## 기술 스택

### 백엔드
- Java 17, Spring Boot 3.5, Spring Security 6.x
- MySQL (JPA/Hibernate), Redis
- JWT (jjwt 0.12.6), BCrypt, AES-256 암호화
- TOTP 2FA, WebAuthn/Passkey (webauthn4j)
- OAuth2 Client (Google, Kakao, Naver)
- SpringDoc OpenAPI (Swagger)

### 프론트엔드
- React 19, React Router 7, Axios
- Vite 7

### 인프라
- Docker & Docker Compose
- Nginx (프론트엔드 프로덕션)

## 주요 기능

### 인증
- **이메일 회원가입/로그인** — 이메일 인증, 비밀번호 정책 적용
- **소셜 로그인** — Google, Kakao, Naver (OIDC/OAuth2)
- **패스키 (WebAuthn)** — 생체인증/보안키 기반 비밀번호 없는 로그인
- **JWT 토큰** — Access Token(30분) + Refresh Token(14일), Redis 블랙리스트

### 보안
- **2단계 인증 (TOTP)** — 앱 기반 OTP, 백업 코드 제공
- **소셜 계정 연동** — 기존 계정에 소셜 로그인 추가/해제
- **신뢰 기기 관리** — 기기별 세션 확인 및 원격 로그아웃
- **보안 대시보드** — 보안 점수, 로그인 이력, 활동 리포트
- **Rate Limiting** — Redis 기반 IP별 요청 제한
- **비밀번호 재사용 방지** — 이전 비밀번호 이력 관리

### 고객센터
- **게시판** — 카테고리별 문의 (계정, 로그인, 보안, 기타)
- **AI 자동 답변** — Claude API 기반 자동 응답 생성
- **상태 관리** — OPEN → IN_PROGRESS → RESOLVED → CLOSED

### 관리자
- **대시보드** — 사용자 통계, 시스템 현황
- **사용자 관리** — 계정 상태 변경, 역할 관리

## 시작하기

### 사전 요구사항
- Java 17+
- Node.js 18+
- MySQL 8.x
- Redis 7.x

### 환경 변수

`.env.example`을 참고하여 환경 변수를 설정합니다.

```bash
# 필수
DB_USERNAME=root
DB_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your_jwt_secret_min_32_chars
ENCRYPTION_SECRET_KEY=your_aes_256_key_32_chars

# 선택 (기본값 있음)
CORS_ALLOWED_ORIGINS=http://localhost:3000
SMS_PROVIDER=log          # log | coolsms
EMAIL_PROVIDER=log        # log | smtp
AI_PROVIDER=log           # log | claude
```

### 백엔드 실행

```bash
./gradlew bootRun
# 기본 포트: 8080, 프로필: local
```

### 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
# http://localhost:3000 에서 실행, /api → localhost:8080 프록시
```

### Docker Compose

```bash
docker-compose up -d
# MySQL, Redis, 백엔드, 프론트엔드 일괄 실행
```

## 프로젝트 구조

```
auth/
├── src/main/java/com/jay/auth/
│   ├── config/          # 보안, Redis, CORS, Rate Limit 등 설정
│   ├── controller/      # REST API 컨트롤러
│   ├── domain/          # 엔티티 및 Enum
│   ├── dto/             # 요청/응답 DTO
│   ├── exception/       # 예외 및 글로벌 핸들러
│   ├── repository/      # JPA 리포지토리
│   ├── security/        # JWT, OAuth2 처리
│   ├── service/         # 비즈니스 로직
│   └── util/            # 유틸리티
├── src/main/resources/
│   ├── application.yml  # 기본 설정
│   └── templates/       # 이메일 템플릿 (Thymeleaf)
├── frontend/
│   └── src/
│       ├── api/         # API 클라이언트
│       ├── components/  # 공통 컴포넌트
│       ├── contexts/    # React Context (인증 상태)
│       ├── pages/       # 페이지 컴포넌트
│       └── styles/      # CSS
├── docker-compose.yml
└── Dockerfile
```

## API 문서

애플리케이션 실행 후 Swagger UI에서 전체 API를 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```

> Swagger UI는 `local`, `dev` 프로필에서만 활성화됩니다.
