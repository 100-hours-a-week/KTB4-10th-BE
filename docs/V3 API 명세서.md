# V3 API 명세서

> 기준일: 2026-09-06 · MySQL 스키마 연동 V3 확정 API 명세
> 기준: [요구사항정의서](./요구사항정의서.md), [테이블정의서](./테이블정의서.md), [ERDCloud SQL](./ERDCloud_schema.sql)
> 구현 이유·요구사항 추적: [API 설계 근거](./API%20설계%20근거.md) · 학습: [REST API 예시 및 Best Practice](./REST%20API%20예시%20및%20Best%20Practice.md)

## 1. 공통 규약

| 항목 | 규약 |
|---|---|
| 기준 | 2026-09-06 · 요구사항정의서/테이블정의서/ERDCloud SQL과 동기화한 V3 구현 계약. 명시적 MVP 제외 항목은 별도 표기. |
| API Prefix | 업무 API는 /api/v1. URL 칼럼에서는 prefix 생략. Actuator 운영 엔드포인트는 prefix 적용 제외. |
| 문서 분리 | 본 명세서는 요청·응답 계약. 요구사항 연결·설계 이유·트레이드오프는 API 설계 근거.md에서 API ID로 조회. |
| 인증 | 별도 공개/PG 표시 외 서버 세션 쿠키 필수. 회원 ID는 유효한 서버 세션에서 결정하며 Body로 받지 않음. 서비스 액세스·리프레시 토큰은 사용하지 않는다. 소유권·탈퇴 여부를 매 요청 검증. POST/PUT/PATCH/DELETE는 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더가 모두 필요하다. |
| JSON | Content-Type: application/json. 성공 {message,data}, 오류 {message,data:null,error:{code,details,trace_id}}. message/code는 안정 키이며 화면 문구는 프론트 매핑. |
| 응답 예외 | OAuth 시작·콜백은 302 리다이렉트이며 JSON Body 없음. 204는 Body 없음. PDF 성공은 application/pdf 바이너리, 실패는 JSON. 예시 객체는 필드 형태 설명이며 실제 계정/상품/전체 일정이 아님. |
| ID / 숫자 | BIGINT 식별자는 JSON/Path에서 양의 10진 문자열로 통일. gb/job은 ≤50자 문자열. page/count/people/score는 JSON 정수. 금액은 최소 화폐단위 정수이며 JS 안전 정수 범위 내 제한 필요. 랭킹 소수는 문자열. |
| 날짜·시각 | 사건 시각 ISO8601 오프셋 포함, 예시 UTC Z. DB DATETIME(6)에 UTC 저장. 여행 날짜 YYYY-MM-DD, 일정 시각 HH:mm:ss. 일간 경계 등 정책은 DEC-03. |
| 좌표 | API latitude/longitude는 WGS84 도 단위 Number. DB location POINT SRID4326과 변환. X/Y 순서를 임의로 위도·경도라고 가정하지 않음. |
| 문자열·URL | 필드별 최대 길이 검증. DB 외부 URL은 ≤2048자, 초과값 자르지 않음. nullable 명시 외 null 불가. 예시 URL은 실제 연동 주소 아님. |
| 커서 목록 | cursor 선택 불투명 문자열, size 기본20·1~100. API별 안정 복합 정렬 키 사용. 응답 items,next_cursor,has_more. 알림은 커서가 아니라 번호 페이지 사용. |
| 번호 페이지 | 알림 page 기본1,size 기본4·1~20. 콘텐츠 page 기본1,size 기본5·최대20. 랭킹 page 기본1,size 기본20·최대100. items,page,size,total_items,total_pages. 빈 목록은 200,items=[]. |
| 멱등 키 | 필수 대상 GDE-02/GDE-09/PAY-04만. String≤100자. 키를 회원·요청 내용과 비교. 다른 내용이면409. 기존 작업/주문 반환 시 부작용 반복 금지. |
| 공통 오류 | 인증401·소유권403·삭제/미존재404·형식400·상태충돌409·업무조건422·호출제한429·내부500. 실제 한도와 Retry-After는 운영 합의 필요. |
| 제공 범위 | 회원5개 도메인 구조 유지. 제외된 ID는 재사용하지 않음. PG 2개 API와 각 미확정 항목은 구현 전 동결 필요. 실행 서버/실 DB 테스트는 이번 검증 범위 밖. |

### 1.1 V1 적용 규칙 (2026-09-15)

- 미지원 Body/Query 필드는 400 COMMON_VALIDATION_ERROR. V1 미제공 경로는 등록하지 않는다. 인증 필터를 통과한 미등록 경로는 404다.
- ONBOARDING 접근은 MEM-03/04/06/07/08/09/10/11과 공개 API로 제한, 나머지는 403 RESOURCE_FORBIDDEN. ACTIVE도 소유·보관 관계 검증 필수다.
- 취향은 [V1 코드표](./V1%20취향%20Enum%20코드표.md). TourAPI 기반 THEME 1~3, 선택한 각 THEME의 DETAIL 1~3, 스타일 0~4. THEME:DETAIL은 1:N이며 중복/부모/코드/개수 오류는 422 PREFERENCE_INVALID.
- 가입월 포함 월 3회 누적 지급, 이월 가능, 탈퇴 소멸·재가입 3회. 현재 생성권 조회 예시 숫자는 지급 정책값이 아니다.
- GDE-03에는 progress/retryable을 아직 추가하지 않는다. AI와 단계 계약 확정 후 별도 변경한다.
- 세부 지역 입력·preference_tags는 받지 않는다. AI 결과의 대표 지역명 title 계약과 HTML은 V1-09/15 실연동 관문이다.
- 공유 등 V3 응답에서도 preference_tags 계약은 제거한다. V1 재생성/공유는 제공하지 않는다.
- GDE-05/07/13/15의 활성 보관 관계 부재는 404 RESOURCE_NOT_FOUND. 해당 표의 403은 회원 상태 제한이며 타인 가이드북 존재를 드러내지 않는다.
- 공통 cursor: 가이드북은 여행 시작일 ASC·가이드북 생성일 DESC·보관 관계 id DESC, 관심 장소는 created_at·content_id, 원장은 created_at·id의 DESC 키를 사용한다. size 기본20·1~100, 마지막 반환 행 기반 size+1 조회. 다음이 없으면 next_cursor=null/has_more=false.
- 커서는 base64url payload+HMAC-SHA256 서명, 최대2048자·24시간. 버전/endpoint/세션 회원/정렬/필터/마지막 키/첫 상한 키/발급시각을 결속한다. 잘못됨·타인·만료는 400. DATETIME(6) 정밀도 유지, 키 행 삭제 후에도 값 비교로 진행한다. 데이터 변경 사이 완전한 snapshot은 보장하지 않는다.
- 상세 session·cursor·TX·탈퇴 규약과 아직 미정인 AI 계약은 [개발 전 결정 목록](./개발%20전%20확정%20필수%20내용.md)을 따른다. CSRF 전달 계약은 API-MEM-16을 따른다.

