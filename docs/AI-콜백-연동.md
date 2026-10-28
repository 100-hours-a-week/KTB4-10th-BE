# AI 진행·결과 콜백 연동

관련 요구사항: BR-GDE-16, FR-GDE-01/02/08/09, NFR-AVL-03.

## 적용 범위와 실행

기존 생성·재생성 POST와 202 응답을 유지한다. AI 접수 본문도 변경하지 않는다.
AI는 기존 request_id를 보관하고, 진행·완료·실패를 BE 콜백으로 보낸다.

콜백은 유일한 처리 경로다. local과 prod 모두 콜백 구현 AI와 연결하고 다음 환경변수를
주입한다. Spring Boot는 .env를 자동으로 읽지 않는다.

```dotenv
AI_SERVER_BASE_URL=http://localhost:8000
AI_CALLBACK_TOKEN=양쪽에-동일하게-주입할-충분히-긴-랜덤-비밀값
AI_GENERATION_TIMEOUT=15m
AI_GENERATION_REDIS_ENABLED=false
```

- local과 prod 모두 HTTP 클라이언트를 사용하며 토큰이 비어 있으면 BE 기동을 거절한다.
- AI GET 상태 조회 메서드·조회 시 동기화·백그라운드 폴러·기존 접수 리스너는 제거했다.
- 사용자 상태 GET은 본인 작업의 DB 조회용으로 유지하며 진행 상태를 변경하지 않는다.
- BE 내부 Fake 클라이언트도 제거했다. 로컬은 Docker AI의 LLM_MODE=fake를 사용한다.
- AI_CLIENT_MODE와 AI_GENERATION_DELIVERY_MODE는 더 이상 사용하지 않는다.
- AI 레포와 FE는 이 BE 구현에서 수정하지 않았다.
- Docker의 LLM_MODE=fake와 생성 지연 설정은 AI 실행 환경에 둔다. 콜백 구현이 포함된
  AI 버전으로 빌드하고, 컨테이너에서 BE에 접근할 수 있는 주소를 주입한다.

## AI → BE 계약

```http
POST {BE_CALLBACK_BASE_URL}/internal/v1/ai/generation-events
Authorization: Bearer {AI_CALLBACK_TOKEN}
Content-Type: application/json
```

경로와 Bearer 인증은 신규 계약이다. 사용자 쿠키·CSRF는 이 단일 내부 경로에서
요구하지 않으며 Bearer가 없거나 틀리면 거절한다. 다른 사용자 API의 CSRF는 유지한다.
운영에서는 내부 라우팅 및 HTTPS/TLS 종료 구간을 구성하고 토큰·결과 원문을 로그에 남기지 않는다.
AI의 BE_CALLBACK_BASE_URL과 AI_CALLBACK_TOKEN 주입은 AI 팀 구현 범위다.

기존 message/data 및 data.job_id/status/result/error를 유지한다.
data.event_id/request_id/sequence/progress를 추가하며 event_type이나 ai_job_id로 바꾸지 않는다.

| 필드 | 규칙 |
| --- | --- |
| event_id | UUID. 같은 이벤트 재전송 시 유지 |
| request_id | BE 요청 값을 그대로 반환. 예: 301-0 |
| sequence | 한 AI 작업 내 1부터 증가하는 이벤트 순서. 재전송 시 유지 |
| job_id | AI 접수 응답의 작업 ID |
| status | pending / processing / completed / failed |
| progress | 진행 중 step/message, 종료 시 null |
| result | completed일 때 기존 AI 완료 결과 |
| error | failed일 때 기존 code/message |

sequence는 재시도·재생성 횟수가 아니다. 실패 재시도는 request_id의 시도 회차로
구분하고 재생성은 별도 BE 작업 ID를 사용한다. 새 AI 작업은 sequence를 1부터 시작한다.

### 진행

```json
{
  "message": "guidebook_processing",
  "data": {
    "event_id": "7cb05532-daae-41da-a633-99bc0ca5dab5",
    "request_id": "301-0",
    "sequence": 1,
    "job_id": "ai-job-301",
    "status": "processing",
    "progress": {"step": "arranging", "message": "여행 일정을 구성하고 있어요."},
    "result": null,
    "error": null
  }
}
```

BE가 허용하는 step은 queued(pending), arranging/writing/rendering(processing)이다.
AI 팀 제안의 기본 흐름은 arranging(1) → writing(2) → completed(3)이며 queued와
rendering 전송은 필수가 아니다. pending을 보내면 message=guidebook_pending을 쓴다.
실제 단계가 바뀔 때만 전송하고 임의 퍼센트를 만들지 않는다. 진행 전송 실패가 AI
계산이나 최종 콜백을 막아서는 안 된다. 화면 문구는 AI·FE와 맞춘다.

