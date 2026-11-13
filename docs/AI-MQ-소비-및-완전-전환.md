# AI MQ 소비 계약 및 완전 전환 계획

관련 Issue: #330  
선행 작업: #308, PR #320
관련 요구사항: BR-GDE-16, FR-GDE-01/02/07/08/09

## 문서 상태와 머지 조건

이 문서는 RabbitMQ 발행 코드와 별도로 관리한다. 아래 조건이 모두 충족되기 전에는 이 문서 PR을
`dev`에 머지하지 않는다.

- AI consumer가 본 문서의 메시지, ACK, 멱등성, retry, DLQ, callback 계약을 승인한다.
- AI consumer 구현이 완료된다.
- BE 실행 선점·lease 갱신·만료 회수·결과 콜백의 소유권 검증을 구현한다.
- staging에서 BE 발행 → RabbitMQ → AI 소비 → BE callback 흐름을 검증한다.
- 최초 생성, 재생성, 사용자 수동 재시도, 중복 전달, 일시 장애, 타임아웃 시나리오를 확인한다.
- 이 문서 PR 머지와 함께 HTTP/MQ 이중 경로를 제거하는 완전 전환 코드를 반영한다.

문서만 먼저 머지하여 아직 존재하지 않는 consumer 동작을 완료된 계약처럼 취급하지 않는다.
이번 개정은 AI 별도 작업 저장소 없이 BE DB를 실행 상태의 기준으로 사용하는 전환안이다.
아래 실행 선점·heartbeat API와 lease 검증은 후속 구현이며, URI·요청/응답 스키마·운영 시간값은
AI 팀과 확정해야 한다. 기존 콜백의 중복 무시만으로 이 계약이 구현된 것으로 간주하지 않는다.

## 전체 구조

```mermaid
flowchart LR
    FE[사용자 요청] --> BE[BE generation_jobs / Outbox]
    BE -->|persistent publish| EX[guidebook.generation.exchange]
    EX --> Q[guidebook.generation]
    Q --> AI[AI consumer]
    AI -->|진행·성공·실패| CB[BE generation callback]
    CB --> DB[(BE DB)]
    Q -->|NACK, requeue=false| REX[guidebook.generation.retry.exchange]
    REX --> RQ[guidebook.generation.retry]
    RQ -->|TTL 만료| EX
    AI -->|복구 불가·재시도 초과| DLX[guidebook.generation.dlx]
    DLX --> DLQ[guidebook.generation.dlq]
```

BE는 생성·재생성 입력을 DB에 먼저 저장한 뒤 RabbitMQ에 발행한다. RabbitMQ publisher confirm ACK를
받은 경우에만 작업을 내부 `QUEUED` 상태로 바꾼다. confirm 실패·타임아웃·returned message는
`PUBLISH_PENDING`을 유지하고 DB Outbox에서 재발행한다.

## RabbitMQ 토폴로지

| 역할 | 이름 | routing key |
| --- | --- | --- |
| 최초 발행 exchange | `guidebook.generation.exchange` | `guidebook.generation` |
| 메인 quorum queue | `guidebook.generation` | - |
| retry exchange | `guidebook.generation.retry.exchange` | `guidebook.generation.retry` |
| retry quorum queue | `guidebook.generation.retry` | - |
| dead-letter exchange | `guidebook.generation.dlx` | `guidebook.generation.failed` |
| dead-letter quorum queue | `guidebook.generation.dlq` | - |

메인 큐에서 `NACK(requeue=false)`된 메시지는 retry queue로 이동한다. retry queue의 TTL이 지나면
메인 exchange로 돌아온다. quorum dead-letter 전달은 `x-dead-letter-strategy=at-least-once`,
`x-overflow=reject-publish`를 사용한다.

## 메시지 계약

최초 생성과 재생성은 같은 큐를 사용하고 `jobType`으로 구분한다.

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

