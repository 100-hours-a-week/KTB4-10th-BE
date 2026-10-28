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

### 로컬 AI 서버 선택

`local`은 기본적으로 백엔드 내부 Fake AI 클라이언트를 사용합니다.
Docker 등으로 별도 실행한 AI 서버를 호출하려면 실행 환경변수를 다음처럼 설정합니다.

```bash
SPRING_PROFILES_ACTIVE=local AI_CLIENT_MODE=http AI_SERVER_BASE_URL=http://localhost:8000 ./gradlew bootRun
```

- `AI_CLIENT_MODE=fake` 또는 미설정: 기존 내부 Fake 사용
- `AI_CLIENT_MODE=http`: HTTP로 AI 접수 및 상태 조회
- 로컬 HTTP 기본 주소: `http://localhost:8000`
- 연결/응답 타임아웃: `AI_SERVER_CONNECT_TIMEOUT=3s`, `AI_SERVER_READ_TIMEOUT=10s`
- `prod`는 로컬 모드 설정과 관계없이 기존 HTTP 클라이언트를 사용합니다.
- 백엔드도 컨테이너라면 `localhost`는 백엔드 컨테이너 자체입니다. 공유 Docker
  네트워크의 AI 서비스 주소를 사용하거나 호스트에 공개한 포트로 접근할 수 있도록 구성합니다.
- AI 서버의 `LLM_MODE=fake`, `FAKE_LLM_DELAY_SEC=60`은 AI 컨테이너에 설정합니다.
  생성 지연과 개별 상태 조회 HTTP 응답 타임아웃은 별개이므로 생성 지연에 맞춰
  `AI_SERVER_READ_TIMEOUT`을 늘릴 필요는 없습니다.
- 내부 Fake로 접수한 `fake-job-*` 작업은 외부 AI 서버에 존재하지 않습니다.
  모드 전환 전 진행 중 작업을 종료하고 전환 후 새 작업으로 검증합니다.

기본 헬스 체크는 내부 관리 포트 `8081`의 `GET /actuator/health`에서 확인할 수 있습니다.

- JPA는 스키마를 자동 변경하지 않고 `ddl-auto=validate`로 Flyway 결과만 검증합니다.
- Flyway는 `src/main/resources/db/migration/V1__init_v1_schema.sql`부터 V1 스키마를 관리합니다. 한 번 적용된 migration은 수정하지 않고 후속 변경을 새 migration으로 추가합니다.
- MySQL 8.4의 정확한 패치 버전은 로컬·CI·운영에서 동일하게 맞춥니다.

## AI 콜백 모드

AI가 진행·완료·실패 콜백을 구현한 뒤 다음 설정으로 전환합니다.

```dotenv
AI_CLIENT_MODE=http
AI_SERVER_BASE_URL=http://localhost:8000
AI_GENERATION_DELIVERY_MODE=callback
AI_CALLBACK_TOKEN=AI와-BE에-동일하게-주입할-랜덤-비밀값
AI_GENERATION_TIMEOUT=15m
```

토큰은 실제 실행 환경에만 주입하고 커밋하지 않습니다. 콜백 모드에서는 상태 GET과
백그라운드 작업이 AI를 폴링하지 않습니다. 진행·종료 SSE, Redis 설정, 15분 메모리
타이머와 재시작 한계는 [AI 콜백 연동](./docs/AI-콜백-연동.md)을 확인하세요.
다중 인스턴스에서는 `AI_GENERATION_REDIS_ENABLED=true`와 같은 채널을 사용합니다.
기존 AI/Fake 클라이언트 검증은 기본값 `AI_GENERATION_DELIVERY_MODE=polling`으로 유지합니다.

## 관측성

- Prometheus 수집 엔드포인트: `GET http://<backend-host>:8081/actuator/prometheus`
- 제공 범위: CPU·메모리, JVM Heap·GC·Thread, HTTP 요청 수·지연·상태, HikariCP Active·Idle·Pending·Max
- 메트릭에는 `application=kgb` 공통 태그가 포함됩니다.
- Actuator는 업무 API 포트 `8080`과 분리된 관리 포트 `8081`에서 제공됩니다.
- `/actuator/prometheus`는 Prometheus scraper가 서비스 세션 없이 접근하며, 운영에서는 관리 포트를 공용 라우팅에 연결하지 않고 보안 그룹이나 프록시로 접근 범위를 제한합니다.
- 백엔드는 메트릭 엔드포인트까지만 제공합니다. Prometheus 서버, Grafana, 수집 주기와 경보 임계값은 인프라에서 구성합니다.
- 별도 로그 파일은 생성하지 않습니다. 애플리케이션 로그는 콘솔로 출력되어 Docker stdout/stderr에서 수집합니다.
- 예기치 않은 예외와 5xx 업무 예외는 trace ID, HTTP method·URI, 오류 코드, 예외·root cause 타입과 stack trace를 남깁니다. 요청 본문과 인증 비밀값은 기록하지 않습니다.

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
