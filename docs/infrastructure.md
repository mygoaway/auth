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
    ├── Redis :6379
    ├── /actuator/prometheus → Prometheus :9090 → Grafana :3001
    └── Pinpoint Agent → Pinpoint Collector :9991-9994
                              └── HBase :2181 → Pinpoint Web :8888
```

## Docker

### 이미지 구성

#### 백엔드 (`Dockerfile`)
멀티스테이지 빌드 사용:
- **Stage 1 (build)**: `gradle:8.14-jdk17` — `./gradlew build -x test`
- **Stage 2 (run)**: `eclipse-temurin:17-jre-jammy` — 빌드된 JAR만 복사해 실행 (alpine → jammy로 변경, 호환성 개선)
- 보안을 위해 `appuser` (non-root) 계정으로 실행
- 포트: `8080`
- **Pinpoint Agent 지원**: `PINPOINT_ACTIVE=true` 환경변수 설정 시 `/pinpoint-agent/pinpoint-bootstrap.jar` 자동 주입. Agent JAR는 `pinpoint-agent-volume` 볼륨에서 마운트됩니다.

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

| 서비스 | 이미지 | 포트 | 용도 | 프로필 |
|---|---|---|---|---|
| `mysql` | mysql:8.0 | 3306 | 메인 데이터베이스 | 기본 |
| `redis` | redis:7-alpine | 6379 | 캐시 / 세션 / 블랙리스트 | 기본 |
| `backend` | 로컬 빌드 | 8080 | Spring Boot API | 기본 |
| `frontend` | 로컬 빌드 | 80 | React + Nginx | 기본 |
| `mailhog` | mailhog/mailhog | 1025 (SMTP), 8025 (UI) | 이메일 테스트 | dev |
| `prometheus` | prom/prometheus | 9090 | 메트릭 수집 | monitoring |
| `grafana` | grafana/grafana | 3001 | 메트릭 시각화 (admin/admin) | monitoring |
| `pinpoint-hbase` | pinpointdocker/pinpoint-hbase | 2181 | Pinpoint 데이터 저장소 (HBase + ZooKeeper) | pinpoint |
| `pinpoint-collector` | pinpointdocker/pinpoint-collector | 9991-9994 | 에이전트 데이터 수집 (gRPC) | pinpoint |
| `pinpoint-web` | pinpointdocker/pinpoint-web | 8888 | Pinpoint APM UI | pinpoint |
| `pinpoint-agent` | pinpointdocker/pinpoint-agent | — | Agent JAR 볼륨 제공 (init 컨테이너) | pinpoint |

### 실행

```bash
# 전체 실행 (MySQL, Redis, 백엔드, 프론트엔드)
docker compose up -d

# MailHog 포함 실행 (dev 프로필)
docker compose --profile dev up -d

# 모니터링 스택(Prometheus + Grafana) 포함 실행
docker compose --profile monitoring up -d

# Pinpoint APM 포함 실행
docker compose -f docker-compose.yml -f docker-compose.pinpoint.yml --profile pinpoint up -d

# dev + monitoring 동시 실행
docker compose --profile dev --profile monitoring up -d

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
# 기본 스택
mysql (healthy) ──┐
                  ├──→ backend ──→ frontend
redis (healthy) ──┘

# Pinpoint 스택 (docker-compose.pinpoint.yml overlay)
pinpoint-hbase (healthy) ──→ pinpoint-collector (started)
pinpoint-hbase (healthy) ──→ pinpoint-web
pinpoint-agent (completed) ──→ backend (+ Pinpoint Agent JVM 옵션)
pinpoint-collector (started) ──→ backend
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

## 모니터링

### 구성 파일

| 파일 | 역할 |
|---|---|
| `monitoring/prometheus.yml` | Prometheus 스크래프 설정 (백엔드 `/actuator/prometheus` 15초 주기) |
| `monitoring/grafana/provisioning/datasources/` | Grafana 데이터소스 자동 프로비저닝 (Prometheus) |
| `monitoring/grafana/provisioning/dashboards/` | Grafana 대시보드 자동 프로비저닝 설정 |
| `monitoring/grafana/dashboards/auth-service.json` | Auth Service 대시보드 정의 |

### 접근

| 서비스 | URL | 인증 |
|---|---|---|
| Prometheus | `http://localhost:9090` | 없음 |
| Grafana | `http://localhost:3001` | admin / admin |
| 메트릭 엔드포인트 | `http://localhost:8080/actuator/prometheus` | 없음 |

