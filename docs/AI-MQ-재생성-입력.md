# AI MQ 재생성 입력 스냅샷 (#308 후속)

관련 요구사항: BR-GDE-16, FR-GDE-07/08/09. PR #316의 저장 전 입력 스냅샷 구조를 확장한다.
RabbitMQ 발행기는 이 후속 단계에서 구현한다. AI 소비는 아직 구현하지 않으며, 기본 HTTP 경로와 사용자 정책은 유지한다.
전환 중에는 RabbitMQ가 API 기동 필수 의존성이 아니므로 기본 health/readiness에서 Rabbit health contributor를 제외한다.
실제 AI 소비를 운영 활성화할 때는 브로커 장애를 어떻게 readiness에 반영할지 별도로 정한다.

로컬 발행 검증에서는 단일 RabbitMQ와 영구 볼륨을 사용하고 quorum 선언을 유지한다.
`AI_GENERATION_OUTBOX_ENABLED=true`와 `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD`를
BE 실행 환경에 주입하면 `guidebook.generation`을 선언하고 발행기를 활성화한다.
AI consumer 연결 전에는 발행 메시지가 큐에 대기하며 DB 내부 상태는 QUEUED로 남는다.

최초 발행은 default exchange가 아니라 `guidebook.generation.exchange`의
`guidebook.generation` routing key를 사용한다. 소비자가 메시지를 NACK하면 메인 큐의
dead-letter 설정이 `guidebook.generation.retry` 큐로 보내고, 설정된 지연 시간이 지나면
메인 exchange로 되돌린다. 재시도 한도를 넘거나 복구할 수 없는 메시지는 소비자가
`guidebook.generation.dlx`의 `guidebook.generation.failed` routing key로 발행하여
`guidebook.generation.dlq`에 격리한다.

## 입력 원본

- MQ 준비 경로에서는 최근 COMPLETED 작업을 completed_at DESC, id DESC로 선택한다.
- AI가 발급한 ID가 아닌 BE GenerationJob ID를 사용한다.
- 원본 message_payload.input의 여행 조건/취향/contents를 복사한다. 후보를 최신 DB로 재조회하지 않는다.
- 현재 guidebook 제목/version과 itinerary_days/items의 일정 스냅샷을 읽는다.
- source_job_id, source_guidebook_version, title, feedback, previous_plan을 덮어쓴다.
- previous_plan은 content_id, day, order, start_time, duration_minutes의 배열이다.
  이는 AI의 PlanItem 입력 형식이며 기존 HTTP 재생성 API의 본문과 동일하지 않다.
- title은 기존 제목 보존용이다. AI 소비 구현 때 날짜/시간 및 PlanItem 변환 계약을 함께 검증해야 한다.

## 저장과 재시도

접수 트랜잭션에서 {schemaVersion:1,input:{...}}을 만든 뒤 작업과 함께 저장한다.
실패 시 전부 롤백한다. 발행 시 jobId/messageId/attempt/jobType과 input.request_id를 붙인다.
사용자 재시도는 최초 재생성 접수의 입력을 재사용한다. 현재 일정이나 후보를 다시 조회하지 않는다.
원본 스냅샷은 수정하지 않는다. 발행 재시도·사용자 재시도는 기존 정책대로 분리한다.

## 전환 경계

- 원본 후보 스냅샷이 없는 기존 HTTP 작업은 활성화된 MQ 경로에서 409로 거절한다.
  원본이 없는 것을 최신 후보로 조용히 대체하지 않는다. 기존 작업의 전환 정책은 후속 결정이다.
- 기본 false 설정에서는 기존 ai_job_id 기반 HTTP 재생성을 유지한다.
- AI 소비·실행 복구 구현 전 운영 활성화하지 않는다.
- 최종 전환 후 HTTP 경로와 경로 선택 설정/분기를 제거한다.
- FE 공개 상태는 기존과 동일하다. 접수·조회·SSE에서 내부 대기 상태는 PENDING으로 반환한다.
  기존 작업의 전환 오류 안내는 운영 활성화 전에 별도로 검토한다.

## 최종 MQ 전환 후 제거/단순화할 코드

