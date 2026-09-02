# REST API 예시 및 Best Practice

> 적용 범위: V3 API 설계  
> 연계 문서: [V3 API 명세서](./V3%20API%20명세서.md)  
> 기준일: 2026-09-02
>
> 데이터 모델 기준: [테이블정의서](./테이블정의서.md)

## 1. 이 문서를 보는 목적

REST API 설계는 URL을 만드는 작업만이 아니다. 프론트엔드와 백엔드가 다음 내용을 같은 의미로 이해하도록 계약을 정하는 작업이다.

- 어떤 리소스를 조회하거나 변경하는가?
- 요청이 성공하면 무엇이 생성·변경되는가?
- 동일 요청이 반복되면 결과가 어떻게 되는가?
- 실패했을 때 프론트엔드는 무엇을 기준으로 분기하는가?
- 오래 걸리는 작업과 외부 시스템 오류를 어떻게 복구하는가?

V3에서는 특히 가이드북 비동기 생성, 생성권 예약·차감, 결제 웹훅, 공유 가이드북 가져오기, 평가 확정이 핵심 설계 대상이다.

## 2. REST API를 이해하는 가장 간단한 기준

### 2.1 URL은 리소스를 나타낸다

URL에는 가능하면 동사가 아니라 관리하려는 대상을 복수 명사로 표현한다.

| 피할 표현 | 권장 표현 | 이유 |
|---|---|---|
| `GET /getGuidebooks` | `GET /guidebooks` | 조회 여부는 HTTP 메서드가 표현한다. |
| `POST /createOrder` | `POST /orders` | 컬렉션에 새 주문을 생성한다는 의미가 명확하다. |
| `POST /deleteFavorite` | `DELETE /favorites/{contentId}` | 삭제 동작과 대상이 메서드·경로에 드러난다. |
| `POST /changeLanguage` | `PATCH /members/me` | 회원 리소스의 일부 속성을 변경한다. |

동사가 필요한 경우도 있다. OAuth 콜백, 평가 제출, 주문 취소처럼 단순 CRUD보다 명령의 의미가 중요한 동작은 하위 액션 리소스로 표현할 수 있다.

```http
POST /api/v1/guidebook-evaluations/{evaluationId}/submit
POST /api/v1/orders/{orderId}/cancellation
```

### 2.2 HTTP 메서드가 동작을 나타낸다

| 메서드 | 의미 | 안전성 | 멱등성 | V3 예시 |
|---|---|---:|---:|---|
| `GET` | 조회 | O | O | `GET /contents` |
| `POST` | 생성 또는 명령 접수 | X | 기본적으로 X | `POST /orders` |
| `PUT` | 식별 가능한 리소스 전체 교체 | X | O | `PUT /members/me/preferences` |
| `PATCH` | 리소스 일부 변경 | X | 설계에 따라 다름 | `PATCH /notifications/{id}` |
| `DELETE` | 리소스 삭제·관계 해제 | X | O | `DELETE /favorites/{contentId}` |

여기서 안전하다는 것은 서버 상태를 변경하지 않는다는 뜻이고, 멱등하다는 것은 동일 요청을 여러 번 실행해도 서버의 최종 상태가 같다는 뜻이다.

`POST`로 주문이나 가져오기를 요청할 때 네트워크 재시도가 중복 생성을 일으킬 수 있으므로, API 명세에서는 `Idempotency-Key`로 이를 보완한다.

## 3. 요청 정보는 어디에 넣는가

### 3.1 Path Parameter

특정 리소스 하나를 식별할 때 사용한다.

```http
GET /api/v1/guidebooks/gb_123
```

`gb_123`은 반드시 필요한 식별자이며, 없으면 다른 URL이 된다.

### 3.2 Query Parameter

목록의 검색·필터·정렬·페이지 이동처럼 선택 조건에 사용한다.

```http
GET /api/v1/contents?query=한강&category=ATTRACTION&sort=distance&cursor=abc
```

- `query`: 검색어
- `category`: 필터
- `sort`: 정렬 방식
- `cursor`: 다음 페이지 위치

필터가 여러 개면 각 조건의 결합 규칙을 명세해야 한다. V3 요구사항에서는 콘텐츠 필터를 AND로 결합한다.

