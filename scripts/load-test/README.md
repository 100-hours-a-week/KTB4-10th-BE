# 로컬 k6 부하 테스트

로컬 백엔드의 일반 조회 API와 가이드북 생성 흐름을 반복 측정하기 위한 k6 시나리오입니다. 운영 서버에는 이 스크립트를 실행하지 않습니다.

## Docker Compose로 전체 실행

Docker Desktop을 실행한 뒤 다음 명령으로 MySQL, 백엔드, Prometheus, Grafana, Loki, Alloy를 한 번에 실행합니다. 백엔드가 시작되면 부하 테스트 회원 fixture를 생성하고 관광 데이터도 자동으로 복원합니다.

백엔드는 Spring의 `local,loadtest` 프로필로 실행됩니다. `application-loadtest.yml`은 fixture 회원 수, 생성권 잔액과 세션 접두사를 관리하고, fixture 코드는 `local`과 `loadtest`가 동시에 활성화될 때만 동작합니다. k6 서비스도 Compose의 `loadtest` 프로필에 분리되어 평상시에는 실행되지 않습니다.

반복 측정 결과가 로컬 PC의 남은 자원에 따라 크게 흔들리지 않도록 백엔드와 MySQL은 기본적으로 각각 2 CPU, 2GB 메모리로 제한합니다. 백엔드 JVM은 컨테이너 메모리의 25%로 시작하고 최대 70%를 Heap으로 사용합니다.

```bash
docker compose -f compose.loadtest.yml up -d --build
docker compose -f compose.loadtest.yml ps
```

운영 또는 스테이징과 비슷한 자원 조건을 재현하려면 실행 전에 값을 변경합니다.

```bash
BACKEND_CPUS=1 \
BACKEND_MEMORY=1g \
MYSQL_CPUS=2 \
MYSQL_MEMORY=2g \
docker compose -f compose.loadtest.yml up -d --build
```

사용 가능한 설정과 기본값은 다음과 같습니다.

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `BACKEND_CPUS` | `2` | 백엔드 컨테이너 CPU 한도 |
| `BACKEND_MEMORY` | `2g` | 백엔드 컨테이너 메모리 한도 |
| `MYSQL_CPUS` | `2` | MySQL 컨테이너 CPU 한도 |
| `MYSQL_MEMORY` | `2g` | MySQL 컨테이너 메모리 한도 |
| `JVM_INITIAL_RAM_PERCENTAGE` | `25` | JVM 초기 Heap 비율 |
| `JVM_MAX_RAM_PERCENTAGE` | `70` | JVM 최대 Heap 비율 |

CPU 종류, Docker Desktop VM, 디스크와 네트워크가 운영 환경과 다르므로 로컬 결과는 운영 처리량 예측값이 아니라 동일한 조건에서 변경 전후 병목을 비교하는 기준으로 사용합니다.

각 도구는 다음 주소에서 확인합니다.

- 백엔드 API: `http://localhost:8080`
- 백엔드 health·metrics: `http://localhost:8081/actuator/health`, `http://localhost:8081/actuator/prometheus`
- Grafana: `http://localhost:3000` (`admin` / `admin`, `KGB Local Load Test` 대시보드 자동 생성)
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100/ready`
- Alloy: `http://localhost:12345`

일반 API 혼합 부하는 다음처럼 별도 일회성 컨테이너로 실행합니다. 실행 결과는 Prometheus로 전송되므로 Grafana에서 `k6_`로 시작하는 지표를 조회할 수 있습니다.

기본 40 VU 실행은 `4 → 10 → 20 → 40 → 0` 순서로 증가하며 총 4분 20초 동안 진행됩니다.

```bash
K6_VUS=40 docker compose -f compose.loadtest.yml --profile loadtest run --rm k6-general
```

지도 영역 조회만 측정하려면 지도 전용 시나리오를 실행합니다. 서울, 부산, 제주, 대구, 대전, 인천의 클러스터 영역과 상세 영역을 VU마다 순환하며 조회하고, 응답의 이미지 URL을 별도로 다운로드하지 않습니다.

기본 40 VU 실행 시간과 증가 단계는 일반 API 시나리오와 동일한 4분 20초입니다.

```bash
K6_MAP_VUS=40 docker compose -f compose.loadtest.yml --profile loadtest run --rm k6-map
```

전체 테스트 전에 연결과 결과 저장만 빠르게 확인하려면 5초 smoke test를 실행합니다.

```bash
K6_MAP_VUS=1 docker compose -f compose.loadtest.yml --profile loadtest run --rm -e SMOKE=true k6-map
```

가이드북 생성 부하는 동시 사용자 수를 단계적으로 늘려 실행합니다.

```bash
K6_GENERATION_VUS=1 docker compose -f compose.loadtest.yml --profile loadtest run --rm k6-guidebook
K6_GENERATION_VUS=2 docker compose -f compose.loadtest.yml --profile loadtest run --rm k6-guidebook
```

모든 Docker k6 실행은 종료 요약을 `results/load-test`에 UTC 실행 시각이 포함된 JSON 파일로 저장합니다.

```text
results/load-test/general-api-20261003T164000Z.json
results/load-test/map-contents-20261003T170000Z.json
results/load-test/guidebook-generation-20261003T173000Z.json
```

JSON 결과에는 요청 수, check 성공률, 실패율, 평균·p95·p99·최대 응답시간과 threshold 결과가 들어갑니다. 이 파일은 로컬 비교 보고서이므로 Git에는 커밋하지 않습니다. 상세 시계열은 계속 Prometheus에 저장되며 Grafana에서 확인합니다.