- `messageId`: `{BE jobId}-{attempt}`. 자동 재발행과 RabbitMQ 재전달에서는 유지한다.
- `jobId`: BE `generation_jobs.id`. callback의 `backend_job_id`와 같다.
- `attempt`: 사용자 수동 재시도 회차. 이전 회차 callback은 BE가 무시한다.
- `jobType`: `INITIAL` 또는 `REGENERATION`.
- `input`: 최초 생성은 여행 조건·취향·후보 콘텐츠를 포함한다.
- 재생성 input은 최초 입력 스냅샷에 `source_job_id`, `source_guidebook_version`, `title`,
  `feedback`, `previous_plan`을 추가한다.

`previous_plan` 항목은 `content_id`, `day`, `order`, `start_time`, `duration_minutes`로 구성한다.
재생성은 최신 관광 후보를 재조회하지 않고 원본 입력과 현재 저장 일정을 사용한다.

## 전달 보장과 AI 멱등성

전달 보장은 at-least-once다. publisher confirm 응답 유실, BE 재기동, consumer 연결 종료로 동일한
`messageId`가 여러 번 전달될 수 있다.

AI 전용 DB·영속 작업 저장소는 요구하지 않는다. 기존의 「AI 작업 저장소 UNIQUE 등록 후 조기 ACK」
방식을 대체하고, 요청·실행 상태·결과는 BE의 `generation_jobs`에서 영속 관리한다.

1. AI는 메시지 검증 후 `jobId`, `attempt`, `messageId`로 BE에 실행 선점을 요청한다.
2. BE는 행 잠금과 현재 회차·상태 검사로 실행을 원자적으로 선점하고 `lease_token`,
   `lease_version`, `lease_expires_at`을 저장한다. 유효한 소유자가 있는 동안 새 실행을 허용하지 않는다.
3. AI는 선점 성공 후 LLM 작업을 시작하고 heartbeat로 BE lease를 갱신한다. lease 갱신·결과 콜백은
   발급받은 토큰과 버전을 함께 검증한다. 소유권을 잃은 실행의 늦은 결과는 반영하지 않는다.
4. AI는 BE가 최종 성공·실패 결과를 커밋했음을 확인한 뒤 RabbitMQ ACK를 보낸다.
   콜백 전송만 실패한 경우 메모리에 보관한 같은 결과·이벤트를 재전송하며 LLM을 다시 호출하지 않는다.
5. 재전달 시에도 BE 선점을 거친다. BE가 같은 시도의 종료 또는 이전 회차임을 확인하면 실행 없이 ACK한다.
   다른 실행의 lease가 유효하면 즉시 ACK하지 않고 retry queue로 지연 재시도한다.
6. AI 연결 종료로 미ACK 메시지가 재전달되면, BE의 이전 실행 lease 만료 후 새 실행이 선점한다.
   BE 재기동 후에도 DB lease를 기준으로 복구한다. AI 재시작만으로 FAILED를 확정하지 않는다.
7. 사용자 수동 재시도는 `attempt`와 `messageId`가 바뀌므로 새로운 실행으로 처리한다.

AI의 메모리 중복 검사는 보조 수단이다. 최종 콜백만 중복 무시하면 LLM 중복 호출 비용은 막지 못하므로
실행 전 BE 선점을 필수로 한다. 다만 LLM 응답 후 BE 저장 전에 AI가 종료되거나 lease 만료 후 이전 호출이
계속 실행되면 LLM 호출은 중복될 수 있다. 결과 반영은 소유권 검증으로 보호하며 exactly-once 계산은 보장하지 않는다.

BE 전체 중단 중에는 신규 선점·heartbeat·결과 저장이 지연된다. 결과가 확정되지 않은 메시지는
ACK하지 않고 재전달 가능한 상태를 유지한다. lease와 실행 제한·콜백 재시도·consumer acknowledgement
timeout을 함께 설정해야 한다. BE 제한 시간이 먼저 종료한 작업은 재전달 시 종료 상태를 확인해 ACK한다.

## ACK, retry, DLQ 정책