### 완료

```json
{
  "message": "guidebook_completed",
  "data": {
    "event_id": "92d30731-e53e-492d-96d0-1789e43c191f",
    "request_id": "301-0",
    "sequence": 3,
    "job_id": "ai-job-301",
    "status": "completed",
    "progress": null,
    "result": {
      "title": "경주 역사 여행",
      "summary": "경주의 역사 명소를 둘러봅니다.",
      "thumbnail_url": null,
      "content_html": "<article><h1>경주 역사 여행</h1></article>",
      "itinerary": [{
        "day": 1,
        "date": "26.11.01",
        "day_summary": "역사 명소를 방문합니다.",
        "places": [{
          "order": 1,
          "time": "09:00",
          "content_id": "126508",
          "duration_minutes": 60,
          "recommend_reason": "역사를 알아보기 좋은 장소입니다."
        }]
      }]
    },
    "error": null
  }
}
```

실제 여행 조건·콘텐츠 후보에 맞는 result를 보낸다. 기존 결과 검증, 일정 저장,
보관 관계, 생성권 차감, 원장, 완료 상태 전환을 동일 트랜잭션으로 재사용한다.

### 실패

```json
{
  "message": "guidebook_failed",
  "data": {
    "event_id": "487d0581-df18-4b36-9c80-970a468debc7",
    "request_id": "301-0",
    "sequence": 3,
    "job_id": "ai-job-301",
    "status": "failed",
    "progress": null,
    "result": null,
    "error": {"code": "generation_failed", "message": "가이드북 생성에 실패했습니다."}
  }
}
```

### 응답과 재전송

| 응답 | AI 처리 |
| --- | --- |
| 204 | 해당 이벤트 처리 완료 |
| 연결 실패·타임아웃·5xx | 동일 event_id/sequence/본문으로 메모리에서 제한 재시도 |
| 429 | Retry-After를 존중해 제한 재시도 |
| 그 외 4xx | 무한 재시도하지 않고 계약·인증 확인 |

BE는 완료·실패 트랜잭션 커밋 뒤 204를 반환한다. 진행 이벤트의 204는 진행 수신을
뜻하며 Redis 전달이나 브라우저 처리 ACK를 의미하지 않는다.
Redis/SSE 실패는 이미 커밋한 결과를 롤백하지 않는다.
AI 팀 제안은 콜백 HTTP 타임아웃 5초, 완료·실패 총 5회(최초 포함, 대기 1·2·4·8초),
진행 총 2회이며 429는 횟수 상한 안에서 Retry-After를 따른다. 이는 AI 측 구현·설정
확인이 필요한 계약이며 BE가 대신 재전송하거나 AI 결과를 보관하지 않는다.

BE는 request_id의 현재 회차와 job_id를 검증한다. 이전 회차·종료된 작업은 재반영 없이
204, 현재 회차의 다른 AI job_id는 409, 없는 BE 작업은 404, 잘못된 본문은 400이다.
중복 최종 결과는 DB 행 잠금과 종료 상태 검사로 저장·차감을 반복하지 않는다.
event_id 이력 테이블은 추가하지 않는다. 진행 sequence 캐시는 인스턴스 메모리에만 있고
다중 인스턴스에서는 각 SSE 전달 캐시와 FE에서도 순서를 검사해야 한다.

현재 `GenerationCallbackTransactionService.submit()`은 REQUIRES_NEW 쓰기 트랜잭션에서
prepare → AI HTTP 접수 → registerAccepted를 실행한다. 내부 메서드 호출은 같은 빈의
self-invocation이므로 prepare/registerAccepted의 REQUIRES_NEW가 별도 커밋 경계를
만들지 않는다. AI HTTP 동안 작업 행 잠금과 DB 커넥션이 유지된다.

request_id 매핑과 종료 상태 보호 로직은 있지만, AI 202보다 빠른 콜백은 접수
트랜잭션의 잠금 해제까지 기다릴 수 있다. AI가 콜백 ACK를 기다린 뒤 202를 보내면
서로의 응답을 기다리다 HTTP/DB 타임아웃이 발생할 수 있다. AI는 202 접수 응답을
콜백 ACK에 종속시키지 않아야 하며, HTTP 호출을 DB 트랜잭션 밖으로 분리하는 개선과
실제 동시 접수·빠른 콜백 검증은 핵심 리뷰 항목이다. 현재 테스트의 prepare/receive/
registerAccepted 개별 호출 검증은 실제 submit 경로의 잠금 경쟁 검증을 대신하지 않는다.

## 15분 단발 타이머

