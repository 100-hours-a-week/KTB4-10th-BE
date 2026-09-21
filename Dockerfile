# 1. Build(멀티스테이지 빌드)
# 빌드를 위해 도구 모음 jdk 제공
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .


#의존성을 먼저 다운로드
# cache mount를 이용해 Gradle 캐시를 빌드 간 재사용

RUN --mount=type=cache,target=/root/.gradle \
		./gradlew dependencies --no-daemon

COPY src src

#jar생성도 cache mount
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

# 2. Run
# 자바 21 환경 jre 제공
FROM eclipse-temurin:21-jre

WORKDIR /app

# 애플리케이션 실행용 Non-root 사용자/그룹 생성
RUN groupadd --system kgb \
    && useradd --system --gid kgb kgbUser

#위의 빌드단계에서 생성된 jar만 복사
#jar의 소유권도 지정
COPY --from=builder --chown=kgbUser:kgb /app/build/libs/*.jar app.jar

#사용자 지정
USER kgbUser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]