| 상황 | AI consumer 처리 |
| --- | --- |
| 검증 및 BE 실행 선점 성공 | ACK를 보류하고 실행 |
| BE가 같은 시도 종료 또는 이전 회차임을 확인 | 실행 없이 ACK |
| 동일 `messageId`의 유효한 실행 lease 존재 | 실행 없이 retry queue로 지연 재시도 |
| BE 선점 요청의 연결 실패·응답 유실 등 일시 오류 | NACK, `requeue=false`, 재전달 시 BE 상태 재확인 |
| 최종 결과가 BE에 커밋됨 | ACK |
| 결과 콜백의 연결 실패·응답 유실 | 같은 결과 재전송, 완료 확인 전 ACK 보류 |
| JSON, schemaVersion, jobType 등 복구 불가능한 오류 | DLX 발행 confirm 후 원본 ACK |
| `x-death` 기반 접수 재시도 한도 초과 | DLX 발행 confirm 후 원본 ACK |
| LLM 실행 실패 또는 실행 타임아웃을 최종 실패로 확정 | FAILED callback 커밋 확인 후 ACK |

consumer는 `x-death` 헤더로 접수 재시도 횟수를 판단하고 무한 NACK를 허용하지 않는다. 최대 횟수는
AI·BE 팀이 확정한다. 유효한 lease 때문에 기다린 횟수와 실제 처리 실패 횟수를 구분하며,
`x-death` 총횟수만으로 정상 실행 중인 작업을 최종 실패시키지 않는다. DLQ 발행은 publisher confirm을
확인한 뒤 원본 메시지를 ACK한다. 식별 가능한 작업의 DLQ 격리 후 BE 실패 수렴·재처리 절차는 별도 확정한다.

## callback과 타임아웃

AI는 기존 내부 callback을 사용한다.

```text
POST /internal/v1/ai/generation-events
```

- callback에는 `backend_job_id=jobId`와 `attempt`를 포함한다.
- 진행 단계와 최종 성공·실패 결과를 callback으로 전달한다.
- 실행 시작 시각은 BE 선점 트랜잭션에서 등록한다. heartbeat와 진행·최종 콜백에는 실행 소유권을 검증한다.
- LLM 실패·타임아웃을 최종 실패로 확정하면 FAILED callback을 보내고 BE 커밋 확인 후 ACK한다.
- BE는 현재 attempt와 종료 상태를 다시 확인하여 이전 회차·중복 최종 callback을 무시한다.
- sequence는 동일 시도에서 새 이벤트를 만들 때 증가시키며 재전송은 같은 event_id·sequence를 유지한다.
  기존 BE는 비종료 콜백의 낮거나 같은 sequence를 무시하며 종료 콜백은 그 비교에서 제외한다.
  재실행의 소유권은 sequence와 별도로 lease 토큰·버전으로 검증한다.
- 기존 HTTP 경로의 기본 15분 제한은 MQ 큐 대기·실행·결과 전달 제한과 분리해 전환 전에 확정한다.

## 초기 consumer 운영값

- manual ACK
- consumer concurrency 1
- prefetch 1
- consumer acknowledgement timeout은 최대 실행·콜백 재시도 시간을 수용하도록 설정하며 시간값은 확정 필요
- `messageId`, `jobId`, `attempt`, `jobType`, `x-death` 구조화 로그
- 메인/retry/DLQ 큐 적재량 모니터링
- 소비 지연, callback 실패, DLQ 신규 적재 알림

## 완전 전환 시 코드 제거

AI 계약과 staging 통합 검증이 완료되면 이 문서 PR의 머지와 함께 MQ 단일 경로 전환 PR을 반영한다.

### 경로 선택 제거

- `AI_GENERATION_OUTBOX_ENABLED`
- `ai.generation.outbox-enabled`
- MQ 관련 컴포넌트의 경로 선택용 `ConditionalOnProperty`
- MQ/HTTP 이중 경로 조건 분기

