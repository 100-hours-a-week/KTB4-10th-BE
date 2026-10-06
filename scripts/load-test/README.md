# 로컬 k6 부하테스트

팀원이 동일한 로컬 환경에서 MySQL, 백엔드, Prometheus, Grafana, Loki와 k6를 실행하기 위한 구성입니다. 실제 관광 데이터는 저장소에 포함하지 않습니다. TourAPI 스케줄러가 정해진 주기에 데이터를 로컬 MySQL에 적재한 뒤, DB 행 개수를 확인하고 부하테스트를 시작합니다.

> 이 환경은 변경 전후를 같은 조건에서 비교하기 위한 용도입니다. 로컬 PC와 Docker Desktop의 CPU·메모리·디스크 성능이 다르므로 결과를 운영 처리량으로 해석하지 않습니다.

## 1. 포함되는 구성

```text
compose.loadtest.yml
├─ mysql            로컬 테스트 DB
├─ backend          local,loadtest 프로필 백엔드
├─ prometheus       애플리케이션·k6 지표 저장
├─ grafana          공통 대시보드
├─ loki / alloy     백엔드 로그 수집
└─ k6-*             일회성 부하테스트 실행기

scripts/load-test/
├─ README.md
├─ run-with-summary.sh
├─ general-api.js
├─ map-contents.js
├─ map-zoom-15.js
├─ guidebook-generation.js
└─ scenarios/       새 시나리오를 추가하는 위치
```

부하테스트 결과 JSON, MySQL 데이터, 실제 API 키는 Git에 저장하지 않습니다.

## 2. 사전 준비

- Docker Desktop
- TourAPI 서비스 키
- 카카오 REST API 키, Client Secret, Admin Key
- TourAPI 스케줄러가 포함된 백엔드 코드

환경변수 예시를 복사하고 실제 로컬 개발 키를 입력합니다.

```bash
cp .env.loadtest.example .env.loadtest
```

`.env.loadtest`는 Git에 포함되지 않습니다. 개발 DB나 운영 DB 접속 정보를 입력하지 않습니다. Compose 내부 백엔드는 Compose가 생성한 로컬 MySQL만 사용합니다.

## 3. 로컬 스택 실행

```bash
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  up -d --build
```

실행 상태를 확인합니다.

```bash
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  ps
```

접속 주소는 다음과 같습니다.

- 백엔드 API: `http://localhost:8080`
- health: `http://localhost:8081/actuator/health`
- Prometheus metrics: `http://localhost:8081/actuator/prometheus`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100/ready`

Grafana에는 `KGB Local Load Test` 대시보드가 자동으로 생성됩니다.

## 4. 관광 데이터 적재 확인

백엔드가 healthy 상태가 되더라도 관광 데이터 적재가 완료됐다는 의미는 아닙니다. TourAPI 스케줄러가 정해진 주기에 실행될 때까지 기다린 뒤 다음 명령으로 행 개수를 확인합니다.

```bash
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  exec mysql \
  mysql -ukgb -pkgb-local-password -Dkgb_local \
  --table \
  --execute="
    SELECT 'regions' AS table_name, COUNT(*) AS row_count FROM regions
    UNION ALL
    SELECT 'tourism_contents', COUNT(*) FROM tourism_contents
    UNION ALL
    SELECT 'event_details', COUNT(*) FROM event_details;
  "