### 읽는 방법

각 API의 Request Body와 응답 예시는 이 문서의 V3 구현 계약을 따른다. 엑셀은 다음 일괄 갱신 전까지 이 문서와 다를 수 있다. PG·PDF 한도 등 명시적으로 보류한 항목은 현재 구현 범위에서 제외하며 예시를 최종 정책으로 간주하지 않는다. URL·HTTP 동작·검증 조건은 명세에 남기고, 테이블 선택 이유·FR/BR 연결은 별도 근거 문서로 분리한다.

## 2. 엔드포인트 목록

| API ID | 기능 | Method | URL | V1 제공 | V1 허용 입력 |
|---|---|---|---|---|---|
| API-MEM-01 | 소셜 로그인 시작 | GET | `/auth/oauth/authorize/{provider}` | 예 | V1 Path provider=`kakao`; Query/Body 없음 |
| API-MEM-15 | 소셜 로그인 콜백·가입 | GET | `/auth/oauth/callback/{provider}` | 예 | V1 Path provider=`kakao`; Query code/state 또는 error/state |
| API-MEM-16 | CSRF 토큰 계약 조회 | GET | `/auth/csrf` | 예 | Query/Body 없음; 공개 |
| API-MEM-03 | 로그아웃 | POST | `/auth/logout` | 예 | Body 없음 |
| API-MEM-04 | 내 회원 조회 | GET | `/members/me` | 예 | Query/Body 없음; 응답 email 포함 |
| API-MEM-06 | 회원 탈퇴 | DELETE | `/members/me` | 예 | Body 없음 |
| API-MEM-07 | 취향 옵션 조회 | GET | `/preference-options` | 예 | Query/Body 없음; ko 고정 |
| API-MEM-08 | 기본 취향 조회 | GET | `/members/me/preferences` | 예 | Query/Body 없음 |
| API-MEM-09 | 기본 취향 전체 저장 | PUT | `/members/me/preferences` | 예 | Body selections[{preference_type,preference_code}] |
| API-MEM-10 | 설정 조회 | GET | `/members/me/settings` | 예 | Query/Body 없음; language_code=ko 응답 |
| API-MEM-11 | 설정 부분 수정 | PATCH | `/members/me/settings` | 예 | Body push_enabled만(Boolean) |
| API-MEM-12 | 정책 안내 목록 | GET | `/policies` | 예 | Query/Body 없음 |
| API-MEM-13 | 정책 안내 상세 | GET | `/policies/{policy_type}` | 예 | Path policy_type; Body 없음 |
| API-NOT-01 | 미읽은 알림 목록 | GET | `/notifications` | 예 | Query page,size; Body 없음 |
| API-NOT-02 | 알림 개별 삭제(읽기) | DELETE | `/notifications/{notification_id}` | 예 | Path notification_id; Body 없음 |
| API-NOT-03 | 알림 전체 삭제 | DELETE | `/notifications` | 예 | Body 없음 |
| API-CON-01 | 관광 콘텐츠 검색 | GET | `/contents` | 예 | Query page,size만; q/region_code/month/category 미지원 |
| API-CON-02 | 관광 콘텐츠 상세 | GET | `/contents/{content_id}` | 예 | Path content_id; Body 없음 |
| API-CON-03 | 지도 콘텐츠 조회 | GET | `/map/contents` | 예 | Query latitude,longitude,radius_m 또는 south,west,north,east; zoom,limit. category 미지원 |
| API-CON-04 | 관심 장소 목록 | GET | `/members/me/favorites` | 예 | Query cursor,size |
| API-CON-05 | 관심 장소 등록 | PUT | `/members/me/favorites/{content_id}` | 예 | Path content_id; Body 없음 |
| API-CON-06 | 관심 장소 해제 | DELETE | `/members/me/favorites/{content_id}` | 예 | Path content_id; Body 없음 |
| API-GDE-01 | 내 가이드북 목록 | GET | `/guidebooks` | 예 | Query cursor,size |
| API-GDE-02 | 최초 가이드북 생성 접수 | POST | `/guidebook-generations` | 예 | Idempotency-Key; Body province,city,start_date,end_date,companion,people_count |
| API-GDE-03 | 생성 상태 조회 | GET | `/guidebook-generations/{job_id}` | 예 | Path job_id; Body 없음 |
| API-GDE-05 | 가이드북 상세 | GET | `/guidebooks/{guidebook_id}` | 예 | Path guidebook_id; Body 없음 |
| API-GDE-07 | 일정 조회 | GET | `/guidebooks/{guidebook_id}/itinerary` | 아니오 | API-GDE-05의 itinerary 재사용; 독립 소비처 확정 후 분리 검토 |
| API-GDE-09 | 가이드북 재생성 접수 | POST | `/guidebooks/{guidebook_id}/regenerations` | 아니오 | V3 |
| API-GDE-10 | 공유 링크 발급 | POST | `/guidebooks/{guidebook_id}/shares` | 아니오 | V3 |
| API-GDE-11 | 공유 미리보기 | GET | `/shares/{share_token}` | 아니오 | V3 |
| API-GDE-12 | 공유 가이드북 가져오기 | POST | `/shares/{share_token}/imports` | 아니오 | V3 |
| API-GDE-13 | PDF 다운로드 | POST | `/guidebooks/{guidebook_id}/exports` | 예 | Path guidebook_id; Body format=PDF |
| API-GDE-15 | HTML 뷰어 데이터 | GET | `/guidebooks/{guidebook_id}/viewer` | 예 | Path guidebook_id; Body 없음 |
| API-GDE-16 | 가이드북 삭제 | DELETE | `/guidebooks/{guidebook_id}` | 예 | Path guidebook_id; Body 없음 |
| API-RNK-01 | 평가 대상·진행 목록 | GET | `/guidebook-evaluations` | 아니오 | V3 |
| API-RNK-02 | 평가 시작·재개 | PUT | `/guidebooks/{guidebook_id}/evaluation` | 아니오 | V3 |
| API-RNK-03 | 평가 대상 조회 | GET | `/guidebook-evaluations/{evaluation_id}` | 아니오 | V3 |
| API-RNK-05 | 평가 다음에 하기 | PATCH | `/guidebook-evaluations/{evaluation_id}` | 아니오 | V3 |
| API-RNK-06 | 평가 최종 제출 | POST | `/guidebook-evaluations/{evaluation_id}/submit` | 아니오 | V3 |
| API-RNK-07 | 랭킹 조회 | GET | `/rankings` | 아니오 | V3 |
| API-PAY-01 | 생성권 지갑 조회 | GET | `/credits/wallet` | 예 | Query/Body 없음 |
| API-PAY-02 | 생성권 원장 조회 | GET | `/credits/transactions` | 예 | Query cursor,size,type(FREE_GRANT/CONSUME/REVOKE/ADJUSTMENT); 구매분 미지원 |
| API-PAY-03 | 생성권 상품 목록 | GET | `/credit-products` | 아니오 | V2 |
| API-PAY-04 | 주문 생성 | POST | `/orders` | 아니오 | V2 |
| API-PAY-05 | 주문 조회 | GET | `/orders/{merchant_order_id}` | 아니오 | V2 |
| API-PAY-06 | 결제 시도 생성 [PG 미확정] | POST | `/orders/{merchant_order_id}/payment-attempts` | 아니오 | V2 |
| API-PAY-07 | PG 웹훅 [PG 미확정] | POST | `/payments/webhooks/{provider}` | 아니오 | V2 |
| API-OPS-01 | 로드밸런서 헬스체크 | GET | `/actuator/health` | 예 | Query/Body 없음; 인프라 내부 |

