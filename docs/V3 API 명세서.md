# V3 API 명세서

> 기준일: 2026-09-06 · MySQL 스키마 연동 V3 확정 API 명세
> 기준: [요구사항정의서](./요구사항정의서.md), [테이블정의서](./테이블정의서.md), [ERDCloud SQL](./ERDCloud_schema.sql)
> 구현 이유·요구사항 추적: [API 설계 근거](./API%20설계%20근거.md) · 학습: [REST API 예시 및 Best Practice](./REST%20API%20예시%20및%20Best%20Practice.md)

## 1. 공통 규약

| 항목 | 규약 |
|---|---|
| 기준 | 2026-09-06 · 요구사항정의서/테이블정의서/ERDCloud SQL과 동기화한 V3 구현 계약. 명시적 MVP 제외 항목은 별도 표기. |
| API Prefix | /api/v1. URL 칼럼에서는 prefix 생략. |
| 문서 분리 | 본 명세서는 요청·응답 계약. 요구사항 연결·설계 이유·트레이드오프는 API 설계 근거.md에서 API ID로 조회. |
| 인증 | 별도 공개/PG 표시 외 서버 세션 쿠키 필수. 회원 ID는 유효한 서버 세션에서 결정하며 Body로 받지 않음. 서비스 액세스·리프레시 토큰은 사용하지 않는다. 소유권·탈퇴 여부를 매 요청 검증. |
| JSON | Content-Type: application/json. 성공 {message,data}, 오류 {message,data:null,error:{code,details,trace_id}}. message/code는 안정 키이며 화면 문구는 프론트 매핑. |
| 응답 예외 | 204는 Body 없음. PDF 성공은 application/pdf 바이너리, 실패는 JSON. 예시 객체는 필드 형태 설명이며 실제 계정/상품/전체 일정이 아님. |
| ID / 숫자 | BIGINT 식별자는 JSON/Path에서 양의 10진 문자열로 통일. gb/job은 ≤50자 문자열. page/count/people/score는 JSON 정수. 금액은 최소 화폐단위 정수이며 JS 안전 정수 범위 내 제한 필요. 랭킹 소수는 문자열. |
| 날짜·시각 | 사건 시각 ISO8601 오프셋 포함, 예시 UTC Z. DB DATETIME(6)에 UTC 저장. 여행 날짜 YYYY-MM-DD, 일정 시각 HH:mm:ss. 일간 경계 등 정책은 DEC-03. |
| 좌표 | API latitude/longitude는 WGS84 도 단위 Number. DB location POINT SRID4326과 변환. X/Y 순서를 임의로 위도·경도라고 가정하지 않음. |
| 문자열·URL | 필드별 최대 길이 검증. DB 외부 URL은 ≤2048자, 초과값 자르지 않음. nullable 명시 외 null 불가. 예시 URL은 실제 연동 주소 아님. |
| 커서 목록 | cursor 선택 불투명 문자열, size 기본20·1~100. 최신 created_at DESC,id DESC. 응답 items,next_cursor,has_more. 알림은 기본4·최대20 우선. |
| 번호 페이지 | 콘텐츠 page 기본1,size 기본5·최대20. 랭킹 page 기본1,size 기본20·최대100. items,page,size,total_items,total_pages. 빈 목록은 200,items=[]. |
| 멱등 키 | 필수 대상 GDE-02/GDE-09/PAY-04만. String≤100자. 키를 회원·요청 내용과 비교. 다른 내용이면409. 기존 작업/주문 반환 시 부작용 반복 금지. |
| 공통 오류 | 인증401·소유권403·삭제/미존재404·형식400·상태충돌409·업무조건422·호출제한429·내부500. 실제 한도와 Retry-After는 운영 합의 필요. |
| 제공 범위 | 회원5개 도메인 구조 유지. 제외된 ID는 재사용하지 않음. PG 2개 API와 각 미확정 항목은 구현 전 동결 필요. 실행 서버/실 DB 테스트는 이번 검증 범위 밖. |

### 읽는 방법

각 API의 Request Body와 응답 예시는 이 문서의 V3 구현 계약을 따른다. 엑셀은 다음 일괄 갱신 전까지 이 문서와 다를 수 있다. PG·PDF 한도 등 명시적으로 보류한 항목은 현재 구현 범위에서 제외하며 예시를 최종 정책으로 간주하지 않는다. URL·HTTP 동작·검증 조건은 명세에 남기고, 테이블 선택 이유·FR/BR 연결은 별도 근거 문서로 분리한다.

## 2. 엔드포인트 목록

| API ID | 기능 | Method | URL |
|---|---|---|---|
| API-MEM-01 | 소셜 로그인·가입 | POST | `/auth/oauth/{provider}/login` |
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
| POST | `/auth/oauth/{provider}/login` | 공개(세션 쿠키 불필요); OAuth 검증 필수, 성공 시 세션 쿠키 발급 |

