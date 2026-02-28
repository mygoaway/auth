# Stage 1: Build
FROM gradle:8.14-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle build -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=build /app/build/libs/*.jar app.jar

# Pinpoint Agent 마운트 경로 (docker-compose volumes로 주입)
RUN mkdir -p /pinpoint-agent && chown appuser:appgroup /pinpoint-agent

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

# PINPOINT_ACTIVE=true 환경변수가 설정된 경우 Pinpoint Agent를 활성화
# Agent JAR는 /pinpoint-agent 볼륨에서 마운트됨
ENTRYPOINT ["/bin/sh", "-c", "\
  if [ \"$PINPOINT_ACTIVE\" = \"true\" ] && [ -f /pinpoint-agent/pinpoint-bootstrap.jar ]; then \
    exec java \
      -javaagent:/pinpoint-agent/pinpoint-bootstrap.jar \
      -Dpinpoint.agentId=${PINPOINT_AGENT_ID:-auth-backend-1} \
      -Dpinpoint.applicationName=${PINPOINT_APP_NAME:-auth-service} \
      -Dpinpoint.config=/pinpoint-agent/pinpoint.config \
      -jar app.jar; \
  else \
    exec java -jar app.jar; \
  fi"]