### 3.3 Request Body

생성하거나 변경할 데이터 구조를 JSON으로 보낸다.

```json
{
  "start_date": "2026-09-10",
  "end_date": "2026-09-12",
  "region_code": "11",
  "travelers": 2
}
```

필수·선택 필드, 자료형, 허용 범위, 기본값을 함께 적어야 구현 시 해석 차이가 없다.

### 3.4 Header

리소스 자체의 값이 아니라 요청 처리에 필요한 메타데이터를 보낸다.

```http
Authorization: Bearer {access_token}
Idempotency-Key: 8a3b1d2e-...
Accept-Language: ko-KR
```

사용자 ID는 Body에 넣기보다 인증 토큰에서 얻는 편이 안전하다. 클라이언트가 다른 회원의 ID를 임의로 보내는 수평 권한 상승 위험을 줄일 수 있기 때문이다.

## 4. 응답 형식은 일관되게 유지한다

### 4.1 성공 응답

```json
{
  "message": "guidebook_detail_success",
  "data": {
    "guidebook_id": "gb_123",
    "title": "서울 2박 3일 여행"
  },
  "meta": {
    "request_id": "req_123"
  }
}
```

- `message`: 성공 결과를 구분하는 안정적인 키
- `data`: 실제 리소스 데이터
- `meta`: 추적 ID, 페이지 정보 등 부가 정보

`message`를 사용자 화면에 그대로 노출하기보다는 프론트의 다국어 문구와 매핑한다.

### 4.2 오류 응답

```json
{
  "message": "invalid_request",
  "data": null,
  "error": {
    "code": "GUIDEBOOK_INVALID_DATE_RANGE",
    "details": [
      {
        "field": "end_date",
        "reason": "must_be_on_or_after_start_date"
      }
    ],
    "trace_id": "req_123"
  }
}
```

오류 응답은 최소한 다음 역할을 분리하는 편이 좋다.

- HTTP 상태 코드: 오류의 큰 범주
- `error.code`: 프론트 분기 및 서버 로그용 안정적인 코드
- `details`: 입력 필드별 원인
- `trace_id`: 운영 환경에서 요청 추적

상세한 내부 예외 메시지, SQL, 스택 트레이스는 응답에 포함하지 않는다.

## 5. HTTP 상태 코드 선택법

| 코드 | 언제 사용하는가 | V3 예시 |
|---:|---|---|
| `200 OK` | 조회 또는 본문을 반환하는 변경 성공 | 가이드북 상세, 알림 읽음 처리 |
| `201 Created` | 즉시 새 리소스 생성 완료 | 주문, 공유 링크 |
| `202 Accepted` | 작업은 접수했지만 아직 미완료 | AI 생성, PDF 생성, 환불 처리 |
| `204 No Content` | 성공했지만 반환할 본문이 없음 | 로그아웃, 관심 장소 삭제 |
| `400 Bad Request` | JSON·타입·형식·범위 오류 | 검색어 10자 초과, 날짜 역전 |
| `401 Unauthorized` | 인증 정보가 없거나 유효하지 않음 | 토큰 만료 |
| `403 Forbidden` | 인증됐지만 해당 동작 권한이 없음 | 다른 회원 가이드북 접근 |
| `404 Not Found` | 대상 리소스가 없거나 공개할 수 없음 | 존재하지 않는 가이드북 |
| `409 Conflict` | 현재 상태와 요청이 충돌 | 중복 가져오기, 이미 제출한 평가 |
| `422 Unprocessable Content` | 형식은 맞지만 도메인 규칙상 처리 불가 | 생성권 부족, 여행 종료 전 평가 |
| `429 Too Many Requests` | 호출 제한 초과 | 검색·생성 요청 과다 |
| `500 Internal Server Error` | 예상하지 못한 서버 오류 | 내부 예외 |
| `502 Bad Gateway` | 외부 연동 실패 | PG·관광 API 응답 오류 |
| `503 Service Unavailable` | 일시적 서비스 불가 | AI 생성 서비스 장애 |

### 자주 헷갈리는 구분

