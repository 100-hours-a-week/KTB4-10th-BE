# KGB Backend

KGB 서비스의 백엔드 애플리케이션입니다.

## 개발 환경

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3 Wrapper / Kotlin DSL
- MySQL 8.4 LTS / InnoDB / utf8mb4

## 로컬 데이터베이스 설정

각 개발자는 자신의 MySQL 8.4 LTS 데이터베이스를 사용합니다. `.env.example`과 같은 이름의 환경변수를 설정하되 실제 비밀번호는 Git에 커밋하지 않습니다.

```bash
export DB_URL="jdbc:mysql://localhost:3306/kgb_local"
export DB_USERNAME="kgb"
export DB_PASSWORD="로컬 비밀번호"
```

Spring Boot는 프로젝트 루트의 `.env`를 자동으로 읽지 않으므로 IntelliJ 실행 구성 또는 셸 환경변수로 전달합니다.

## 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 헬스 체크는 `GET /actuator/health`에서 확인할 수 있습니다.

- JPA는 스키마를 자동 변경하지 않고 `ddl-auto=validate`로 Flyway 결과만 검증합니다.
- Flyway migration은 후속 작업에서 `src/main/resources/db/migration/`에 추가합니다.
- MySQL 8.4의 정확한 패치 버전은 로컬·CI·운영에서 동일하게 맞춥니다.

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
