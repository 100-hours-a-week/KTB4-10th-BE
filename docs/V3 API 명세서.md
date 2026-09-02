# V3 API 명세서

> 기준 문서: [요구사항정의서](./요구사항정의서.md), [테이블정의서](./테이블정의서.md)
>
> 기준일: 2026-09-02
>
> 문서 상태: ERD 초안 연동 API 설계
>
> API Prefix: `/api/v1`

## 1. 문서 목적

V3의 5개 도메인을 프론트엔드가 호출할 REST API 계약으로 변환한다. 사용자 행동과 비즈니스 규칙은 요구사항정의서를, 식별자·상태·유일 제약·저장 가능 여부는 테이블정의서를 기준으로 삼는다.

두 기준 문서가 아직 일치하지 않는 항목은 임의로 확정하지 않고 `14. 구현 전 결정 사항`에 기록했다. 해당 API는 모양을 검토할 수는 있지만 관련 정책이나 테이블이 확정되기 전에는 구현 확정본으로 보지 않는다.

## 2. 공통 설계 원칙

1. URL은 복수형 리소스 명사를 사용한다. 조회는 `GET`, 생성·명령은 `POST`, 전체 교체는 `PUT`, 일부 변경은 `PATCH`, 관계 해제는 `DELETE`로 표현한다.
2. 회원 ID는 Body로 받지 않고 Bearer 토큰에서 얻는다.
3. 생성·재생성·가져오기·평가 제출·주문·환불은 `Idempotency-Key`를 사용한다.
4. AI 생성과 PDF 내보내기는 `202 Accepted`와 작업 ID를 반환한다.
5. 외부 노출 가이드북·생성 작업 ID는 문자열(`gb_...`, `job_...`)을 사용한다.
6. 생성 작업 상태는 `PENDING | PROCESSING | COMPLETED | FAILED`로 통일한다.
7. 재생성은 새 작업을 만들지만 성공 시 기존 가이드북 ID를 유지하고 `version`만 증가시킨다.
8. 생성권은 `credit_wallets`의 총잔액·예약수량으로 판단하며 모든 변경을 `credit_transactions`에 기록한다.
9. 알림은 DOM-01 회원의 하위 책임이며 알림 실패가 원본 업무를 롤백하지 않는다.

## 3. 공통 규약

### 3.1 요청 헤더

| 헤더 | 필수 | 적용 | 설명 |
|---|---:|---|---|
| `Authorization: Bearer {access_token}` | 조건부 | 인증·공개 공유·PG 웹훅 제외 | 로그인 회원 식별 |
| `Content-Type: application/json` | Body가 있을 때 | JSON 요청 | 요청 형식 |
| `Accept-Language` | 선택 | 전체 | `ko`, `en`; 없으면 회원 설정 또는 `ko` |
| `Idempotency-Key` | 조건부 필수 | 중복 생성 위험 요청 | 같은 키·같은 Body는 기존 결과 반환 |
| `X-Request-Id` | 선택 | 전체 | 추적 ID. 없으면 서버 생성 |

### 3.2 응답 형식

```json
{
  "message": "guidebook_get_success",
  "data": {},
  "meta": {"request_id": "req_01J..."}
}
```

```json
{
  "message": "invalid_request",
  "data": null,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "details": [{"field": "end_date", "reason": "must_be_on_or_after_start_date"}],
    "trace_id": "req_01J..."
  }
}
```

`message`와 `error.code`는 프론트 분기용 안정 키다. 사용자 노출 문구는 프론트 다국어 리소스에서 매핑한다.

### 3.3 HTTP 상태 코드