- `401`과 `403`: 로그인 자체가 유효하지 않으면 `401`, 로그인했지만 소유권·역할이 없으면 `403`이다.
- `400`과 `422`: JSON 형식이나 타입이 틀리면 `400`, 형식은 맞지만 비즈니스 규칙을 통과하지 못하면 `422`로 구분할 수 있다.
- `200`과 `202`: 결과 리소스가 완성되었으면 `200/201`, 접수만 됐으면 `202`다.

## 6. 목록 API: 검색·필터·정렬·페이지네이션

### 6.1 목록 조건은 명시적으로 정의한다

```http
GET /api/v1/contents?query=궁궐&category=ATTRACTION&region_code=11&sort=distance
```

명세에 다음을 함께 적는다.

- 검색 대상 필드와 최대 길이
- 필터의 허용 enum과 결합 규칙
- 정렬 기준 및 동점 처리
- 조건이 없을 때 기본 정렬
- 결과가 없을 때 빈 배열 반환 여부

### 6.2 Offset과 Cursor

| 방식 | 예시 | 장점 | 단점 | 적합한 곳 |
|---|---|---|---|---|
| Offset | `page=3&size=20` | 구현·이해가 쉬움 | 데이터 변동 시 중복·누락, 깊은 페이지 성능 저하 | 변경이 적은 관리 화면 |
| Cursor | `cursor=abc&size=20` | 대량·실시간 데이터에 안정적 | 임의 페이지 이동이 어려움 | 알림, 콘텐츠 피드, 가이드북 목록 |

V3 명세는 변화가 잦은 목록에 Cursor 방식을 기본으로 사용한다. 응답에는 `next_cursor`와 `has_next`를 포함한다.

```json
{
  "items": [],
  "page": {
    "next_cursor": "eyJpZCI6MTAwfQ",
    "has_next": true
  }
}
```

## 7. PUT과 PATCH를 구분하는 방법

`PUT`은 해당 URL이 가리키는 리소스의 전체 상태를 클라이언트가 알고 있을 때 적합하다.

```http
PUT /api/v1/members/me/preferences
```

```json
{
  "preference_option_ids": [1, 4, 7]
}
```

기존 취향 구성을 이 값으로 교체한다.

`PATCH`는 일부 속성만 바꿀 때 적합하다.

```http
PATCH /api/v1/members/me
```

```json
{
  "language": "en-US"
}
```

PATCH에서 누락 필드는 유지하고, 명시적인 `null`은 삭제인지 허용하지 않는 값인지 필드별로 정의해야 한다.

## 8. 멱등성: 재시도해도 중복되지 않게 만들기

모바일 네트워크에서는 서버가 주문을 생성한 뒤 응답만 유실될 수 있다. 사용자가 다시 누르거나 앱이 재시도하면 같은 주문이 두 개 생길 수 있다.

```http
POST /api/v1/orders
Idempotency-Key: 8a3b1d2e-...
```

서버 처리 원칙은 다음과 같다.

1. `회원 ID + API 종류 + Idempotency-Key`를 고유하게 저장한다.
2. 처음 받은 요청은 처리하고 결과를 저장한다.
3. 같은 키와 같은 요청이 재전송되면 저장된 결과를 반환한다.
4. 같은 키인데 Body가 다르면 `409 Conflict`를 반환한다.
5. 키 보관 기간을 정하고 문서화한다.

멱등 키만으로 모든 중복을 막지는 못한다. 공유 가져오기는 `회원 ID + 공유 링크 ID`, 결제 웹훅은 `PG사 + 외부 이벤트 ID` 같은 DB 고유 제약도 함께 둔다.

## 9. 오래 걸리는 작업은 비동기로 처리한다

가이드북 AI 생성이나 PDF 생성이 수십 초 걸릴 수 있는데 HTTP 연결을 계속 유지하면 타임아웃과 중복 요청이 생기기 쉽다.

### 9.1 요청 접수

```http
POST /api/v1/guidebook-generations
```

```http
HTTP/1.1 202 Accepted
Location: /api/v1/guidebook-generations/gen_123
```

```json
{
  "message": "guidebook_generation_accepted",
  "data": {
    "job_id": "job_123",
    "status": "PENDING"
  }
}
```

### 9.2 상태 조회