### 2.1 운영 헬스체크

### API-OPS-01 로드밸런서 헬스체크

| Method | URL | 인증 |
|---|---|---|
| GET | `/actuator/health` | 배포·로드밸런서 내부 헬스체크. 세션 인증 없음 |

- 애플리케이션과 MySQL 연결 같은 필수 의존성의 요청 처리 가능 상태를 확인한다.
- AI·관광·PG·푸시 공급자는 헬스체크에서 제외하고 별도로 감시한다.
- `UP`이면 200, `DOWN`·`OUT_OF_SERVICE`이면 503을 반환한다.
- 응답은 상태만 공개하고 DB URL, 자격증명, 예외 메시지를 노출하지 않는다.

```json
{"status":"UP"}
```

**구현 기준:** Spring Boot Actuator health를 사용한다. 인프라의 검사 주기·timeout·실패 기준은 배포 환경에서 설정하며, 일반 업무 API의 `{message,data}` wrapper를 적용하지 않는다. 가능하면 로드밸런서·배포 인프라에서만 접근하도록 제한한다.

## 3. 회원

### API-MEM-01 소셜 로그인 시작

| Method | URL | 인증 |
|---|---|---|
| GET | `/auth/oauth/authorize/{provider}` | 공개. 브라우저 최상위 페이지 이동으로 호출 |

- Path provider: 필수 소문자 String, `kakao` 또는 `google`. V1은 `kakao`, V2 이상(V3 포함)은 두 공급자 지원. DB/Java Enum은 `KAKAO`, `GOOGLE`을 유지한다.
- 출시 단계 V1/V2/V3와 API Prefix `/api/v1`은 별개다. 위 URL에도 공통 prefix를 적용한다.
- Query/Body 없음. 프론트는 code, state, verifier, redirect_uri를 만들거나 Body로 전달하지 않는다.
- 서버가 공급자별 client_id·client_secret·redirect_uri를 설정으로 관리한다. 임의 return URL을 받지 않는다.
- 새로운 로그인 시도와 state·PKCE S256 값을 만든 뒤 공급자의 인가 URL로 이동시킨다. 이 단계에서는 회원이나 서비스 세션을 생성하지 않는다.

**응답 302**: `Location`에 공급자 인가 URL. `response_type=code`, 고정 client_id/redirect_uri, scope, state, code_challenge, `code_challenge_method=S256`을 포함한다. OIDC 흐름은 nonce도 포함한다. JSON Body 없음. `Cache-Control: no-store`.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 지원하지 않는 provider |
| 500 | INTERNAL_SERVER_ERROR | 시작 처리 실패. 공통 JSON 오류, 비밀값 제외 |

### API-MEM-15 소셜 로그인 콜백·가입

| Method | URL | 인증 |
|---|---|---|
| GET | `/auth/oauth/callback/{provider}` | 공개 경로지만 기존 로그인 시도·브라우저 결속 검증 필수 |

- Path provider: 소문자 `kakao` 또는 `google`. 서버가 저장한 로그인 시도의 provider와 일치해야 하며 내부에서는 `KAKAO`, `GOOGLE` Enum으로 변환한다.
- 성공 Query: `code`, `state` 필수 String. 실패 Query: 공급자의 `error`, 가능하면 `state`. Body 없음.
- 공급자 콘솔에는 prefix를 포함한 실제 백엔드 콜백 주소를 정확히 등록한다. V1 카카오는 로컬 `http://localhost:8080/api/v1/auth/oauth/callback/kakao`, 배포 `https://kguidebook.site/api/v1/auth/oauth/callback/kakao`다. Spring의 redirectUri, 인가 요청과 토큰 요청에도 환경별 동일 값을 사용한다.
- state·브라우저·provider·유효기간을 검증하고 시도를 원자적으로 소비한 뒤, 저장한 verifier로 코드를 교환한다. client_secret과 verifier는 백엔드에서만 공급자에 전달한다.
- 검증된 `(oauth_provider, oauth_subject)`로 회원을 조회·생성한다. 같은 이메일로 계정을 자동 병합하지 않는다. 동시 신규 가입은 DB 유일 제약과 트랜잭션으로 중복 생성을 방지한다.
- 성공한 회원/세션 저장을 커밋한 뒤 새 불투명 서비스 세션 ID를 쿠키로 발급한다. 로그인 전 임시 세션 ID를 서비스 세션 ID로 재사용하지 않는다.

**성공 응답 302**: `Set-Cookie`로 서비스 세션을 발급하고, `Location: {FRONTEND_ORIGIN}/auth/complete`. JSON Body 없음. `Cache-Control: no-store`.

- 정식 FE가 없는 V1 개발 단계에는 고정된 동일 서버 경로 `GET /api/v1/members/me`로 이동한다. 이 API의 `status`가 `ONBOARDING`이면 이후 FE가 PREF-01, `ACTIVE`이면 MAP-01로 이동한다. 콜백이 취향 옵션이나 지도 콘텐츠 API로 직접 이동하지 않는다.