- Path provider: 필수 String, `KAKAO` 또는 `GOOGLE` (V3 지원 공급자 확정)
- 서비스 버전별 범위: V1은 KAKAO만, V2 이상은 KAKAO와 GOOGLE 모두 지원
- 위 V1/V2/V3는 기능 출시 단계이며 공통 URL `/api/v1`의 API 계약 버전과 다름
- 공급자 2개 지원은 한 회원에 두 소셜 계정을 연결하는 기능을 의미하지 않음. 기존 회원 판별은 `(oauth_provider, oauth_subject)` 기준
- Body authorization_code: 필수 String, 일회용 인증 코드
- redirect_uri: 필수 String, 등록된 콜백과 일치
- 기기 ID·서비스 약관 동의 값은 받지 않음
- 성공 응답에 `Set-Cookie`로 불투명한 세션 ID를 발급하고 서비스 액세스·리프레시 토큰은 응답하지 않음
- onboarding_required: Boolean
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
    "member": {"member_id":"1","nickname":"여행자","profile_image_url":null,"language_code":"ko","status":"ONBOARDING"},
    "onboarding_required": true
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_SESSION_EXPIRED | 세션 만료·폐기 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 502 | UPSTREAM_SERVICE_ERROR | 동기 외부 연동 실패 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** 공급자와 서버 세션 방식은 확정됐다. 현재 Body의 authorization_code/redirect_uri만으로 안전한 로그인 전체 흐름이 정의된 것은 아니며, 시작·콜백 주체와 세션 쿠키 수명·SameSite 정책 확정 전 구현 완료로 보지 않는다.

#### OAuth 검증 결정표 — API-DEC-01

| 결정 항목 | 무엇을 정해야 하는가 | 권장 방향 / API 영향 |
|---|---|---|
| 실행 환경·연동 방식 | 웹 리다이렉트, 팝업 SDK, 네이티브 중 실제 플랫폼과 공급자별 SDK | KAKAO·GOOGLE 지원 여부가 아닌 연동 방식의 결정. code 흐름과 ID token credential 흐름을 섞지 않음 |
| 로그인 시작·콜백 주체 | 프론트와 백엔드 중 누가 인가 요청을 만들고 콜백을 받는가 | 백엔드 관리 인가 요청을 우선 검토. 프론트 콜백을 유지하면 code와 로그인 시도 식별을 백엔드까지 안전하게 전달 |
| state 생성·결속 | 난수 생성 주체, 요청 브라우저/세션과의 연결, provider/client_id/redirect_uri 매핑 | 백엔드가 로그인 시작 전에 발급·보관하는 방안을 권장. 프론트가 state_valid=true라고 보내는 값은 신뢰하지 않음 |
| state 저장·TTL·소비 | 임시 세션/저장소, 실제 만료 시간, 원자적인 일회 소비, 다중 탭·다중 서버 | 로그인 시도별 분리. 누락·불일치·만료·재사용은 거절. 서명된 state도 만료/재사용 대응이 별도로 필요 |
| PKCE 적용 방식 | 각 플랫폼/공급자 흐름의 S256 지원과 라이브러리 옵션 | 지원되는 인가 코드 흐름에서 S256 적용을 우선. 미지원이면 조용히 plain/PKCE 미사용으로 낮추지 말고 대안 흐름의 보안을 검토 |
| verifier 보관·전달 | 누가 code_verifier를 만들고 토큰 교환 시 공급자에 전달하는가 | challenge는 인가 요청, verifier는 토큰 교환에 사용. verifier를 인가 URL·로그에 노출하지 않음. 백엔드 보관이면 프론트 Body에 추가할 필요 없음 |
| 다중 공급자·OIDC | 공급자 혼동 방지, client/redirect 고정, ID token 사용 여부 | 로그인 시도에 결속된 provider로 토큰 교환. OIDC 사용 시 서명·iss·aud·exp 및 요청한 nonce 검증 |
| 성공·취소·오류 복귀 | 실패 코드·HTTP 매핑, 재시작 화면, 허용된 복귀 경로 | 검증 실패 시 회원·서비스 세션 생성 금지. 로그인 거절과 공급자 장애를 구분. 임의 외부 return URL 금지 |
| 서비스 세션 | 쿠키 이름·수명, Secure·HttpOnly·SameSite, CSRF/CORS, 동시 로그인 허용 수 | OAuth 인가 요청용 임시 상태와 로그인 후 `auth_sessions`를 분리 |

현재 API-MEM-01은 코드 교환 결과의 JSON 계약 초안이다. 프론트 콜백 방식이면 state와 임시 로그인 시도 결속을 전달할 계약이 추가로 필요할 수 있고, 백엔드 콜백 방식이면 시작/콜백 URL과 세션 전달 방식이 필요하다. 어느 쪽이든 **요청 전에 만든 값과 비교**해야 하며, 콜백에서 처음 받은 state를 그대로 저장한 뒤 비교하면 검증이 아니다. state/code_verifier 필드를 무조건 현재 Body에 추가하는 것으로 문제를 해결하지 않는다.