```http
GET /api/v1/guidebook-generations/job_123
```

생성 작업 상태는 테이블 정의서와 동일하게 `PENDING → PROCESSING → COMPLETED` 또는 `FAILED`로 이동한다. 최초 생성 성공이면 새 `guidebook_id`를, 재생성 성공이면 기존 `guidebook_id`와 증가한 `guidebook_version`을 반환한다. `CANCELED`는 현재 저장 enum에 없으므로 사용자 취소를 제공하려면 상태 모델을 먼저 확정해야 한다.

프론트는 짧은 간격의 무한 폴링을 피하고 점진적으로 조회 간격을 늘리거나, 추후 SSE·WebSocket·푸시를 적용할 수 있다.

## 10. 생성권과 가이드북의 정합성

가이드북 생성 요청과 생성권 차감을 각각 독립적으로 처리하면 다음 오류가 생길 수 있다.

- 생성권은 차감됐지만 작업이 생성되지 않음
- AI 생성은 성공했지만 생성권이 차감되지 않음
- 재시도로 생성권이 두 번 차감됨

따라서 V3에서는 다음 흐름이 안전하다.

1. 생성 요청과 멱등 키를 검증한다.
2. `credit_wallets.reserved_count`를 조건부 원자 연산으로 1 증가시키고 `credit_transactions(RESERVE)`를 기록한다.
3. 백엔드가 `job_...` ID의 GenerationJob을 만들고 같은 ID를 AI에 전달한다.
4. 기술적 재시도는 같은 작업 ID와 예약을 사용하며 최대 3회 수행한다.
5. 최초 생성 성공이면 새 Guidebook을 저장하고 총잔액·예약수량을 각각 1 감소시킨 뒤 `CONSUME` 원장을 기록한다.
6. 재생성 성공이면 같은 가이드북 ID의 본문·일정을 교체하고 version을 증가시킨 뒤 동일하게 `CONSUME` 처리한다.
7. 최종 실패면 예약수량을 1 감소시키고 `RELEASE` 원장을 기록한다. 재생성 실패는 기존 가이드북을 변경하지 않는다.

`credit_transactions`의 `(generation_job_id, type)` 유일 제약이 같은 작업의 예약·확정·해제를 중복 반영하지 않게 한다. 별도 예약 테이블이나 `RESERVED/CONSUMED/RELEASED` 예약 엔티티를 API에 노출하지 않는다.

## 11. 결제·웹훅 설계 시 주의점

클라이언트의 “결제 성공” 화면만 믿고 생성권을 지급하면 안 된다. 서버가 PG 결과를 검증한 뒤 지급해야 한다.

- PG 서명 또는 인증 정보를 검증한다.
- 외부 거래 ID와 주문 ID가 일치하는지 확인한다.
- 결제 금액·통화·상품 가격을 서버 저장값과 대조한다.
- 동일 웹훅이 반복되어도 한 번만 상태를 변경한다.
- 웹훅 수신과 후속 처리를 분리할 경우 이벤트 원문과 처리 상태를 보관한다.
- 결제 성공, 생성권 지급, 원장 기록의 정합성을 트랜잭션 또는 재처리 가능한 이벤트로 보장한다.

외부 시스템에는 서버 내부 예외 대신 정상 수신 여부를 명확히 응답하고, 재시도 정책과 서명 실패 로그를 운영 기준에 포함한다.

### 11.1 생성권 API 용어는 테이블과 맞춘다

API에서도 `entitlement`보다 테이블 정의서와 같은 `credit` 용어를 사용한다.

```http
GET /api/v1/credits/wallet
GET /api/v1/credits/transactions
GET /api/v1/credit-products
```

- `credit_balance`: 보유 총수량
- `reserved_count`: 진행 중 생성 작업에 예약된 수량
- `available_count`: `credit_balance - reserved_count` 파생값
- `credit_delta`, `reserved_delta`: 원장 한 건의 변화량
- `credit_balance_after`, `reserved_count_after`: 처리 직후 값

지갑과 원장은 구현 내부 모델일 뿐, 클라이언트가 예약·차감 API를 직접 호출하지 않는다. 가이드북 생성 애플리케이션 서비스가 같은 트랜잭션 경계에서 처리한다.