| 상태 | 의미 | 대표 상황 |
|---:|---|---|
| `200` | 조회·수정·멱등 재요청 성공 | 상세, PUT/PATCH, 기존 가져오기 안내 |
| `201` | 리소스 생성 완료 | 주문, 공유 링크 |
| `202` | 비동기 작업 접수 | AI 생성, PDF, 환불(잠정) |
| `204` | 본문 없는 성공 | 로그아웃, 관심 장소 해제 |
| `400` | 형식·자료형·범위 오류 | 검색어 길이, 필드 형식 |
| `401` | 인증 실패 | 토큰 누락·만료, 웹훅 서명 실패 |
| `403` | 인가 실패 | 타인 소유 리소스 접근 |
| `404` | 없거나 공개하지 않을 리소스 | 콘텐츠, 가이드북, 유효하지 않은 공유 링크 |
| `409` | 상태·멱등성 충돌 | 제출 완료 평가 수정, 같은 키의 다른 Body |
| `422` | 비즈니스 규칙 위반 | 생성권 부족, 여행 기간 초과 |
| `429` | 호출 제한 초과 | 검색·OAuth·생성 요청 과다 |
| `500` | 내부 오류 | 예상하지 못한 서버 예외 |
| `502` | 외부 연동 오류 | AI·PG·관광 API 오류 |
| `503` | 일시적 이용 불가 | 외부 장기 장애·점검 |

공유 토큰이 존재하지 않거나 만료된 경우는 모두 `404 SHARE_LINK_UNAVAILABLE`로 응답한다.

### 3.4 목록 규칙

| 목록 | 방식 | 기본값 |
|---|---|---|
| 콘텐츠 검색 | `page`, `size` | `page=1`, `size=5`, 최대 20 |
| 지도 콘텐츠 | 영역/반경 + `limit` | 서버 최대 마커 수 적용 |
| 가이드북·알림·생성권 원장 | `cursor`, `size` | `size=20`, 최신순 |
| 랭킹 | `page`, `size` | `page=1`, `size=20` |

## 4. 엔드포인트 요약

| 도메인 | 범위 | 주요 URL |
|---|---|---|
| DOM-01 회원 | API-MEM-01~14, API-NOT-01~05 | `/auth`, `/members/me`, `/preference-options`, `/policies`, `/notifications` |
| DOM-02 관광 콘텐츠·탐색 | API-CON-01~06 | `/contents`, `/map/contents`, `/members/me/favorites` |
| DOM-03 가이드북 | API-GDE-01~15 | `/guidebooks`, `/guidebook-generations`, `/shares`, `/guidebook-exports` |
| DOM-04 평가·랭킹 | API-RNK-01~07 | `/guidebook-evaluations`, `/rankings` |
| DOM-05 생성권·결제 | API-PAY-01~08 | `/credits`, `/credit-products`, `/orders`, `/payments` |

## 5. DOM-01 회원 API