| 현재 코드 | 전환 완료 후 처리 |
| --- | --- |
| ai.generation.outbox-enabled / ConditionalOnProperty / 빈 존재 여부 기반 분기 | 제거하고 MQ 준비를 단일 경로로 사용 |
| GenerationSubmissionQueue 및 admit()의 커밋 후 submit 호출 | 공유 MQ 발행기로 대체 |
| admit()의 writes.execute() 및 접수 진입점 Propagation.NEVER | HTTP 큐 전달이 사라진 뒤 접수 서비스를 @Transactional로 단순화. DB 트랜잭션 자체는 유지 |
| Admission.jobId의 HTTP 전달 대상/null 구분 | 커밋 후 HTTP 전달 조정이 없어지면 제거 |
| GenerationCallbackSubmissionService와 AI HTTP 접수 클라이언트 | 실제 AI 직접 소비 전환·기존 진행 작업 정리 후 제거 |
| source_ai_job_id / ai_job_id 기반 재생성 원본 선택 / 기존 PENDING 호환 상태 | 기존 작업 소진·스냅샷 이관 정책 적용 후 제거 |

결과 저장·권한·멱등 처리·사용자 202 응답과 DB 트랜잭션은 유지한다.
진행/결과 콜백은 실제 AI 결과 전달 계약을 확정하기 전 일괄 제거하지 않는다.
현재 writes.execute()는 저장 트랜잭션이 반환되어 커밋된 다음 HTTP 큐에 등록하기 위한 경계다.
MQ 발행기는 별도로 DB를 조회하므로 최종 경로에서는 이 커밋 후 전달 조정이 필요하지 않다.

## 함수 흐름

```mermaid
flowchart TD
    T["admit → writes.execute: 트랜잭션 시작"] --> K{작업 종류}
    K -->|최초 생성| A["prepareInitial → mapper.mapInput"]
    K -->|재생성| B["prepareRegeneration → regeneration.build"]
    K -->|재생성 사용자 재시도| C["기존 snapshot 보관 → job.retry → prepareRegenerationRetry"]
    A --> P["job.preparePublication: 입력 저장 · PUBLISH_PENDING"]
    B --> P
    C --> P
    P --> N{신규 작업인가}
    N -->|예| S["repository.save: INSERT"]
    N -->|아니오| U["기존 엔티티 변경 감지: UPDATE"]
    S --> E["writes.execute 반환: COMMIT → 사용자 202"]
    U --> E
```

buildMessage(job)는 요청 접수 흐름에서 호출하지 않는다.
발행기가 저장된 입력에 jobId/attempt/messageId/jobType을 붙여 MQ에 보낼 때 사용한다.
위 도식의 트랜잭션은 맨 처음부터 COMMIT까지 전체를 감싸며 save 뒤에 시작되는 것이 아니다.

## 발행기 흐름

`GenerationOutboxPublisher`는 접수 트랜잭션의 AFTER_COMMIT 이벤트로 즉시 한 번 깨어나고,
500ms 폴링으로 누락된 이벤트·재발행 예약·만료 선점을 회수한다. 이벤트는 지연을 줄이는 신호일 뿐
전달 보장의 근거가 아니다.

```mermaid
sequenceDiagram
    participant R as 접수 트랜잭션
    participant P as GenerationOutboxPublisher
    participant DB as generation_jobs
    participant MQ as RabbitMQ

    R->>DB: message_payload 저장, PUBLISH_PENDING
    R-->>P: AFTER_COMMIT wakeup
    P->>DB: Tx 1: SKIP LOCKED 선점·토큰/만료시각 저장 후 커밋
    P->>MQ: Tx 밖에서 persistent 메시지 발행·confirm/return 대기
    P->>DB: Tx 2: 같은 시도·토큰이면 QUEUED, 실패면 backoff 예약
```

발행 확인 직전/직후 프로세스가 종료되면 같은 `messageId`가 재발행될 수 있다. 따라서 후속 AI 소비자는
`messageId` 기준 중복 처리를 해야 한다. 선점 토큰은 만료된 선점을 다른 인스턴스가 회수한 뒤
이전 발행기의 늦은 결과가 현재 작업 상태를 덮어쓰지 못하게 한다.

## AI consumer 전달 계약

BE는 최초 생성과 재생성을 같은 `guidebook.generation` 큐에 발행한다. AI consumer는
`jobType`으로 처리기를 구분한다. 발행 메시지의 공통 외피는 다음과 같다.

