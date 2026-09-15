# KGB Backend

KGB 서비스의 백엔드 애플리케이션입니다.

## 개발 환경

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper / Kotlin DSL

## 실행

```bash
./gradlew bootRun
```

기본 헬스 체크는 `GET /actuator/health`에서 확인할 수 있습니다.

## 검증

```bash
./gradlew test
./gradlew build
```

협업 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 따릅니다.
