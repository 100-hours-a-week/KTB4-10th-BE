# V3 API 명세서

> 기준일: 2026-09-04 · MySQL 스키마 연동 API 설계안
> 기준: [요구사항정의서](./요구사항정의서.md), [테이블정의서](./테이블정의서.md), [ERDCloud SQL](./ERDCloud_schema.sql)
> 구현 이유·요구사항 추적: [API 설계 근거](./API%20설계%20근거.md) · 학습: [REST API 예시 및 Best Practice](./REST%20API%20예시%20및%20Best%20Practice.md)

## 1. 공통 규약

| 항목 | 규약 |
|---|---|
| 기준 | 2026-09-04 · 로컬 repo 85f4083의 요구사항/테이블/ERDCloud SQL. 구현 완료 문서가 아닌 최신 스키마 연동 설계안. |
| API Prefix | /api/v1. URL 칼럼에서는 prefix 생략. |
| 문서 분리 | 본 명세서는 요청·응답 계약. 요구사항 연결·설계 이유·트레이드오프는 API 설계 근거.md에서 API ID로 조회. |
| 인증 | 별도 공개/PG 표시 외 Bearer 필수. 회원 ID는 인증 세션에서 결정하며 Body로 받지 않음. 소유권·탈퇴 여부를 매 요청 검증. |
| JSON | Content-Type: application/json. 성공 {message,data}, 오류 {message,data:null,error:{code,details,trace_id}}. message/code는 안정 키이며 화면 문구는 프론트 매핑. |
| 응답 예외 | 204는 Body 없음. PDF 성공은 application/pdf 바이너리, 실패는 JSON. 예시 객체는 필드 형태 설명이며 실제 계정/상품/전체 일정이 아님. |
| ID / 숫자 | BIGINT 식별자는 JSON/Path에서 양의 10진 문자열로 통일하는 설계안. gb/job은 ≤50자 문자열. page/count/people/score는 JSON 정수. 금액은 최소 화폐단위 정수이며 JS 안전 정수 범위 내 제한 필요. 랭킹 소수는 문자열. |
| 날짜·시각 | 사건 시각 ISO8601 오프셋 포함, 예시 UTC Z. DB DATETIME(6)에 UTC 저장. 여행 날짜 YYYY-MM-DD, 일정 시각 HH:mm:ss. 일간 경계 등 정책은 DEC-03. |
| 좌표 | API latitude/longitude는 WGS84 도 단위 Number. DB location POINT SRID4326과 변환. X/Y 순서를 임의로 위도·경도라고 가정하지 않음. |
| 문자열·URL | 필드별 최대 길이 검증. DB 외부 URL은 ≤2048자, 초과값 자르지 않음. nullable 명시 외 null 불가. 예시 URL은 실제 연동 주소 아님. |
| 커서 목록 | cursor 선택 불투명 문자열, size 기본20·1~100(설계안). 최신 created_at DESC,id DESC. 응답 items,next_cursor,has_more. 알림은 기본4·최대20 우선. |
| 번호 페이지 | 콘텐츠 page 기본1,size 기본5·최대20. 랭킹 page 기본1,size 기본20·최대100(설계안). items,page,size,total_items,total_pages. 빈 목록은 200,items=[]. |
| 멱등 키 | 필수 대상 GDE-02/GDE-09/PAY-04만. String≤100자. 키를 회원·요청 내용과 비교. 다른 내용이면409. 기존 작업/주문 반환 시 부작용 반복 금지. |
| 공통 오류 | 인증401·소유권403·삭제/미존재404·형식400·상태충돌409·업무조건422·호출제한429·내부500. 실제 한도와 Retry-After는 운영 합의 필요. |
| 제공 범위 | 회원5개 도메인 구조 유지. 제외된 ID는 재사용하지 않음. PG 2개 API와 각 미확정 항목은 구현 전 동결 필요. Figma/실행 서버/실 DB 테스트는 이번 검증 범위 밖. |

### 읽는 방법

각 API의 Request Body와 응답 예시는 엑셀과 동일하다. `미확정`은 팀 결정이 필요한 계약이며 예시 숫자나 Enum을 최종 정책으로 간주하지 않는다. URL·HTTP 동작·검증 조건은 명세에 남기고, 테이블 선택 이유·FR/BR 연결은 별도 근거 문서로 분리한다.

## 2. 엔드포인트 목록

| API ID | 기능 | Method | URL |
|---|---|---|---|
| API-MEM-01 | 소셜 로그인·가입 | POST | `/auth/oauth/{provider}/login` |
| API-MEM-02 | 토큰 갱신 | POST | `/auth/token/refresh` |
| API-MEM-03 | 로그아웃 | POST | `/auth/logout` |
| API-MEM-04 | 내 회원 조회 | GET | `/members/me` |
| API-MEM-06 | 회원 탈퇴 | DELETE | `/members/me` |
| API-MEM-07 | 취향 옵션 조회 | GET | `/preference-options` |
| API-MEM-08 | 기본 취향 조회 | GET | `/members/me/preferences` |
| API-MEM-09 | 기본 취향 전체 저장 | PUT | `/members/me/preferences` |
| API-MEM-10 | 설정 조회 | GET | `/members/me/settings` |
| API-MEM-11 | 설정 부분 수정 | PATCH | `/members/me/settings` |
| API-MEM-12 | 정책 안내 목록 | GET | `/policies` |
| API-MEM-13 | 정책 안내 상세 | GET | `/policies/{policy_type}` |
| API-NOT-01 | 미읽은 알림 목록 | GET | `/notifications` |
| API-NOT-02 | 알림 개별 삭제(읽기) | DELETE | `/notifications/{notification_id}` |
| API-NOT-03 | 알림 전체 삭제 | DELETE | `/notifications` |
| API-CON-01 | 관광 콘텐츠 검색 | GET | `/contents` |
| API-CON-02 | 관광 콘텐츠 상세 | GET | `/contents/{content_id}` |
| API-CON-03 | 지도 콘텐츠 조회 | GET | `/map/contents` |
| API-CON-04 | 관심 장소 목록 | GET | `/members/me/favorites` |
| API-CON-05 | 관심 장소 등록 | PUT | `/members/me/favorites/{content_id}` |
| API-CON-06 | 관심 장소 해제 | DELETE | `/members/me/favorites/{content_id}` |
| API-GDE-01 | 내 가이드북 목록 | GET | `/guidebooks` |
| API-GDE-02 | 최초 가이드북 생성 접수 | POST | `/guidebook-generations` |
| API-GDE-03 | 생성 상태 조회 | GET | `/guidebook-generations/{job_id}` |
| API-GDE-05 | 가이드북 상세 | GET | `/guidebooks/{guidebook_id}` |
| API-GDE-07 | 일정 조회 | GET | `/guidebooks/{guidebook_id}/itinerary` |
| API-GDE-09 | 가이드북 재생성 접수 | POST | `/guidebooks/{guidebook_id}/regenerations` |
| API-GDE-10 | 공유 링크 발급 | POST | `/guidebooks/{guidebook_id}/shares` |
| API-GDE-11 | 공유 미리보기 | GET | `/shares/{share_token}` |
| API-GDE-12 | 공유 가이드북 가져오기 | POST | `/shares/{share_token}/imports` |
| API-GDE-13 | PDF 다운로드 | POST | `/guidebooks/{guidebook_id}/exports` |
| API-GDE-15 | HTML 뷰어 데이터 | GET | `/guidebooks/{guidebook_id}/viewer` |
| API-GDE-16 | 가이드북 삭제 | DELETE | `/guidebooks/{guidebook_id}` |
| API-RNK-01 | 평가 대상·진행 목록 | GET | `/guidebook-evaluations` |
| API-RNK-02 | 평가 시작·재개 | PUT | `/guidebooks/{guidebook_id}/evaluation` |
| API-RNK-03 | 평가 대상 조회 | GET | `/guidebook-evaluations/{evaluation_id}` |
| API-RNK-05 | 평가 다음에 하기 | PATCH | `/guidebook-evaluations/{evaluation_id}` |
| API-RNK-06 | 평가 최종 제출 | POST | `/guidebook-evaluations/{evaluation_id}/submit` |
| API-RNK-07 | 랭킹 조회 | GET | `/rankings` |
| API-PAY-01 | 생성권 지갑 조회 | GET | `/credits/wallet` |
| API-PAY-02 | 생성권 원장 조회 | GET | `/credits/transactions` |
| API-PAY-03 | 생성권 상품 목록 | GET | `/credit-products` |
| API-PAY-04 | 주문 생성 | POST | `/orders` |
| API-PAY-05 | 주문 조회 | GET | `/orders/{merchant_order_id}` |
| API-PAY-06 | 결제 시도 생성 [PG 미확정] | POST | `/orders/{merchant_order_id}/payment-attempts` |
| API-PAY-07 | PG 웹훅 [PG 미확정] | POST | `/payments/webhooks/{provider}` |