| API ID·기능 | Method | URL | 요청 | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-MEM-01 소셜 로그인/가입 | POST | `/auth/oauth/{provider}/login` | `provider=KAKAO\|GOOGLE`; `authorization_code`, `redirect_uri`, `device_identifier`, 선택 `policy_consents[]` | `200 login_success` 또는 `201 member_created`; 토큰, member, onboarding_required | 400, 401, 409, 502 | FR-MEM-01, BR-MEM-01~02. `oauth_provider + oauth_subject` 유일 제약으로 분기한다. |
| API-MEM-02 토큰 갱신 | POST | `/auth/token/refresh` | `refresh_token` | `200 token_refresh_success` | 401, 409 | FR-MEM-02. 해시 저장 세션과 토큰 회전을 사용한다. |
| API-MEM-03 로그아웃 | POST | `/auth/logout` | Auth; `refresh_token`, `all_devices=false` | `204` | 401 | 세션의 `revoked_at`을 기록한다. |
| API-MEM-04 내 회원 조회 | GET | `/members/me` | Auth | `200 member_get_success`; 프로필, `language_code`, status, unread count | 401 | `members`와 알림 요약을 조합한다. |
| API-MEM-05 프로필 수정 | PATCH | `/members/me` | Auth; `nickname`, `profile_image_url` 중 변경값 | `200 member_update_success` | 400, 401, 409 | FR-MEM-04. 일부 필드 변경이므로 PATCH다. |
| API-MEM-06 회원 탈퇴 | DELETE | `/members/me` | Auth; `confirmation` | `202 withdrawal_accepted` | 400, 401, 409 | BR-MEM-03/09. `WITHDRAWN` 전환과 세션 폐기. 보존 정책은 DEC-11이다. |
| API-MEM-07 취향 옵션 조회 | GET | `/preference-options` | 선택 `language_code`, `active=true` | `200 preference_option_list_success`; `items[{id,code,name,description,sort_order}]` | 400 | `preference_options` 단일 목록이다. 임의의 카테고리 계층을 가정하지 않는다. |
| API-MEM-08 기본 취향 조회 | GET | `/members/me/preferences` | Auth | `200 preference_get_success`; `preference_option_ids`, options | 401 | `member_preference_selections`의 현재값이다. |
| API-MEM-09 기본 취향 저장 | PUT | `/members/me/preferences` | Auth; `preference_option_ids[]` 전체 | `200 preference_update_success` | 400, 401, 422 | 회원 선택 전체를 트랜잭션으로 교체한다. |
| API-MEM-10 설정 조회 | GET | `/members/me/settings` | Auth | `200 member_setting_get_success`; `language_code`, `push_enabled` | 401 | 두 값 모두 `members`에 저장된다. |
| API-MEM-11 설정 수정 | PATCH | `/members/me/settings` | Auth; 변경할 `language_code`, `push_enabled` | `200 member_setting_update_success` | 400, 401 | 누락 필드는 유지한다. |
| API-MEM-12 정책 목록 **잠정** | GET | `/policies` | `type`, `current_only=true` | `200 policy_list_success` | 400 | FR-MEM-05는 요구하지만 정책 테이블이 없어 DEC-13 확정 전 잠정이다. |
| API-MEM-13 정책 상세 **잠정** | GET | `/policies/{policy_id}` | Path `policy_id` | `200 policy_get_success` | 404 | 정책 본문·버전·시행일 저장 구조가 필요하다. |
| API-MEM-14 약관 동의 저장 **잠정** | POST | `/members/me/consents` | Auth; `consents[{policy_id,agreed}]` | `201 consent_saved` | 400, 401, 422 | BR-MEM-04~05를 구현할 동의 이력 테이블이 필요하다. |
| API-NOT-01 알림 목록 | GET | `/notifications` | Auth; `cursor`, `size`, `unread_only` | `200 notification_list_success`; reference, read_at, cursor, unread_count | 401 | 최대 20, 초기 4다. |
| API-NOT-02 개별 읽음 | PATCH | `/notifications/{notification_id}` | Auth; `read=true` | `200 notification_read` | 401, 403, 404 | 회원 소유 알림의 `read_at`만 변경한다. |
| API-NOT-03 모두 읽음 | POST | `/notifications/read-all` | Auth; 선택 `before` | `200 notifications_read_all`; updated_count | 401 | 요청 시점 이전 알림만 처리한다. |
| API-NOT-04 푸시 설정 조회 | GET | `/push-preferences` | Auth | `200 push_preference_get_success`; enabled | 401 | `members.push_enabled`를 반환한다. |
| API-NOT-05 푸시 설정 변경 | PATCH | `/push-preferences` | Auth; enabled | `200 push_preference_update_success` | 400, 401 | 인앱 알림 저장 여부와 분리한다. |

취향 저장 Body는 `{"preference_option_ids":[1,4,7]}` 형식이다.

## 6. DOM-02 관광 콘텐츠·탐색 API