### 11.2 환불 API는 저장 구조와 함께 확정한다

요구사항에는 환불이 있지만 현재 테이블 정의서에는 환불 요청 ID, 요청 금액, 사유, 멱등 키, 처리 상태를 독립적으로 보존할 테이블이 없다. `payment_attempts.status=REFUNDED`만으로는 접수부터 PG 완료 전까지의 비동기 상태를 표현하기 어렵다.

따라서 `POST /orders/{merchantOrderId}/refund`를 확정하려면 먼저 다음 중 하나를 선택해야 한다.

- `refunds` 테이블을 추가해 요청·PG 결과·원장 반영 상태를 분리한다.
- 부분 환불을 제외하고 동기 전체 취소만 지원하도록 범위를 줄인다.

정책과 저장 모델이 정해지기 전에는 환불 API를 구현 확정본으로 표시하지 않는다.

## 12. 평가 API와 저장 모델을 함께 본다

V3의 영속 리소스 이름은 `rating-session`이 아니라 `guidebook_evaluations`와 `place_ratings`다.

```http
PUT   /api/v1/guidebooks/{guidebookId}/evaluation
GET   /api/v1/guidebook-evaluations/{evaluationId}
PUT   /api/v1/guidebook-evaluations/{evaluationId}/places/{contentId}
PATCH /api/v1/guidebook-evaluations/{evaluationId}
POST  /api/v1/guidebook-evaluations/{evaluationId}/submit
```

`다음에 하기`는 `DEFERRED` 상태 전이가 아니다. `prompt_dismissed_at`만 기록하며 평가 status는 `PENDING` 또는 `IN_PROGRESS`를 유지한다.

별점 값은 다음처럼 구분한다.

- `score: 0`: 유효한 0점 평가
- `score: null`: 해당 장소 건너뛰기
- 응답 항목 또는 초안 값 없음: 아직 응답하지 않음

다만 현재 `place_ratings`는 `(member_id, tourism_content_id)`당 최종값 하나만 유지한다. 이 구조만으로는 제출 전 초안과 이전에 제출된 최종 평가를 분리하기 어렵다. 이전·다음 이동 중 저장과 최종 제출을 모두 보장하려면 평가별 초안 JSON 또는 평가 항목 테이블을 추가할지 결정해야 한다.

## 13. 재생성과 재시도를 구분한다

두 동작은 모두 AI를 다시 호출하지만 비즈니스 의미가 다르다.

| 구분 | 작업 ID | 가이드북 ID | 생성권 | 성공 결과 |
|---|---|---|---|---|
| 기술 재시도 | 같은 `job_id` | 그대로 | 추가 예약 없음 | 동일 작업 완료 |
| 사용자 재생성 | 새 `job_id` | 기존 `guidebook_id` 참조 | 새 1회 예약 | 같은 가이드북 갱신, version 증가 |

재생성 성공 시 새 가이드북을 만들면 공유·평가·목록의 참조가 갈라진다. 현재 요구사항과 테이블은 같은 ID 유지 방식을 선택했으므로 응답도 `source_guidebook_id`나 새 결과 ID가 아니라 대상 `guidebook_id`와 `guidebook_version`을 반환한다.

## 14. API와 테이블의 연결을 확인하는 법

API 필드가 테이블 컬럼과 이름이 완전히 같을 필요는 없지만 저장·복구 경로는 설명할 수 있어야 한다.

| API 값 | 저장 또는 계산 기준 |
|---|---|
| `preference_option_ids` | `member_preference_selections.preference_option_id` |
| `region_code` | `regions.administrative_code`로 조회한 `region_id` |
| `job_id` | `generation_jobs.id`; 백엔드가 생성해 AI에도 전달 |
| `guidebook_version` | `guidebooks.version` |
| `available_count` | `credit_balance - reserved_count` |
| `share_token` | 원문은 응답에만 노출, DB에는 `share_links.token_hash` 저장 |
| 지도 marker/cluster | 콘텐츠·좌표를 조합한 응답 모델, 별도 테이블 없음 |

