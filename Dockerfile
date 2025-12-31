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

# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 시스템 도구 설치
RUN apk add --no-cache bash

# Gradle Wrapper 복사
COPY gradlew .
COPY gradle gradle

# Gradle 빌드 파일 복사
COPY build.gradle settings.gradle ./

# 소스 코드 복사
COPY src src

# 실행 권한 부여
RUN chmod +x ./gradlew

# Gradle 빌드 (네트워크 이슈 대비 재시도 로직)
# Retry Gradle build up to 3 times in case of network issues
RUN for i in 1 2 3; do ./gradlew bootJar --no-daemon && break || sleep 5; done

# Runtime stage
FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp

# Healthcheck 및 디버깅용 curl 설치 (일부 서비스에서 사용)
RUN apk update && apk add --no-cache curl

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app.jar"]