MQ가 유일한 생성 전달 경로가 되므로 활성화 flag를 운영 토글로 남기지 않는다.

### 기존 HTTP 제출 제거

- `GuidebookAiClient.requestGeneration`
- `GuidebookAiClient.requestRegeneration`
- `HttpGuidebookAiClient`의 생성·재생성 접수 경로
- `GenerationCallbackSubmissionService`의 AI HTTP 접수
- HTTP 접수 전용 `AiGuidebookRegenerationRequest`
- AI 접수용 base URL, connect timeout, read timeout 설정 중 더 이상 사용하지 않는 값

상태 callback 클라이언트나 다른 용도로 실제 사용 중인 설정은 참조를 확인한 뒤 제거한다.

### 메모리 제출 큐 제거

- `GenerationSubmissionQueue`
- 메모리 동시 실행 슬롯과 wakeup 로직
- 커밋 후 HTTP submit을 위한 `Propagation.NEVER`, `writes.execute()` 경계
- HTTP 접수를 위해 유지하던 admission 결과와 분기

접수 트랜잭션은 입력 스냅샷과 Outbox 발행 대기 상태를 함께 저장하는 단일 DB 트랜잭션으로
단순화한다. RabbitMQ 발행은 저장 커밋 이후 Outbox publisher가 담당한다.

### 레거시 AI 작업 ID 의존 제거

- `source_ai_job_id` 기반 재생성 전달
- `ai_job_id` 기반 재생성 원본 선택
- 기존 HTTP 작업 호환을 위한 상태·선택 분기

기존 진행 작업을 소진하거나 이관한 뒤 제거한다. DB 컬럼 제거가 필요하면 별도 migration으로 수행하고,
운영 데이터가 남아 있는 상태에서 바로 삭제하지 않는다.

### 유지 대상

- `generation_jobs` 요청·메시지 스냅샷
- DB Outbox 발행, publisher confirm, 발행 backoff
- AI 진행·성공·실패 callback
- 결과 검증·저장과 생성권 차감 트랜잭션
- 생성 전용 SSE와 완료 알림
- 사용자 수동 재시도 횟수·권한 정책
- 늦은 attempt 및 중복 callback 차단

## 완전 전환 검증 체크리스트

- [ ] INITIAL 메시지를 AI가 소비하고 성공 callback한다.
- [ ] REGENERATION 메시지를 `previous_plan`과 `feedback`으로 처리한다.
- [ ] 같은 시도의 유효한 lease가 있는 동안 중복 메시지는 새 LLM 실행을 시작하지 않는다.
- [ ] AI 종료 후 미ACK 재전달과 BE lease 만료로 재실행할 수 있다.
- [ ] BE 결과 커밋 후 ACK 전 AI 종료 시 재실행 없이 ACK한다.
- [ ] BE 재기동 후 DB lease·종료 상태를 기준으로 복구한다.
- [ ] 만료된 실행의 늦은 heartbeat·결과 콜백은 현재 실행에 반영되지 않는다.
- [ ] 콜백 응답 유실 시 같은 이벤트를 재전송하고 결과 중복 저장·생성권 중복 차감을 막는다.
- [ ] AI 접수 전 일시 장애가 retry queue를 거쳐 복구된다.
- [ ] 접수 재시도 초과·잘못된 메시지가 DLQ에 격리된다.
- [ ] LLM 실패·타임아웃이 FAILED callback으로 종료된다.
- [ ] 사용자 수동 재시도는 새 `messageId`로 실행된다.
- [ ] 이전 attempt의 늦은 callback이 현재 결과를 덮어쓰지 않는다.
- [ ] RabbitMQ 중단 중 `PUBLISH_PENDING`이 유지되고 복구 후 발행된다.
- [ ] HTTP 제출·메모리 큐·경로 선택 flag 제거 후 전체 테스트가 통과한다.
- [ ] staging 환경에서 큐 적재량, 소비 지연, DLQ, callback 실패를 관찰할 수 있다.
