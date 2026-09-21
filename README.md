# KGB Backend

KGB 서비스의 백엔드 애플리케이션입니다.

## 가이드북 생성 API 구현 상태

PR #41의 생성 접수 API는 `PENDING` 저장까지만 구현되어 있으며 아직 사용자에게 제공 가능한 생성 API가 아니다. AI 트리거가 없어 작업이 계속 `PENDING`으로 남고 후속 요청이 `GENERATION_IN_PROGRESS`로 차단될 수 있다. #42의 회원·취향·생성권 검증, 동시성 처리와 커밋 후 AI 트리거 연동 및 검증을 마치기 전에는 프론트엔드 사용자 흐름에 연결하거나 운영에 공개하지 않는다. 상태 조회는 #43에서 구현한다.

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
- Flyway는 `src/main/resources/db/migration/V1__init_v1_schema.sql`부터 V1 스키마를 관리합니다. 한 번 적용된 migration은 수정하지 않고 후속 변경을 새 migration으로 추가합니다.
- MySQL 8.4의 정확한 패치 버전은 로컬·CI·운영에서 동일하게 맞춥니다.

## 브라우저 CSRF·CORS 전제

- 운영은 브라우저가 `https://kguidebook.site`에 접속하고 CloudFront가 같은 호스트의
  `/api/*` 요청을 백엔드로 전달하는 구성을 전제로 합니다.
- 이 구성에서는 FE가 host-only `XSRF-TOKEN` 쿠키를 읽어 상태 변경 요청의
  `X-XSRF-TOKEN` 헤더로 전달합니다. `KGB_SESSION`은 계속 HttpOnly입니다.
- 로컬에서 FE와 BE 포트가 다르면 FE origin을 `CORS_ALLOWED_ORIGINS`에 등록하고 두 서버
  모두 `localhost`를 사용합니다. `localhost`와 `127.0.0.1`을 섞으면 쿠키 호스트가 달라집니다.
- FE와 BE를 실제로 서로 다른 호스트에 배포하면 FE JavaScript는 백엔드의 host-only
  쿠키를 읽을 수 없습니다. 이 경우 현재 계약을 그대로 사용하지 말고 동일 호스트 프록시
  또는 CSRF 토큰 응답 방식 중 하나를 보안 검토 후 확정해야 합니다.
- 배포 전 실제 브라우저에서 CSRF 조회 → 쿠키 확인 → preflight → 상태 변경 요청 순서를
  검증합니다.

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