## 3. 회원

### API-MEM-01 소셜 로그인·가입

| Method | URL | 인증 |
|---|---|---|
| POST | `/auth/oauth/{provider}/login` | 공개; OAuth state/PKCE 검증 흐름 별도 확정 |

- Path provider: String, KAKAO 또는 GOOGLE(지원 공급자 확정 필요)
- Body authorization_code: 필수 String, 일회용 인증 코드
- redirect_uri: 필수 String, 등록된 콜백과 일치
- 기기 ID·서비스 약관 동의 값은 받지 않음
- 응답 expires_in: Integer, 초 단위; 토큰 String; onboarding_required: Boolean
- member.status=ONBOARDING이면 취향 선택, ACTIVE이면 지도 화면으로 분기

**Request Body**

```json
{
  "authorization_code": "oauth_code",
  "redirect_uri": "https://app.example.com/oauth/callback"
}
```

**응답 200**

```json
{
  "message": "login_success",
  "data": {
    "access_token": "access_token",
    "refresh_token": "refresh_token",
    "expires_in": 3600,
    "member": {"member_id":"1","nickname":"여행자","profile_image_url":null,"language_code":"ko","status":"ACTIVE"},
    "onboarding_required": false
  }
}
```

**응답 201**

```json
{
  "message": "member_created",
  "data": {
    "access_token": "access_token",
    "refresh_token": "refresh_token",
    "expires_in": 3600,
    "member": {"member_id":"1","nickname":"여행자","profile_image_url":null,"language_code":"ko","status":"ONBOARDING"},
    "onboarding_required": true
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_EXPIRED | 토큰 만료·폐기 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 502 | UPSTREAM_SERVICE_ERROR | 동기 외부 연동 실패 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-01: OAuth 플랫폼·state/PKCE·토큰 보관/수명. 3600초는 예시이지 확정 TTL이 아님.

### API-MEM-02 토큰 갱신

| Method | URL | 인증 |
|---|---|---|
| POST | `/auth/token/refresh` | Bearer 불필요; refresh_token 검증 |

- refresh_token: 필수 String. 만료·폐기·탈퇴 회원 세션 사용 불가

**Request Body**

```json
{
  "refresh_token": "refresh_token"
}
```

**응답 200**

```json
{
  "message": "token_refresh_success",
  "data": {
    "access_token": "new_access_token",
    "refresh_token": "new_refresh_token",
    "expires_in": 3600
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-01: 토큰 회전 및 동시 갱신 재시도 정책 확정 필요.

### API-MEM-03 로그아웃

| Method | URL | 인증 |
|---|---|---|
| POST | `/auth/logout` | Bearer 필수 |

- refresh_token: 필수 String, 로그인 회원의 현재 세션
- 기기별 관리·all_devices 옵션 미제공

**Request Body**

```json
{
  "refresh_token": "refresh_token"
}
```

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-04 내 회원 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me` | Bearer 필수 |

- Query/Body 없음
- 응답 nickname ≤50자, profile_image_url: String|null ≤2048자
- status: ONBOARDING|ACTIVE; unread_count: Integer ≥0

**Request Body**

없음.

**응답 200**

```json
{
  "message": "member_get_success",
  "data": {
    "member_id": "1",
    "nickname": "여행자",
    "profile_image_url": null,
    "language_code": "ko",
    "status": "ACTIVE",
    "unread_count": 4
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-06 회원 탈퇴

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/members/me` | Bearer 필수 |

- Body 없음. 확인 모달은 프론트 처리
- 완료 후 현재 세션 포함 모든 세션 사용 불가

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-11: 보존·파기 및 재가입 정책; API-DEC-02: 진행 중 생성/결제와 탈퇴 경합 처리.

### API-MEM-07 취향 옵션 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/preference-options` | Bearer 필수 |

- Query language_code: 선택 String ≤10자
- 응답 preference_type: THEME|DETAIL|TRAVEL_STYLE
- code ≤50자; label String; parent_code String|null; sort_order Integer
- 옵션 코드 예시는 테이블 정의서의 예시이며 전체 목록은 확정 필요

**Request Body**

없음.

**응답 200**

```json
{
  "message": "preference_option_list_success",
  "data": {
    "items": [{"preference_type":"THEME","code":"HEALING","label":"힐링","parent_code":null,"sort_order":1},{"preference_type":"DETAIL","code":"QUIET_PLACE","label":"조용한 곳","parent_code":"HEALING","sort_order":1},{"preference_type":"TRAVEL_STYLE","code":"RELAXED","label":"여유롭게","parent_code":null,"sort_order":1}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-03: 허용 코드·부모 관계·표시명·언어 목록 확정.

### API-MEM-08 기본 취향 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me/preferences` | Bearer 필수 |

- 응답 selections: Array<{preference_type:String,preference_code:String}>
- 최초 미선택이면 빈 배열

**Request Body**

없음.

**응답 200**

```json
{
  "message": "preference_get_success",
  "data": {
    "selections": [{"preference_type":"THEME","preference_code":"HEALING"},{"preference_type":"DETAIL","preference_code":"QUIET_PLACE"},{"preference_type":"TRAVEL_STYLE","preference_code":"RELAXED"}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-09 기본 취향 전체 저장

| Method | URL | 인증 |
|---|---|---|
| PUT | `/members/me/preferences` | Bearer 필수 |

- selections: 필수 Array, 전체 선택 집합
- preference_type: 필수 THEME|DETAIL|TRAVEL_STYLE
- preference_code: 필수 String ≤50자, 허용 Enum
- THEME 1~3개; DETAIL은 선택된 부모 필요; 중복 조합 불가
- 누락 선택 제거; 최초 유효 저장 후 ACTIVE

**Request Body**

```json
{
  "selections": [
    {"preference_type":"THEME","preference_code":"HEALING"},
    {"preference_type":"DETAIL","preference_code":"QUIET_PLACE"},
    {"preference_type":"TRAVEL_STYLE","preference_code":"RELAXED"}
  ]
}
```

**응답 200**

```json
{
  "message": "preference_update_success",
  "data": {
    "selections": [{"preference_type":"THEME","preference_code":"HEALING"},{"preference_type":"DETAIL","preference_code":"QUIET_PLACE"},{"preference_type":"TRAVEL_STYLE","preference_code":"RELAXED"}],
    "status": "ACTIVE"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |

**구현 전 확인:** API-DEC-03: 중분류·스타일 최소/최대 선택 수 확정.

### API-MEM-10 설정 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me/settings` | Bearer 필수 |

- 응답 language_code: String ≤10자, 기본 ko
- push_enabled: Boolean

**Request Body**

없음.

**응답 200**

```json
{
  "message": "member_setting_get_success",
  "data": {
    "language_code": "ko",
    "push_enabled": true
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-11 설정 부분 수정

| Method | URL | 인증 |
|---|---|---|
| PATCH | `/members/me/settings` | Bearer 필수 |

- language_code: 선택 String ≤10자, 지원 코드만
- push_enabled: 선택 Boolean
- 최소 1개 필수; 누락은 유지, null 불가
- push_enabled=false는 푸시 발송만 금지하며, 필요한 인앱 알림 저장에는 영향을 주지 않음

**Request Body**

```json
{
  "language_code": "ko",
  "push_enabled": false
}
```

**응답 200**

```json
{
  "message": "member_setting_update_success",
  "data": {
    "language_code": "ko",
    "push_enabled": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-12 정책 안내 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/policies` | 공개 |

- Body 없음. 정적 안내 콘텐츠 목록
- policy_type: 안정적인 문자열 코드; 동의 상태 없음

**Request Body**

없음.

**응답 200**

```json
{
  "message": "policy_list_success",
  "data": {
    "items": [{"policy_type":"terms","title":"이용약관"},{"policy_type":"privacy","title":"개인정보 처리방침"}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-04: 정적 문서 배포 위치·type 목록. 별도 동의 API는 범위 제외.

### API-MEM-13 정책 안내 상세

| Method | URL | 인증 |
|---|---|---|
| GET | `/policies/{policy_type}` | 공개 |

- Path policy_type: 필수 String, 목록에 있는 코드
- 응답 content: String, format: MARKDOWN(설계안)

**Request Body**

없음.

**응답 200**

```json
{
  "message": "policy_get_success",
  "data": {
    "policy_type": "privacy",
    "title": "개인정보 처리방침",
    "format": "MARKDOWN",
    "content": "정책 안내 본문"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-04: 프론트 렌더링 형식 확정.

### API-NOT-01 미읽은 알림 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/notifications` | Bearer 필수 |

- Query cursor: 선택 불투명 문자열; size: Integer 1~20, 기본 4
- 최신 created_at DESC,id DESC. 읽은 알림은 없음
- reference_type/id: 둘 다 값 또는 둘 다 null

**Request Body**

없음.

**응답 200**

```json
{
  "message": "notification_list_success",
  "data": {
    "items": [{"notification_id":"301","type":"GUIDEBOOK_COMPLETED","title":"가이드북 완성","body":"가이드북을 확인해 주세요.","reference_type":"GUIDEBOOK","reference_id":"gb_example","created_at":"2026-09-04T00:00:00Z"}],
    "next_cursor": null,
    "has_more": false,
    "unread_count": 1
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-08: 초기 4/5·보관 기간; API-DEC-03: 알림 3종의 정확한 Enum과 이동 대상 매핑. 응답 type은 예시.

### API-NOT-02 알림 개별 삭제(읽기)

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/notifications/{notification_id}` | Bearer 필수 |

- Path notification_id: 필수 ID 문자열
- Body 없음; 자기 알림만 삭제, 이미 없으면 204

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-NOT-03 알림 전체 삭제

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/notifications` | Bearer 필수 |

- Body 없음
- 요청 처리 시작 시 존재하는 자기 알림만 삭제; 이후 생성된 알림은 유지

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

## 4. 관광콘텐츠

### API-CON-01 관광 콘텐츠 검색

| Method | URL | 인증 |
|---|---|---|
| GET | `/contents` | Bearer 필수 |

- Query q: 선택 String, 최대 10자; 빈 값=전체
- region_code: 선택 String ≤20자, 광역 행정코드; 생략=전체
- category: 선택 EVENT|CULTURAL_HERITAGE|ATTRACTION
- month: 선택 YYYY-MM(설계안), 행사 기간과 겹침
- page Integer ≥1 기본1; size 1~20 기본5; sort=title,asc(설계안)
- 조건 AND; 결과 없음 items=[]

**Request Body**

없음.

**응답 200**

```json
{
  "message": "content_list_success",
  "data": {
    "items": [{"content_id":"101","title":"불국사","category":"CULTURAL_HERITAGE","region":{"administrative_code":"47","name":"경상북도"},"latitude":35.7898,"longitude":129.3321,"thumbnail_url":null}],
    "page": 1,
    "size": 5,
    "total_items": 1,
    "total_pages": 1
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-02: 월 선택 가능 연도·상시 장소 혼합 정책; API-DEC-05: 검색 정렬·검색 방식 확정.

### API-CON-02 관광 콘텐츠 상세

| Method | URL | 인증 |
|---|---|---|
| GET | `/contents/{content_id}` | Bearer 필수 |

- Path content_id: 필수 ID 문자열
- latitude/longitude: Number, WGS84 도 단위
- category: 단일 Enum; description/address/phone/URL: String|null
- images: Array; event_detail: EVENT에서만 객체, 그 외 null
- URL ≤2048자; 비활성·삭제 콘텐츠는 일반 상세에서 제외
- event_detail.start_date/end_date: YYYY-MM-DD; operating_hours/organizer: String|null
- images[].image_id: ID; image_url: String ≤2048자; sort_order: Integer

**Request Body**

없음.

**응답 200**

```json
{
  "message": "content_get_success",
  "data": {
    "content_id": "101",
    "title": "불국사",
    "category": "CULTURAL_HERITAGE",
    "region": {"administrative_code":"47","name":"경상북도"},
    "latitude": 35.7898,
    "longitude": 129.3321,
    "thumbnail_url": null,
    "description": "장소 소개",
    "address": "경상북도 경주시",
    "phone": null,
    "homepage_url": null,
    "images": [{"image_id":"201","image_url":"https://example.com/image.jpg","sort_order":0}],
    "event_detail": null
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-CON-03 지도 콘텐츠 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/map/contents` | Bearer 필수 |

- Query (latitude,longitude,radius_m) 또는 (south,west,north,east) 중 하나
- 좌표 Number: 위도 -90~90, 경도 (-180,180]; radius_m >0
- zoom: Integer; limit: Integer >0; category 선택
- 응답 markers[]/clusters[]/has_more; 좌표는 도, 거리 m
- 필수 조합·최대 반경·마커 수·줌 범위는 미확정

**Request Body**

없음.

**응답 200**

```json
{
  "message": "map_content_success",
  "data": {
    "markers": [{"content_id":"101","title":"불국사","category":"CULTURAL_HERITAGE","region":{"administrative_code":"47","name":"경상북도"},"latitude":35.7898,"longitude":129.3321,"thumbnail_url":null}],
    "clusters": [],
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-05: 지도 범위 제한·클러스터 스키마/집계 주체·권한 거부 UX. 미확정 전 계약 동결 불가.

### API-CON-04 관심 장소 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me/favorites` | Bearer 필수 |

- Query cursor, size: 공통 커서 규칙
- 최신 등록 순; 응답 content와 favorited_at
- 비활성 콘텐츠 표시 방식은 미확정

**Request Body**

없음.

**응답 200**

```json
{
  "message": "favorite_list_success",
  "data": {
    "items": [{"content_id":"101","title":"불국사","category":"CULTURAL_HERITAGE","region":{"administrative_code":"47","name":"경상북도"},"latitude":35.7898,"longitude":129.3321,"thumbnail_url":null,"favorited_at":"2026-09-04T00:00:00Z"}],
    "next_cursor": null,
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-06: 비활성 즐겨찾기의 숨김/사용 불가 카드 정책.

### API-CON-05 관심 장소 등록

| Method | URL | 인증 |
|---|---|---|
| PUT | `/members/me/favorites/{content_id}` | Bearer 필수 |

- Path content_id: 필수 ID 문자열
- Body 없음. 활성 콘텐츠만 신규 등록; 중복 요청도 200

**Request Body**

없음.

**응답 200**

```json
{
  "message": "favorite_saved",
  "data": {
    "content_id": "101",
    "is_favorite": true
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-CON-06 관심 장소 해제

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/members/me/favorites/{content_id}` | Bearer 필수 |

- Path content_id: 필수 ID 문자열
- Body 없음. 이미 해제된 경우도 204

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

## 5. 가이드북

### API-GDE-01 내 가이드북 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks` | Bearer 필수 |

- Query cursor, size: 공통 커서 규칙
- sort=created_at,desc 고정; id DESC 보조 정렬
- 소유자 일치 및 deleted_at IS NULL

**Request Body**

없음.

**응답 200**

```json
{
  "message": "guidebook_list_success",
  "data": {
    "items": [{"guidebook_id":"gb_example","title":"경주 역사 여행","region":{"administrative_code":"47","name":"경상북도"},"start_date":"2026-10-12","end_date":"2026-10-14","companion":"FRIEND","people_count":2,"version":1,"updated_at":"2026-09-04T00:00:00Z"}],
    "next_cursor": null,
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-02 최초 가이드북 생성 접수

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebook-generations` | Bearer 필수 |

- Idempotency-Key: 필수 String ≤100자
- region_code: 필수 String ≤20자, 17개 광역 지역 중 하나
- start_date/end_date: 필수 YYYY-MM-DD, 양끝 포함 1~7일
- companion: 필수 Enum 문자열 ≤20자(목록 미확정)
- people_count: 필수 Integer ≥1
- 취향은 서버에서 현재값 조회; 세부 지역·개별 취향 Body 없음
- ACTIVE 회원·유효 기본 취향·잔액≥1·진행 작업 없음 필수
- 동일 키 재요청은 기존 작업의 현재 상태 반환; 새 AI 작업 생성 안 함

**Request Body**

```json
{
  "region_code": "47",
  "start_date": "2026-10-12",
  "end_date": "2026-10-14",
  "companion": "FRIEND",
  "people_count": 2
}
```

**응답 202**

```json
{
  "message": "guidebook_generation_accepted",
  "data": {
    "job_id": "job_example",
    "job_type": "INITIAL",
    "status": "PENDING",
    "guidebook_id": null
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 키에 다른 요청 또는 다른 회원 |
| 409 | GENERATION_IN_PROGRESS | 이미 진행 중인 생성 작업 존재 |
| 422 | CREDIT_INSUFFICIENT | 생성권 잔액 1개 미만 |
| 422 | GUIDEBOOK_INVALID_PERIOD | 종료일 역전 또는 양끝 포함 7일 초과 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-09 및 API-DEC-03: 총 시도 수/재시도 수·동행 Enum·인원 상한 확정.

### API-GDE-03 생성 상태 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-generations/{job_id}` | Bearer 필수 |

- Path job_id: 필수 String ≤50자
- status: PENDING|PROCESSING|COMPLETED|FAILED
- attempt_count: Integer 0~3; 최초 생성 완료 전 guidebook_id=null
- error: null 또는 {code,message}; 내부 AI payload 미노출

**Request Body**

없음.

**응답 200**

```json
{
  "message": "generation_job_get_success",
  "data": {
    "job_id": "job_example",
    "job_type": "INITIAL",
    "status": "COMPLETED",
    "guidebook_id": "gb_example",
    "attempt_count": 1,
    "guidebook_version": 1,
    "error": null
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-05 가이드북 상세

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks/{guidebook_id}` | Bearer 필수 |

- Path guidebook_id: 필수 String ≤50자
- 응답 companion String; people_count Integer; version Integer ≥1
- content_html: String|null; region은 광역 정보
- 삭제본은 404; 생성 당시 취향 카드 값은 기본 응답 제외

**Request Body**

없음.

**응답 200**

```json
{
  "message": "guidebook_get_success",
  "data": {
    "guidebook_id": "gb_example",
    "title": "경주 역사 여행",
    "region": {"administrative_code":"47","name":"경상북도"},
    "start_date": "2026-10-12",
    "end_date": "2026-10-14",
    "companion": "FRIEND",
    "people_count": 2,
    "version": 1,
    "updated_at": "2026-09-04T00:00:00Z",
    "content_html": "<article>여행 안내</article>"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-01: 기존 카드에 표시할 취향의 기준.

### API-GDE-07 일정 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks/{guidebook_id}/itinerary` | Bearer 필수 |

- days[].day_number: Integer ≥1; itinerary_date: YYYY-MM-DD
- items[].item_id: ID; content_id: ID|null
- sequence: Integer ≥1; scheduled_time: HH:mm:ss|null
- place_snapshot: 생성/저장 시점 장소 정보 객체

**Request Body**

없음.

**응답 200**

```json
{
  "message": "itinerary_get_success",
  "data": {
    "guidebook_id": "gb_example",
    "days": [{"day_number":1,"itinerary_date":"2026-10-12","items":[{"item_id":"501","content_id":"101","sequence":1,"scheduled_time":"10:00:00","place_snapshot":{"title":"불국사","address":"경상북도 경주시","latitude":35.7898,"longitude":129.3321}}]}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-09 가이드북 재생성 접수

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebooks/{guidebook_id}/regenerations` | Bearer 필수 |

- Idempotency-Key: 필수 String ≤100자
- feedback: 필수 String, 공백만 불가, ≤200자(기존 API 설계안). 사용자가 초안 확인 화면에서 입력한 자연어 수정 요청
- 일정·제목을 직접 수정하는 API는 제공하지 않으며, 변경은 이 재생성으로만 반영
- 현재 기본 취향 사용; feedback은 해당 가이드북만 반영하고 회원 기본 취향을 변경하지 않음
- 새 작업; 성공 시 같은 guidebook_id의 version 증가
- ACTIVE 회원·유효 기본 취향·잔액≥1·진행 작업 없음 필수
- 동일 키 재요청은 기존 작업의 현재 상태 반환; 새 AI 작업 생성 안 함

**Request Body**

```json
{
  "feedback": "걷는 시간을 줄여 주세요."
}
```

**응답 202**

```json
{
  "message": "regeneration_accepted",
  "data": {
    "job_id": "job_regeneration",
    "job_type": "REGENERATION",
    "status": "PENDING",
    "guidebook_id": "gb_example",
    "current_version": 1
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 키에 다른 요청 또는 다른 회원 |
| 409 | GENERATION_IN_PROGRESS | 이미 진행 중인 생성 작업 존재 |
| 422 | CREDIT_INSUFFICIENT | 생성권 잔액 1개 미만 |
| 422 | GUIDEBOOK_INVALID_PERIOD | 종료일 역전 또는 양끝 포함 7일 초과 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |

**구현 전 확인:** DEC-12: 평가 완료/진행 중 재생성, 공유 및 PDF 반영. feedback 상한 최종 합의 필요.

### API-GDE-10 공유 링크 발급

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebooks/{guidebook_id}/shares` | Bearer 필수 |

- expires_at: 선택 ISO8601 오프셋 시각; 값이 있으면 미래
- null/생략의 기본 만료 정책 미확정
- share_url: String; expires_at: String|null
- Idempotency-Key 보장 없음; 재요청 시 새 링크 가능

**Request Body**

```json
{
  "expires_at": "2026-11-01T00:00:00Z"
}
```

**응답 201**

```json
{
  "message": "share_created",
  "data": {
    "share_url": "https://app.example.com/shares/random_token",
    "expires_at": "2026-11-01T00:00:00Z"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |

**구현 전 확인:** DEC-06/12: 기본 만료·공개 범위·재생성 시 링크 동작.

### API-GDE-11 공유 미리보기

| Method | URL | 인증 |
|---|---|---|
| GET | `/shares/{share_token}` | 공개 |

- Path share_token: 필수 난수 문자열
- 미존재·만료·원본 삭제는 모두 404
- 응답은 개인정보 없는 미리보기; 상세 공개 필드는 미확정

**Request Body**

없음.

**응답 200**

```json
{
  "message": "share_preview_success",
  "data": {
    "title": "경주 역사 여행",
    "region": {"administrative_code":"47","name":"경상북도"},
    "start_date": "2026-10-12",
    "end_date": "2026-10-14"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 404 | SHARE_LINK_UNAVAILABLE | 공유 토큰 미존재·만료·대상 삭제 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-06: 로그인 전 일정·HTML 포함 여부. 현재 예시는 최소 미리보기 설계안.

### API-GDE-12 공유 가이드북 가져오기

| Method | URL | 인증 |
|---|---|---|
| POST | `/shares/{share_token}/imports` | Bearer 필수 |

- Path share_token: 필수 String
- Body 없음. 최초 원본 기준 같은 회원의 중복 복사 금지
- 첫 복사 201; 기존 복사본이면 200 및 기존 guidebook_id

**Request Body**

없음.

**응답 201**

```json
{
  "message": "guidebook_imported",
  "data": {
    "guidebook_id": "gb_imported",
    "already_imported": false
  }
}
```

**응답 200**

```json
{
  "message": "already_imported",
  "data": {
    "guidebook_id": "gb_imported",
    "already_imported": true
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 404 | SHARE_LINK_UNAVAILABLE | 공유 토큰 미존재·만료·대상 삭제 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-07: 삭제한 복사본의 재가져오기 처리. Idempotency-Key 없이 자연 유일성으로 중복 복사를 방지.

### API-GDE-13 PDF 다운로드

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebooks/{guidebook_id}/exports` | Bearer 필수 |

- format: 필수, PDF만 허용
- 성공 Content-Type: application/pdf
- Content-Disposition: attachment; filename*=UTF-8''guidebook.pdf
- 동기 생성, JSON envelope·export_id·상태 조회 없음
- 실패 Content-Type: application/json; 원본은 유지

**Request Body**

```json
{
  "format": "PDF"
}
```

**응답 200**

PDF 바이너리 (JSON 아님)

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 503 | SERVICE_UNAVAILABLE | 일시적 서비스 이용 불가 |

**구현 전 확인:** API-DEC-08: PDF 응답 시간·최대 크기·동시 처리 한도.

### API-GDE-15 HTML 뷰어 데이터

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks/{guidebook_id}/viewer` | Bearer 필수 |

- 응답 content_html: String|null; version: Integer; updated_at: ISO8601
- Content-Type: application/json
- 스크립트·이벤트 핸들러 제거 후 격리 렌더링

**Request Body**

없음.

**응답 200**

```json
{
  "message": "viewer_get_success",
  "data": {
    "guidebook_id": "gb_example",
    "content_html": "<article>여행 안내</article>",
    "version": 1,
    "updated_at": "2026-09-04T00:00:00Z"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-16 가이드북 삭제

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/guidebooks/{guidebook_id}` | Bearer 필수 |

- Path guidebook_id: 필수 String ≤50자
- Body 없음; 소유자만 삭제
- 목록·상세·공유 즉시 차단; 다른 회원의 복사본 유지

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-02: 진행 중 재생성·PDF와 삭제 경합 정책.

## 6. 평가랭킹

### API-RNK-01 평가 대상·진행 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-evaluations` | Bearer 필수 |

- Query status: 선택 PENDING|SUBMITTED, 기본 PENDING
- cursor,size: 공통 규칙
- 여행 종료 다음 날 이후 자기 가이드북; 평가 행 미생성 대상도 포함
- evaluation_id: ID|null, null이면 RNK-02로 준비

**Request Body**

없음.

**응답 200**

```json
{
  "message": "evaluation_list_success",
  "data": {
    "items": [{"evaluation_id":null,"guidebook_id":"gb_example","status":"PENDING","prompt_dismissed_at":null}],
    "next_cursor": null,
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-RNK-02 평가 시작·재개

| Method | URL | 인증 |
|---|---|---|
| PUT | `/guidebooks/{guidebook_id}/evaluation` | Bearer 필수 |

- Body 없음; 소유 가이드북·종료 다음 날 이후
- 최초 행 생성 201; 기존 평가 반환 200
- 제출 전 상태 PENDING, 완료 후 SUBMITTED

**Request Body**

없음.

**응답 201**

```json
{
  "message": "evaluation_ready",
  "data": {
    "evaluation_id": "701",
    "guidebook_id": "gb_example",
    "status": "PENDING"
  }
}
```

**응답 200**

```json
{
  "message": "evaluation_ready",
  "data": {
    "evaluation_id": "701",
    "guidebook_id": "gb_example",
    "status": "PENDING"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 422 | EVALUATION_NOT_ELIGIBLE | 여행 종료 다음 날 이전 또는 평가 대상 불일치 |

### API-RNK-03 평가 대상 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-evaluations/{evaluation_id}` | Bearer 필수 |

- Path evaluation_id: 필수 ID 문자열
- places[].content_id: ID; current_score: Integer 0~5|null
- current_score는 회원·장소의 기존 최종값이지 작성 중 초안이 아님
- 반복 장소는 content_id 기준 중복 제거(설계안)
- 미매핑 content_id=null 장소의 평가 처리 미확정

**Request Body**

없음.

**응답 200**

```json
{
  "message": "evaluation_get_success",
  "data": {
    "evaluation_id": "701",
    "guidebook_id": "gb_example",
    "status": "PENDING",
    "places": [{"content_id":"101","title":"불국사","current_score":0}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-05 및 API-DEC-09: 대상 중복·미매핑·기존 최종값 제시/덮어쓰기.

### API-RNK-05 평가 다음에 하기

| Method | URL | 인증 |
|---|---|---|
| PATCH | `/guidebook-evaluations/{evaluation_id}` | Bearer 필수 |

- prompt_dismissed: 필수 Boolean, true만 허용
- status는 PENDING 유지; 이후 자동 모달 재표시 안 함
- 목록에서 직접 평가 재개 가능

**Request Body**

```json
{
  "prompt_dismissed": true
}
```

**응답 200**

```json
{
  "message": "evaluation_prompt_dismissed",
  "data": {
    "evaluation_id": "701",
    "status": "PENDING",
    "prompt_dismissed_at": "2026-09-04T00:00:00Z"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |

### API-RNK-06 평가 최종 제출

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebook-evaluations/{evaluation_id}/submit` | Bearer 필수 |

- ratings: 필수 Array; content_id 필수 ID 문자열·중복 불가
- score: 필수 Integer 0~5 또는 null(건너뛰기)
- Body에 최종 점수 전체 전달; 초안 저장 API 없음
- 제출 시 place_ratings 반영 및 SUBMITTED를 원자적으로 처리
- 랭킹은 후속 비동기 집계, 즉시 갱신 보장 안 함

**Request Body**

```json
{
  "ratings": [
    {"content_id":"101","score":0},
    {"content_id":"102","score":null}
  ]
}
```

**응답 200**

```json
{
  "message": "evaluation_submitted",
  "data": {
    "evaluation_id": "701",
    "status": "SUBMITTED",
    "submitted_at": "2026-09-04T00:00:00Z"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 422 | EVALUATION_NOT_ELIGIBLE | 여행 종료 다음 날 이전 또는 평가 대상 불일치 |
| 409 | EVALUATION_ALREADY_SUBMITTED | 이미 제출된 평가의 다른 내용 제출 |

**구현 전 확인:** DEC-05/API-DEC-09: 부분 제출·제출 후 수정·기존 점수의 null 덮어쓰기·재요청 비교 정책. 제출 payload 이력이 없어 정확한 응답 재생은 보장하지 않음.

### API-RNK-07 랭킹 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/rankings` | Bearer 필수 |

- Query period_type: 필수 DAILY|WEEKLY|MONTHLY
- period_start: 필수 YYYY-MM-DD; region_code 선택, 생략=전국
- page Integer≥1 기본1; size Integer1~100 기본20(상한 설계안)
- 응답 calculated_at ISO8601|null; rank_position Integer≥1
- weighted_score/raw_average: 소수 문자열; rating_count Integer≥0
- 유효 구간 결과 없음은 200 items=[], 미집계 calculated_at=null

**Request Body**

없음.

**응답 200**

```json
{
  "message": "ranking_list_success",
  "data": {
    "period_type": "DAILY",
    "period_start": "2026-09-03",
    "period_end": "2026-09-03",
    "region_code": null,
    "calculated_at": "2026-09-04T00:00:00Z",
    "items": [{"content_id":"101","rank_position":1,"weighted_score":"4.12345","raw_average":"4.50","rating_count":10}],
    "page": 1,
    "size": 20,
    "total_items": 1,
    "total_pages": 1
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-03/04: 기간 경계·점수 귀속 시각·공식·동점 규칙·갱신 지연 목표.

## 7. 생성권결제

### API-PAY-01 생성권 지갑 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/credits/wallet` | Bearer 필수 |

- credit_balance: Integer ≥0
- active_job_id: String|null, PENDING/PROCESSING 작업
- can_generate: Boolean, 잔액≥1·활성 작업 없음·유효 취향·활성 회원
- 예약 수량·지갑 version 응답 없음

**Request Body**

없음.

**응답 200**

```json
{
  "message": "credit_wallet_get_success",
  "data": {
    "credit_balance": 6,
    "active_job_id": null,
    "can_generate": true
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-02 생성권 원장 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/credits/transactions` | Bearer 필수 |

- Query cursor,size: 공통 규칙
- type 선택 FREE_GRANT|PURCHASE_GRANT|CONSUME|REVOKE|ADJUSTMENT
- credit_delta: Integer; credit_balance_after: Integer≥0
- order_id: ID|null; generation_job_id: String|null

**Request Body**

없음.

**응답 200**

```json
{
  "message": "credit_transaction_list_success",
  "data": {
    "items": [{"transaction_id":"801","type":"CONSUME","credit_delta":-1,"credit_balance_after":5,"order_id":null,"generation_job_id":"job_example","created_at":"2026-09-04T00:00:00Z"}],
    "next_cursor": null,
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-03 생성권 상품 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/credit-products` | Bearer 필수 |

- Body 없음; 서버가 ACTIVE 및 deleted_at IS NULL 필터
- price: Integer ≥0, KRW 최소 화폐단위
- credit_amount: Integer >0; currency: 3자리 문자열
- 금액·상품 예시는 정책 확정값 아님

**Request Body**

없음.

**응답 200**

```json
{
  "message": "credit_product_list_success",
  "data": {
    "items": [{"product_id":"1","name":"생성권 5회","credit_amount":5,"price":5000,"currency":"KRW"}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-04 주문 생성

| Method | URL | 인증 |
|---|---|---|
| POST | `/orders` | Bearer 필수 |

- Idempotency-Key: 필수 String ≤100자
- product_id: 필수 ID 문자열
- 수량·금액·통화는 Body로 받지 않음

**Request Body**

```json
{
  "product_id": "1"
}
```

**응답 201**

```json
{
  "message": "order_created",
  "data": {
    "merchant_order_id": "order_example",
    "product_id": "1",
    "ordered_credit_amount": 5,
    "total_amount": 5000,
    "currency": "KRW",
    "status": "CREATED",
    "created_at": "2026-09-04T00:00:00Z"
  }
}
```

**응답 200**

```json
{
  "message": "order_get_success",
  "data": {
    "merchant_order_id": "order_example",
    "product_id": "1",
    "ordered_credit_amount": 5,
    "total_amount": 5000,
    "currency": "KRW",
    "status": "CREATED",
    "created_at": "2026-09-04T00:00:00Z"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 키에 다른 요청 또는 다른 회원 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-05 주문 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/orders/{merchant_order_id}` | Bearer 필수 |

- Path merchant_order_id: 필수 String ≤100자
- status: CREATED|PAYMENT_PENDING|PAID|FAILED|CANCELED
- 결제 시도 status: REQUESTED|APPROVED|FAILED|CANCELED
- PG 원문·서명·민감 checkout 정보 미노출

**Request Body**

없음.

**응답 200**

```json
{
  "message": "order_get_success",
  "data": {
    "merchant_order_id": "order_example",
    "product_id": "1",
    "ordered_credit_amount": 5,
    "total_amount": 5000,
    "currency": "KRW",
    "status": "CREATED",
    "created_at": "2026-09-04T00:00:00Z",
    "payment_attempts": []
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-06 결제 시도 생성 [PG 미확정]

| Method | URL | 인증 |
|---|---|---|
| POST | `/orders/{merchant_order_id}/payment-attempts` | Bearer 필수 |

- Path merchant_order_id: 필수 String ≤100자
- Body 필드·PG 공급자·checkout 응답·재시도 키는 미확정
- 아래 응답은 내부 모델 설계 예시이며 구현 확정 계약 아님

**Request Body**

미확정: PG 계약 확정 후 작성.

**응답 201**

```json
{
  "message": "payment_attempt_created",
  "data": {
    "payment_attempt_id": "901",
    "merchant_order_id": "order_example",
    "status": "REQUESTED"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_TOKEN_REQUIRED | 인증 토큰 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 502 | UPSTREAM_SERVICE_ERROR | 동기 외부 연동 실패 |

**구현 전 확인:** API-DEC-10: PG 선정 후 Body/checkout·승인 확인 API·키/실패 복구 확정. 현재 구현 보류.

### API-PAY-07 PG 웹훅 [PG 미확정]

| Method | URL | 인증 |
|---|---|---|
| POST | `/payments/webhooks/{provider}` | PG 서명 검증; Bearer 사용 안 함 |

- PG 원문 요청·서명 헤더·응답 규격은 공급자별로 확정
- 임의의 공통 JSON Body를 PG 실제 요청으로 사용하지 않음
- 원문 바이트로 서명 확인 후 금액·통화·주문 검증
- 상태 코드/응답 예시는 내부 설계안

**Request Body**

미확정: PG 계약 확정 후 작성.

**응답 200**

```json
{
  "message": "webhook_processed",
  "data": null
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | PAYMENT_WEBHOOK_INVALID | PG 서명 검증 실패: 실제 응답 규약은 PG 확정 후 적용 |
| 409 | ORDER_AMOUNT_MISMATCH | 검증된 PG 응답과 주문 금액·통화 불일치 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-10: 실제 PG 응답 규약·이벤트 중복 키·역순 이벤트·재처리 확정. 현재 구현 보류.

## 8. 공통 오류 응답

```json
{
  "message": "invalid_request",
  "data": null,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "details": [],
    "trace_id": "req_example"
  }
}
```

검증 오류는 details에 `{field,reason}`을 추가할 수 있다. 오류에도 위 형식을 사용하며 DB 예외·PG 비밀값을 그대로 전달하지 않는다.

| error.code | HTTP | message | 사용 조건 |
|---|---|---|---|
| COMMON_VALIDATION_ERROR | 400 | invalid_request | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| AUTH_TOKEN_REQUIRED | 401 | authentication_required | 인증 토큰 누락·유효하지 않음 |
| AUTH_TOKEN_EXPIRED | 401 | token_expired | 토큰 만료·폐기 |
| RESOURCE_FORBIDDEN | 403 | forbidden | 타인 소유 데이터 또는 허용되지 않은 상태 |
| RESOURCE_NOT_FOUND | 404 | resource_not_found | 없거나 삭제된 리소스 |
| SHARE_LINK_UNAVAILABLE | 404 | share_link_unavailable | 공유 토큰 미존재·만료·대상 삭제 |
| IDEMPOTENCY_CONFLICT | 409 | idempotency_conflict | 같은 키에 다른 요청 또는 다른 회원 |
| GENERATION_IN_PROGRESS | 409 | generation_in_progress | 이미 진행 중인 생성 작업 존재 |
| RESOURCE_STATE_CONFLICT | 409 | resource_state_conflict | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| EVALUATION_ALREADY_SUBMITTED | 409 | evaluation_already_submitted | 이미 제출된 평가의 다른 내용 제출 |
| ORDER_AMOUNT_MISMATCH | 409 | order_amount_mismatch | 검증된 PG 응답과 주문 금액·통화 불일치 |
| CREDIT_INSUFFICIENT | 422 | credit_insufficient | 생성권 잔액 1개 미만 |
| GUIDEBOOK_INVALID_PERIOD | 422 | invalid_trip_period | 종료일 역전 또는 양끝 포함 7일 초과 |
| PREFERENCE_INVALID | 422 | invalid_preference | 대분류 개수·코드·상하위 관계 위반 |
| EVALUATION_NOT_ELIGIBLE | 422 | evaluation_not_eligible | 여행 종료 다음 날 이전 또는 평가 대상 불일치 |
| PAYMENT_WEBHOOK_INVALID | 401 | payment_webhook_invalid | PG 서명 검증 실패: 실제 응답 규약은 PG 확정 후 적용 |
| RATE_LIMIT_EXCEEDED | 429 | rate_limit_exceeded | 호출 한도 초과; 적용 한도·Retry-After는 운영 설정 |
| INTERNAL_SERVER_ERROR | 500 | internal_server_error | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| UPSTREAM_SERVICE_ERROR | 502 | upstream_service_error | 동기 외부 연동 실패 |
| SERVICE_UNAVAILABLE | 503 | service_unavailable | 일시적 서비스 이용 불가 |

## 9. 제외·보류된 기존 API ID

| API ID | 이전 URL | 현재 처리 |
|---|---|---|
| API-MEM-05 | PATCH /members/me | 직접 프로필 수정 보류. FR-MEM-04와 테이블의 닉네임 직접 수정 미제공을 팀에서 정리해야 함. 언어는 MEM-11. |
| API-MEM-14 | POST /members/me/consents | 별도 서비스 약관 동의 저장은 현재 범위 제외. 안내는 정적 콘텐츠. |
| API-NOT-04 | GET /push-preferences | 푸시 설정 조회는 MEM-10 회원 설정 조회로 통합. |
| API-NOT-05 | PATCH /push-preferences | 푸시 설정 변경은 MEM-11 회원 설정 부분 수정으로 통합. |
| API-GDE-04 | POST /guidebook-generations/{job_id}/retry | 기술 재시도는 서버 내부 동일 작업 처리. 사용자 재생성은 GDE-09. |
| API-GDE-06 | PATCH /guidebooks/{guidebook_id} | 사용자의 가이드북 제목 직접 수정 기능이 없어 제외. |
| API-GDE-08 | PUT /guidebooks/{guidebook_id}/itinerary | 사용자의 일정 직접 편집 기능이 없어 제외. 변경은 GDE-09 자연어 피드백 재생성으로만 처리. |
| API-GDE-14 | GET /guidebook-exports/{export_id} | MVP는 동기 PDF이므로 작업 조회 API 미제공. |
| API-RNK-04 | PUT /guidebook-evaluations/{evaluation_id}/places/{content_id} | 서버 초안 저장 API 미제공. 최종 ratings를 RNK-06 Body로 제출. |
| API-PAY-08 | POST /orders/{merchant_order_id}/refund | 환불은 MVP 이후. 현재 구현 대상 아님. |

## 10. 구현 전 확인 항목

| 결정 ID | 영향 | 확인할 내용 |
|---|---|---|
| DEC-01 | GDE-01/05 | 생성 당시/현재 취향 카드 표시는 미확정. 기본 응답에 취향 필드 없음. |
| DEC-02 | CON-01 | 월 필터 연도와 비행사 혼합 처리. month=YYYY-MM은 제안 계약. |
| DEC-03/04 | RNK-07 | 랭킹 기간 경계·귀속 시각·C/m·동점·배치 주기·최대 지연. updated_at 변경 탐지와 점수의 기간 귀속은 구분. |
| DEC-05 | RNK-03/06 | 부분 제출·제출 후 수정. 현재 확정은 정수 0~5, null 건너뛰기, 프론트 초안. |
| DEC-06 | GDE-10/11 | 기본 만료·미리보기 공개 범위. |
| DEC-07 | GDE-12 | 원본 보존 및 삭제된 복사본 재가져오기 처리. |
| DEC-08 | NOT-01 | 최대20, 초기4를 현 기준으로 반영. 4/5 최종 선택 및 정리 기간. |
| DEC-09 | GDE-02/03/09 | attempt_count 0~3와 최대 재시도3의 해석, 타임아웃·복구. CANCELED 상태 없는 현재 모델에서 사용자 취소 API 미제공. |
| DEC-10 | PAY-08 제외 | 환불 정책·저장·API는 MVP 이후. |
| DEC-11 | MEM-06 | 탈퇴 보존·파기·재가입. WITHDRAWN enum이 아닌 deleted_at 사용. |
| DEC-12 | GDE-09~16/RNK | 재생성 가능 시점·공유/PDF/평가 영향·HTML 일관성. |
| API-DEC-01 | MEM-01~03 | 지원 OAuth 플랫폼, state/PKCE, 쿠키/Body, 토큰 TTL·회전·동시 갱신. 예시 TTL은 확정값 아님. |
| API-DEC-02 | MEM-06/GDE-16 | 탈퇴·삭제와 진행 중 작업의 경합. 즉시 삭제/완료 거절/작업 종료 중 선택 필요. |
| API-DEC-03 | MEM-07~11/NOT/GDE | 취향·부모 관계·동행·언어·알림 Enum 및 선택/인원 상한. |
| API-DEC-04 | MEM-05/12/13 | 직접 프로필 수정 요구 충돌; 정적 정책 배포 위치와 형식. |
| API-DEC-05 | CON-01/03 | 검색 정렬·월 형식·지도 최대 반경/마커/줌·클러스터 DTO. |
| API-DEC-06 | CON-04 | 비활성 관심 장소 표시 정책. |
| API-DEC-08 | GDE-13 | 동기 PDF 한도·타임아웃·비동기 전환 기준. |
| API-DEC-09 | RNK-03/06 | 다른 여행의 기존 최종 평가 덮어쓰기, null 의미, 미매핑 장소, 중복 제출 비교 및 오류/재응답 정책. |
| API-DEC-10 | PAY-06/07 | PG 원문 계약·서명·승인 API·결제 시도/이벤트 멱등 저장. |
| API-DEC-11 | NOT 내부 처리 | 알림 실패 원본 비롤백과 동일 트랜잭션 생성 요구의 양립 방식·재시도 누락/중복 처리. |
| API-DEC-12 | 공통 | 페이지/커서 한도·ID 문자열 직렬화·URL 변경·호환 경로를 프론트와 합의. 이는 DB만으로 확정되는 값이 아님. |