- AI 접수 시도에서 기존 attempt_started_at을 기록하고 기한을 계산한다. 현재 리스너는
  submit 트랜잭션 반환 뒤 그 기한으로 타이머를 등록하므로 HTTP 대기를 제외한 새 15분을
  주는 것은 아니다. 다만 submit 커밋 후 등록 전 종료 시 타이머가 없는 작업이 남을 수 있다.
- AI 큐 대기와 계산·전달을 모두 포함하며 진행 이벤트가 와도 기한을 연장하지 않는다.
- 타이머는 실행 시 현재 회차·진행 상태를 행 잠금 아래 다시 검사한다.
- 제한 시각 이상이면 FAILED / GENERATION_TIMEOUT, retryable=true로 처리하고 차감하지 않는다.
- 콜백도 같은 기한을 검사하므로 타이머 스레드가 늦게 실행돼도 기한 후 성공을 반영하지 않는다.
- 완료·실패 후 타이머는 취소하거나 실행 시 종료 상태를 보고 무시한다.
- 재시도 전 타이머는 이전 회차만 대상으로 하며 새 시도를 실패시키지 않는다.
- 접수 응답이 유실된 AiClientException은 즉시 실패시키지 않고 콜백 또는 제한 시간을 기다린다.
- 결과 콜백이 실패·유실되면 AI가 성공했어도 BE는 타임아웃 실패로 처리할 수 있다.
- BE의 타임아웃이 AI 계산 자체를 취소하지는 않는다.

attempt_started_at은 기존 작업 컬럼을 재사용한다. 새 deadline/outbox/inbox 테이블은 없으며
타이머·진행 캐시 복구, 자동 재접수와 복구 폴링을 구현하지 않는다.
BE가 재시작하면 타이머가 사라진다. 이후 콜백이 오면 기한을 검사하지만 콜백도 없으면
DB 작업이 진행 중으로 남을 수 있다. AI 재시작 시 미전달 결과도 유실될 수 있다.

다중 인스턴스에서 타이머는 접수 인스턴스에만 있고, 다른 인스턴스가 종료 콜백을 받으면
기존 타이머는 DB의 종료 상태를 확인하고 아무것도 하지 않는다. 타이머 소유 인스턴스가
종료됐을 때 다른 인스턴스가 타이머를 인계받는 기능은 없다.

## BE → FE 생성 전용 SSE

```http
GET /api/v1/guidebook-generations/{jobId}/stream
Accept: text/event-stream
```

ACTIVE 서비스 세션과 작업 소유권을 검증한다. 알림 push_enabled와 무관하게 구독한다.
이벤트 이름은 generation-status이며 초기 연결/재연결 시 DB 상태 snapshot을 전송한다.

```json
{
  "member_id": 7,
  "job_id": 301,
  "request_id": "301-0",
  "sequence": 2,
  "status": "PROCESSING",
  "progress": {"step": "writing", "message": "가이드북을 작성하고 있어요."},
  "guidebook_id": null,
  "code": null
}
```

- FE용 job_id는 BE 작업 ID다. AI 콜백의 job_id는 AI 작업 ID이므로 혼동하지 않는다.
- SSE에는 AI 결과 HTML, 생성 입력, 인증 토큰을 넣지 않는다.
- 진행 캐시가 없으면 snapshot progress=null, sequence=0이며 최종 상태는 DB가 기준이다.
- 타임아웃 같은 BE 종료 이벤트도 sequence=0일 수 있다. FE는 같은 회차에서 종료 상태를
  진행 상태보다 우선하며 종료 이벤트를 sequence만으로 버리면 안 된다.
- 같은 회차 진행 이벤트는 더 큰 sequence만 반영하고 이전 회차 이벤트는 무시한다.
- COMPLETED/FAILED/CANCELED를 받으면 EventSource.close()한다. 서버도 종료 이벤트 후 닫는다.
- 15초 heartbeat, 인스턴스당 기본 1000개/회원당 5개 연결 제한이 있다.
- 로그아웃·탈퇴 시 기존 연결 종료 경로를 통해 생성 SSE도 종료한다.
- Redis 사용 시 ai.generation.redis.channel로 fan-out한다. prod 기본 활성,
  local 기본 비활성이다. 다중 인스턴스는 AI_GENERATION_REDIS_ENABLED=true와 같은 채널이 필요하다.
- Redis Pub/Sub은 best-effort다. 연결 유지 중 이벤트 유실을 자동 탐지하거나 재생하지 않는다.
  FE는 재연결·복귀·수동 재확인 시 기존 상태 GET으로 종료 상태를 복구한다.
- 진행 캐시는 재시작 후 사라지고 과거 단계 replay는 제공하지 않는다.

FE의 생성 SSE 구독 구현과 실제 진행 문구의 Figma 반영은 별도다.

## 이번 구현의 보장 밖과 운영 리뷰 항목