```json
{
  "schemaVersion": 1,
  "messageId": "123-0",
  "jobId": 123,
  "attempt": 0,
  "jobType": "INITIAL",
  "input": {
    "request_id": "123-0"
  }
}
```

- `messageId`: `{BE jobId}-{attempt}`. 자동 재발행과 RabbitMQ 재전달에서는 바뀌지 않는다.
- `jobId`: BE `generation_jobs.id`. 콜백의 `backend_job_id`와 동일하다.
- `attempt`: 사용자 수동 재시도 회차. 이전 회차의 늦은 콜백은 BE가 무시한다.
- `jobType`: `INITIAL` 또는 `REGENERATION`.
- `input`: 최초 생성은 여행 조건·취향·후보를 포함한다. 재생성은 이에 더해
  `source_job_id`, `source_guidebook_version`, `title`, `feedback`, `previous_plan`을 포함한다.

### 전달 보장과 멱등성

전달 보장은 at-least-once다. publisher confirm 응답 유실, BE 재기동, consumer 연결 종료로 같은
`messageId`가 여러 번 전달될 수 있다. AI는 작업 저장소에서 `messageId`를 UNIQUE로 관리한다.

1. 처음 본 `messageId`이면 AI 작업을 영속화한다.
2. 작업 영속화가 커밋된 뒤 RabbitMQ ACK를 보낸다.
3. 이미 PROCESSING 또는 COMPLETED인 `messageId`이면 새 LLM 작업을 만들지 않고 ACK한다.
4. PROCESSING 작업이 실행 주체와 함께 유실될 수 있다면 lease/heartbeat 만료 후 회수 정책을 AI가 둔다.
5. 사용자 수동 재시도는 `attempt`와 `messageId`가 바뀌므로 새로운 작업으로 처리한다.

LLM 실행 완료까지 RabbitMQ delivery를 unacked로 잡아두지 않는다. 메시지 ACK는 LLM 완료가 아니라
AI 내부 작업의 안전한 접수를 의미한다. 실제 진행·성공·실패는 기존 콜백
`POST /internal/v1/ai/generation-events`로 전달한다.

### ACK, retry, DLQ

| 상황 | AI consumer 처리 |
| --- | --- |
| 메시지 검증 성공, AI 작업 저장 성공 | ACK |
| 같은 `messageId`가 이미 저장됨 | 상태 확인 후 중복 실행 없이 ACK |
| DB 연결 실패 등 접수 단계의 일시 오류 | NACK, `requeue=false` |
| JSON/schemaVersion/jobType 오류 | DLX에 명시적으로 발행 후 ACK |
| 접수 재시도 한도 초과 | DLX에 명시적으로 발행 후 ACK |
| ACK 이후 LLM 실행 실패·타임아웃 | MQ 재시도 대신 FAILED 콜백 |

메인 큐의 NACK는 `guidebook.generation.retry`로 이동하고
`AI_GENERATION_RABBIT_RETRY_DELAY`가 지난 뒤 메인 큐로 복귀한다. consumer는 RabbitMQ의
`x-death` 헤더로 접수 재시도 횟수를 판단한다. 최대 횟수는 AI 운영 정책으로 확정하되 무한 NACK를
허용하지 않는다. DLQ 발행은 publisher confirm을 확인한 뒤 원본 메시지를 ACK하여 격리 과정의
유실을 막는다.

### 실행 타임아웃과 콜백

- MQ 접수 재시도는 AI가 작업을 영속화하기 전의 일시 장애만 대상으로 한다.
- AI 작업 영속화 이후 LLM 오류·실행 타임아웃은 같은 `messageId`를 새로 실행하지 않고 FAILED 콜백한다.
- 콜백에는 `backend_job_id=jobId`, `attempt`, 작업 단계 또는 최종 결과를 사용한다.
- BE는 현재 회차와 종료 상태를 다시 확인하므로 이전 회차·중복 최종 콜백은 결과를 덮어쓰지 않는다.
- BE의 전체 생성 제한 시간 기본값은 15분이며, AI 내부 모델 호출 제한과는 별개다.

### 초기 소비 설정 권장값

- manual ACK
- consumer concurrency 1
- prefetch 1
- 접수 재시도 횟수 제한 적용
- `messageId`, `jobId`, `attempt`, `jobType`, `x-death`를 구조화 로그에 기록
- DLQ 적재 건수와 메인 큐 대기량 모니터링
