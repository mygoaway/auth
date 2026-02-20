# 인프라 가이드

## 전체 구조

```
[브라우저]
    │
    ▼
[Frontend - Nginx :80]
    │ /api/, /oauth2/, /login/oauth2/ → 리버스 프록시
    ▼
[Backend - Spring Boot :8080]
    ├── MySQL :3306
    └── Redis :6379
```

## Docker

### 이미지 구성

#### 백엔드 (`Dockerfile`)
멀티스테이지 빌드 사용:
- **Stage 1 (build)**: `gradle:8.14-jdk17` — `./gradlew build -x test`
- **Stage 2 (run)**: `eclipse-temurin:17-jre-alpine` — 빌드된 JAR만 복사해 실행
- 보안을 위해 `appuser` (non-root) 계정으로 실행
- 포트: `8080`

#### 프론트엔드 (`frontend/Dockerfile`)
멀티스테이지 빌드 사용:
- **Stage 1 (build)**: `node:22-alpine` — `npm run build`
- **Stage 2 (serve)**: `nginx:alpine` — dist 파일을 Nginx로 서빙
- 포트: `80`

### 수동 이미지 빌드

```bash
# 백엔드
docker build -t auth-backend .

# 프론트엔드
docker build -t auth-frontend ./frontend
```

## docker-compose

로컬 개발 환경에서 전체 스택을 한 번에 실행하기 위한 구성입니다.

### 서비스 목록

| 서비스 | 이미지 | 포트 | 용도 |
|---|---|---|---|
| `mysql` | mysql:8.0 | 3306 | 메인 데이터베이스 |
| `redis` | redis:7-alpine | 6379 | 캐시 / 세션 / 블랙리스트 |
| `backend` | 로컬 빌드 | 8080 | Spring Boot API |
| `frontend` | 로컬 빌드 | 80 | React + Nginx |
| `mailhog` | mailhog/mailhog | 1025 (SMTP), 8025 (UI) | 이메일 테스트 (dev 프로필만) |

### 실행

```bash
# 전체 실행 (MySQL, Redis, 백엔드, 프론트엔드)
docker compose up -d

# MailHog 포함 실행 (dev 프로필)
docker compose --profile dev up -d

# 중지
docker compose down

# 볼륨까지 삭제 (DB 초기화)
docker compose down -v
```

### 환경 변수 (.env)

`docker-compose.yml`은 `.env` 파일을 자동으로 읽습니다. 아래 값들을 설정할 수 있습니다.

```env
DB_ROOT_PASSWORD=rootpassword
DB_NAME=auth_db
DB_USERNAME=authuser
DB_PASSWORD=authpassword

REDIS_PASSWORD=redispassword

JWT_SECRET=your-256-bit-secret-key-here-must-be-at-least-32-characters-long
ENCRYPTION_SECRET_KEY=your-32-character-encryption-key!

SPRING_PROFILE=dev
DDL_AUTO=update

OAUTH2_REDIRECT_URI=http://localhost/oauth2/callback

MAIL_HOST=mailhog
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
```

### 서비스 의존 관계

```
mysql (healthy) ──┐
                  ├──→ backend ──→ frontend
redis (healthy) ──┘
```

## Nginx 설정 (`frontend/nginx.conf`)

프론트엔드 컨테이너 내부 Nginx가 담당하는 역할:

| 경로 | 처리 방식 |
|---|---|
| `/api/` | 백엔드(`:8080`)로 리버스 프록시 |
| `/oauth2/authorization/` | 백엔드(`:8080`)로 리버스 프록시 |
| `/login/oauth2/` | 백엔드(`:8080`)로 리버스 프록시 |
| `/` | SPA fallback (`index.html`) |
| `*.js, *.css, 이미지, 폰트` | 1년 캐시 (`Cache-Control: public, immutable`) |

## CI (`github/workflows/ci.yml`)

`main`, `develop` 브랜치에 push 또는 PR 시 자동 실행됩니다.

### 잡 실행 순서

```
backend-test ──→ backend-build ──→ docker-build (main 브랜치만)
frontend-lint ──→ frontend-build ──↗
```

| 잡 | 내용 |
|---|---|
| `backend-test` | Redis 컨테이너 구동 후 `./gradlew test`, 결과 아티팩트 업로드 |
| `backend-build` | `./gradlew build -x test`, JAR 아티팩트 업로드 |
| `frontend-lint` | `npm run lint` |
| `frontend-build` | `npm run build`, dist 아티팩트 업로드 |
| `docker-build` | Docker 이미지 빌드 검증 (push 없음, main 브랜치만) |

> `docker-build` 잡은 이미지를 레지스트리에 push하지 않습니다. 빌드 가능 여부만 검증합니다. 실제 배포 파이프라인이 필요하다면 Docker Hub / ghcr.io / AWS ECR 등으로 push하는 단계를 추가해야 합니다.