저장할 곳을 설명할 수 없는 요청 필드는 구현 전에 테이블을 보강하거나 API에서 제외해야 한다. 현재 약관 동의, PDF 작업, 평가 초안, 환불 작업이 대표적인 검토 대상이다.

## 15. 인증과 인가를 분리한다

- 인증(Authentication): “누가 요청했는가?”를 확인한다.
- 인가(Authorization): “그 사용자가 이 작업을 해도 되는가?”를 확인한다.

예를 들어 유효한 로그인 회원이 다른 사람의 비공개 가이드북 ID를 알고 있어도 조회할 수 없어야 한다. 모든 소유 리소스 조회·변경 쿼리에는 토큰에서 얻은 회원 ID를 조건으로 사용한다.

공개 공유 링크는 로그인이 없어도 미리보기를 허용할 수 있지만, 만료 시간·폐기 여부를 검증하고 내부 DB 순번처럼 예측 가능한 토큰을 사용하지 않는다.

### 15.1 도메인 병합과 내부 모듈 경계

V3에서는 알림을 별도 도메인으로 두지 않고 DOM-01 회원의 하위 책임으로 관리한다. 알림이 수신 회원에게 귀속되고 현재 범위가 인앱 목록·읽음 상태·푸시 설정 중심이기 때문이다.

다만 도메인을 합쳤다고 해서 인증·회원 코드와 알림 전송 코드를 한 클래스나 패키지에 섞는 것은 아니다.

- DOM-01 내부에서 회원·취향·알림 패키지를 구분한다.
- 가이드북·평가 등은 성공 후 사건을 발행하고 알림 모듈이 이를 소비한다.
- `수신 회원 + 사건 ID`를 고유 기준으로 중복 알림을 방지한다.
- 알림 저장·푸시 실패는 원본 가이드북 생성이나 평가 제출을 롤백하지 않는다.
- 채널과 발송량이 커지면 현재 내부 경계를 기준으로 독립 도메인·서비스 분리를 검토한다.

## 16. 데이터 표현 규칙

### 16.1 날짜와 시간

- 날짜만 의미하면 ISO 8601의 `YYYY-MM-DD`를 사용한다.
- 시각은 타임존이 포함된 형식을 사용한다. 예: `2026-09-01T14:30:00+09:00`.
- 저장 기준과 랭킹 집계 기준 타임존을 명시한다.

### 16.2 금액

부동소수점 대신 최소 화폐 단위의 정수를 사용한다.

```json
{
  "amount": 4900,
  "currency": "KRW"
}
```

### 16.3 Enum

서버와 프론트가 함께 쓰는 상태값은 대문자 영문 enum으로 고정하고 임의 문자열을 허용하지 않는다.

```text
PENDING, PROCESSING, COMPLETED, FAILED
```

새 enum이 추가될 가능성을 고려해 프론트는 알 수 없는 값을 받았을 때의 기본 UI도 준비한다.

### 16.4 `null`, 누락, 빈 배열

- 누락: 값이 전달되지 않았거나 PATCH에서 변경하지 않음
- `null`: 값이 명시적으로 없음을 표현
- `[]`: 목록 값은 존재하지만 항목이 없음

평가에서는 `score: 0`이 유효한 별점이고 `score: null`은 미평가이므로 둘을 truthy/falsy 검사로 처리하면 안 된다.

## 17. API 버전과 호환성

V3 명세는 `/api/v1` Prefix를 사용한다. 여기의 `v1`은 앱 기획 버전 V3와 다른 개념으로, 외부 API 계약의 첫 번째 버전이라는 뜻이다.

호환성을 깨뜨리는 변경의 예시는 다음과 같다.

- 기존 필드 삭제 또는 이름 변경
- 필드 자료형 변경
- 필수 요청 필드 추가
- 기존 enum의 의미 변경
- 응답 상태 코드 의미 변경

선택 응답 필드를 추가하는 변경은 대체로 호환 가능하지만, 프론트가 알 수 없는 필드를 무시하도록 구현해야 한다.

## 18. OpenAPI로 옮길 때 확인할 내용

Markdown 명세가 합의되면 OpenAPI 문서에 다음을 구조화할 수 있다.