| API ID·기능 | Method | URL | 요청 | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-CON-01 콘텐츠 검색 | GET | `/contents` | `q`≤10자, `region_code`, `month`, `category_code`, `page`, `size`, `sort` | `200 content_list_success`; items, page | 400, 502 | 필터는 AND다. code는 서버가 내부 ID로 변환한다. |
| API-CON-02 콘텐츠 상세 | GET | `/contents/{content_id}` | Path `content_id` | `200 content_get_success`; 공통 정보, event_detail, images, categories | 404, 502 | `tourism_contents` 기준 조합 응답이다. |
| API-CON-03 지도 콘텐츠 | GET | `/map/contents` | 위치+반경 또는 bounds, zoom, category_code, limit | `200 map_content_success`; markers, clusters, has_more | 400, 422, 502 | 마커·클러스터는 응답 모델이며 테이블이 아니다. |
| API-CON-04 관심 장소 목록 | GET | `/members/me/favorites` | Auth; cursor, size | `200 favorite_list_success` | 401 | 회원 소유 N:M 관계 조회다. |
| API-CON-05 관심 장소 등록 | PUT | `/members/me/favorites/{content_id}` | Auth | `200 favorite_saved` | 401, 404 | 복합 PK와 PUT 멱등성을 맞춘다. |
| API-CON-06 관심 장소 해제 | DELETE | `/members/me/favorites/{content_id}` | Auth | `204` | 401, 404 | 이미 해제된 경우도 204다. |

## 7. DOM-03 가이드북 API

| API ID·기능 | Method | URL | 요청 | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-GDE-01 가이드북 목록 | GET | `/guidebooks` | Auth; cursor, size, `sort=created_at,desc` | `200 guidebook_list_success`; id, title, dates, version, cursor | 401 | 소유자와 생성순 인덱스를 사용한다. |
| API-GDE-02 최초 생성 접수 | POST | `/guidebook-generations` | Auth, Idempotency-Key; 지역, 기간, companion, people_count | `202 guidebook_generation_accepted`; job_id, `PENDING`, `RESERVED` | 400, 401, 409, 422, 502 | 기본 취향을 서버가 조회해 `request_payload`에 스냅샷으로 저장한다. |
| API-GDE-03 생성 상태 조회 | GET | `/guidebook-generations/{job_id}` | Auth | `200 generation_job_get_success`; type, status, attempt_count, guidebook_id/version, error | 401, 403, 404 | DB에 저장되는 상태만 계약으로 보장한다. |
| API-GDE-04 기술 재시도 | POST | `/guidebook-generations/{job_id}/retry` | Auth, Idempotency-Key | `202 generation_retry_accepted`; **같은 job_id**, attempt_count | 401, 403, 404, 409, 422 | 동일 작업 최대 3회. 새 생성권을 예약하지 않는다. |
| API-GDE-05 가이드북 상세 | GET | `/guidebooks/{guidebook_id}` | Auth | `200 guidebook_get_success`; 대표 정보, HTML, events, version | 401, 403, 404 | 실제 저장 필드만 기본 계약으로 삼는다. |
| API-GDE-06 제목 수정 | PATCH | `/guidebooks/{guidebook_id}` | Auth; title 1~15자 | `200 guidebook_update_success` | 400, 401, 403, 404 | AI 재생성이나 version 증가를 일으키지 않는다. |
| API-GDE-07 일정 조회 | GET | `/guidebooks/{guidebook_id}/itinerary` | Auth | `200 itinerary_get_success`; days/items, content_id, place_snapshot | 401, 403, 404 | 현재 콘텐츠와 생성 당시 스냅샷을 함께 제공한다. |
| API-GDE-08 일정 전체 저장 | PUT | `/guidebooks/{guidebook_id}/itinerary` | Auth; 전체 days/items | `200 itinerary_update_success` | 400, 401, 403, 404, 422 | 날짜·순서 유일성을 트랜잭션으로 검증한다. |
| API-GDE-09 재생성 접수 | POST | `/guidebooks/{guidebook_id}/regenerations` | Auth, Idempotency-Key; feedback≤200자 | `202 regeneration_accepted`; 새 job_id, 같은 guidebook_id, current_version, PENDING | 400, 401, 403, 404, 409, 422 | 성공 시 같은 가이드북을 갱신하고 version 증가. 실패 시 기존 내용 유지. 새 생성권 예약. |
| API-GDE-10 공유 링크 발급 | POST | `/guidebooks/{guidebook_id}/shares` | Auth, Idempotency-Key; 선택 expires_at | `201 share_created`; share_url, expires_at | 401, 403, 404, 422 | 원문 토큰은 응답하고 DB에는 hash만 저장한다. |
| API-GDE-11 공유 미리보기 | GET | `/shares/{share_token}` | 공개 또는 선택 Auth | `200 share_preview_success` | 404 | 만료·미존재를 같은 오류로 처리한다. |
| API-GDE-12 가져오기 | POST | `/shares/{share_token}/imports` | Auth, Idempotency-Key | 첫 요청 `201`; 재요청 `200 already_imported`와 기존 ID | 401, 404 | 유일 제약으로 중복 복사본을 만들지 않는다. |
| API-GDE-13 PDF 요청 **잠정** | POST | `/guidebooks/{guidebook_id}/exports` | Auth, Idempotency-Key; `format=PDF` | `202 export_accepted`; export_id, PENDING | 401, 403, 404, 409 | 비동기 요구는 있으나 작업 테이블이 없어 DEC-15가 필요하다. |
| API-GDE-14 PDF 상태 **잠정** | GET | `/guidebook-exports/{export_id}` | Auth | `200 export_get_success`; status, download_url, expires_at, error | 401, 403, 404 | 작업 소유권·상태·파일 만료 저장 구조가 필요하다. |
| API-GDE-15 HTML 뷰어 | GET | `/guidebooks/{guidebook_id}/viewer` | Auth | `200 viewer_get_success`; content_html, version | 401, 403, 404, 503 | version을 오프라인 캐시 키로 사용한다. |