state는 로그인 요청과 응답의 연결 및 CSRF 방어에, PKCE는 인가 코드와 최초 요청자의 verifier를 결속하는 데 사용한다. 본 설계안은 state 결속과 지원 흐름의 PKCE S256을 함께 검토한다. 둘의 역할을 같다고 보거나 PKCE가 모든 애플리케이션의 CSRF 방어를 대체한다고 보지 않는다. [OAuth 보안 BCP](https://www.rfc-editor.org/rfc/rfc9700.html), [PKCE 규격](https://www.rfc-editor.org/rfc/rfc7636.html)

공급자 확인 자료: [카카오 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api), [구글 웹 서버 OAuth](https://developers.google.com/identity/protocols/oauth2/web-server). 카카오는 OIDC 메타데이터에 S256을 명시하지만, 실제 사용할 SDK/REST 흐름의 파라미터 처리까지 PoC로 확인한다. 공급자 지원 사실과 우리 앱의 검증 구현 완료는 별개다.

확정 시 정상 로그인 외에 state 누락/변조/만료/재사용, provider 교체, 잘못된 verifier, redirect 불일치, 사용자 취소, 다중 탭·서버 환경을 테스트한다. 오류 코드 세분화와 추가 요청 필드는 그 흐름에 맞춰 동결한다.

### API-MEM-03 로그아웃

| Method | URL | 인증 |
|---|---|---|
| POST | `/auth/logout` | 세션 쿠키 필수 |

- 현재 요청의 세션을 폐기하고 세션 쿠키를 만료시킴
- 기기별 관리·all_devices 옵션 미제공

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-04 내 회원 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-06 회원 탈퇴

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/members/me` | 세션 쿠키 필수 |

- Body 없음. 확인 모달은 프론트 처리
- 완료 후 현재 세션 포함 모든 세션 사용 불가
- 회원은 소프트 삭제하고 서비스 데이터는 30일 보관 후 삭제·비식별화. 결제·원장은 법정 보존 정책 따름
- 동일 소셜 계정의 재가입은 기존 회원 복구가 아닌 새 회원 생성으로 처리
- 진행 중인 AI 생성 작업은 취소 요청하고 늦은 완료 결과를 반영하지 않음

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-11/API-DEC-02 확정: 세션 즉시 폐기, 30일 보관, 재가입은 새 회원, 진행 중 AI 작업은 취소 요청. 결제 중 탈퇴 경합은 PG 계약 확정 후 별도 확정 필요.

### API-MEM-07 취향 옵션 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/preference-options` | 세션 쿠키 필수 |

- Query language_code: 선택 String ≤10자
- 응답 preference_type: THEME|DETAIL|TRAVEL_STYLE
- code ≤50자; label String; parent_code String|null; sort_order Integer
- 허용 코드·부모 관계·표시명·선택 상한은 현재 승인된 화면정의서·기능설계도에 정의된 범위만 사용

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-03 확정: 현재 승인된 화면정의서·기능설계도에 존재하는 허용 코드·부모 관계·표시명·언어만 사용.

### API-MEM-08 기본 취향 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me/preferences` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-09 기본 취향 전체 저장

| Method | URL | 인증 |
|---|---|---|
| PUT | `/members/me/preferences` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |

**구현 전 확인:** API-DEC-03 확정: 중분류·스타일 최소/최대 선택 수는 현재 승인된 화면정의서·기능설계도의 범위만 사용.

### API-MEM-10 설정 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me/settings` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-MEM-11 설정 부분 수정

| Method | URL | 인증 |
|---|---|---|
| PATCH | `/members/me/settings` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
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

**구현 전 확인:** API-DEC-04 확정: 정책 문서는 서버 API에서 Markdown으로 제공한다. 별도 동의 저장 API와 프로필 직접 수정 API는 범위 제외.

### API-MEM-13 정책 안내 상세

| Method | URL | 인증 |
|---|---|---|
| GET | `/policies/{policy_type}` | 공개 |

- Path policy_type: 필수 String, 목록에 있는 코드
- 응답 content: String, format: MARKDOWN 고정

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

**구현 전 확인:** API-DEC-04 확정: 정책 본문은 서버 API에서 `MARKDOWN` 형식으로 제공한다.

### API-NOT-01 미읽은 알림 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/notifications` | 세션 쿠키 필수 |

- Query cursor: 선택 불투명 문자열; size: Integer 1~20, 기본 4
- 최신 created_at DESC,id DESC. 읽은 알림은 없음
- 회원별 미읽음 알림은 20개만 보관. 새 알림 저장 시 초과분은 가장 오래된 행부터 삭제
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-08 확정: 초기 4개, 미읽음 최대 20개, 초과 시 최고령순 삭제. API-DEC-03의 알림 Enum·이동 대상은 승인된 화면정의서·기능설계도에 존재하는 값만 사용.

### API-NOT-02 알림 개별 삭제(읽기)

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/notifications/{notification_id}` | 세션 쿠키 필수 |

- Path notification_id: 필수 ID 문자열
- Body 없음; 자기 알림만 삭제, 이미 없으면 204

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-NOT-03 알림 전체 삭제

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/notifications` | 세션 쿠키 필수 |

- Body 없음
- 요청 처리 시작 시 존재하는 자기 알림만 삭제; 이후 생성된 알림은 유지

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

## 4. 관광콘텐츠

### API-CON-01 관광 콘텐츠 검색

| Method | URL | 인증 |
|---|---|---|
| GET | `/contents` | 세션 쿠키 필수 |

- Query q: 선택 String, 최대 10자; 빈 값=전체
- region_code: 선택 String ≤20자, 광역 행정코드; 생략=전체
- category: 선택 EVENT|CULTURAL_HERITAGE|ATTRACTION
- month: 선택 YYYY-MM. `EVENT`는 선택 월과 행사 기간이 겹치면 포함하고 `ATTRACTION`·`CULTURAL_HERITAGE`는 기간이 없으므로 항상 포함
- page Integer ≥1 기본1; size 1~20 기본5
- 검색어 유무와 관계없이 `tourism_contents.created_at DESC, id DESC` 고정 정렬
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-02/API-DEC-05 확정: `month=YYYY-MM`, 행사에만 기간 중첩 조건을 적용하고 상시 장소는 포함. 검색은 `title` 부분 일치이며 `%`, `_`, 이스케이프 문자는 일반 문자로 처리한다. 검색어 유무와 관계없이 DB 등록 최신순이다.

### API-CON-02 관광 콘텐츠 상세

| Method | URL | 인증 |
|---|---|---|
| GET | `/contents/{content_id}` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-CON-03 지도 콘텐츠 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/map/contents` | 세션 쿠키 필수 |

- Query (latitude,longitude,radius_m) 또는 (south,west,north,east) 중 정확히 하나의 조합
- 좌표 Number: 위도 -90~90, 경도 (-180,180]. radius_m 1~10,000; 최초 진입 기본 반경 3,000m
- bounds 조합은 south<north, west<east이고 대각선 거리가 20km 이하여야 함
- zoom: Integer 6~21, 최초 진입 16~17; limit: Integer 1~200, 기본 100; category 선택
- 응답 markers[]/clusters[]/has_more; 좌표는 도, 거리 m
- 클러스터 표시는 실제 개수 1~9, 10 이상은 `9+`; 클러스터링 시작 기본 줌은 13~14
- 서버가 요청 범위와 zoom을 기준으로 클러스터링한다. `clusters[]`는 cluster_id, latitude, longitude, count, display_count를 반환하며 markers와 clusters 합계가 limit을 초과하면 has_more=true

**Request Body**

없음.

**응답 200**

```json
{
  "message": "map_content_success",
  "data": {
    "markers": [{"content_id":"101","title":"불국사","category":"CULTURAL_HERITAGE","region":{"administrative_code":"47","name":"경상북도"},"latitude":35.7898,"longitude":129.3321,"thumbnail_url":null}],
    "clusters": [{"cluster_id":"cl_example","latitude":35.7890,"longitude":129.3300,"count":12,"display_count":"9+"}],
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-05 확정: Figma MAP-01 기준 기본 반경 3km, 줌 6~21(최초 16~17), 클러스터 숫자 10 이상 `9+`. 백엔드 상한은 반경 10km, 마커·클러스터 합계 200개이며 서버가 클러스터를 집계한다.

### API-CON-04 관심 장소 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/members/me/favorites` | 세션 쿠키 필수 |

- Query cursor, size: 공통 커서 규칙
- 최신 등록 순; 응답 content와 favorited_at
- 비활성·삭제된 콘텐츠는 관심 장소 목록에서 숨김

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-06 확정: 비활성·삭제 관심 장소는 목록에서 숨김.

### API-CON-05 관심 장소 등록

| Method | URL | 인증 |
|---|---|---|
| PUT | `/members/me/favorites/{content_id}` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-CON-06 관심 장소 해제

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/members/me/favorites/{content_id}` | 세션 쿠키 필수 |

- Path content_id: 필수 ID 문자열
- Body 없음. 이미 해제된 경우도 204

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

## 5. 가이드북

### API-GDE-01 내 가이드북 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks` | 세션 쿠키 필수 |

- Query cursor, size: 공통 커서 규칙
- sort=created_at,desc 고정; id DESC 보조 정렬
- 소유자 일치 및 deleted_at IS NULL
- preference_tags는 각 가이드북 생성 시 AI 결과에서 확정·저장된 표시 태그

**Request Body**

없음.

**응답 200**

```json
{
  "message": "guidebook_list_success",
  "data": {
    "items": [{"guidebook_id":"gb_example","title":"경주 역사 여행","region":{"administrative_code":"47","name":"경상북도"},"start_date":"2026-10-12","end_date":"2026-10-14","companion":"FRIEND","people_count":2,"preference_tags":["HEALING","NATURE"],"version":1,"updated_at":"2026-09-04T00:00:00Z"}],
    "next_cursor": null,
    "has_more": false
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-02 최초 가이드북 생성 접수

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebook-generations` | 세션 쿠키 필수 |

- Idempotency-Key: 필수 String ≤100자
- region_code: 필수 String ≤20자, 17개 광역 지역 중 하나
- start_date/end_date: 필수 YYYY-MM-DD, 양끝 포함 1~7일
- companion: 필수 Enum 문자열 ≤20자. 현재 승인된 화면정의서·기능설계도의 허용 코드만 사용
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 키에 다른 요청 또는 다른 회원 |
| 409 | GENERATION_IN_PROGRESS | 이미 진행 중인 생성 작업 존재 |
| 422 | CREDIT_INSUFFICIENT | 생성권 잔액 1개 미만 |
| 422 | GUIDEBOOK_INVALID_PERIOD | 종료일 역전 또는 양끝 포함 7일 초과 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-09 확정: 개별 시도 300초, 재시도 3회(`attempt_count=0~3`, 최초 포함 최대 4회). API-DEC-03의 동행 Enum·인원 상한은 승인된 화면정의서·기능설계도 범위만 구현.

### API-GDE-03 생성 상태 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-generations/{job_id}` | 세션 쿠키 필수 |

- Path job_id: 필수 String ≤50자
- status: PENDING|PROCESSING|COMPLETED|FAILED|CANCELED
- attempt_count: Integer 0~3, 재시도 횟수. 최초 시도를 포함하면 최대 4회; 최초 생성 완료 전 guidebook_id=null
- 개별 시도는 300초 타임아웃. 탈퇴·대상 가이드북 삭제로 취소된 작업의 늦은 완료 결과는 무시
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-05 가이드북 상세

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks/{guidebook_id}` | 세션 쿠키 필수 |

- Path guidebook_id: 필수 String ≤50자
- 응답 companion String; people_count Integer; version Integer ≥1
- content_html: String|null; region은 광역 정보
- 삭제본은 404; preference_tags는 현재 회원 취향이 아니라 가이드북 생성 시 AI 결과에서 확정·저장된 표시 태그

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
    "preference_tags": ["HEALING", "NATURE"],
    "version": 1,
    "updated_at": "2026-09-04T00:00:00Z",
    "content_html": "<article>여행 안내</article>"
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-01 확정: 생성 시 AI 결과의 취향 태그를 가이드북에 저장하고 카드·상세·공유 미리보기에 표시. 현재 회원 취향 변경은 기존 가이드북 표시값을 바꾸지 않음.

### API-GDE-07 일정 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks/{guidebook_id}/itinerary` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-09 가이드북 재생성 접수

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebooks/{guidebook_id}/regenerations` | 세션 쿠키 필수 |

- Idempotency-Key: 필수 String ≤100자
- title: 필수 String, 앞뒤 공백 제거 후 빈 값 불가, ≤15자. 재생성 결과에 적용할 가이드북 제목
- feedback: 필수 String, 앞뒤 공백 제거 후 빈 값 불가, ≤200자. 사용자가 가이드북 상세 화면에서 입력한 자연어 수정 요청
- 제목만 또는 일정을 직접 수정하는 별도 API는 제공하지 않으며, 제목 변경은 필수 feedback과 함께 이 재생성으로만 반영
- 재생성 진입 버튼은 해당 가이드북 페이지에서만 노출하고 다른 화면에서는 재생성 진입을 제공하지 않음
- 소유자의 삭제되지 않은 가이드북만 허용. 기존 제출 평가는 유지하고 성공한 최신 버전은 상세·공유·HTML/PDF에 사용
- 현재 기본 취향 사용; feedback은 해당 가이드북만 반영하고 회원 기본 취향을 변경하지 않음
- 새 작업; 성공 시 같은 guidebook_id의 제목·본문·일정을 함께 갱신하고 version 증가
- ACTIVE 회원·유효 기본 취향·잔액≥1·진행 작업 없음 필수
- 동일 키 재요청은 기존 작업의 현재 상태 반환; 새 AI 작업 생성 안 함

**Request Body**

```json
{
  "title": "경주 여유 여행",
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 키에 다른 요청 또는 다른 회원 |
| 409 | GENERATION_IN_PROGRESS | 이미 진행 중인 생성 작업 존재 |
| 422 | CREDIT_INSUFFICIENT | 생성권 잔액 1개 미만 |
| 422 | GUIDEBOOK_INVALID_PERIOD | 종료일 역전 또는 양끝 포함 7일 초과 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |

**구현 전 확인:** DEC-12 확정: 소유자의 삭제되지 않은 가이드북 상세 페이지에서만 재생성 가능. 성공한 최신 버전은 상세·공유·HTML/PDF에 반영하고 기존 제출 평가는 유지한다. feedback 상한은 200자다.

### API-GDE-10 공유 링크 발급

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebooks/{guidebook_id}/shares` | 세션 쿠키 필수 |

- 요청 본문 없음; 만료 시각은 발급 시점부터 24시간 후로 서버가 계산
- share_url: String; expires_at: String
- Idempotency-Key 보장 없음; 재요청 시 새 링크 가능

**Request Body**

없음.

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |

**구현 전 확인:** DEC-06/12 확정: 발급 후 24시간 만료하며, 유효 링크는 조회 시점의 동일 가이드북 최신 버전을 표시한다.

### API-GDE-11 공유 미리보기

| Method | URL | 인증 |
|---|---|---|
| GET | `/shares/{share_token}` | 세션 쿠키 필수 |

- Path share_token: 필수 난수 문자열
- 미존재·만료·원본 삭제는 모두 404
- 응답은 로그인한 수신자용 최소 미리보기
- 공유자 닉네임, 가이드북 제목, 시작일·종료일, 장소 수, 생성 시 취향 태그만 포함
- 상세 일정·HTML 본문·동행·인원·생성 입력·피드백은 제외

**Request Body**

없음.

**응답 200**

```json
{
  "message": "share_preview_success",
  "data": {
    "sharer_nickname": "동원",
    "title": "강릉 3박 4일",
    "start_date": "2026-10-12",
    "end_date": "2026-10-15",
    "place_count": 12,
    "preference_tags": ["HEALING", "NATURE"]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 404 | SHARE_LINK_UNAVAILABLE | 공유 토큰 미존재·만료·대상 삭제 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-06 확정: 로그인 필수. 공유자 닉네임·제목·여행 기간·장소 수·생성 시 취향 태그만 공개하고 상세 일정·HTML·개인화 입력은 제외.

### API-GDE-12 공유 가이드북 가져오기

| Method | URL | 인증 |
|---|---|---|
| POST | `/shares/{share_token}/imports` | 세션 쿠키 필수 |

- Path share_token: 필수 String
- Body 없음. 최초 원본 기준 같은 회원의 중복 복사 금지
- 첫 복사 201; 기존 복사본이면 200 및 기존 guidebook_id
- 기존 복사본이 소프트 삭제되었다면 새 행을 만들지 않고 `deleted_at`을 해제해 복구한 후 200 반환

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 404 | SHARE_LINK_UNAVAILABLE | 공유 토큰 미존재·만료·대상 삭제 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-07 확정: 삭제한 복사본은 기존 행을 복구. 현재 `guidebook_imports(imported_by_member_id, root_guidebook_id)` UNIQUE와 충돌하지 않으며 Idempotency-Key 없이 자연 유일성으로 중복 복사를 방지.

### API-GDE-13 PDF 다운로드

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebooks/{guidebook_id}/exports` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 503 | SERVICE_UNAVAILABLE | 일시적 서비스 이용 불가 |

**구현 전 확인:** API-DEC-08 부분 확정: MVP는 동기 200 PDF 응답. 응답 시간·최대 크기·동시 처리 한도와 비동기 전환 기준은 추후 확정.

### API-GDE-15 HTML 뷰어 데이터

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebooks/{guidebook_id}/viewer` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-GDE-16 가이드북 삭제

| Method | URL | 인증 |
|---|---|---|
| DELETE | `/guidebooks/{guidebook_id}` | 세션 쿠키 필수 |

- Path guidebook_id: 필수 String ≤50자
- Body 없음; 소유자만 삭제
- 목록·상세·공유 즉시 차단; 다른 회원의 복사본 유지

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-02: 진행 중 재생성·PDF와 삭제 경합 정책.

## 6. 평가랭킹

### API-RNK-01 평가 대상·진행 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-evaluations` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-RNK-02 평가 시작·재개

| Method | URL | 인증 |
|---|---|---|
| PUT | `/guidebooks/{guidebook_id}/evaluation` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 422 | EVALUATION_NOT_ELIGIBLE | 여행 종료 다음 날 이전 또는 평가 대상 불일치 |

### API-RNK-03 평가 대상 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-evaluations/{evaluation_id}` | 세션 쿠키 필수 |

- Path evaluation_id: 필수 ID 문자열
- places[].content_id: ID; current_score: Integer 0~5|null
- current_score는 회원·장소의 기존 최종값이지 작성 중 초안이 아님
- 다른 여행에서 같은 장소를 제출하면 기존 점수를 덮어쓰고, 최종 `score:null`이면 기존 평가 행을 삭제
- 유효 content_id가 있는 장소만 최초 등장 순으로 중복 제거해 평가 대상으로 제공
- content_id=null인 미매핑 일정 항목은 평가 대상에서 제외

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** API-DEC-09 확정: 기존 최종 점수는 덮어쓰고 `null`은 기존 행 삭제. 대상은 유효 content_id 기준 최초 등장 순으로 중복 제거하며 미매핑 항목은 제외한다.

### API-RNK-05 평가 다음에 하기

| Method | URL | 인증 |
|---|---|---|
| PATCH | `/guidebook-evaluations/{evaluation_id}` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |

### API-RNK-06 평가 최종 제출

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebook-evaluations/{evaluation_id}/submit` | 세션 쿠키 필수 |

- ratings: 필수 Array; content_id 필수 ID 문자열·중복 불가
- score: 필수 Integer 0~5 또는 null(건너뛰기)
- Body에 최종 점수 전체 전달; 초안 저장 API 없음
- 평가 대상 전체를 ratings에 포함해야 하며 대상 누락은 허용하지 않음. 건너뛴 장소도 `score:null`로 전달
- 마지막 장소 화면의 `완료하기`를 누를 때 최종 제출하고, 제출 후 수정은 허용하지 않음
- 기존 회원·장소 평가가 있으면 0~5는 덮어쓰고 `null`은 기존 행을 삭제. 기존 행이 없는 `null`은 새 행을 만들지 않음
- 제출 시 place_ratings 반영 및 SUBMITTED를 원자적으로 처리
- 이미 `SUBMITTED`인 평가에 대한 재요청은 내용과 관계없이 409
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 422 | EVALUATION_NOT_ELIGIBLE | 여행 종료 다음 날 이전 또는 평가 대상 불일치 |
| 409 | EVALUATION_ALREADY_SUBMITTED | 이미 제출 완료된 평가에 대한 재요청 |

**구현 전 확인:** DEC-05/API-DEC-09 확정: 부분 제출·최종 제출 후 수정 불가. 완료 전 입력은 프론트 초안이며 `완료하기` 시 전체 대상을 제출한다. 제출 완료 후 재요청은 내용 비교 없이 409다.

### API-RNK-07 랭킹 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/rankings` | 세션 쿠키 필수 |

- Query period_type: 필수 DAILY|WEEKLY|MONTHLY
- period_start: 필수 YYYY-MM-DD; region_code 선택, 생략=전국
- page Integer≥1 기본1; size Integer1~100 기본20
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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-03/04: 기간 경계·점수 귀속 시각·공식·동점 규칙·갱신 지연 목표.

## 7. 생성권결제

### API-PAY-01 생성권 지갑 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/credits/wallet` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-02 생성권 원장 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/credits/transactions` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-03 생성권 상품 목록

| Method | URL | 인증 |
|---|---|---|
| GET | `/credit-products` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-04 주문 생성

| Method | URL | 인증 |
|---|---|---|
| POST | `/orders` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 키에 다른 요청 또는 다른 회원 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-05 주문 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/orders/{merchant_order_id}` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

### API-PAY-06 결제 시도 생성 [PG 미확정]

| Method | URL | 인증 |
|---|---|---|
| POST | `/orders/{merchant_order_id}/payment-attempts` | 세션 쿠키 필수 |

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
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 409 | RESOURCE_STATE_CONFLICT | 동시에 수정됐거나 현재 상태에서 처리 불가 |
| 502 | UPSTREAM_SERVICE_ERROR | 동기 외부 연동 실패 |

**구현 전 확인:** API-DEC-10: PG 선정 후 Body/checkout·승인 확인 API·키/실패 복구 확정. 현재 구현 보류.

### API-PAY-07 PG 웹훅 [PG 미확정]

| Method | URL | 인증 |
|---|---|---|
| POST | `/payments/webhooks/{provider}` | PG 서명 검증; 세션 쿠키 사용 안 함 |

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
| AUTH_SESSION_REQUIRED | 401 | authentication_required | 세션 쿠키 누락·유효하지 않음 |
| AUTH_SESSION_EXPIRED | 401 | session_expired | 세션 만료·폐기 |
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
| API-MEM-02 | POST /auth/token/refresh | 서버 세션 방식에서는 별도 토큰 갱신이 필요하지 않아 제외. ID는 재사용하지 않음. |
| API-MEM-05 | PATCH /members/me | 프로필 직접 수정은 제공하지 않는 것으로 확정. OAuth 프로필은 로그인 시 동기화하고 언어는 MEM-11에서 변경. |
| API-MEM-14 | POST /members/me/consents | 별도 서비스 약관 동의 저장은 현재 범위 제외. 안내는 정적 콘텐츠. |
| API-NOT-04 | GET /push-preferences | 푸시 설정 조회는 MEM-10 회원 설정 조회로 통합. |
| API-NOT-05 | PATCH /push-preferences | 푸시 설정 변경은 MEM-11 회원 설정 부분 수정으로 통합. |
| API-GDE-04 | POST /guidebook-generations/{job_id}/retry | 기술 재시도는 서버 내부 동일 작업 처리. 사용자 재생성은 GDE-09. |
| API-GDE-06 | PATCH /guidebooks/{guidebook_id} | 제목만 직접 수정하는 기능은 제외. 제목 변경은 필수 feedback과 함께 GDE-09 재생성으로만 처리. |
| API-GDE-08 | PUT /guidebooks/{guidebook_id}/itinerary | 사용자의 일정 직접 편집 기능이 없어 제외. 변경은 GDE-09 자연어 피드백 재생성으로만 처리. |
| API-GDE-14 | GET /guidebook-exports/{export_id} | MVP는 동기 PDF이므로 작업 조회 API 미제공. |
| API-RNK-04 | PUT /guidebook-evaluations/{evaluation_id}/places/{content_id} | 서버 초안 저장 API 미제공. 최종 ratings를 RNK-06 Body로 제출. |
| API-PAY-08 | POST /orders/{merchant_order_id}/refund | 환불은 MVP 이후. 현재 구현 대상 아님. |

## 10. 결정 현황 및 명시적 보류 항목

| 결정 ID | 영향 | 확인할 내용 |
|---|---|---|
| DEC-01 | GDE-01/05/11 | 확정: 생성 시 AI 결과의 취향 태그를 가이드북에 저장하고 카드·상세·공유 미리보기에 표시. 현재 회원 취향 변경은 기존 표시값에 영향 없음. |
| DEC-02 | CON-01 | 확정: `month=YYYY-MM`. `EVENT`는 선택 월과 기간이 겹치면 포함, `ATTRACTION`·`CULTURAL_HERITAGE`는 상시 포함. |
| DEC-03/04 | RNK-07 | 부분 확정: `Asia/Seoul` 기준, 일간 00시·주간 월요일 00시·월간 1일 00시 시작, 최초 제출 시각 귀속, 베이지안 가중 평균, 동점은 평가 수 내림차순 후 `content_id` 오름차순, 10분 배치·최대 지연 10분. `C` 범위와 `m` 값은 미확정. `updated_at`은 변경 탐지에만 사용. |
| DEC-05 | RNK-03/06 | 확정: 정수 0~5, `null` 건너뛰기, 완료 전 프론트 초안. 마지막 장소에서 `완료하기` 시 전체 대상을 최종 제출하며 부분 제출·제출 후 수정은 불가. |
| DEC-06 | GDE-10/11 | 확정: 발급 후 24시간 만료, 미리보기도 로그인 필수. 공유자 닉네임·제목·여행 기간·장소 수·생성 시 취향 태그만 공개하고 상세 일정·HTML·개인화 입력은 제외. |
| DEC-07 | GDE-12 | 확정: 기존 가져온 복사본이 소프트 삭제된 경우 새 복사본을 만들지 않고 `deleted_at`을 해제해 복구. 현재 유일 제약으로 처리 가능. |
| DEC-08 | NOT-01 | 확정: 초기 4개 노출, 회원별 미읽음 최대 20개 보관. 새 알림이 추가될 때 20개를 초과하면 가장 오래된 행부터 삭제. |
| DEC-09 | GDE-02/03/09 | 확정: 개별 시도 300초 타임아웃, 재시도 최대 3회. `attempt_count=0~3`은 재시도 횟수이므로 최초 포함 최대 4회 실행. 탈퇴·대상 삭제 시 작업 취소 요청 및 늦은 결과 무시. |
| DEC-10 | PAY-08 제외 | 환불 정책·저장·API는 MVP 이후. |
| DEC-11 | MEM-06 | 확정: 탈퇴 시 세션 즉시 폐기·`deleted_at` 소프트 삭제, 서비스 데이터 30일 보관 후 삭제·비식별화. 재가입은 기존 회원을 복구하지 않고 새 회원 생성. 결제·원장은 법정 보존 예외. |
| DEC-12 | GDE-09~16/RNK | 확정: 소유자의 삭제되지 않은 가이드북 상세 페이지에서만 재생성 가능. 성공한 최신 버전은 상세·공유·HTML/PDF에 반영하고 기존 제출 평가는 유지. |
| API-DEC-01 | MEM-01/03 | 서버 세션 방식과 지원 공급자는 확정. 서비스 액세스·리프레시 토큰과 갱신 API는 사용하지 않음. 남은 결정은 실행 플랫폼/SDK, 시작·콜백 주체, state 결속·TTL·일회성, PKCE/OIDC 검증, 세션 쿠키 수명·SameSite·동시 로그인 수. |
| API-DEC-02 | MEM-06/GDE-16 | 부분 확정: 탈퇴·가이드북 삭제 시 진행 중 AI 작업에 취소 명령을 전달하고 늦은 완료 결과를 무시. 결제 중 탈퇴는 PG 계약 후 확정. |
| API-DEC-03 | MEM-07~11/NOT/GDE | 확정: 취향·부모 관계·동행·언어·알림 Enum과 선택/인원 상한은 현재 승인된 화면정의서·기능설계도에 정의된 범위만 구현. |
| API-DEC-04 | MEM-05/12/13 | 확정: 프로필 직접 수정은 제외하고 OAuth 프로필은 로그인 시 동기화. 정책 문서는 서버가 `MARKDOWN`으로 제공. |
| API-DEC-05 | CON-01/03 | 확정: 검색은 title 부분 일치 후 DB 등록 최신순. 지도 기본 반경 3km, 최대 10km, 줌 6~21, 응답 기본 100·최대 200, 서버 클러스터링, 10개 이상 `9+`. |
| API-DEC-06 | CON-04 | 확정: 비활성·삭제된 관심 장소는 목록에서 숨김. |
| API-DEC-08 | GDE-13 | 부분 확정: MVP는 기존 설계대로 동기 200 PDF 응답. 응답 시간·최대 크기·동시 처리 한도와 비동기 전환 기준은 추후 확정. |
| API-DEC-09 | RNK-03/06 | 확정: 기존 최종 평가는 덮어쓰고 null은 삭제. 유효 content_id를 최초 등장 순으로 중복 제거하고 미매핑 항목은 제외. 제출 완료 후 모든 재요청은 409. |
| API-DEC-10 | PAY-06/07 | PG 원문 계약·서명·승인 API·결제 시도/이벤트 멱등 저장. |
| API-DEC-11 | NOT 내부 처리 | 확정: 별도 아웃박스 테이블 없음. 원본 업무 커밋 후 알림을 별도 트랜잭션으로 생성하고 제한적 재시도 후 최종 실패를 로그·모니터링에 기록. 장애 후 누락 복구는 보장하지 않음. |
| API-DEC-12 | 공통 | 확정: 공통 계약은 백엔드 API 명세를 기준으로 하고 프론트가 따른다. 현재 명세의 페이지/커서 한도와 ID 문자열 직렬화를 적용하며, URL 변경·호환 정책도 백엔드 명세에서 버전별로 정의한다. |