```

`tourism_contents`가 0건이면 테스트를 실행하지 않습니다. 데이터 수는 TourAPI 동기화 시점에 따라 바뀔 수 있으므로 특정 숫자와 정확히 일치하는지보다, 적재가 완료됐고 비교할 두 테스트가 동일한 데이터 상태인지 확인합니다.

필요하면 백엔드 로그에서 스케줄러 실행 상태를 확인합니다.

```bash
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  logs -f backend
```

## 5. 부하테스트 회원 fixture

백엔드는 `local,loadtest` 프로필로 실행되며 시작할 때 테스트 회원, 취향, 생성권과 세션을 만듭니다.

- 기본 회원 수: 500명
- 지원 범위: 1~999명
- 기본 세션: `k6-local-session-001`부터 순차 생성
- 기본 생성권: 100

fixture에는 실제 사용자 개인정보나 실제 세션이 포함되지 않습니다. 인증 우회는 사용하지 않으며, 모든 k6 요청은 운영 코드와 동일하게 DB에서 세션을 조회하고 `lastUsedAt`을 갱신합니다.

VU는 반드시 `LOAD_TEST_USER_COUNT` 이하로 설정합니다. 회원 수를 바꾸려면 스택 시작 전에 지정합니다.

```bash
LOAD_TEST_USER_COUNT=700 docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  up -d --build
```

## 6. 제공 시나리오

모든 명령은 프로젝트 루트에서 실행합니다. k6는 테스트가 끝나면 요약을 `results/load-test`에 JSON으로 저장하지만 이 파일은 Git에 커밋하지 않습니다.

### 일반 API 혼합

```bash
K6_VUS=40 docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm k6-general
```

fixture 회원별로 일반 조회 API를 정해진 비율로 호출합니다.

### 지도 이동

```bash
K6_MAP_VUS=100 docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm k6-map
```

최초 진입, 같은 지역 이동, 단계적 확대를 섞어 실제 지도 탐색에 가까운 요청을 만듭니다.

### zoom 15 상세 지도

```bash
K6_MAP_ZOOM_15_VUS=100 docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm k6-map-zoom-15
```

zoom 15 상세 조회의 구현 변경 전후를 비교할 때 사용합니다. 비교할 때는 VU, 지역 수, 데이터 상태와 애플리케이션 상태를 동일하게 유지합니다.

### 가이드북 생성

```bash
K6_GENERATION_VUS=2 docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm k6-guidebook
```

로컬 `FakeGuidebookAiClient`를 사용하므로 실제 AI 서버 성능이 아니라 생성 접수, polling과 결과 저장 흐름을 측정합니다.

## 7. Smoke test

본 테스트 전에 VU 1명으로 연결, 인증, 결과 저장을 확인합니다.

```bash
K6_MAP_VUS=1 docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm \
  -e SMOKE=true k6-map
```

## 8. 새 시나리오 추가

새 파일은 `scripts/load-test/scenarios/<scenario-name>.js`에 추가합니다. 공통 환경변수는 다음과 같습니다.

- `BASE_URL`: Compose가 자동으로 `http://backend:8080` 전달
- `VUS`: `K6_CUSTOM_VUS` 값
- `SESSION_PREFIX`: fixture 세션 접두사

예시:

```text
scripts/load-test/scenarios/search-contents.js
```

공통 runner로 실행합니다.

```bash
K6_SCRIPT=scenarios/search-contents.js \
K6_RESULT_NAME=search-contents \
K6_CUSTOM_VUS=50 \
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  --profile loadtest run --rm k6-custom
```

`K6_RESULT_NAME`은 결과 파일명과 Prometheus의 `testid` 태그에 사용합니다. 기존 시나리오와 다른 환경변수가 필요하면 `docker compose run -e NAME=value`로 전달합니다.

새 시나리오는 최소한 다음 항목을 포함합니다.

- 요청 성공 여부를 검사하는 `check`
- `http_req_failed` threshold
- `http_req_duration` p95 threshold
- VU마다 서로 다른 fixture 세션 사용
- 테스트 목적과 입력 데이터 조건을 설명하는 주석

## 9. Grafana에서 확인할 지표

- k6 완료 요청 수, RPS, 평균, p95, p99와 최대 응답시간
- HTTP 실패율과 5xx
- 지도 API 단계별 p95
- HikariCP active, idle, pending과 acquire 시간
- JVM Heap과 애플리케이션 CPU
- MySQL CPU
- 변경 대상 기능에서 별도로 노출한 애플리케이션 지표

Grafana 시간 범위에는 한 번의 테스트 구간만 포함합니다. 여러 실행이 섞이면 k6 누적값과 최대값을 잘못 해석할 수 있습니다.

## 10. 종료와 초기화

컨테이너만 종료합니다.

```bash
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  down
```

MySQL, Prometheus와 Grafana 로컬 볼륨까지 삭제하려면 다음 명령을 사용합니다.

```bash
docker compose \
  --env-file .env.loadtest \
  -f compose.loadtest.yml \
  down -v
```

`down -v` 이후에는 관광 데이터를 다음 스케줄러 실행에서 다시 적재해야 합니다.

## 11. Git에 포함하는 파일

포함:

- `compose.loadtest.yml`
- `.env.loadtest.example`
- `scripts/load-test/**/*.js`
- `scripts/load-test/run-with-summary.sh`
- `scripts/load-test/README.md`
- `docker/observability/**`
- 부하테스트 fixture 생성 코드와 `application-loadtest.yml`

제외:

- `.env.loadtest`
- TourAPI에서 수집한 실제 데이터와 DB dump
- `results/load-test/*.json`
- Docker volume
- 실제 세션, API 키와 DB 접속 정보