최초 생성 접수 예시:

```json
{
  "region_code": "47",
  "detail_region_code": "47130",
  "start_date": "2026-10-12",
  "end_date": "2026-10-14",
  "companion": "FRIEND",
  "people_count": 2
}
```

```json
{
  "message": "guidebook_generation_accepted",
  "data": {
    "job_id": "job_01J...",
    "job_type": "INITIAL",
    "status": "PENDING",
    "credit_status": "RESERVED",
    "guidebook_id": null
  }
}
```

## 8. DOM-04 평가·랭킹 API

| API ID·기능 | Method | URL | 요청 | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-RNK-01 평가 진행 목록 | GET | `/guidebook-evaluations` | Auth; `status=PENDING\|IN_PROGRESS`, cursor, size | `200 evaluation_list_success`; guidebook, status, prompt_dismissed_at | 401 | 종료 다음 날 이후의 대상만 반환한다. |
| API-RNK-02 평가 시작/재개 | PUT | `/guidebooks/{guidebook_id}/evaluation` | Auth | `200 evaluation_ready`; evaluation_id, status | 401, 403, 404, 422 | `(guidebook_id, member_id)` 유일 제약에 맞춘 멱등 PUT이다. |
| API-RNK-03 평가 조회 | GET | `/guidebook-evaluations/{evaluation_id}` | Auth | `200 evaluation_get_success`; 일정순 장소와 현재 score | 401, 403, 404 | 미응답은 항목 부재, 건너뛰기는 null, 평가는 숫자다. |
| API-RNK-04 장소 평가 저장 **잠정** | PUT | `/guidebook-evaluations/{evaluation_id}/places/{content_id}` | Auth; score 0~5 또는 null | `200 rating_saved` | 400, 401, 403, 404, 409, 422 | `place_ratings`만으로 제출 전 초안과 기존 최종값을 분리할 수 없어 DEC-14가 필요하다. |
| API-RNK-05 다음에 하기 | PATCH | `/guidebook-evaluations/{evaluation_id}` | Auth; `prompt_dismissed=true` | `200 evaluation_prompt_dismissed`; 기존 status 유지 | 401, 403, 404, 409 | `DEFERRED`를 만들지 않고 시각만 기록한다. |
| API-RNK-06 평가 제출 | POST | `/guidebook-evaluations/{evaluation_id}/submit` | Auth, Idempotency-Key | `200 evaluation_submitted`; rated_count, skipped_count | 401, 403, 404, 409, 422 | `SUBMITTED`를 한 번만 반영한다. |
| API-RNK-07 랭킹 조회 | GET | `/rankings` | period_type, period_start, 선택 region_code, page, size | `200 ranking_list_success`; snapshot metadata, entries | 400, 404 | period_end, scope_key, global_average, minimum_votes, formula_version을 포함한다. |

