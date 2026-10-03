# 로컬 k6 부하 테스트

로컬 백엔드의 일반 조회 API와 가이드북 생성 흐름을 반복 측정하기 위한 k6 시나리오입니다. 운영 서버에는 이 스크립트를 실행하지 않습니다.

## 사전 준비

1. MySQL 8.4 로컬 DB와 관광 데이터를 준비합니다.
2. `local,loadtest` 프로필로 백엔드를 실행합니다.
3. 애플리케이션 시작 시 fixture가 회원별 취향, 생성권과 인증 세션을 준비합니다.

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

## Grafana 확인

로컬 백엔드의 `/actuator/prometheus`를 Prometheus가 수집하도록 구성한 경우 Grafana에서 다음 지표를 함께 확인합니다.

- API별 요청 수와 응답 시간
- 4xx·5xx
- JVM heap·GC·thread
- HikariCP active·idle·pending connection

Prometheus와 Grafana의 설치·수집 설정은 백엔드 저장소의 범위에 포함하지 않습니다. 로컬 Grafana를 사용할 때 Prometheus 컨테이너에서는 호스트 백엔드를 `host.docker.internal:8081`로 수집할 수 있습니다.

## 주의사항

- fixture의 예측 가능한 세션은 로컬 부하 테스트에서만 사용합니다.
- `VUS`가 백엔드 실행 시 지정한 `LOAD_TEST_USER_COUNT`를 넘지 않게 설정합니다.
- DB 비밀번호와 실제 사용자 세션은 파일이나 Git에 저장하지 않습니다.
- 생성 테스트는 DB에 생성 작업과 가이드북 데이터를 남깁니다.
- 높은 VUS를 바로 적용하지 말고 `1 → 2 → 5 → 10 → 20` 순서로 확인합니다.
- 테스트 전후 동일한 데이터 조건을 사용해야 결과를 비교할 수 있습니다.