### 대시보드 업데이트

Grafana 파일 프로비저닝은 최초 로드 시에만 적용됩니다. 대시보드 JSON 수정 후 반영하려면 API로 직접 push합니다.

```bash
curl -s -u admin:admin \
  -X POST http://localhost:3001/api/dashboards/db \
  -H 'Content-Type: application/json' \
  -d "{\"dashboard\": $(cat monitoring/grafana/dashboards/auth-service.json), \"overwrite\": true}"
```

자세한 메트릭 목록과 추가 방법은 [metrics.md](./metrics.md)를 참고하세요.

---

## Pinpoint APM

분산 추적(APM) 스택. HTTP 요청의 전체 실행 경로(SQL, Redis, 외부 API 호출 등)를 트랜잭션 단위로 추적합니다. Prometheus/Grafana와 독립적으로 동작합니다.

### 아키텍처

```
[Backend JVM]
  └── Pinpoint Agent (JVM 에이전트, -javaagent)
        │  gRPC (포트 9991-9994)
        ▼
[Pinpoint Collector :9991-9994]
        │
        ▼
[HBase + ZooKeeper :2181]  ←→  [Pinpoint Web UI :8888]
```

### 구성 파일

| 파일 | 역할 |
|---|---|
| `docker-compose.pinpoint.yml` | Pinpoint 프로필 overlay — backend에 Agent 볼륨/환경변수 추가 |
| `pinpoint/agent-config/pinpoint.config` | Pinpoint Agent 설정 (Collector 주소, 샘플링, SQL 추적 등) |
| `Dockerfile` | `PINPOINT_ACTIVE=true` 시 `-javaagent` 옵션 자동 적용 |

### 실행

```bash
# 1. Pinpoint 전체 스택 시작 (HBase 초기화에 약 90초 소요)
docker compose -f docker-compose.yml -f docker-compose.pinpoint.yml --profile pinpoint up -d

# 2. HBase 준비 확인
docker logs pinpoint-hbase 2>&1 | grep "HMaster.*online"

# 3. Pinpoint Web UI 접근
open http://localhost:8888

# 4. 중지
docker compose -f docker-compose.yml -f docker-compose.pinpoint.yml --profile pinpoint down
```

### 접근

| 서비스 | URL | 설명 |
|---|---|---|
| Pinpoint Web UI | `http://localhost:8888` | 분산 추적 대시보드 |
| HBase ZooKeeper | `localhost:2181` | Collector / Web 내부 연결용 |

### 주요 설정 (`pinpoint/agent-config/pinpoint.config`)

| 설정 | 기본값 | 설명 |
|---|---|---|
| `profiler.sampling.counting.sampling-rate` | `10` | 100요청 중 10개 샘플링 (10%) |
| `profiler.jdbc.tracesqlbindvalue` | `true` | SQL 바인딩 파라미터 수집 (운영 시 `false` 권장) |
| `profiler.redis` | `true` | Redis (Lettuce) 호출 추적 |
| `profiler.spring.beans` | `true` | Spring Bean 메서드 추적 |
| `profiler.applicationName` | `auth-service` | Pinpoint UI에 표시될 앱 이름 |

### Pinpoint vs Prometheus/Grafana 비교

| 항목 | Prometheus + Grafana | Pinpoint |
|---|---|---|
| 목적 | 집계 메트릭 (카운터, 게이지, 히스토그램) | 개별 트랜잭션 추적 (APM) |
| 데이터 단위 | 집계값 (초당 요청 수, 평균 응답시간 등) | 개별 요청 단위 (어느 SQL이 느렸는지) |
| 적합한 질문 | "평균 응답시간이 얼마인가?" | "이 요청이 왜 느렸는가?" |
| 저장소 | Prometheus TSDB | HBase |
| 코드 변경 | 필요 (`@AuthTimed`, `AuthMetrics`) | 불필요 (JVM 에이전트 방식) |

### 환경변수 (docker-compose.pinpoint.yml)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `PINPOINT_ACTIVE` | `true` | 에이전트 활성화 여부 |
| `PINPOINT_AGENT_ID` | `auth-backend-1` | 인스턴스 식별자 (스케일 아웃 시 각 인스턴스마다 고유값) |
| `PINPOINT_APP_NAME` | `auth-service` | Pinpoint UI 애플리케이션 그룹 이름 |