별점은 `score: 0`도 유효하다. `score: null`은 건너뛰기이며 미응답은 아직 저장된 항목이 없는 상태다.

## 9. DOM-05 생성권·결제 API

| API ID·기능 | Method | URL | 요청 | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-PAY-01 지갑 조회 | GET | `/credits/wallet` | Auth | `200 credit_wallet_get_success`; credit_balance, reserved_count, available_count, version | 401 | 사용 가능량은 총잔액-예약수량이다. |
| API-PAY-02 원장 조회 | GET | `/credits/transactions` | Auth; cursor, size, type | `200 credit_transaction_list_success`; delta와 처리 후 잔액 | 401 | append-only 이력을 최신순 제공한다. |
| API-PAY-03 상품 목록 | GET | `/credit-products` | active=true | `200 credit_product_list_success`; id, name, credit_amount, price, currency | 502 | 서버 가격을 사용한다. |
| API-PAY-04 주문 생성 | POST | `/orders` | Auth, Idempotency-Key; product_id | `201 order_created`; merchant_order_id, 주문 스냅샷, status | 401, 404, 409, 422 | 서버 상품 값으로 수량·금액·통화를 고정한다. |
| API-PAY-05 주문 조회 | GET | `/orders/{merchant_order_id}` | Auth | `200 order_get_success`; 주문과 결제 시도 | 401, 403, 404 | 프론트·PG에는 merchant order ID를 노출한다. |
| API-PAY-06 결제 시도 생성 | POST | `/orders/{merchant_order_id}/payment-attempts` | Auth, Idempotency-Key; pg_provider, return_url | `201 payment_attempt_created`; id, checkout 정보 | 401, 403, 404, 409, 422, 502 | 주문 1:N 결제 시도로 재시도를 표현한다. |
| API-PAY-07 PG 웹훅 | POST | `/payments/webhooks/{provider}` | PG 서명과 원문 payload | `200 webhook_processed` | 400, 401, 409 | 승인·주문·PURCHASE 원장을 한 번만 반영한다. |
| API-PAY-08 환불 요청 **잠정** | POST | `/orders/{merchant_order_id}/refund` | Auth, Idempotency-Key; reason, 선택 amount | `202 refund_accepted` | 400, 401, 403, 404, 409, 422, 502 | 환불 요청 저장 구조가 없어 DEC-10/16 확정 전 구현 불가다. |

| 처리 | type | credit_delta | reserved_delta |
|---|---|---:|---:|
| 생성 요청 예약 | `RESERVE` | 0 | +1 |
| 생성 성공 확정 | `CONSUME` | -1 | -1 |
| 생성 실패·취소 해제 | `RELEASE` | 0 | -1 |
| 결제 성공 지급 | `PURCHASE` | +상품 수량 | 0 |

각 생성 작업은 `(generation_job_id, type)` 유일 제약으로 같은 원장 처리를 한 번만 기록한다.

## 10. 주요 스키마

```json
{
  "job_id": "job_01J...",
  "job_type": "REGENERATION",
  "guidebook_id": "gb_01J...",
  "status": "PROCESSING",
  "attempt_count": 1,
  "guidebook_version": 3,
  "error": null
}
```

