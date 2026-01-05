# ========================================
# 통합 Spring Boot 애플리케이션 Dockerfile
# 
# 통합된 서비스:
# - Gateway (Spring Cloud Gateway)
# - Common Service
# - User Service
# - Environment Service
# - Social Service
# - Governance Service
# - OAuth Service (Google Login + JWT)
# ========================================

########## Build Stage ##########
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 시스템 도구 설치
RUN apk add --no-cache bash

# Gradle Wrapper & 설정 먼저 복사 (캐시 최적화)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

# 의존성 다운로드 (캐시 효율 개선 - 네트워크 이슈 대비 재시도)
RUN for i in 1 2 3; do \
      ./gradlew dependencies --no-daemon && break || sleep 10; \
    done || true

# 소스 코드 복사
COPY src src

# 애플리케이션 빌드 (재시도 포함)
RUN for i in 1 2 3; do \
      ./gradlew bootJar --no-daemon && break || sleep 10; \
    done

########## Runtime Stage ##########
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
VOLUME /tmp

# Healthcheck 및 디버깅용 curl 설치
RUN apk update && apk add --no-cache curl

# 빌드 결과 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 기본 JVM 옵션 (메모리 안정화)
ENV JAVA_OPTS="-Xms256m -Xmx768m"

# Spring profile 외부 지정 가능
ENV SPRING_PROFILES_ACTIVE=production

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