- `paths`: URL과 메서드
- `parameters`: Path·Query·Header 파라미터
- `requestBody`: 요청 JSON 스키마
- `responses`: 상태 코드별 응답과 예시
- `components.schemas`: 공통 모델·오류 모델
- `securitySchemes`: Bearer 인증
- `operationId`: 프론트 API 클라이언트 함수명 생성 기준

OpenAPI를 사용하면 Swagger UI, 요청 검증, 타입·클라이언트 코드 생성에 활용할 수 있다. 다만 자동 생성물이 비즈니스 규칙과 설계 근거를 대신하지 않으므로 Markdown 설명과 요구사항 ID 연결은 유지하는 편이 좋다.

## 19. 자주 발생하는 안티패턴

| 안티패턴 | 문제 | 개선 |
|---|---|---|
| 모든 요청을 `POST`로 작성 | 캐시·멱등성·의도가 불명확 | 리소스 변화에 맞는 메서드 사용 |
| 항상 `200`으로 응답 | 프론트·모니터링이 실패 유형을 구분하기 어려움 | 의미에 맞는 HTTP 상태 코드 사용 |
| 오류 메시지만 반환 | 문구 변경 시 분기 로직이 깨짐 | 안정적인 `error.code` 제공 |
| Body로 `member_id` 수신 | 다른 사용자의 ID로 요청할 위험 | 인증 토큰에서 회원 식별 |
| 무제한 목록 반환 | 응답 지연과 메모리 사용 증가 | 페이지네이션과 최대 size 제한 |
| AI 완료까지 연결 유지 | 타임아웃·중복 생성 위험 | `202`와 작업 상태 API 사용 |
| 결제 성공을 클라이언트만 신뢰 | 위·변조와 오지급 가능 | 서버의 PG 검증 후 지급 |
| 재시도 정책만 있고 멱등성 없음 | 중복 주문·가이드북·차감 발생 | 멱등 키와 DB 고유 제약 병행 |
| 내부 오류를 그대로 노출 | 보안 정보 유출 | 표준 오류 코드와 추적 ID 사용 |

## 20. API 리뷰 체크리스트

각 엔드포인트를 제출하기 전에 아래를 확인한다.

- [ ] URL이 리소스와 계층 관계를 명확히 나타내는가?
- [ ] HTTP 메서드 선택 이유를 설명할 수 있는가?
- [ ] 필수·선택 파라미터, 타입, 범위, 기본값이 적혀 있는가?
- [ ] 정상 응답과 주요 오류 응답 예시가 있는가?
- [ ] 인증과 소유권 검사가 정의되어 있는가?
- [ ] 동일 요청 재전송 시 결과가 정의되어 있는가?
- [ ] 목록 API에 필터·정렬·페이지네이션 규칙이 있는가?
- [ ] 비동기 작업에 작업 ID와 상태 조회 방법이 있는가?
- [ ] 외부 API 실패·타임아웃·재시도·복구 방법이 있는가?
- [ ] 요구사항 ID와 설계 근거를 추적할 수 있는가?
- [ ] 프론트가 상태와 오류 코드에 따라 화면을 구현할 수 있는가?
- [ ] 로그·메트릭·추적에 필요한 식별자가 있는가?

## 21. V3 API 명세서를 읽는 순서

1. `엔드포인트 요약`에서 화면에 필요한 API를 찾는다.
2. 해당 도메인 표에서 요청, 성공 응답, 오류, 인증 조건을 확인한다.
3. `요구사항` 열로 원본 요구사항과 연결한다.
4. `설계 근거` 열에서 메서드와 데이터 구조의 선택 이유를 확인한다.
5. 비동기 작업은 `GenerationJob`, 결제는 주문·결제 상태 전이를 함께 본다.
6. 미확정 정책은 `DEC-*` 항목을 팀 논의 후 명세에 반영한다.

## 22. 참고 자료

- [V3 요구사항정의서](https://github.com/100-hours-a-week/KTB4-10th-BE/blob/main/docs/%EC%9A%94%EA%B5%AC%EC%82%AC%ED%95%AD%EC%A0%95%EC%9D%98%EC%84%9C.md)
- [V3 테이블정의서](./테이블정의서.md)
- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [OpenAPI Specification 3.2.0](https://spec.openapis.org/oas/v3.2.0.html)