```json
{
  "guidebook_id": "gb_01J...",
  "title": "경주 역사 여행",
  "region": {"id": 47, "administrative_code": "47130", "name": "경주시"},
  "start_date": "2026-10-12",
  "end_date": "2026-10-14",
  "companion": "FRIEND",
  "people_count": 2,
  "content_html": "<article>...</article>",
  "events": [],
  "version": 3
}
```

```json
{
  "credit_balance": 6,
  "reserved_count": 1,
  "available_count": 5,
  "version": 8
}
```

## 11. 도메인 오류 코드

| 오류 코드 | HTTP | message | 의미 |
|---|---:|---|---|
| `COMMON_VALIDATION_ERROR` | 400 | `invalid_request` | 필드·쿼리 검증 실패 |
| `AUTH_TOKEN_REQUIRED` | 401 | `authentication_required` | 인증 토큰 없음 |
| `AUTH_TOKEN_EXPIRED` | 401 | `token_expired` | 토큰 만료 |
| `MEMBER_POLICY_CONSENT_REQUIRED` | 403 | `policy_consent_required` | 필수 약관 미동의(저장 구조 확정 필요) |
| `RESOURCE_FORBIDDEN` | 403 | `forbidden` | 타인 소유 리소스 접근 |
| `CONTENT_NOT_FOUND` | 404 | `content_not_found` | 콘텐츠 없음 |
| `CONTENT_QUERY_TOO_LONG` | 400 | `query_too_long` | 검색어 10자 초과 |
| `GUIDEBOOK_NOT_FOUND` | 404 | `guidebook_not_found` | 가이드북 없음 |
| `GUIDEBOOK_INVALID_PERIOD` | 422 | `invalid_trip_period` | 날짜 역전·7일 초과 |
| `GENERATION_ATTEMPT_EXHAUSTED` | 409 | `generation_attempt_exhausted` | 최대 3회 시도 소진 |
| `GENERATION_ALREADY_COMPLETED` | 409 | `generation_already_completed` | 완료 작업 재시도 |
| `CREDIT_INSUFFICIENT` | 422 | `credit_insufficient` | 사용 가능 생성권 부족 |
| `CREDIT_CONCURRENT_UPDATE` | 409 | `credit_concurrent_update` | 지갑 조건부 갱신 충돌 |
| `SHARE_LINK_UNAVAILABLE` | 404 | `share_link_unavailable` | 공유 링크 미존재 또는 만료 |
| `EVALUATION_NOT_ELIGIBLE` | 422 | `evaluation_not_eligible` | 여행 종료 전 평가 |
| `EVALUATION_ALREADY_SUBMITTED` | 409 | `evaluation_already_submitted` | 제출 완료 평가 변경 |
| `RATING_SCORE_OUT_OF_RANGE` | 422 | `rating_score_out_of_range` | 0~5 범위 위반 |
| `ORDER_AMOUNT_MISMATCH` | 409 | `order_amount_mismatch` | PG 금액·통화 불일치 |
| `PAYMENT_WEBHOOK_INVALID` | 401 | `payment_webhook_invalid` | 웹훅 서명 검증 실패 |
| `REFUND_NOT_ALLOWED` | 422 | `refund_not_allowed` | 환불 정책 불충족 |
| `UPSTREAM_SERVICE_ERROR` | 502 | `upstream_service_error` | AI·PG·관광 API 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | `internal_server_error` | 내부 오류 |

## 12. 비동기 작업과 정합성

### 최초 생성

1. 멱등 키·여행 조건을 검증한다.
2. 지갑의 예약수량을 조건부 증가시키고 `RESERVE` 원장을 기록한다.
3. 백엔드가 `job_...`을 만들고 같은 ID를 AI에 전달한다.
4. 기술 재시도는 같은 작업 ID로 최대 3회 수행한다.
5. 성공 시 가이드북·일정·작업 완료·`CONSUME`를 하나의 트랜잭션으로 반영한다.
6. 최종 실패 시 가이드북을 만들지 않고 `FAILED`와 `RELEASE`를 반영한다.

### 재생성