- DB 작업/완료 결과는 영속화하지만 타이머·진행 sequence·SSE 캐시·연결은 메모리다.
  Redis Pub/Sub은 메시지 보관·재전달·타이머 인계를 제공하지 않는다.
- event_id는 형식 검증만 하고 별도 중복 이력에 저장하지 않는다. 최종 결과의 중복
  차단은 DB 잠금·현재 회차·종료 상태 검사에 의존한다. 진행의 전역 순서는 보장하지 않는다.
- 접수 예외는 AiClientException 전체를 불확실한 접수로 취급한다. 확정적인 거절과
  응답 유실을 세분하지 않아 거절된 작업도 15분 뒤 실패할 수 있다.
- 요청 복원/후보 조립, 클라이언트 조회, 커밋 또는 타이머 등록 자체가 실패하면 정상적인
  타이머 등록 흐름에 도달하지 못할 수 있다. 영속 재처리나 보상 워커는 없다.
- DB 커밋과 Redis 발행은 원자적이지 않다. 커밋 직후 프로세스 종료나 Redis 오류 시
  이벤트가 유실될 수 있다. 진행 이벤트의 204는 진행 캐시의 영속 ACK가 아니다.
- Redis 발행과 SSE 전송은 비동기 큐로 격리하지 않았다. callback 204가 AFTER_COMMIT
  처리 지연의 영향을 받을 수 있다. 기존 완료 알림 후처리의 중첩 REQUIRES_NEW와
  커넥션 풀 점유도 함께 관찰한다. #300의 Web Push Outbox와 별개 경로다.
- SSE 서비스의 synchronized 구간에 snapshot DB 조회와 emitter 전송이 포함된다.
  느린 조회/클라이언트가 다른 연결·heartbeat·발행을 지연시킬 수 있으며 역압력·연결별
  전송 큐·느린 소비자 격리·전용 지표는 이번 범위 밖이다.
- 연결 제한은 회원당 인스턴스 로컬 5개이며 전역 제한이 아니다. 다중 탭과 기존 알림
  SSE가 추가 연결을 차지하므로 브라우저·프록시·서버 연결 한도도 검증한다.
- Last-Event-ID replay, 연결 중 무음 유실 탐지, 무조건적인 세션 만료 시각의 연결 종료,
  이전 polling 버전으로 접수한 작업의 자동 인계는 구현하지 않았다.
- 내부 경로의 네트워크 노출 제한, TLS, 토큰 회전/겹침 기간, 요청 크기·요청률 제한,
  Nginx/LB 버퍼링·idle timeout·드레이닝과 재접속 정책을 운영에서 확인한다.
- AI 작업 상한뿐 아니라 큐 대기·전달·DB 저장도 BE 15분에 포함한다. 큐가 길면 AI가
  성공해도 BE는 실패할 수 있다. 이번 제한은 AI 연산 취소나 충분한 큐 처리량을 보장하지 않는다.
- AI/FE 실연동, 실제 Redis 다중 인스턴스, MySQL 잠금·커넥션 포화, 재시작 장애,
  부하 개선 수치는 이번 자동 테스트만으로 검증한 것이 아니다.

## 로컬 실제 연동 확인

AI dev의 콜백 구현을 별도 fake LLM 컨테이너로 실행하고, 격리 MySQL DB·Redis 세션
네임스페이스의 BE에 실제 생성 POST를 보냈다. 접수 202 → arranging/writing/completed
콜백 각각 204 → 생성 SSE 종료 → DB 가이드북·알림 저장과 생성권 1개 차감을 확인했다.
AI 메트릭에서 생성 접수 POST만 발생하고 AI 상태 GET은 발생하지 않았다.
검증은 단일 BE·Redis 생성 fan-out 비활성 환경이며 FE 브라우저·다중 인스턴스 검증과는 별개다.

## 장애 실험과 검증

1. 같은 완료 콜백 동시 전송 → 가이드북·생성권 차감 각각 한 번.
2. 역순 진행·중복 최종 콜백·종료 뒤 진행 → 상태 역행 없음.
3. 이전 회차 콜백·타이머 → 새 시도에 영향 없음.
4. AI 접수 응답 전 콜백 → 정상 매핑, 접수 응답으로 종료 상태 덮어쓰기 없음.
5. 14분 59초 진행과 15분 기한 → 연장 없이 timeout, 늦은 콜백 무시.
6. AI 생성 중 종료 → 콜백 없으면 BE timeout 실패.
7. BE 종료 → 타이머 소실·진행 중 잔류를 직접 관찰.
8. Redis 중단 → DB 결과는 커밋, GET으로 확인 가능.
9. 인증 누락·잘못된 본문·타인 SSE → 거절.

MQ를 도입하기 전에는 무손실 전달·서버 재시작 자동 복구를 보장하지 않는다.