- 세션 쿠키 이름 `KGB_SESSION`은 기존 예시를 계약명으로 사용한다(서비스 브랜드 확정을 뜻하지 않음). 운영 속성은 `HttpOnly; Secure; SameSite=Lax; Path=/`, Domain 미지정(host-only).
- 운영은 같은 사이트 구성을 기준으로 한다. 교차 origin API 호출은 프론트 credentials와 서버의 정확한 허용 origin 설정이 필요하다. 다른 사이트 배포는 별도 보안 검토 없이 쿠키 정책을 바꾸지 않는다.
- 서비스 세션은 절대 8시간·유휴 30분·회원당 최대 1개, 절대 만료 연장 없음. Max-Age=28800. 로그인 성공 TX에서 회원 행을 잠그고 기존 유효 세션을 모두 폐기한 후 새 세션을 발급한다. 상세 원자 처리·접근표는 V1-03을 따른다. PKCE 10분과 구분한다.
- 프론트 완료 화면은 세션 쿠키로 `GET /members/me`를 호출한다. `status=ONBOARDING`이면 PREF-01, `ACTIVE`이면 MAP-01. 401이면 로그인 화면으로 복귀한다. `onboarding_required`를 별도 로그인 응답으로 전달하지 않는다.
- 서비스 액세스·리프레시 토큰은 발급하지 않으며, URL·localStorage에 인증 자격증명을 전달하지 않는다.

**실패 응답 302**: `Location: {FRONTEND_ORIGIN}/auth/error?code={아래의 허용된 코드}`. JSON Body 없음. 프론트는 오류 문구와 로그인 재시작 버튼을 표시한다. 원본 공급자 오류·인가 코드·state·verifier는 URL/응답/로그에 노출하지 않는다.

- 정식 FE가 없는 개발 단계의 기본 실패 경로는 동일 서버의 `/api/v1/auth/oauth/error?code=...`다. 허용된 코드만 공통 JSON 오류로 보여주며, 운영 FE 주소가 준비되면 설정값으로 교체한다.

| 복귀 code | 조건 | 복구 |
|---|---|---|
| OAUTH_ACCESS_DENIED | 결속 검증된 공급자 응답의 사용자 취소·거절 | 로그인 화면에서 재시작 |
| OAUTH_INVALID_REQUEST | state 누락/불일치/만료/재사용, 브라우저·provider 불일치, 필수 Query 오류 | 기존 시도로 재시도하지 않고 새 로그인 시작 |
| OAUTH_AUTHENTICATION_FAILED | 코드 교환 거절, 잘못된 verifier, ID token 검증 실패 | 새 로그인 시작 |
| OAUTH_PROVIDER_UNAVAILABLE | 공급자 연결 3초·응답 5초 타임아웃, 5xx·통신 장애 | 잠시 후 새 로그인 시작 |
| OAUTH_INTERNAL_ERROR | 회원/세션 저장 등 내부 처리 실패 | 새 로그인 시작 |

오류 복귀 code는 공통 JSON의 HTTP error.code 표와 구분한다. 공급자가 state를 돌려주지 않으면 임의 오류 문자열을 신뢰하지 않고 OAUTH_INVALID_REQUEST로 처리한다. 실패한 시도에서는 신규 회원·서비스 세션을 발급하지 않는다. 매칭되지 않는 state 요청은 다른 정상 시도를 삭제하지 않는다.

#### OAuth 확정 계약 — API-DEC-01

| 항목 | 확정 내용 |
|---|---|
| 실행 방식 | 웹 리다이렉트 Authorization Code + Spring Security `oauth2Login()`. 시작·콜백·코드 교환 모두 백엔드 담당. 프론트 SDK 코드 전달 방식은 사용하지 않음 |
| state | 암호학적 난수로 생성. 로그인 전 HttpSession에 로그인 시도별로 provider·고정 redirect_uri·생성 시각과 함께 저장하고 해당 브라우저의 임시 세션 쿠키에 결속 |
| 임시 보관 | Spring AuthorizationRequestRepository를 HttpSession 기반으로 구성. state별 map에 verifier 원문·필요한 nonce를 보관. 생성부터 10분 후 만료, 연장 없음. 10분은 프로젝트 기본값이지 OAuth 표준 고정값이 아님 |
| 일회성·다중 탭 | 동일 state의 검증·소비를 원자 처리해 콜백 중복 교환 금지. 각 탭은 별도 state로 관리. 만료 정리 및 브라우저당 최대 5개 시도, 초과 시작은 가장 오래된 시도를 폐기 |
| PKCE | 두 공급자 로그인에 S256 사용. 서버에서 32바이트 난수를 base64url(no padding)로 인코딩한 verifier 생성. challenge는 BASE64URL(SHA256(verifier)). challenge만 인가 요청에, verifier 원문은 토큰 교환에 전달 |
| 공급자·OIDC | Google은 OIDC(scope openid)로 ID token 서명·iss·aud·exp·nonce 검증. Kakao는 별도 OIDC를 활성화하지 않고 OAuth 사용자 정보 API의 검증된 id로 식별. 공급자별 응답을 공통 회원 식별로 매핑 |
| 비밀값 | client_secret·verifier·공급자 토큰은 서버에서만 처리, 브라우저와 로그에 노출 금지. 서비스 세션 ID는 DB에 SHA-256 해시만 보관 |
| 다중 서버 | 로그인 후 세션은 기존 공용 MySQL 사용. 로그인 전 HttpSession 저장소는 별개다. 단일 서버에서는 메모리 사용, 다중 서버 배포 전에 공유 HttpSession 저장소와 원자 소비를 적용·검증해야 함. Redis/새 업무 테이블을 지금 추가하지 않음 |
| 쿠키·CSRF | 임시 세션 쿠키도 운영 HttpOnly·Secure·SameSite=Lax·host-only 적용. 서비스 세션 인증의 POST/PUT/PATCH/DELETE에는 Spring Security CSRF 토큰 검증 적용. PKCE/state가 일반 API의 CSRF 방어를 대체하지 않음 |
| 서비스 로그인 | 기존 auth_sessions 기반 쿠키 인증 유지. API-MEM-02 갱신 API는 복원하지 않음. 회원당 유효 세션은 1개이며 새 로그인은 기존 세션을 폐기. 현재 세션 로그아웃, 탈퇴 시 모든 세션 폐기 |

