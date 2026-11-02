# AI 진행·결과 콜백 연동

관련 요구사항: BR-GDE-16, FR-GDE-01/02/08/09, NFR-AVL-03.

관련 작업: #305(HTTP/트랜잭션 경계), #307(단일 BE 메모리 큐), #308(공유 MQ 후속 설계).
현재 문서는 #307까지의 구현이다. #308의 MQ·영속 전달·다중 소비자 설계는 아직 구현하지 않았다.

## 적용 범위와 실행

기존 생성·재생성 POST와 202 응답을 유지한다. AI 접수 본문도 변경하지 않는다.
AI는 기존 request_id를 보관하고, 진행·완료·실패를 BE 콜백으로 보낸다.

콜백은 유일한 처리 경로다. local과 prod 모두 콜백 구현 AI와 연결하고 다음 환경변수를
주입한다. Spring Boot는 .env를 자동으로 읽지 않는다.

```dotenv
AI_SERVER_BASE_URL=http://localhost:8000
AI_CALLBACK_TOKEN=양쪽에-동일하게-주입할-충분히-긴-랜덤-비밀값
AI_GENERATION_TIMEOUT=15m
AI_GENERATION_SUBMISSION_CONCURRENCY=3
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

## 단일 BE 메모리 대기열과 트랜잭션 경계 (#305)

최초 생성·재생성·수동 재시도는 기존 POST/202와 AI ID 계약을 유지한다.
`GuidebookGenerationService`는 외부 트랜잭션 참여를 NEVER로 거절하고,
TransactionTemplate으로 작업 저장·커밋·자원 정리를 마친 뒤 무제한 FIFO 메모리 큐에 적재하고
202를 반환한다. 202는 BE 접수이며 AI 접수 성공을 뜻하지 않는다.
AFTER_COMMIT 접수 리스너와 접수 요청 이벤트는 제거했다. 멱등 재요청은 기존 응답만
반환하고 AI를 다시 호출하지 않는다.

`GenerationSubmissionQueue`는 단일 BE에서 기본 3개 슬롯만 사용한다.
AI_GENERATION_SUBMISSION_CONCURRENCY로 AI 처리 용량에 맞춘다. 다른 BE나 외부 요청의
실행 수는 포함하지 않으므로 전역 제한이 아니며 다중 인스턴스용으로 사용하지 않는다.
무제한은 별도 건수 제한을 두지 않는다는 뜻이며 실제 메모리와 DB 용량은 유한하다.
DB 이력은 남지만 큐 복원용 조회는 하지 않으므로 영속 대기열 보장은 아니다.

큐에서 슬롯을 확보한 워커는 다음 순서로 실행한다.

1. 별도 빈의 prepare 트랜잭션에서 행 잠금 아래 회차를 확인하고 attempt_started_at으로
   접수를 선점한다. 이미 시작했거나 AI ID가 있으면 재접수하지 않는다.
2. 준비 커밋·자원 정리 후 시도별 15분 타이머를 등록한다.
3. DB 트랜잭션·행 잠금 없이 AI HTTP 접수 응답을 기다린다.
4. 별도 registerAccepted 트랜잭션에서 AI ID·접수 상태를 저장한다. 콜백이나 타이머가
   먼저 종료시켰거나 회차가 바뀌었으면 늦은 응답은 반영하지 않는다.

prepare/registerAccepted/failSubmission은 프록시를 거치는 독립 호출이며 기본
REQUIRED를 사용한다. 바깥 트랜잭션이 없으므로 각각 시작·커밋된다. AFTER_COMMIT에
남은 자원을 붙잡은 채 HTTP를 실행하거나 REQUIRES_NEW로 중첩 커넥션을 요구하지 않는다.
AI는 접수 HTTP와 콜백을 독립적으로 처리하는 것이 권장되지만, 빠른 콜백이 이 접수
경로의 행 잠금 해제를 기다리는 구조는 제거했다.

HTTP 응답만으로 슬롯을 반환하지 않는다. 실제 completed/failed 콜백의 DB 커밋 뒤
슬롯을 반환하고 다음 작업 하나를 전달한다. 진행 콜백·롤백·BE 타임아웃·취소에는 슬롯을
반환하지 않는다. 중복·이전 회차 이벤트도 다른 작업의 슬롯을 반환하지 않는다.
콜백이 접수 HTTP 응답보다 먼저 와도 현재 request_id를 미리 등록해 처리한다.

AI가 명확하게 접수를 거절하는 503 또는 AI 호출 전 준비된 시도의 런타임 실패는
실패 커밋 뒤 슬롯을 반환한다. 자동 재접수는 하지 않는다. 그 밖의 AiClientException과
접수 응답 저장 실패는 실행 여부가 불확실하므로 슬롯을 유지한다.
요청 복원·prepare 자체가 실패한 경우에는 AI를 호출하지 않고 슬롯만 반환하며
DB 이력 복구는 제공하지 않는다.

대기열에는 대기 제한 시간·15분 타이머를 적용하지 않는다. 슬롯 확보 후 준비 시각부터
기존 15분 제한이 적용된다. 콜백 유실·접수 불확실·타임아웃 작업의 슬롯 복구는 의도적으로
제외했으므로 슬롯이 모두 소진되면 큐가 멈출 수 있다. DB를 조회하는 복구 워커,
Outbox/MQ, 재시작 복원도 없다. 최초 이력 커밋과 메모리 적재도 원자적이지 않다.

## 15분 단발 타이머

- 준비 트랜잭션에서 attempt_started_at을 기록하고 커밋한 뒤 HTTP 전에 타이머를
  등록한다. HTTP 대기를 포함한 15분이며 빠른 종료 콜백도 이미 등록된 타이머를 취소한다.
  준비 커밋 후 등록 전 프로세스 종료 시에는 여전히 타이머 없는 작업이 남을 수 있다.
- 타이머 등록 자체의 런타임 실패는 AI를 호출하기 전에 현재 미접수 작업을 실패 처리한다.
  접수 응답 저장 트랜잭션 실패는 AI 실패로 오인하지 않으며 기존 타이머·콜백으로 판정한다.
- BE 메모리 큐 대기는 제외하고, AI 접수 HTTP·AI 내부 큐 대기·계산·전달을 포함한다.
  진행 이벤트가 와도 기한을 연장하지 않는다.
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
- 연결은 ConcurrentHashMap으로 관리한다. 연결 수 검사·등록·제거만 짧은 잠금으로,
  최신 이벤트 갱신은 작업 키별 원자 연산으로, 초기화·전송 순서는 연결별 잠금으로 보호한다.
  DB snapshot 조회·전송에 전역 잠금을 사용하지 않으며 조회 중 이벤트는 최신 값만 보관한다.
- 전송 실패 시 연결만 제거하고 completeWithError를 다시 호출하지 않는다.
  연결 종료 예외도 다른 연결 순회를 중단하지 않는다.
- 로그아웃·탈퇴 시 기존 연결 종료 경로를 통해 생성 SSE도 종료한다.
- Redis 사용 시 ai.generation.redis.channel로 fan-out한다. prod 기본 활성,
  local 기본 비활성이다. 다중 인스턴스는 AI_GENERATION_REDIS_ENABLED=true와 같은 채널이 필요하다.
- Redis Pub/Sub은 best-effort다. 연결 유지 중 이벤트 유실을 자동 탐지하거나 재생하지 않는다.
  FE는 재연결·복귀·수동 재확인 시 기존 상태 GET으로 종료 상태를 복구한다.
- 진행 캐시는 재시작 후 사라지고 과거 단계 replay는 제공하지 않는다.

FE의 생성 SSE 구독 구현과 실제 진행 문구의 Figma 반영은 별도다.

## 이번 구현의 보장 밖과 운영 리뷰 항목

- DB 작업/완료 결과는 영속화하지만 접수 큐·슬롯·타이머·진행 sequence·SSE 캐시·연결은 메모리다.
  Redis Pub/Sub은 메시지 보관·재전달·타이머 인계를 제공하지 않는다.
- event_id는 형식 검증만 하고 별도 중복 이력에 저장하지 않는다. 최종 결과의 중복
  차단은 DB 잠금·현재 회차·종료 상태 검사에 의존한다. 진행의 전역 순서는 보장하지 않는다.
- AI 접수 503은 실패 처리하고 그 밖의 AiClientException은 불확실로 취급한다.
  콜백 유실 복구를 제외했으므로 타임아웃으로 DB가 FAILED가 돼도 슬롯은 유지될 수 있다.
- 요청 복원/후보 조립·준비 커밋 실패 또는 준비 커밋 후 프로세스 종료 시 타이머가 없는
  작업이 남을 수 있다. 준비 커밋 후 HTTP 전달 전 종료되면 접수 선점 표시만 남아
  자동 재접수하지 않는다. 타이머 등록 실패 보상 자체의 DB 오류도 운영 대응 대상이다.
  영속 재처리나 보상 워커는 없다.
- DB 커밋과 Redis 발행은 원자적이지 않다. 커밋 직후 프로세스 종료나 Redis 오류 시
  이벤트가 유실될 수 있다. 진행 이벤트의 204는 진행 캐시의 영속 ACK가 아니다.
- Redis 발행과 SSE 전송은 비동기 큐로 격리하지 않았다. callback 204가 AFTER_COMMIT
  처리 지연의 영향을 받을 수 있다. 기존 완료 알림 후처리의 중첩 REQUIRES_NEW와
  커넥션 풀 점유도 함께 관찰한다. #300의 Web Push Outbox와 별개 경로다.
- 생성 SSE의 전역 잠금에서 snapshot DB 조회·전송을 제거했다. 연결별 전송 직렬화와
  순차 fan-out/heartbeat는 유지하므로 같은 순회 안에서의 느린 소비자 지연은 남는다.
  역압력·연결별 비동기 전송 큐·전용 지표와 실제 최대 처리량 검증은 이번 범위 밖이다.
- 연결 제한은 회원당 인스턴스 로컬 5개이며 전역 제한이 아니다. 다중 탭과 기존 알림
  SSE가 추가 연결을 차지하므로 브라우저·프록시·서버 연결 한도도 검증한다.
- Last-Event-ID replay, 연결 중 무음 유실 탐지, 무조건적인 세션 만료 시각의 연결 종료,
  이전 polling 버전으로 접수한 작업의 자동 인계는 구현하지 않았다.
- 내부 경로의 네트워크 노출 제한, TLS, 토큰 회전/겹침 기간, 요청 크기·요청률 제한,
  Nginx/LB 버퍼링·idle timeout·드레이닝과 재접속 정책을 운영에서 확인한다.
- AI 작업 상한뿐 아니라 AI 내부 큐 대기·전달·DB 저장도 BE 15분에 포함한다. BE 메모리
  큐 대기는 제외한다. AI 내부 큐가 길면 AI가
  성공해도 BE는 실패할 수 있다. 이번 제한은 AI 연산 취소나 충분한 큐 처리량을 보장하지 않는다.
- AI/FE 실연동, 실제 Redis 다중 인스턴스, MySQL 잠금·커넥션 포화, 재시작 장애,
  부하 개선 수치는 이번 자동 테스트만으로 검증한 것이 아니다.

## #305 트랜잭션 경계 자동 검증과 메모리 큐 후속 검증

- 큐 도입 전 HTTP 경계 분리 단계에서 Java 21 전체 테스트 589개와
  Checkstyle·SpotBugs 통과를 확인했다. 당시 명령은 `./gradlew test checkstyleMain spotbugsMain`이다.
  현재 큐 버전의 최종 전체 검증 결과는 연결된 PR의 테스트 결과를 따른다.
- MySQL 8.4 Testcontainers의 Hikari 최대 연결을 1개로 제한했다. 실제 생성 서비스의
  작업 저장·준비 커밋 뒤 AI 클라이언트 호출 시 활성 트랜잭션과 DataSource 바인딩이 없고,
  풀의 active connection이 0개인 것을 검사했다.
- AI 클라이언트는 테스트 대역이며 네트워크·LLM 부하 검증이 아니다. 접수 응답 반환 전에
  다른 스레드에서 최종 실패 콜백을 처리하고, 늦은 202가 종료 상태를 덮어쓰지 않음을 확인했다.
- 같은 작업의 첫 HTTP 응답을 latch로 보류한 동안 두 번째 접수는 반환되고 AI 호출은
  1회만 실행된다. 이때도 풀 active connection은 0개다.
- HTTP 대기 중 15분 기한 만료와 늦은 202, 외부 트랜잭션 호출 거절,
  접수 응답 유실 이후 PENDING 유지와 기한 실패를 함께 검증했다(MySQL 6건).
  큐 도입 후 명확한 503은 즉시 접수 실패 처리하는 별도 단위 테스트를 추가했다.
- 큐 테스트는 200개 ID의 FIFO·3개 실행 제한, 중복/이전 회차의 슬롯 중복 반환 방지,
  HTTP 대기와 무관한 적재 반환, 빠른 종료 콜백, 종료/이미 시작한 작업 건너뛰기,
  불확실 접수의 슬롯 유지 경로를 검증한다.
- 10 RPS × 10분 접수 부하는 [메모리 큐 부하테스트](./AI-메모리큐-10RPS-부하테스트.md)를 따른다.
  메모리 고갈·다중 BE·콜백 유실 복구는 검증하지 않았다.
- 고정 Clock이 과거를 가리키므로 이 MySQL 테스트의 타이머 실행기는 mock으로 제어한다.
  타이머 등록 순서·등록 실패·접수 결과 DB 실패 분류는 별도 단위 테스트로 검증한다.
- 기존 10VU·fake 생성 60초 시나리오의 단일 인스턴스 A/B는 별도로 실행했다.
  접수 p95 약 30.72초 → 0.80초, Hikari 획득 timeout 9건 → 0건이며 상세 조건·
  클라이언트 110초 제한·원시 결과는 [부하테스트 기록](./AI-접수-트랜잭션-부하테스트.md)을 따른다.
  실제 LLM·다중 인스턴스·강제 접수 HTTP 지연·반복 측정은 미검증이며
  이 결과를 운영 처리량이나 전달 무손실·재시작 복구 보장으로 해석하지 않는다.

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
4. 단일 MySQL 커넥션 풀에서 AI 접수 대기 중 콜백 처리 → 정상 매핑, 접수 응답으로 종료 상태 덮어쓰기 없음.
5. 14분 59초 진행과 15분 기한 → 연장 없이 timeout, 늦은 콜백 무시.
6. AI 생성 중 종료 → 콜백 없으면 BE timeout 실패.
7. BE 종료 → 타이머 소실·진행 중 잔류를 직접 관찰.
8. Redis 중단 → DB 결과는 커밋, GET으로 확인 가능.
9. 인증 누락·잘못된 본문·타인 SSE → 거절.

MQ를 도입하기 전에는 무손실 전달·서버 재시작 자동 복구를 보장하지 않는다.