첫 로그인 후 홈 화면에 `KGB Local Load Test` 대시보드가 표시됩니다. 대시보드는 트래픽, API p95, 5xx 비율, k6 결과, JVM Heap, CPU, DB connection pool과 백엔드 로그를 5초 간격으로 갱신합니다.

Grafana의 **Explore → Loki**에서 `{service_name="backend"}`를 입력해 백엔드 컨테이너 로그만 별도로 검색할 수도 있습니다. 예외 발생 시 애플리케이션이 기록한 예외 타입, 메시지, stack trace도 같은 로그에서 조회합니다. 요청·응답 원문이나 세션·인증 정보는 별도로 기록하지 않습니다.

종료할 때는 다음 명령을 사용합니다.

```bash
docker compose -f compose.loadtest.yml down
```

DB와 Grafana 대시보드 등 로컬 테스트 데이터까지 완전히 초기화할 때만 `down -v`를 사용합니다. 이 명령은 Compose가 만든 로컬 볼륨을 삭제합니다.

```bash
docker compose -f compose.loadtest.yml down -v
```

## 백엔드만 직접 실행

Docker를 사용하지 않고 백엔드만 직접 실행할 수도 있습니다. 이 경우 MySQL 8.4 로컬 DB와 관광 데이터를 직접 준비한 뒤 `local,loadtest` 프로필로 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=local,loadtest \
LOAD_TEST_USER_COUNT=40 \
./gradlew bootRun
```

fixture는 다음 원문 세션을 생성하고 DB에는 SHA-256 해시만 저장합니다.

```text
k6-local-session-001
k6-local-session-002
...
k6-local-session-040
```

회원 수는 1~999 범위에서 지정할 수 있습니다. 생성권 기본 잔액과 세션 접두사도 변경할 수 있습니다.

```bash
LOAD_TEST_CREDIT_BALANCE=100
LOAD_TEST_SESSION_PREFIX=k6-local-session-
```

fixture는 `local`과 `loadtest` 프로필이 함께 활성화될 때만 실행되며 외부 테스트용 API를 노출하지 않습니다. 같은 설정으로 다시 실행하면 기존 테스트 회원을 재사용하고 세션을 갱신합니다.

헬스와 Prometheus 메트릭은 별도 관리 포트에서 확인합니다.

```text
http://localhost:8081/actuator/health
http://localhost:8081/actuator/prometheus
```

## 일반 API 혼합 부하

각 VU가 fixture로 생성한 서로 다른 회원 세션을 사용해 조회 API를 정해진 비율로 호출합니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=40 \
  scripts/load-test/general-api.js
```

기본 임계값은 실패율 1% 미만, 전체 요청 p95 1초 미만입니다. 이 값은 초기 기준선이며 측정 결과를 바탕으로 별도 조정합니다.

## 가이드북 생성 E2E 부하

회원은 동시에 하나의 활성 생성 작업만 가질 수 있으므로 VU마다 fixture로 생성한 서로 다른 회원 세션을 사용합니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=2 \
  scripts/load-test/guidebook-generation.js
```

필요하면 여행 날짜를 명시할 수 있습니다. 지정하지 않으면 실행일 기준 2일 뒤부터 3일 뒤까지를 사용합니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=1 \
  -e TRIP_START_DATE=2026-10-10 \
  -e TRIP_END_DATE=2026-10-11 \
  scripts/load-test/guidebook-generation.js
```

기본값과 다른 세션 접두사를 사용했다면 백엔드와 k6에 동일하게 전달합니다.

```bash
LOAD_TEST_SESSION_PREFIX=my-local-session- ./gradlew bootRun
k6 run -e SESSION_PREFIX=my-local-session- scripts/load-test/general-api.js
```

가이드북 생성 테스트는 다음을 출력합니다.

- 생성 요청 접수 성공 여부
- 생성 완료·실패 건수
- 접수부터 완료까지 `generation_e2e_duration`
- p90·p95·p99 생성 시간

로컬 프로필의 `FakeGuidebookAiClient`를 사용하므로 실제 AI 서버의 성능을 측정하지 않습니다. 이 시나리오는 백엔드의 생성 접수, 상태 polling, 결과 저장 흐름을 검증합니다.

## 직접 구성한 Grafana 확인

로컬 백엔드의 `/actuator/prometheus`를 Prometheus가 수집하도록 구성한 경우 Grafana에서 다음 지표를 함께 확인합니다.

- API별 요청 수와 응답 시간
- 4xx·5xx
- JVM heap·GC·thread
- HikariCP active·idle·pending connection

Compose 대신 기존 Grafana를 사용할 때 Prometheus 컨테이너에서는 호스트 백엔드를 `host.docker.internal:8081`로 수집할 수 있습니다.

## 주의사항

- fixture의 예측 가능한 세션은 로컬 부하 테스트에서만 사용합니다.
- `VUS`가 백엔드 실행 시 지정한 `LOAD_TEST_USER_COUNT`를 넘지 않게 설정합니다.
- DB 비밀번호와 실제 사용자 세션은 파일이나 Git에 저장하지 않습니다.
- 생성 테스트는 DB에 생성 작업과 가이드북 데이터를 남깁니다.
- 높은 VUS를 바로 적용하지 말고 `1 → 2 → 5 → 10 → 20` 순서로 확인합니다.
- 테스트 전후 동일한 데이터 조건을 사용해야 결과를 비교할 수 있습니다.