프레임워크 기본 설정만으로 10분 TTL·다중 시도·원자 소비·현재 auth_sessions 매핑이 자동 구현되는 것은 아니다. AuthorizationRequestRepository 확장, 로그인 성공 처리, 서비스 세션 검증 및 CSRF 연동을 구현한다. 서비스 세션 수치는 V1-03에 정의하며 CSRF 전달·연동은 별도 보안 구현 항목이며 PKCE 설계 미정 항목이 아니다.

**구현 검증 체크리스트(설계 재결정 아님):** 선택한 Spring 버전에 맞춰 confidential client에도 S256을 명시 적용하고, KAKAO·GOOGLE 실제 요청에서 challenge/verifier 및 틀린 verifier 거절을 확인한다. state 누락·변조·만료·재사용, 브라우저/공급자 교체, 다중 탭·동시 콜백, 사용자 취소, 공급자 장애, 저장 실패, 로그인 후 세션 폐기·CSRF 거절을 테스트한다. 공급자/라이브러리 호환성 실패 시 배포를 막고 원인을 해결하며 plain 또는 PKCE 미사용으로 조용히 낮추지 않는다. 문서 확정은 연동 테스트 완료를 뜻하지 않는다.

참고: [Spring OAuth 로그인](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html), [Spring PKCE 설정](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html), [PKCE RFC 7636](https://www.rfc-editor.org/rfc/rfc7636.html), [OAuth 보안 RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html), [Spring CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

### API-MEM-16 CSRF 토큰 계약 조회

| Method | URL | 인증 |
|---|---|---|
| GET | `/auth/csrf` | 공개 |

- 서버는 `XSRF-TOKEN` 쿠키를 `HttpOnly=false; SameSite=Lax; Path=/; Domain 미지정`으로 발급한다. 운영에서는 Secure를 적용한다.
- 운영은 브라우저가 `https://kguidebook.site`에 접속하고 CloudFront가 같은 호스트의 `/api/*`를 백엔드로 전달하는 구성을 전제로 한다. FE는 이 host-only 쿠키 값을 읽어 POST/PUT/PATCH/DELETE 요청의 `X-XSRF-TOKEN` 헤더에 동일하게 넣는다.
- 로컬에서 FE와 BE의 포트만 다르면 FE origin을 `CORS_ALLOWED_ORIGINS`에 등록하고 `credentials: include`를 사용한다. 쿠키의 host가 같도록 둘 다 `localhost`를 사용하며 `localhost`와 `127.0.0.1`을 섞지 않는다.
- FE와 BE가 서로 다른 호스트라면 FE JavaScript는 BE의 host-only `XSRF-TOKEN` 쿠키를 읽을 수 없다. `credentials: include`와 CORS 허용만으로 해결되지 않으므로 현재 계약을 사용하지 않고 동일 호스트 프록시 또는 토큰 원문 응답 방식 중 하나를 별도 보안 검토 후 확정한다.
- CORS preflight는 `Content-Type`, `Idempotency-Key`, `X-XSRF-TOKEN`을 허용한다.
- 로그인 성공과 로그아웃 성공 시 기존 CSRF 쿠키를 만료한다. 리다이렉트 완료 또는 로그아웃 완료 후 이 API를 호출해 새 토큰을 받는다.
- 이 쿠키는 서비스 로그인 자격증명이 아니다. `KGB_SESSION`은 계속 HttpOnly이며 JavaScript에 노출하지 않는다.
- 응답과 쿠키를 캐시하지 않는다.

**응답 200**

```json
{
  "message": "CSRF 토큰 조회에 성공했습니다.",
  "data": {
    "cookie_name": "XSRF-TOKEN",
    "header_name": "X-XSRF-TOKEN"
  }
}
```

토큰 원문은 응답 JSON이 아니라 `XSRF-TOKEN` 쿠키에서 읽는다. 토큰이 누락되거나 쿠키와 헤더 값이 다르면 `403 RESOURCE_FORBIDDEN`이며 상태 변경은 수행하지 않는다.

### API-MEM-03 로그아웃

| Method | URL | 인증 |
|---|---|---|
| POST | `/auth/logout` | 세션 쿠키 필수 |

- 현재 요청의 세션을 폐기하고 세션 쿠키를 만료시킴
- 성공 시 XSRF-TOKEN도 만료하며, 이후 변경 요청 전 API-MEM-16으로 다시 발급받음
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
- 응답 nickname ≤50자, profile_image_url: String|null ≤2048자, email: String|null ≤254자. email=null이면 FE는 ‘이메일 정보 없음’을 표시
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
    "email": null,
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
- 회원은 소프트 삭제하고 서비스 데이터는 30일 보관 후 삭제·비식별화. V1 무료 원장은 30일 정리, V2 결제·유료 원장은 별도 보존 정책 적용
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

- V1 Query/Body 없음. label은 한국어(ko) 고정
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
    "items": [{"preference_type":"THEME","code":"NATURE","label":"자연","parent_code":null,"sort_order":10},{"preference_type":"DETAIL","code":"NATURE_MOUNTAIN","label":"산","parent_code":"NATURE","sort_order":10},{"preference_type":"TRAVEL_STYLE","code":"RELAXING","label":"여유롭게","parent_code":null,"sort_order":10}]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 기준:** API-DEC-03 확정: [V1 취향 Enum 코드표](./V1%20취향%20Enum%20코드표.md)의 허용 코드·부모 관계·표시 순서를 그대로 사용한다. `THEME`·`DETAIL`은 TourAPI 기반 관광 분류와 매핑하고 `TRAVEL_STYLE`은 별도 성향으로 사용한다.

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
    "selections": [{"preference_type":"THEME","preference_code":"NATURE"},{"preference_type":"DETAIL","preference_code":"NATURE_MOUNTAIN"},{"preference_type":"TRAVEL_STYLE","preference_code":"RELAXING"}]
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
- THEME 1~3개; 선택한 각 THEME별 DETAIL 1~3개; TRAVEL_STYLE 0~4개; THEME:DETAIL은 1:N이며 부모 없는 중분류·중복 조합 불가
- 누락 선택 제거; 최초 유효 저장 후 ACTIVE

**Request Body**

```json
{
  "selections": [
    {"preference_type":"THEME","preference_code":"NATURE"},
    {"preference_type":"DETAIL","preference_code":"NATURE_MOUNTAIN"},
    {"preference_type":"TRAVEL_STYLE","preference_code":"RELAXING"}
  ]
}
```

**응답 200**

```json
{
  "message": "preference_update_success",
  "data": {
    "selections": [{"preference_type":"THEME","preference_code":"NATURE"},{"preference_type":"DETAIL","preference_code":"NATURE_MOUNTAIN"},{"preference_type":"TRAVEL_STYLE","preference_code":"RELAXING"}],
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

**구현 기준:** API-DEC-03 보완: V1 코드표의 THEME 1~3, 선택한 부모별 DETAIL 1~3, 스타일 0~4를 적용한다. THEME·DETAIL은 TourAPI 기반 관광 분류와 동일한 안정 코드로 매핑한다.

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

- Query page: Integer ≥1, 기본1; size: Integer 1~20, 기본4. cursor 미지원(400)
- 빈 목록 total_pages=0, 범위 밖 page는 200 items=[]. 삭제 후 빈 마지막 페이지는 FE가 이전 유효 페이지 재조회
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
    "items": [{"notification_id":"301","type":"GUIDEBOOK_COMPLETED","title":"가이드북 완성","body":"가이드북을 확인해 주세요.","reference_type":"GUIDEBOOK","reference_id":"101","created_at":"2026-09-04T00:00:00Z"}],
    "page": 1,
    "size": 4,
    "total_items": 1,
    "total_pages": 1,
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
- category는 TourAPI 원본 분류를 V1 취향 코드표의 THEME·DETAIL 안정 코드에 매핑한다. TRAVEL_STYLE은 관광 category에 포함하지 않는다.
- 응답 markers[]/clusters[]/has_more; 좌표는 도, 거리 m
- 클러스터 표시는 실제 개수 1~9, 10 이상은 `9+`; 클러스터링 시작 기본 줌은 13~14
- 서버가 요청 범위와 zoom을 기준으로 클러스터링한다. `clusters[]`는 cluster_id, latitude, longitude, count, display_count를 반환하며 markers와 clusters 합계가 limit을 초과하면 has_more=true
- V1은 주기 동기화한 MySQL 관광 콘텐츠를 공간 인덱스로 조회한다. 동일 지역 AI 후보 또는 낮은 줌·고정 타일의 클러스터 계산이 실제 병목으로 확인되면 V2에서 해당 파생 결과만 Redis에 캐싱하며, 캐시 미스·장애 시 MySQL 조회로 복구한다.

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

**구현 전 확인:** API-DEC-05 확정: Figma MAP-01 기준 기본 반경 3km, 줌 6~21(최초 16~17), 클러스터 숫자 10 이상 `9+`. 백엔드 상한은 반경 10km, 마커·클러스터 합계 200개이며 서버가 클러스터를 집계한다. 관광 콘텐츠 6개 분류의 정확한 코드와 TourAPI 매핑은 샘플 검증 후 확정한다.

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
- `guidebooks.start_date ASC, guidebooks.created_at DESC, member_guidebooks.id DESC` 고정
- 로그인 회원의 `member_guidebooks.deleted_at IS NULL` 관계가 있는 항목만 조회
- preference_tags는 제공하지 않음. 현재 회원 취향을 과거 카드 표시값으로 대체하지 않음
- 가이드북 표지 저장 계약이 확정되기 전에는 thumbnail_url을 제공하지 않고 클라이언트 기본 이미지를 사용
- 화면에서 계산 가능한 일정 길이는 별도 필드로 제공하지 않음
- `people_count`, `version`, `updated_at`, `content_html`은 목록 카드에서 사용하지 않으므로 제외

**Request Body**

없음.

**응답 200**

```json
{
  "message": "guidebook_list_success",
  "data": {
    "items": [{"guidebook_id":101,"title":"경주 역사 여행","start_date":"2026-10-12","end_date":"2026-10-14","companion":"FRIEND"}],
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

> 구현 상태: 생성 요청·현재 취향 스냅샷 저장과 커밋 후 AI 접수 이벤트, AI 요청 변환 및 외부 `job_id` 저장을 구현했다. AI 접수 응답은 `PENDING`으로 받고 내부 작업도 `PENDING`으로 유지하며, 상태 조회에서 `PROCESSING`을 받으면 내부 상태와 시작 시각을 갱신한다. `local` 프로필에서는 Fake AI Client를 사용하며, 완료 결과·보관 관계·생성권 차감 원장·작업 완료를 한 트랜잭션으로 저장한다. 실제 AI HTTP Client, 접수 시점 생성권 검증, 동시성 보강과 실패 복구를 마치기 전에는 운영에 공개하지 않는다.

| Method | URL | 인증 |
|---|---|---|
| POST | `/guidebook-generations` | 세션 쿠키 필수 |

- Idempotency-Key: 필수 String ≤100자
- province: 필수 String ≤20자, 행정구역 시도명
- city: 필수 String ≤20자. 특별시·광역시는 구·군, 도는 시·군까지만 입력하며 일반 시 산하 구는 제외. 세종은 `세종특별자치시`
- start_date/end_date: 필수 YYYY-MM-DD, 서울 기준 today <= start_date <= end_date <= today.plusYears(1), 양끝 포함 1~7일
- companion: ALONE/FRIEND/COUPLE/FAMILY/GROUP 중 하나
- people_count: 본인 포함 Integer. ALONE=1, FRIEND=2~4, COUPLE=2, FAMILY=2~6, GROUP=2~10. 혼합 구성은 GROUP, 구성 배열 미지원
- 취향은 서버에서 현재값 조회하며 개별 취향 Body는 없음
- ACTIVE 회원·유효 기본 취향·잔액≥1·진행 작업 없음 필수
- 동일 키 재요청은 기존 작업의 현재 상태 반환; 새 AI 작업 생성 안 함

**Request Body**

```json
{
  "province": "경상북도",
  "city": "경주시",
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
    "job_id": 301,
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
| 409 | IDEMPOTENCY_CONFLICT | 같은 회원이 같은 키를 다른 요청에 재사용 |
| 409 | GENERATION_IN_PROGRESS | 이미 진행 중인 생성 작업 존재 |
| 422 | CREDIT_INSUFFICIENT | 생성권 잔액 1개 미만 |
| 422 | GUIDEBOOK_INVALID_REGION | 지원하지 않는 시도·시군구 또는 서로 일치하지 않는 조합 |
| 422 | GUIDEBOOK_INVALID_PERIOD | 종료일 역전 또는 양끝 포함 7일 초과 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-09 확정: 개별 시도 300초, 재시도 3회(`attempt_count=0~3`, 최초 포함 최대 4회). API-DEC-03의 동행 Enum·인원 상한은 승인된 화면정의서·기능설계도 범위만 구현.

### API-GDE-03 생성 상태 조회

> 구현 상태: `local` 프로필에서 종료되지 않은 작업을 조회하면 Fake AI 상태를 한 번 동기화한다. `COMPLETED`는 가이드북·일정·회원 보관 관계·생성권 차감·원장·작업 완료를 한 트랜잭션으로 반영한다. 실제 AI 연동과 주기적 백그라운드 폴링은 후속 구현이다.

| Method | URL | 인증 |
|---|---|---|
| GET | `/guidebook-generations/{job_id}` | 세션 쿠키 필수 |

- Path job_id: 필수 양의 정수
- status: PENDING|PROCESSING|COMPLETED|FAILED|CANCELED
- attempt_count: Integer 0~3, 재시도 횟수. 최초 생성 성공 전 guidebook_id=null. 재생성 작업은 완료 전에도 기존 대상 guidebook_id를 유지한다.
- 개별 시도는 300초 타임아웃. 탈퇴·대상 가이드북 삭제로 취소된 작업의 늦은 완료 결과는 무시
- error: FAILED이면 {code:"GENERATION_FAILED",message:"가이드북 생성에 실패했습니다."}, 그 외 상태는 null. 내부 AI error_payload는 반환하지 않는다.

**Request Body**

없음.

**응답 200**

```json
{
  "message": "generation_job_get_success",
  "data": {
    "job_id": 301,
    "status": "COMPLETED",
    "guidebook_id": 101,
    "attempt_count": 1,
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

- Path guidebook_id: 필수 양의 정수
- 생성 완료·재생성 완료 화면에서 사용하는 가이드북 기본 정보와 전체 일정을 반환
- itinerary는 일차를 `day_number ASC`, 방문 장소를 `sequence ASC`로 제공
- 생성 완료 화면의 "첫 장소 외 N곳" 문구는 프론트엔드가 각 일차의 첫 항목과 전체 개수로 계산
- HTML 본문은 API-GDE-15 뷰어에서만 제공
- 가이드북 표지 저장 계약이 확정되기 전에는 thumbnail_url을 제공하지 않고 클라이언트 기본 이미지를 사용
- 활성 `member_guidebooks` 관계가 없으면 404. preference_tags 미제공

**Request Body**

없음.

**응답 200**

```json
{
  "message": "guidebook_get_success",
  "data": {
    "guidebook_id": 101,
    "title": "경주 역사 여행",
    "start_date": "2026-10-12",
    "end_date": "2026-10-14",
    "people_count": 2,
    "itinerary": [
      {
        "day_number": 1,
        "itinerary_date": "2026-10-12",
        "items": [
          {"item_id":501,"content_id":101,"sequence":1,"scheduled_time":"10:00:00","place_snapshot":{"title":"첨성대"}},
          {"item_id":502,"content_id":102,"sequence":2,"scheduled_time":"13:00:00","place_snapshot":{"title":"교촌마을"}}
        ]
      }
    ]
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 403 | RESOURCE_FORBIDDEN | ONBOARDING 등 허용되지 않은 회원 상태. 공통 인가 정책 구현 후 적용 |
| 404 | RESOURCE_NOT_FOUND | 없는 가이드북, 미보관, 삭제된 관계, 타인 보관 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-01 대체: preference_tags 저장·표시를 제거한다. DB 현재 취향은 변경되지만 접수된 작업의 request_payload와 기존 가이드북 결과는 바뀌지 않음.

### API-GDE-07 일정 조회

> 현재 V1에서는 API-GDE-05가 전체 `itinerary`를 반환하므로 별도 엔드포인트를 구현하지 않는다. 지도·공유·평가에서 독립 일정 조회가 필요해질 때 분리를 검토한다.

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
    "guidebook_id": 101,
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
- 재생성 진입 버튼은 최초 생성 결과 화면에서만 노출하고 화면 이탈 후에는 제공하지 않음
- 요청 회원의 활성 보관 관계가 있는 가이드북만 허용
- 현재 기본 취향 사용; feedback은 해당 가이드북만 반영하고 회원 기본 취향을 변경하지 않음
- 새 작업; 성공 시 같은 guidebook_id의 제목·본문·일정을 갱신하고 version 증가
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
    "job_id": 302,
    "job_type": "REGENERATION",
    "status": "PENDING",
    "guidebook_id": 101,
    "current_version": 1
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 400 | COMMON_VALIDATION_ERROR | 필드·쿼리 자료형, 형식 또는 범위 오류 |
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 409 | IDEMPOTENCY_CONFLICT | 같은 회원이 같은 키를 다른 요청에 재사용 |
| 409 | GENERATION_IN_PROGRESS | 이미 진행 중인 생성 작업 존재 |
| 422 | CREDIT_INSUFFICIENT | 생성권 잔액 1개 미만 |
| 422 | GUIDEBOOK_INVALID_PERIOD | 종료일 역전 또는 양끝 포함 7일 초과 |
| 422 | PREFERENCE_INVALID | 대분류 개수·코드·상하위 관계 위반 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |
| 403 | RESOURCE_FORBIDDEN | 타인 소유 데이터 또는 허용되지 않은 상태 |
| 404 | RESOURCE_NOT_FOUND | 없거나 삭제된 리소스 |

**구현 전 확인:** DEC-12 확정: 재생성은 최초 생성 결과 화면에서만 제공하고 같은 가이드북을 갱신한다. 공유는 이 흐름이 끝난 뒤부터 제공하므로 공유된 가이드북은 변경되지 않는다.

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

**구현 전 확인:** DEC-06/12 확정: 발급 후 24시간 만료하며 불변 가이드북을 참조한다. 발급 회원의 보관 관계가 삭제되면 링크 사용을 차단한다.

### API-GDE-11 공유 미리보기

| Method | URL | 인증 |
|---|---|---|
| GET | `/shares/{share_token}` | 세션 쿠키 필수 |

- Path share_token: 필수 난수 문자열
- 미존재·만료·원본 삭제는 모두 404
- 응답은 로그인한 수신자용 최소 미리보기
- 공유자 닉네임, 가이드북 제목, 시작일·종료일, 장소 수만 포함
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
    "place_count": 12
  }
}
```

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 404 | SHARE_LINK_UNAVAILABLE | 공유 토큰 미존재·만료·대상 삭제 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

**구현 전 확인:** DEC-06 확정: 로그인 필수. 공유자 닉네임·제목·여행 기간·장소 수만 공개하고 상세 일정·HTML·개인화 입력은 제외.

### API-GDE-12 공유 가이드북 가져오기

| Method | URL | 인증 |
|---|---|---|
| POST | `/shares/{share_token}/imports` | 세션 쿠키 필수 |

- Path share_token: 필수 String
- Body 없음. 회원·가이드북 기준 중복 보관 금지
- 첫 가져오기는 같은 guidebook_id의 `member_guidebooks` 관계를 만들고 201
- 기존 관계가 활성 상태면 200, 소프트 삭제 상태면 `deleted_at`을 해제하고 200

**Request Body**

없음.

**응답 201**

```json
{
  "message": "guidebook_imported",
  "data": {
    "guidebook_id": 101,
    "already_imported": false
  }
}
```

**응답 200**

```json
{
  "message": "already_imported",
  "data": {
    "guidebook_id": 101,
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

**구현 전 확인:** DEC-07 확정: 가이드북·일정은 복사하지 않는다. `member_guidebooks(member_id, guidebook_id)` UNIQUE로 중복을 막고 삭제된 관계는 복구한다.

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
    "guidebook_id": 101,
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

- Path guidebook_id: 필수 양의 정수
- Body 없음; 활성 보관 관계를 가진 회원만 삭제
- 자신의 `member_guidebooks` 관계만 소프트 삭제하고 다른 회원 관계와 원본 가이드북·일정은 유지

**Request Body**

없음.

**응답 204**

Body 없음.

| 오류 HTTP | error.code | 조건 |
|---|---|---|
| 401 | AUTH_SESSION_REQUIRED | 세션 쿠키 누락·유효하지 않음 |
| 404 | RESOURCE_NOT_FOUND | 없거나 미보관·타인 보관·이미 삭제된 관계 |
| 500 | INTERNAL_SERVER_ERROR | 내부 오류; 원본 예외·개인정보는 응답에서 제외 |

V1에서는 회원별 보관 관계 삭제만 처리한다. 공유 링크, 생성·재생성 작업 취소, 원본 물리 삭제는 이 API 범위에 포함하지 않는다.

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
    "items": [{"evaluation_id":null,"guidebook_id":101,"status":"PENDING","prompt_dismissed_at":null}],
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
    "guidebook_id": 101,
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
    "guidebook_id": 101,
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
    "guidebook_id": 101,
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
- active_job_id: Long|null, PENDING/PROCESSING 작업
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
- order_id: ID|null; generation_job_id: Long|null

**Request Body**

없음.

**응답 200**

```json
{
  "message": "credit_transaction_list_success",
  "data": {
    "items": [{"transaction_id":"801","type":"CONSUME","credit_delta":-1,"credit_balance_after":5,"order_id":null,"generation_job_id":301,"created_at":"2026-09-04T00:00:00Z"}],
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
| 409 | IDEMPOTENCY_CONFLICT | 같은 회원이 같은 키를 다른 요청에 재사용 |
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
| IDEMPOTENCY_CONFLICT | 409 | idempotency_conflict | 같은 회원이 같은 키를 다른 요청에 재사용 |
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
| API-MEM-01(구 계약) | POST /auth/oauth/{provider}/login | 프론트 코드 전달 초안 폐기. MEM-01 시작 GET + MEM-15 콜백 GET으로 대체. |
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
| DEC-01 | GDE-01/05/11 | 확정: preference_tags 저장·표시 제거. 현재 취향은 DB, 생성 입력은 request_payload snapshot으로 구분. |
| DEC-02 | CON-01 | 확정: `month=YYYY-MM`. 행사 기간이 있는 콘텐츠는 선택 월과 기간이 겹치면 포함하고, 행사 기간이 없는 콘텐츠는 상시 포함. 관광 분류는 V1 취향 코드표의 THEME·DETAIL 안정 코드와 매핑. |
| DEC-03/04 | RNK-07 | 부분 확정: `Asia/Seoul` 기준, 일간 00시·주간 월요일 00시·월간 1일 00시 시작, 최초 제출 시각 귀속, 베이지안 가중 평균, 동점은 평가 수 내림차순 후 `content_id` 오름차순, 10분 배치·최대 지연 10분. `C` 범위와 `m` 값은 미확정. `updated_at`은 변경 탐지에만 사용. |
| DEC-05 | RNK-03/06 | 확정: 정수 0~5, `null` 건너뛰기, 완료 전 프론트 초안. 마지막 장소에서 `완료하기` 시 전체 대상을 최종 제출하며 부분 제출·제출 후 수정은 불가. |
| DEC-06 | GDE-10/11 | 확정: 발급 후 24시간 만료, 미리보기도 로그인 필수. 공유자 닉네임·제목·여행 기간·장소 수만 공개하고 상세 일정·HTML·개인화 입력은 제외. |
| DEC-07 | GDE-12 | 확정: 가이드북을 복제하지 않고 `member_guidebooks` 관계를 생성하며 삭제된 관계는 복구. |
| DEC-08 | NOT-01 | 확정: 초기 4개 노출, 회원별 미읽음 최대 20개 보관. 새 알림이 추가될 때 20개를 초과하면 가장 오래된 행부터 삭제. |
| DEC-09 | GDE-02/03/09 | 확정: 개별 시도 300초 타임아웃, 재시도 최대 3회. `attempt_count=0~3`은 재시도 횟수이므로 최초 포함 최대 4회 실행. 탈퇴·대상 삭제 시 작업 취소 요청 및 늦은 결과 무시. |
| DEC-10 | PAY-08 제외 | 환불 정책·저장·API는 MVP 이후. |
| DEC-11 | MEM-06 | 확정: 탈퇴 시 세션 즉시 폐기·`deleted_at` 소프트 삭제, 서비스 데이터 30일 보관 후 삭제·비식별화. 재가입은 기존 회원을 복구하지 않고 새 회원 생성. 결제·원장은 법정 보존 예외. |
| DEC-12 | GDE-09~16/RNK | 확정: 최초 생성 결과 화면에서만 같은 가이드북을 재생성. 공유는 재생성 흐름 종료 후 제공. |
| API-DEC-01 | MEM-01/15/03 | 확정: 웹 Authorization Code, Spring Security OAuth2 Login, 백엔드 시작·콜백, state 브라우저 결속·10분 TTL·일회 소비, PKCE S256 및 서버 verifier 보관. 서비스 세션 쿠키 유지, 서비스 액세스·리프레시 토큰과 갱신 API 없음. 서비스 세션은 V1-03 확정, 배포 주소·CSRF 연동 검증은 별도. |
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
