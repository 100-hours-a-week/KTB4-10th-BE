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
./gradlew checkstyleMain
./gradlew spotbugsMain
./gradlew build
```

- Checkstyle은 Google Java Style 기반 위반을 `build/reports/checkstyle/`에 기록하지만 Task를 실패시키지 않습니다.
- SpotBugs 4.9.8은 Rank 1~4 문제를 검사하며 발견 시 Task를 실패시킵니다. HTML 리포트는 `build/reports/spotbugs/main.html`에서 확인할 수 있습니다.
- V1에서는 main Source Set만 정적 분석하며 `checkstyleTest`, `spotbugsTest`는 비활성화합니다.

협업 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 따릅니다.
