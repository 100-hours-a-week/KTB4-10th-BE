# V3 API 명세서

> 기준 요구사항: [KTB4-10th-BE V3 요구사항정의서](https://github.com/100-hours-a-week/KTB4-10th-BE/blob/main/docs/%EC%9A%94%EA%B5%AC%EC%82%AC%ED%95%AD%EC%A0%95%EC%9D%98%EC%84%9C.md)  
> 기준일: 2026-09-01  
> 문서 상태: 2단계 API 설계 초안  
> API Prefix: `/api/v1`

## 1. 문서 목적

이 문서는 V3의 5개 도메인과 사용자 시나리오를 REST API로 변환한 명세서다. 엔드포인트, HTTP 메서드, 요청 파라미터·본문, 응답 구조, 상태 코드, 오류 코드와 설계 근거를 한 문서에 포함한다.

제공된 요청·응답 템플릿에 `인증/멱등성`, `오류`, `요구사항`, `설계 근거` 열을 확장했으므로 별도의 API 명세 문서를 추가로 만들 필요는 없다. 설계 근거도 엔드포인트와 떨어진 별도 문서보다 같은 표에서 확인하는 편이 리뷰와 추적에 유리하다.

## 2. API 설계 원칙

1. URL은 동사보다 복수형 리소스 명사를 사용한다. 예: `/guidebooks`, `/orders`.
2. `GET`은 조회, `POST`는 서버가 ID를 발급하는 생성·명령, `PUT`은 식별 가능한 하위 리소스 전체 교체, `PATCH`는 일부 속성 변경, `DELETE`는 삭제에 사용한다.
3. 사용자 리소스는 토큰의 회원 ID를 기준으로 하며 클라이언트가 임의의 `member_id`를 보내지 않는다.
4. AI 생성·PDF 생성·결제처럼 즉시 끝나지 않는 작업은 `202 Accepted`와 작업 ID를 반환하고 상태 조회 API를 제공한다.
5. 생성 요청, 재생성, 가져오기, 평가 제출, 주문·환불에는 `Idempotency-Key`를 사용한다.
6. 관광 콘텐츠·가이드북·알림 목록은 페이지네이션을 필수로 한다.
7. 동일 응답 envelope와 오류 구조를 전 API에서 사용한다.
8. 가이드북 생성권 예약·확정·해제는 공개 API로 노출하지 않고, 가이드북 생성 애플리케이션 서비스가 DOM-05를 내부 호출한다.
9. 알림은 DOM-01 회원의 하위 책임으로 관리한다. 다른 도메인의 사건을 받아 알림을 생성하되 알림·푸시 실패가 원본 업무를 롤백하지 않는다.

## 3. 공통 규약

### 3.1 공통 헤더

| 헤더 | 필수 | 적용 | 설명 |
|---|---:|---|---|
| `Authorization: Bearer {access_token}` | 조건부 | 인증 API·공개 공유 미리보기·PG 웹훅 제외 | 로그인 회원 식별 |
| `Content-Type: application/json` | Body가 있으면 필수 | JSON 요청 | 요청 형식 |
| `Accept-Language` | 선택 | 전체 | `ko-KR`, `en-US`; 없으면 회원 설정 또는 기본 언어 |
| `Idempotency-Key` | 조건부 필수 | 생성·재생성·가져오기·평가 제출·주문·환불 | UUID 권장, 동일 키는 동일 결과 반환 |
| `X-Request-Id` | 선택 | 전체 | 클라이언트 추적 ID; 없으면 서버 생성 |

### 3.2 성공 응답

```json
{
  "message": "content_list_success",
  "data": {},
  "meta": {
    "request_id": "req_01J..."
  }
}
```

목록 응답은 `data.items`와 페이지 정보를 함께 반환한다.

```json
{
  "message": "guidebook_list_success",
  "data": {
    "items": [],
    "page": {
      "next_cursor": "eyJpZCI6MTAwfQ",
      "has_next": false
    }
  },
  "meta": {
    "request_id": "req_01J..."
  }
}
```

### 3.3 오류 응답

```json
{
  "message": "invalid_request",
  "data": null,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "details": [
      {
        "field": "end_date",
        "reason": "must_be_on_or_after_start_date"
      }
    ],
    "trace_id": "req_01J..."
  }
}
```

- `message`: 프론트 분기와 로그에 사용하는 안정적인 영문 메시지 키
- `error.code`: 도메인까지 포함하는 세부 오류 코드
- `error.details`: 필드 오류 등 선택 정보
- `trace_id`: 서버 로그 추적값
- 사용자 노출 한글 문구는 프론트의 다국어 리소스에서 `message` 또는 `error.code`에 매핑한다.

### 3.4 공통 HTTP 상태 코드

| 상태 | 의미 | 대표 상황 |
|---:|---|---|
| `200 OK` | 조회·변경 성공 | 목록, 상세, PATCH, PUT |
| `201 Created` | 리소스 생성 성공 | 주문, 공유 링크, 평가 세션 |
| `202 Accepted` | 비동기 작업 접수 | AI 생성, PDF 내보내기, 환불 처리 |
| `204 No Content` | 본문 없는 성공 | 로그아웃, 관심 장소 해제, 삭제 |
| `400 Bad Request` | JSON 형식·파라미터 오류 | 날짜 역전, 검색어 초과 |
| `401 Unauthorized` | 인증 없음·만료 | 토큰 누락·만료 |
| `403 Forbidden` | 권한·상태상 불가 | 타인 리소스, 탈퇴 회원, 필수 약관 미동의 |
| `404 Not Found` | 리소스 없음 | 콘텐츠·가이드북·링크 없음 |
| `409 Conflict` | 현재 상태·유일성 충돌 | 중복 가져오기, 이미 제출, 잔액 예약 충돌 |
| `410 Gone` | 있었으나 더 이상 유효하지 않음 | 만료된 공유 링크 |
| `422 Unprocessable Content` | 문법은 맞지만 비즈니스 규칙 위반 | 생성권 부족, 기간 7일 초과 |
| `429 Too Many Requests` | 호출 제한 | OAuth·검색·생성 요청 제한 |
| `500 Internal Server Error` | 내부 오류 | 예상하지 못한 서버 오류 |
| `502 Bad Gateway` | 외부 연동 오류 | TourAPI·PG·AI 오류 |
| `503 Service Unavailable` | 일시적 서비스 불가 | 외부 연동 장기 장애·점검 |

### 3.5 페이지네이션·정렬

| 목록 | 방식 | 기본값 | 이유 |
|---|---|---|---|
| 관광 콘텐츠 검색 | `page`, `size` | `page=1`, `size=5`, 최대 20 | 화면이 번호 페이지를 표시하므로 offset 페이지가 자연스러움 |
| 지도 콘텐츠 | 영역/반경 + `limit` | 최대 마커 수 서버 설정 | 전체 페이지보다 현재 화면 렌더링 범위가 중요함 |
| 가이드북 | `cursor`, `size` | `size=20`, `created_at DESC` | 새 가이드북이 추가돼도 중복·누락이 적음 |
| 알림 | `cursor`, `size` | 초기값은 기획 확정 전 4, 최대 20 | 4개/5개 불일치를 API 파라미터로 흡수 가능 |
| 원장 | `cursor`, `size` | `size=20`, `created_at DESC` | 변경 이력은 append-only이며 최신순 탐색 |
| 랭킹 | `page`, `size` | `page=1`, `size=20` | 고정된 스냅샷 순위를 번호로 탐색 |

## 4. 엔드포인트 요약

| 도메인 | API ID 범위 | 주요 URL |
|---|---|---|
| DOM-01 회원 | API-MEM-01~14, API-NOT-01~05 | `/auth`, `/members/me`, `/preference-options`, `/policies`, `/notifications`, `/push-preferences` |
| DOM-02 관광 콘텐츠·탐색 | API-CON-01~06 | `/contents`, `/map/contents`, `/members/me/favorites` |
| DOM-03 가이드북 | API-GDE-01~16 | `/guidebooks`, `/guidebook-generations`, `/shares`, `/guidebook-exports` |
| DOM-04 평가·랭킹 | API-RNK-01~07 | `/rating-sessions`, `/rankings` |
| DOM-05 생성권·결제 | API-PAY-01~09 | `/entitlements`, `/products`, `/orders`, `/refunds` |

## 5. DOM-01 회원 API

회원·인증, 기본 취향, 언어·약관뿐 아니라 회원에게 귀속되는 인앱 알림·읽음 상태·푸시 수신 설정을 함께 관리한다. 알림 처리 코드는 회원 핵심 인증 로직과 분리된 내부 하위 모듈로 구성한다.

| API ID·요청 | Method | URL | Request | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-MEM-01 소셜 로그인/가입 | POST | `/auth/oauth/{provider}/login` | Path: `provider=KAKAO\|GOOGLE`<br>Body: `authorization_code`, `redirect_uri`, `device_id`, `policy_consents[]` | `200 login_success` 또는 `201 member_created`; `access_token`, `refresh_token`, `member`, `onboarding_required` | 400, 401, 409, 502 | FR-MEM-01, BR-MEM-01~02. OAuth 로그인과 가입을 별도 API로 나누지 않고 공급자 식별자 조회 결과로 분기해 중복 회원 생성을 막는다. |
| API-MEM-02 토큰 갱신 | POST | `/auth/token/refresh` | Body: `refresh_token` | `200 token_refresh_success`; 새 토큰 쌍 | 401, 409 | FR-MEM-02. 갱신 토큰 회전을 지원하고 탈취 토큰 재사용을 차단한다. |
| API-MEM-03 로그아웃 | POST | `/auth/logout` | Auth<br>Body: `refresh_token`, `all_devices=false` | `204` | 401 | FR-MEM-02. 서버 세션 폐기가 필요하므로 단순 클라이언트 토큰 삭제로 끝내지 않는다. |
| API-MEM-04 내 회원 조회 | GET | `/members/me` | Auth | `200 member_get_success`; 프로필·온보딩·언어·미읽음 수 요약 | 401 | FR-MEM-02,04. 마이페이지의 조합 응답은 제공하되 원본 데이터 소유권은 각 도메인에 둔다. |
| API-MEM-05 프로필 수정 | PATCH | `/members/me` | Auth<br>Body: 수정할 `nickname`, `profile_image_url`만 전달 | `200 member_update_success`; 변경된 member | 400, 401, 409 | FR-MEM-04. 전체 교체가 아니라 일부 필드만 변경하므로 PATCH를 사용한다. |
| API-MEM-06 회원 탈퇴 | DELETE | `/members/me` | Auth<br>Body: `confirmation="회원 탈퇴"` | `202 withdrawal_accepted`; `withdrawal_job_id` | 400, 401, 409 | FR-MEM-02, BR-MEM-03/09. 여러 도메인 데이터 처리 가능성이 있어 비동기 접수로 설계한다. |
| API-MEM-07 취향 옵션 조회 | GET | `/preference-options` | Query: `language` 선택 | `200 preference_option_list_success`; 카테고리·중분류·스타일 | 400 | FR-MEM-03. 회원 선택과 별개인 기준 데이터를 조회한다. |
| API-MEM-08 기본 취향 조회 | GET | `/members/me/preferences` | Auth | `200 preference_get_success`; `category_ids`, `subcategory_ids`, `style_ids`, `updated_at` | 401, 404 | FR-MEM-03, BR-MEM-06. 회원별 하나의 현재 기본 취향을 반환한다. |
| API-MEM-09 기본 취향 저장 | PUT | `/members/me/preferences` | Auth<br>Body: 전체 `category_ids`, `subcategory_ids`, `style_ids` | `200 preference_update_success`; 저장된 전체 취향 | 400, 401, 422 | FR-MEM-03, BR-MEM-06~07. 하나의 프로필 전체 선택값을 교체하므로 PUT을 사용한다. |
| API-MEM-10 회원 설정 조회 | GET | `/members/me/settings` | Auth | `200 member_setting_get_success`; `language` | 401 | FR-MEM-04. 언어 설정을 반환하며 푸시 설정은 같은 DOM-01의 알림 하위 모듈이 별도 API로 제공한다. |
| API-MEM-11 회원 설정 수정 | PATCH | `/members/me/settings` | Auth<br>Body: `language` | `200 member_setting_update_success` | 400, 401 | FR-MEM-04, BR-MEM-08. 언어만 부분 변경한다. |
| API-MEM-12 정책 목록 | GET | `/policies` | Query: `type`, `current_only=true` | `200 policy_list_success`; ID·type·version·effective_at | 400 | FR-MEM-05. 로그인 화면에서도 조회하므로 공개 GET이다. 결제 정책은 동일 조회 경로로 표현해도 소유 규칙은 DOM-05다. |
| API-MEM-13 정책 상세 | GET | `/policies/{policy_id}` | Path: `policy_id` | `200 policy_get_success`; 본문·version·시행일 | 404 | FR-MEM-05. URL에 버전 의미를 넣기보다 정책 리소스 ID로 조회한다. |
| API-MEM-14 약관 동의 저장 | POST | `/members/me/consents` | Auth<br>Body: `consents[{policy_id, agreed}]` | `201 consent_saved`; 저장된 문서 버전·시각 | 400, 401, 422 | BR-MEM-04~05. 동의 이력을 append-only 기록으로 남긴다. |
| API-NOT-01 알림 목록 | GET | `/notifications` | Auth<br>Query: `cursor`, `size`, `unread_only=true` | `200 notification_list_success`; 알림·cursor·unread_count | 401 | FR-NOT-01, BR-NOT-03/06. DOM-01 내부 알림 하위 책임이며 회원 소유권을 토큰으로 제한한다. |
| API-NOT-02 개별 읽음 | PATCH | `/notifications/{notification_id}` | Auth<br>Body: `read=true` | `200 notification_read` | 401, 403, 404 | FR-NOT-02. 회원 소유 알림의 읽음 속성 일부만 변경하므로 PATCH를 사용한다. |
| API-NOT-03 모두 읽음 | POST | `/notifications/read-all` | Auth<br>Body: 선택 `before` | `200 notifications_read_all`; `updated_count` | 401 | BR-NOT-04. 요청 시점 이전의 회원 미읽음 알림을 처리하는 도메인 명령이다. |
| API-NOT-04 푸시 설정 조회 | GET | `/push-preferences` | Auth | `200 push_preference_get_success`; `enabled` | 401 | FR-NOT-03. 회원에게 귀속되며 인앱 알림 저장과 독립된 전송 설정이다. |
| API-NOT-05 푸시 설정 변경 | PATCH | `/push-preferences` | Auth<br>Body: `enabled` | `200 push_preference_update_success` | 400, 401 | DOM-01 내부 알림 하위 책임이다. 실패 시 클라이언트가 이전 토글 상태로 복원할 수 있다. |

### 5.1 소셜 로그인 예시

```http
POST /api/v1/auth/oauth/KAKAO/login
Content-Type: application/json
```

```json
{
  "authorization_code": "oauth_code_from_kakao",
  "redirect_uri": "app://oauth/callback",
  "device_id": "device_01J...",
  "policy_consents": [
    {"policy_id": "pol_terms_v3", "agreed": true},
    {"policy_id": "pol_privacy_v3", "agreed": true}
  ]
}
```

```json
{
  "message": "member_created",
  "data": {
    "access_token": "eyJ...",
    "refresh_token": "eyJ...",
    "expires_in": 3600,
    "member": {
      "member_id": "mem_01J...",
      "nickname": "여행자1",
      "profile_image_url": null,
      "language": "ko-KR"
    },
    "onboarding_required": true
  },
  "meta": {"request_id": "req_01J..."}
}
```

## 6. DOM-02 관광 콘텐츠·탐색 API

| API ID·요청 | Method | URL | Request | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-CON-01 콘텐츠 검색 | GET | `/contents` | Query: `q`≤10자, `region_code`, `month`, `category`, `page=1`, `size=5`, `sort` | `200 content_list_success`; `items`, `page_number`, `total_pages`, `total_items` | 400, 502 | FR-CON-01~03, BR-CON-02~06. 필터는 AND이며 번호 페이지 UI를 지원한다. GET은 원본을 변경하지 않는다. |
| API-CON-02 콘텐츠 상세 | GET | `/contents/{content_id}` | Path: `content_id` | `200 content_get_success`; 상세·이미지·주소·기간·출처·갱신일 | 404, 502 | FR-CON-01. 가이드북·평가도 동일 콘텐츠 ID를 참조한다. |
| API-CON-03 지도 콘텐츠 | GET | `/map/contents` | Query: `latitude`, `longitude`, `radius_m=3000` 또는 `sw_lat`, `sw_lng`, `ne_lat`, `ne_lng`; `zoom`, `category`, `limit` | `200 map_content_success`; `markers`, `clusters`, `has_more` | 400, 422, 502 | FR-CON-04, NFR-PERF-02. 지도는 페이지 번호보다 현재 영역과 줌이 조회 기준이다. |
| API-CON-04 관심 장소 목록 | GET | `/members/me/favorites` | Auth<br>Query: `cursor`, `size` | `200 favorite_list_success`; 콘텐츠 카드·cursor | 401 | FR-CON-05. 회원 소유 관계를 조회한다. |
| API-CON-05 관심 장소 등록 | PUT | `/members/me/favorites/{content_id}` | Auth, Body 없음 | `200 favorite_saved`; `content_id`, `favorited=true` | 401, 404 | FR-CON-05, BR-CON-07. 경로로 식별되는 관계를 원하는 상태로 만들기 때문에 재요청에 안전한 PUT을 사용한다. |
| API-CON-06 관심 장소 해제 | DELETE | `/members/me/favorites/{content_id}` | Auth | `204` | 401, 404 | FR-CON-05. 이미 해제된 상태도 204로 처리해 멱등성을 유지한다. |

### 6.1 콘텐츠 검색 예시

```http
GET /api/v1/contents?q=야행&region_code=41&month=8&category=EVENT_FESTIVAL&page=1&size=5
```

```json
{
  "message": "content_list_success",
  "data": {
    "items": [
      {
        "content_id": "con_01J...",
        "content_type": "EVENT_FESTIVAL",
        "title": "수원 국가유산 야행",
        "thumbnail_url": "https://cdn.example.com/event.webp",
        "period": {"start_date": "2026-08-14", "end_date": "2026-08-30"},
        "region": {"sido": "경기도", "sigungu": "수원시"},
        "event_status": "ENDED",
        "favorited": false
      }
    ],
    "page": {"number": 1, "size": 5, "total_pages": 1, "total_items": 1}
  },
  "meta": {"request_id": "req_01J..."}
}
```

## 7. DOM-03 가이드북 API

| API ID·요청 | Method | URL | Request | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-GDE-01 가이드북 목록 | GET | `/guidebooks` | Auth<br>Query: `cursor`, `size`, `sort=created_at,desc` | `200 guidebook_list_success`; 카드 목록·cursor | 401 | FR-GDE-03. 계속 추가되는 목록이므로 cursor를 사용한다. 정렬 최종값은 기획 확인이 필요하다. |
| API-GDE-02 최초 생성 접수 | POST | `/guidebook-generations` | Auth, Idempotency-Key<br>Body: `region_code`, `detail_region_code`, `start_date`, `end_date`, `companion_type`, `party_size` | `202 guidebook_generation_accepted`; `job_id`, `status=QUEUED`, `entitlement_reservation_status=RESERVED` | 400, 401, 409, 422, 502 | FR-GDE-01~02, BR-GDE-01~05. 생성권 예약 성공 후에만 AI 작업을 접수한다. 기본 취향은 서버가 DOM-01에서 조회한다. |
| API-GDE-03 생성 상태 조회 | GET | `/guidebook-generations/{job_id}` | Auth | `200 generation_job_get_success`; 상태·단계·진행률·attempt·결과 ID·오류 | 401, 404 | NFR-PERF-03. 앱 이탈·재접속 후 복구와 폴링/SSE 대안의 공통 기반이다. |
| API-GDE-04 생성 취소 | POST | `/guidebook-generations/{job_id}/cancel` | Auth | `202 generation_cancel_accepted` | 401, 404, 409 | 취소는 리소스 삭제가 아니라 상태 전이 명령이므로 POST action을 사용한다. 예약은 서버 내부에서 해제한다. |
| API-GDE-05 가이드북 상세 | GET | `/guidebooks/{guidebook_id}` | Auth | `200 guidebook_get_success`; 제목·조건·취향 표시값·상태·공유 가능 여부 | 401, 403, 404 | 회원 소유권을 검증한다. |
| API-GDE-06 제목 수정 | PATCH | `/guidebooks/{guidebook_id}` | Auth<br>Body: `title`≤15자 | `200 guidebook_update_success` | 400, 401, 403, 404 | 제목 일부만 변경하므로 PATCH를 사용하고 AI 재생성을 일으키지 않는다. |
| API-GDE-07 일정 조회 | GET | `/guidebooks/{guidebook_id}/itinerary` | Auth | `200 itinerary_get_success`; days/items/content summary | 401, 403, 404 | FR-GDE-06. 일정은 가이드북 하위 리소스다. |
| API-GDE-08 일정 전체 저장 | PUT | `/guidebooks/{guidebook_id}/itinerary` | Auth<br>Body: 전체 `days[{date, items[{content_id, sequence, planned_time}]}]` | `200 itinerary_update_success`; 검증된 전체 일정 | 400, 401, 403, 404, 422 | BR-GDE-06~07. 순서 유일성과 연속성을 원자 검증하기 위해 전체 표현 교체 PUT을 사용한다. |
| API-GDE-09 가이드북 삭제 | DELETE | `/guidebooks/{guidebook_id}` | Auth | `204` | 401, 403, 404, 409 | 소유자만 삭제한다. 결제·평가 보존 정책에 따라 내부 soft delete 가능하다. |
| API-GDE-10 재생성 접수 | POST | `/guidebooks/{guidebook_id}/regenerations` | Auth, Idempotency-Key<br>Body: `feedback`≤200자, 선택 `title` | `202 regeneration_accepted`; 새 `job_id`, `source_guidebook_id` | 400, 401, 403, 404, 409, 422 | FR-GDE-07, BR-GDE-13. 원본을 덮어쓰지 않고 새 생성 작업·가이드북을 만든다. 새 생성권을 예약한다. |
| API-GDE-11 공유 링크 발급 | POST | `/guidebooks/{guidebook_id}/shares` | Auth, Idempotency-Key<br>Body: 선택 `expires_in_seconds` | `201 share_created`; `share_url`, `expires_at` | 401, 403, 404, 422 | FR-GDE-04. 난수 원문 대신 해시를 저장하며 만료 기본값은 DEC-06 확정 필요다. |
| API-GDE-12 공유 미리보기 | GET | `/shares/{share_token}` | 공개 또는 선택 Auth | `200 share_preview_success`; 최소 가이드북 정보·`imported` | 404, 410 | 로그인 전 미리보기 범위는 DEC-06에서 확정한다. 만료는 410으로 구분한다. |
| API-GDE-13 공유 가이드북 가져오기 | POST | `/shares/{share_token}/imports` | Auth, Idempotency-Key | `201 guidebook_imported`; 복사본 `guidebook_id`<br>중복이면 `200 already_imported`와 기존 ID | 401, 404, 410, 409 | FR-GDE-05, BR-GDE-09~10. 동일 회원·링크 유일 제약으로 중복 복사본을 막는다. |
| API-GDE-14 내보내기 요청 | POST | `/guidebooks/{guidebook_id}/exports` | Auth, Idempotency-Key<br>Body: `format=PDF` | `202 export_accepted`; `export_id`, `status=QUEUED` | 401, 403, 404, 409 | FR-GDE-06, BR-GDE-11. PDF 실패가 원본 상태를 바꾸지 않도록 별도 작업으로 둔다. |
| API-GDE-15 내보내기 상태 | GET | `/guidebook-exports/{export_id}` | Auth | `200 export_get_success`; 상태·다운로드 URL·만료·오류 | 401, 403, 404 | 클라이언트가 다운로드 진행과 재시도를 표시할 수 있다. |
| API-GDE-16 HTML 뷰어 | GET | `/guidebooks/{guidebook_id}/viewer` | Auth | `200 viewer_get_success`; `content_html`, `content_version`, `offline_assets[]` | 401, 403, 404, 503 | 저장된 가이드북의 오프라인 캐시를 위해 버전과 필수 자산을 제공한다. |

### 7.1 생성 접수 예시

```http
POST /api/v1/guidebook-generations
Authorization: Bearer {access_token}
Idempotency-Key: 4ec7b362-1e26-4b88-a498-c8c13b45fa41
Content-Type: application/json
```

```json
{
  "region_code": "47",
  "detail_region_code": "47130",
  "start_date": "2026-10-12",
  "end_date": "2026-10-14",
  "companion_type": "FRIEND",
  "party_size": 2
}
```

```json
{
  "message": "guidebook_generation_accepted",
  "data": {
    "job_id": "gen_01J...",
    "status": "QUEUED",
    "generation_type": "INITIAL",
    "entitlement_reservation_status": "RESERVED",
    "created_at": "2026-09-01T10:00:00+09:00"
  },
  "meta": {"request_id": "req_01J..."}
}
```

### 7.2 생성 상태 예시

```json
{
  "message": "generation_job_get_success",
  "data": {
    "job_id": "gen_01J...",
    "status": "PROCESSING",
    "step": "CHECK_EVENTS",
    "progress": 66,
    "attempt_count": 1,
    "source_guidebook_id": null,
    "result_guidebook_id": null,
    "error": null
  },
  "meta": {"request_id": "req_01J..."}
}
```

## 8. DOM-04 평가·랭킹 API

| API ID·요청 | Method | URL | Request | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-RNK-01 평가 대기 목록 | GET | `/rating-sessions/pending` | Auth<br>Query: `cursor`, `size` | `200 pending_rating_list_success`; 종료 여행·자동 노출 여부 | 401 | FR-RNK-01. 실제 방문 검증 없이 종료된 가이드북과 세션 상태로 판단한다. |
| API-RNK-02 평가 세션 시작/재개 | POST | `/guidebooks/{guidebook_id}/rating-session` | Auth, Idempotency-Key | `201 rating_session_created` 또는 `200 rating_session_resumed`; 세션 ID | 401, 403, 404, 409, 422 | BR-RNK-01~02/07. 회원·가이드북 조합당 하나의 세션을 유지한다. |
| API-RNK-03 평가 세션 조회 | GET | `/rating-sessions/{session_id}` | Auth | `200 rating_session_get_success`; 일정순 장소·RATED/SKIPPED/UNANSWERED | 401, 403, 404 | 이전·다음 이동 중 값을 복원한다. |
| API-RNK-04 장소 평가 임시 저장 | PUT | `/rating-sessions/{session_id}/places/{content_id}` | Auth<br>Body: `state=RATED`, `score=0..5` 또는 `state=SKIPPED`, `score=null` | `200 rating_answer_saved` | 400, 401, 403, 404, 409, 422 | BR-RNK-03~06. 식별 가능한 장소 답변 전체 상태를 교체하므로 PUT이며 제출 전 재호출 가능하다. |
| API-RNK-05 평가 다음에 하기 | POST | `/rating-sessions/{session_id}/defer` | Auth | `200 rating_session_deferred`; `status=DEFERRED` | 401, 403, 404, 409 | 자동 모달 재노출만 중단하고 수동 재진입은 허용한다. |
| API-RNK-06 평가 제출 | POST | `/rating-sessions/{session_id}/submit` | Auth, Idempotency-Key<br>Body 없음 | `200 rating_submitted`; 제출 수·건너뛴 수 | 401, 403, 404, 409, 422 | FR-RNK-02, BR-RNK-07~08. 제출 후 원본을 확정하고 중복 집계하지 않는다. 부분 제출 가능 여부는 DEC-05 확정 필요다. |
| API-RNK-07 랭킹 조회 | GET | `/rankings` | Query: `period_type=DAILY\|WEEKLY\|MONTHLY`, `period_start`, `region_code`, `page`, `size` | `200 ranking_list_success`; snapshot 기준·rank·가중/평균 점수·평가 수·콘텐츠 | 400, 404 | FR-RNK-03~04. 스냅샷을 조회해 매 요청 전체 계산을 피하고 안정 순위를 제공한다. |

### 8.1 장소 평가 저장 예시

```json
{
  "state": "RATED",
  "score": 0
}
```

0점은 유효 평가다. 건너뛰기는 다음처럼 별도 상태로 보낸다.

```json
{
  "state": "SKIPPED",
  "score": null
}
```

## 9. DOM-05 생성권·결제 API

| API ID·요청 | Method | URL | Request | 성공 응답 | 오류 | 요구사항·설계 근거 |
|---|---|---|---|---|---|---|
| API-PAY-01 생성권 잔액 | GET | `/entitlements` | Auth | `200 entitlement_get_success`; `ledger_balance`, `reserved`, `available` | 401 | FR-PAY-01, BR-PAY-01. 화면에는 사용 가능량을 표시하되 원장·예약을 구분한다. |
| API-PAY-02 생성권 원장 | GET | `/entitlement-ledger` | Auth<br>Query: `cursor`, `size`, `reason_type` | `200 entitlement_ledger_list_success`; 증감·사유·시각 | 401 | 감사·분쟁 대응용 append-only 이력이다. |
| API-PAY-03 상품 목록 | GET | `/products` | Query: `type=GUIDEBOOK_ENTITLEMENT`, `active=true` | `200 product_list_success`; 서버 상품 ID·수량·가격·통화 | 502 | FR-PAY-03, BR-PAY-05. 클라이언트 가격은 표시용이며 주문 시 서버가 다시 검증한다. |
| API-PAY-04 주문 생성 | POST | `/orders` | Auth, Idempotency-Key<br>Body: `product_id` | `201 order_created`; `order_id`, 서버 금액·통화·status | 401, 404, 409, 422 | FR-PAY-03. 같은 구매 탭/재전송으로 주문이 중복 생성되지 않게 한다. |
| API-PAY-05 주문 조회 | GET | `/orders/{order_id}` | Auth | `200 order_get_success`; 주문·최근 결제 시도·지급 상태 | 401, 403, 404 | PG 복귀 후 서버 확정 상태를 재조회한다. |
| API-PAY-06 결제 시도 생성 | POST | `/orders/{order_id}/payment-attempts` | Auth, Idempotency-Key<br>Body: `pg_provider`, `return_url` | `201 payment_attempt_created`; `payment_attempt_id`, `checkout_url` 또는 PG payload | 401, 403, 404, 409, 422, 502 | 주문과 시도를 1:N으로 두어 실패 후 재시도를 표현한다. |
| API-PAY-07 PG 웹훅 | POST | `/payments/webhooks/{provider}` | PG 서명 헤더<br>Provider payload | `200 webhook_processed` 또는 이미 처리된 경우 동일 200 | 400, 401, 409 | BR-PAY-05~08, NFR-SEC-04. 서명·거래 ID·금액·상태 검증 후 결제·원장을 한 번만 반영한다. 외부 전용 API다. |
| API-PAY-08 환불 요청 | POST | `/orders/{order_id}/refunds` | Auth, Idempotency-Key<br>Body: `reason`, 선택 `amount` | `202 refund_accepted`; `refund_id`, `status=PENDING` | 400, 401, 403, 404, 409, 422, 502 | FR-PAY-04. PG 환불 완료 전 생성권을 먼저 회수하지 않는다. 정책 DEC-10 확정 필요다. |
| API-PAY-09 환불 조회 | GET | `/refunds/{refund_id}` | Auth | `200 refund_get_success`; 상태·금액·원장 반영 여부 | 401, 403, 404 | 비동기 PG 처리 결과와 생성권 회수 상태를 복구한다. |

### 9.1 주문 생성 예시

```http
POST /api/v1/orders
Authorization: Bearer {access_token}
Idempotency-Key: f8db23bb-18bb-44aa-a29f-86ae84c5f92e
Content-Type: application/json
```

```json
{
  "product_id": "prd_guide_10"
}
```

```json
{
  "message": "order_created",
  "data": {
    "order_id": "ord_01J...",
    "product": {
      "product_id": "prd_guide_10",
      "name": "AI 가이드북 생성권 10회",
      "quantity": 10
    },
    "amount": 9900,
    "currency": "KRW",
    "status": "CREATED"
  },
  "meta": {"request_id": "req_01J..."}
}
```

## 10. 주요 스키마

### 10.1 GenerationJob

```json
{
  "job_id": "gen_01J...",
  "generation_type": "INITIAL",
  "source_guidebook_id": null,
  "status": "QUEUED",
  "step": null,
  "progress": 0,
  "attempt_count": 0,
  "result_guidebook_id": null,
  "error": null,
  "created_at": "2026-09-01T10:00:00+09:00",
  "updated_at": "2026-09-01T10:00:00+09:00"
}
```

`status`: `QUEUED | PROCESSING | SUCCEEDED | FAILED | CANCELED`

`step`: `FIND_PLACES | CHECK_EVENTS | BUILD_ITINERARY`

### 10.2 GuidebookSummary

```json
{
  "guidebook_id": "gde_01J...",
  "source_guidebook_id": null,
  "title": "경주",
  "thumbnail_url": "https://cdn.example.com/guidebook.webp",
  "start_date": "2026-10-12",
  "end_date": "2026-10-14",
  "duration_text": "2박 3일",
  "party_size": 2,
  "companion_type": "FRIEND",
  "preference_labels": ["힐링", "문화"],
  "created_at": "2026-09-01T10:01:20+09:00"
}
```

### 10.3 RatingSession

```json
{
  "session_id": "rts_01J...",
  "guidebook_id": "gde_01J...",
  "status": "IN_PROGRESS",
  "items": [
    {
      "content_id": "con_01J...",
      "sequence": 1,
      "title": "경포해변",
      "answer_state": "RATED",
      "score": 5
    }
  ]
}
```

### 10.4 RankingEntry

```json
{
  "rank": 1,
  "content_id": "con_01J...",
  "weighted_rating": 4.72,
  "average_rating": 4.81,
  "rating_count": 328,
  "content": {
    "title": "경포해변",
    "content_type": "ATTRACTION",
    "region": {"sido": "강원특별자치도", "sigungu": "강릉시"}
  }
}
```

### 10.5 Entitlement

```json
{
  "ledger_balance": 6,
  "reserved": 1,
  "available": 5,
  "updated_at": "2026-09-01T10:00:00+09:00"
}
```

## 11. 도메인 오류 코드

| 오류 코드 | HTTP | message | 의미 |
|---|---:|---|---|
| `COMMON_VALIDATION_ERROR` | 400 | `invalid_request` | 필드·쿼리 검증 실패 |
| `AUTH_TOKEN_REQUIRED` | 401 | `authentication_required` | 인증 토큰 없음 |
| `AUTH_TOKEN_EXPIRED` | 401 | `token_expired` | 액세스·리프레시 토큰 만료 |
| `MEMBER_POLICY_CONSENT_REQUIRED` | 403 | `policy_consent_required` | 현재 필수 약관 미동의 |
| `RESOURCE_FORBIDDEN` | 403 | `forbidden` | 타인 소유 리소스 접근 |
| `CONTENT_NOT_FOUND` | 404 | `content_not_found` | 콘텐츠 없음 |
| `CONTENT_QUERY_TOO_LONG` | 400 | `query_too_long` | 검색어 10자 초과 |
| `LOCATION_PERMISSION_REQUIRED` | 403 | `location_permission_required` | 앱 정책상 위치 권한 필요 |
| `GUIDEBOOK_NOT_FOUND` | 404 | `guidebook_not_found` | 가이드북 없음 |
| `GUIDEBOOK_INVALID_PERIOD` | 422 | `invalid_trip_period` | 날짜 역전·7일 초과 |
| `GENERATION_ALREADY_FINISHED` | 409 | `generation_already_finished` | 완료 작업 취소·재처리 요청 |
| `ENTITLEMENT_INSUFFICIENT` | 422 | `entitlement_insufficient` | 예약 가능한 생성권 부족 |
| `SHARE_LINK_NOT_FOUND` | 404 | `share_link_not_found` | 존재하지 않는 토큰 |
| `SHARE_LINK_EXPIRED` | 410 | `share_link_expired` | 만료 링크 |
| `GUIDEBOOK_ALREADY_IMPORTED` | 409 | `guidebook_already_imported` | 가져오기 중복; 응답에 기존 ID 포함 가능 |
| `RATING_NOT_ELIGIBLE` | 422 | `rating_not_eligible` | 아직 종료되지 않은 여행 |
| `RATING_ALREADY_SUBMITTED` | 409 | `rating_already_submitted` | 제출 완료 세션 변경 시도 |
| `RATING_SCORE_OUT_OF_RANGE` | 422 | `rating_score_out_of_range` | 0~5 범위 위반 |
| `ORDER_AMOUNT_MISMATCH` | 409 | `order_amount_mismatch` | PG/클라이언트 금액 불일치 |
| `PAYMENT_WEBHOOK_INVALID` | 401 | `payment_webhook_invalid` | 웹훅 서명 검증 실패 |
| `REFUND_NOT_ALLOWED` | 422 | `refund_not_allowed` | 환불 정책 불충족 |
| `UPSTREAM_SERVICE_ERROR` | 502 | `upstream_service_error` | AI·PG·관광 API 실패 |
| `INTERNAL_SERVER_ERROR` | 500 | `internal_server_error` | 예기치 않은 서버 오류 |

## 12. 비동기 작업과 정합성

### 12.1 가이드북 생성

1. `POST /guidebook-generations` 요청과 멱등 키를 확인한다.
2. 여행 조건과 회원 기본 취향 존재 여부를 검증한다.
3. DOM-05에서 생성권을 조건부 원자 연산으로 예약한다.
4. GenerationJob을 `QUEUED`로 생성하고 `202`를 반환한다.
5. AI 성공 시 새 Guidebook 저장과 예약 `CONSUMED`를 중복 없이 처리한다.
6. 최종 실패·취소 시 예약을 `RELEASED`로 한 번만 전환한다.
7. 완료 사건으로 알림을 생성하되 알림 실패는 생성 성공을 롤백하지 않는다.

### 12.2 결제

1. 서버 상품 정보로 주문 금액을 고정한다.
2. 주문과 결제 시도를 1:N으로 기록한다.
3. PG 웹훅의 서명·거래 ID·금액·주문 상태를 검증한다.
4. `payment_attempts(pg_provider, pg_transaction_id)` 유일 제약으로 중복 완료를 막는다.
5. 결제 완료와 생성권 원장 반영 사이 실패는 재처리 작업과 운영 알람으로 복구한다.

### 12.3 알림·푸시

1. 가이드북 생성·공유, 평가 등 원본 업무가 성공한 뒤 도메인 사건을 발행한다.
2. DOM-01의 내부 알림 모듈은 `수신 회원 + 사건 ID`를 고유 기준으로 알림을 한 번만 생성한다.
3. 인앱 알림 저장 또는 푸시 전송 실패는 원본 업무를 롤백하지 않는다.
4. 푸시 실패는 재시도 가능 여부와 최종 실패 상태를 별도로 기록한다.

## 13. 인증·권한 기준

- `/auth/**`, 공개 정책 조회, `/shares/{token}`, PG 웹훅 외에는 Bearer 인증이 기본이다.
- 목록·상세·수정·삭제 모두 회원 리소스 소유권을 서버에서 검증한다.
- 탈퇴 회원과 필수 약관 미동의 회원은 신규 가이드북·평가·주문 생성을 차단한다.
- 공유 미리보기는 최소 정보만 공개하며 개인정보와 상세 일정 공개 범위는 DEC-06에서 확정한다.
- PG 웹훅은 회원 인증 대신 공급자 서명을 검증하고 별도 rate limit·IP 정책을 적용할 수 있다.

## 14. 확정이 필요한 API 항목

| 결정 ID | API 영향 | 현재 설계의 임시값 |
|---|---|---|
| DEC-01 기존 가이드북 취향 표시 | GuidebookSummary | 생성 시 표시값을 스냅샷으로 보존하는 안 |
| DEC-02 월 필터 대상 | `GET /contents` | 기간형 행사에만 `month` 적용 |
| DEC-03 랭킹 기간 경계 | `GET /rankings` | `period_start`를 명시적으로 요청 |
| DEC-04 랭킹 m·C·동점 | RankingSnapshot | 스냅샷에 계산 버전·m·C 저장 |
| DEC-05 부분 제출 | 평가 제출 | 모든 장소가 RATED/SKIPPED면 제출 허용하는 안 |
| DEC-06 공유 만료·미리보기 | 공유 API | 만료시간 서버 설정, 공개 미리보기 최소화 |
| DEC-08 알림 초기 수 | 알림 목록 | 기본 4, `size`로 변경 가능, 최대 20 |
| DEC-09 생성 타임아웃 | GenerationJob | 서버 설정값과 오류 코드로 관리 |
| DEC-10 환불 | 환불 API | 정책 미확정으로 운영 전 확정 필수 |
| DEC-11 탈퇴 보존 | 회원 탈퇴 | 비동기 처리와 법정 보존 분리 |

## 15. 셀프 리뷰 체크리스트

- 모든 기능 요구사항 `FR-MEM/CON/GDE/RNK/NOT/PAY`이 최소 하나의 API에 연결되는가?
- GET이 서버 상태를 변경하지 않는가?
- 생성·재생성·가져오기·평가 제출·주문·환불에 멱등성이 있는가?
- 목록 API에 필터·정렬·페이지네이션·빈 결과가 정의됐는가?
- 비동기 작업에 `202`, 작업 ID, 상태 조회, 실패 코드, 복구 경로가 있는가?
- 타인 소유 리소스 접근을 모든 계층에서 막는가?
- 가이드북 저장과 생성권 사용 확정, 결제와 원장 반영의 실패 복구가 가능한가?
- 클라이언트가 보낸 가격·회원 ID·권한을 신뢰하지 않는가?
- 아직 결정되지 않은 기획 항목이 `DEC-*`로 노출되어 있는가?