사용자 재생성은 새 작업·새 예약을 만들지만 기존 `guidebook_id`를 참조한다. 성공 시 기존 본문과 일정을 교체하고 version을 증가시키며, 실패 시 기존 내용을 바꾸지 않는다.

### 결제

서버 상품 정보로 주문 스냅샷을 만든다. PG 웹훅의 서명·거래 ID·금액·통화를 검증하고 승인 상태, 주문 `PAID`, 지갑 증가, `PURCHASE` 원장을 중복 없이 반영한다.

## 13. 인증·권한 기준

- `/auth/**`, 정책 공개 조회, `/shares/{token}`, PG 웹훅 외에는 Bearer 인증이 기본이다.
- 모든 회원 데이터는 토큰의 회원 ID로 소유권을 검증한다.
- 탈퇴 회원과 필수 약관 미동의 회원은 신규 가이드북·평가·주문을 차단한다.
- 공유 미리보기는 개인정보를 제외한 최소 정보만 반환한다.

## 14. 구현 전 결정 사항

| 결정 ID | 영향 | 현재 처리 |
|---|---|---|
| DEC-01 기존 가이드북 취향 | 가이드북 응답 | 저장 컬럼이 없어 기본 응답에서 제외. 필요 시 스냅샷 구조 추가 |
| DEC-02 월 필터 | 콘텐츠 검색 | 기간형 행사에만 적용 |
| DEC-03 랭킹 기간 경계 | 랭킹 | period_start 명시 요청 |
| DEC-04 랭킹 공식 | 랭킹 | m·C·공식 버전을 응답에 포함 |
| DEC-05 평가 | 평가 | 부분 제출, 0.5점 단위, 제출 후 수정 여부 필요 |
| DEC-06 공유 | 공유 | 기본 만료시간·공개 범위 필요 |
| DEC-08 알림 | 알림 | 초기 4, 최대 20. 보관·삭제 정책 필요 |
| DEC-09 생성 | 생성 작업 | 최대 3회는 반영. 타임아웃·사용자 취소 여부 필요 |
| DEC-10 환불 | 환불 | 사용분·부분 환불·기한 필요 |
| DEC-11 탈퇴 | 회원 | 보존·파기·유예 정책 필요 |
| DEC-12 재생성 영향 | 공유/PDF/평가 | 기존 산출물의 version 처리 필요 |
| DEC-13 약관 저장 구조 | MEM-12~14 | `policies`, `member_consents`가 없음 |
| DEC-14 평가 임시 저장 | RNK-03~06 | 제출 전 초안과 기존 최종 평가를 분리할 수 없음 |
| DEC-15 PDF 작업 저장 | GDE-13~14 | `export_jobs`와 파일 만료·소유권 구조가 없음 |
| DEC-16 환불 작업 저장 | PAY-08 | 요청·금액·사유·멱등 키 저장 구조가 없음 |
| DEC-17 생성 취소 | 생성/원장 | 요구사항은 RELEASE를 말하지만 작업 enum에 CANCELED가 없음 |
| DEC-18 가이드북 삭제 | 가이드북 | 정책 확정 전 DELETE API 미제공 |

## 15. 셀프 리뷰

- [ ] 각 API가 FR/BR과 연결되는가?
- [ ] Body 필드가 실제 테이블 또는 명시된 파생값으로 저장 가능한가?
- [ ] 생성 상태가 `PENDING/PROCESSING/COMPLETED/FAILED`로 통일됐는가?
- [ ] 재생성 성공 시 같은 가이드북 ID와 증가한 version을 반환하는가?
- [ ] 기술 재시도와 사용자 재생성을 구분해 중복 차감을 막는가?
- [ ] `score=0`, `score=null`, 미응답을 구분하는가?
- [ ] 지갑과 원장이 같은 트랜잭션에서 변경되는가?
- [ ] 잠정 API의 저장 구조가 확정되기 전 구현 완료로 표시되지 않았는가?
