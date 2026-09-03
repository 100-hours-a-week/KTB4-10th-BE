# V3 ERD·테이블 설계 근거 검토본

> 문서 성격: **검토용 비권위 초안**
>
> 현재 요구사항정의서·테이블정의서·ERDCloud 스키마에 반영된 현행 설계와 그 근거를 설명하는 보조 문서다.
> **[변경 제안]**은 아직 미확정인 검토 후보다. 확정되면 요구사항·테이블·ERD에 함께 반영한다.

## 1. 목적

PL 리뷰에서 다음 질문에 답할 수 있도록 한다.

- 왜 이 데이터를 별도 테이블로 만들었는가?
- 왜 이 컬럼과 타입, 길이, NULL 여부가 필요한가?
- PK·FK·UNIQUE·CHECK·인덱스가 어떤 오류를 막는가?
- 중복 요청, 실패, 재시도, 탈퇴 상황에서 정합성을 어떻게 지키는가?
- 현행의 단점은 무엇이며 언제 확장하는가?

표기:

- **[현행]** 현재 테이블 정의서에 반영된 설계
- **[근거]** 현행 선택 이유
- **[트레이드오프]** 현행으로 감수하는 단점
- **[변경 제안]** 검토 후보이며 확정되면 원본 문서에 반영
- **[확장 조건]** 실제 요구가 발생하면 검토할 구조

## 2. 요구사항과 도메인 연결

| 도메인 | 사용자 시나리오 | 요구사항 | 핵심 데이터 |
| --- | --- | --- | --- |
| DOM-01 회원 | 로그인, 온보딩, 취향, 알림 | FR-MEM-01~04, FR-NOT-01~03 | 회원, 세션, 현재 취향, 미읽은 알림 |
| DOM-02 관광 콘텐츠·탐색 | 검색·필터·지도·관심 장소 | FR-CON-01~05 | 지역, 콘텐츠, 행사 상세, 이미지, 관심 관계 |
| DOM-03 가이드북 | AI 생성·재생성, 일정, 공유 | FR-GDE-01~07 | 생성 작업, 결과, 일정, 링크, 가져오기 |
| DOM-04 평가·랭킹 | 여행 후 평가, 기간별 랭킹 | FR-RNK-01~04 | 평가 진행, 평점, 랭킹 스냅샷 |
| DOM-05 생성권·결제 | 잔액, 차감, 구매·결제 | FR-PAY-01~03 | 지갑, 원장, 상품, 주문, 결제 시도 |

도메인은 화면 메뉴가 아니라 **같은 비즈니스 규칙과 데이터 생명주기를 공유하는 영역**을 기준으로 나눈다. 생성권은 회원에게 귀속되지만 결제·무료 지급·AI 생성 성공에 의해 변하고 탈퇴 뒤에도 정산 근거가 남아야 하므로 회원과 분리한다.

## 3. 공통 타입·길이 근거

### 식별자

| 형태 | 사용처 | 근거 | 단점 |
| --- | --- | --- | --- |
| BIGINT AUTO_INCREMENT | 내부 회원·콘텐츠·관계 행 | MySQL의 단순한 대리키로 JPA 식별자 매핑과 연관관계 참조가 쉬움 | 외부 노출 시 순차 추측 가능 |
| VARCHAR(50) ID | 생성 작업, 가이드북 | job_, gb_ 접두사로 종류 구분, 추측 어려운 공개 ID | UUID/ULID 규격 확정 후 길이 재검토 |
| 대리키 + 복합 UNIQUE | 회원 취향, 관심 장소 | BIGINT PK로 JPA 매핑을 단순화하고 업무 조합 UNIQUE로 중복 차단 | PK와 UNIQUE 인덱스를 모두 유지 |

### 문자열

MySQL의 `VARCHAR(n)` 길이는 도메인 입력 상한이며, 긴 문자열을 인덱스할 때는 `utf8mb4`의 인덱스 키 길이도 함께 고려한다.

| 길이 | 사용 예 | 근거 |
| ---: | --- | --- |
| 10 | language_code | ko, ko-KR 같은 언어 태그 |
| 15 | 가이드북 제목 | 화면 기획의 제목 제한 |
| 20~30 | 상태·유형·공급자 | Enum과 공급자 코드 |
| 50 | 닉네임·전화·공개 ID | 짧은 값의 비정상 대용량 입력 차단 |
| 100~150 | 주문·거래·멱등 키 | 외부 시스템의 긴 식별자 |
| 200~255 | 콘텐츠 제목·운영시간·오류 코드 | 중간 길이 표시값 |
| 500 | 주소·알림·실패 메시지 | 현재 화면·도메인의 입력 상한 |

AI 본문과 긴 설명은 TEXT로 저장한다. 외부 URL은 서비스 계약을 명확히 하기 위해 `VARCHAR(2048)`로 제한하고, UNIQUE가 필요한 이미지 URL은 SHA-256 `BINARY(32)` 해시를 인덱싱한다.

### 날짜·시각·수치

- 사건 시각은 타임존 정보가 없는 MySQL `DATETIME(6)`에 UTC 값으로 저장하고 DB 세션과 JDBC 타임존을 UTC로 고정한다. 한국 사용자에게 표시할 때만 `Asia/Seoul`로 변환한다.
- 여행일·행사일은 달력 날짜가 의미이므로 DATE, 하루 안 방문 예정 시각은 TIME을 사용한다.
- 수량·인원·순위는 INT, 최대 7일의 일차처럼 작은 범위는 SMALLINT를 사용한다.
- 금액은 원 단위 BIGINT로 부동소수점 오차를 피한다.
- 좌표는 반경·영역 조회를 위해 `POINT SRID 4326`, 랭킹 점수는 고정 소수 계산을 위해 DECIMAL을 사용한다.
- 별점은 0~5 정수만 허용하므로 SMALLINT를 사용한다.

### 상태값

상태를 저장하지 않는다는 뜻이 아니다. DB에는 VARCHAR로 저장하고 애플리케이션에서 Enum으로 제한한다. 코드 안정성과 DB 네이티브 Enum보다 쉬운 변경을 얻는 대신, DB를 직접 우회한 오타는 막지 못한다. 핵심 상태는 CHECK로 보강할 수 있으나 상태 추가 시 마이그레이션이 필요하다.

### JSON

generation_jobs.request_payload와 itinerary_items.place_snapshot은 AI 계약이 변할 수 있고 당시 원문 보존이 필요해 JSON를 사용한다. 검색·정렬·FK·유일성 검사가 필요한 지역, 날짜, 소유자, 상태는 일반 컬럼으로 둔다.

**[트레이드오프]** JSON 내부 구조는 강한 DB 제약을 걸기 어렵다.
**[변경 제안]** AI 계약 변경 가능성이 커지면 payload_schema_version을 추가한다.

## 4. 제약·인덱스 해석

- **PK**: 한 행을 유일하게 식별한다.
- **FK**: 존재하지 않는 부모 데이터 참조를 차단한다. MySQL InnoDB의 자식 FK 인덱스 생성 여부와 별개로 실제 JOIN·삭제 쿼리에 적합한지 검토한다.
- **UNIQUE**: 조회 성능뿐 아니라 중복되면 깨지는 규칙을 보장한다. OAuth 계정, 관심 등록, 멱등 키가 예다.
- **CHECK**: 날짜 역전, 음수 잔액, 범위 밖 점수를 애플리케이션 검증 누락 시에도 차단한다.
- **복합 인덱스**: 왼쪽 컬럼부터 사용된다. 실제 API의 WHERE·ORDER BY가 근거여야 한다.
- **생성 컬럼 UNIQUE**: MySQL에서 완료 이력은 여러 건 허용하면서 진행 중 상태만 하나로 제한한다.

~~~sql
active_member_id BIGINT GENERATED ALWAYS AS (
  CASE WHEN status IN ('PENDING', 'PROCESSING') THEN member_id ELSE NULL END
) STORED,
UNIQUE (active_member_id)
~~~

이는 회원별 진행 작업만 하나로 제한한다. UNIQUE(member_id)는 과거 완료·실패 이력까지 한 건으로 제한하므로 맞지 않는다.

## 5. 도메인별 ERD

전체 테이블을 한 장에 넣으면 관계가 흐려지므로 도메인별로 나눈다. 외부 엔티티는 필요한 키만 축약했다.

### DOM-01 회원

~~~mermaid
erDiagram
    MEMBERS ||--o{ AUTH_SESSIONS : issues
    MEMBERS ||--o{ MEMBER_PREFERENCES : selects
    MEMBERS ||--o{ NOTIFICATIONS : receives
    MEMBERS {
        bigint id PK
        varchar oauth_provider UK
        varchar oauth_subject UK
        varchar status
        datetime deleted_at
    }
    AUTH_SESSIONS {
        bigint id PK
        bigint member_id FK
        varchar refresh_token_hash UK
    }
    MEMBER_PREFERENCES {
        bigint id PK
        bigint member_id FK,UK
        varchar preference_type UK
        varchar preference_code UK
    }
    NOTIFICATIONS {
        bigint id PK
        bigint recipient_member_id FK
        varchar type
        varchar reference_type
        varchar reference_id
    }
~~~

### DOM-02 관광 콘텐츠·탐색

~~~mermaid
erDiagram
    REGIONS ||--o{ TOURISM_CONTENTS : contains
    TOURISM_CONTENTS ||--o| EVENT_DETAILS : extends
    TOURISM_CONTENTS ||--o{ CONTENT_IMAGES : has
    MEMBERS ||--o{ FAVORITE_CONTENTS : registers
    TOURISM_CONTENTS ||--o{ FAVORITE_CONTENTS : favorited
    REGIONS {
        bigint id PK
        varchar administrative_code UK
    }
    TOURISM_CONTENTS {
        bigint id PK
        bigint region_id FK
        varchar category
        varchar source_provider UK
        varchar source_content_id UK
        point location "NOT NULL, SRID 4326"
        varchar status
    }
    EVENT_DETAILS {
        bigint content_id PK,FK
        date start_date
        date end_date
    }
    CONTENT_IMAGES {
        bigint id PK
        bigint content_id FK
        binary image_url_hash UK
        int sort_order
    }
    FAVORITE_CONTENTS {
        bigint id PK
        bigint member_id FK,UK
        bigint content_id FK,UK
    }
    MEMBERS {
        bigint id PK
    }
~~~

### DOM-03 가이드북

~~~mermaid
erDiagram
    MEMBERS ||--o{ GENERATION_JOBS : requests
    MEMBERS ||--o{ GUIDEBOOKS : owns
    GUIDEBOOKS o|--o{ GUIDEBOOKS : origin_of
    GUIDEBOOKS o|--o{ GENERATION_JOBS : result_or_target
    GUIDEBOOKS ||--|{ ITINERARY_DAYS : contains
    ITINERARY_DAYS ||--|{ ITINERARY_ITEMS : contains
    TOURISM_CONTENTS o|--o{ ITINERARY_ITEMS : references
    GUIDEBOOKS ||--o{ SHARE_LINKS : shares
    SHARE_LINKS ||--o{ GUIDEBOOK_IMPORTS : used_for
    MEMBERS ||--o{ GUIDEBOOK_IMPORTS : imports
    GUIDEBOOKS ||--o| GUIDEBOOK_IMPORTS : copied_as
    GUIDEBOOKS ||--o{ GUIDEBOOK_IMPORTS : root_of
    GENERATION_JOBS {
        varchar id PK
        bigint member_id FK
        varchar guidebook_id FK
        varchar status
        json request_payload
        varchar idempotency_key UK
        bigint active_member_id UK
    }
    GUIDEBOOKS {
        varchar id PK
        bigint owner_member_id FK
        varchar origin_guidebook_id FK
        date start_date
        date end_date
        int version
        datetime deleted_at
    }
    ITINERARY_DAYS {
        bigint id PK
        varchar guidebook_id FK
        smallint day_number UK
        date itinerary_date UK
    }
    ITINERARY_ITEMS {
        bigint id PK
        bigint itinerary_day_id FK
        bigint tourism_content_id FK
        smallint sequence UK
        json place_snapshot
    }
    SHARE_LINKS {
        bigint id PK
        varchar guidebook_id FK
        varchar token_hash UK
    }
    GUIDEBOOK_IMPORTS {
        bigint id PK
        bigint share_link_id FK
        bigint imported_by_member_id FK
        varchar root_guidebook_id FK,UK
        varchar imported_guidebook_id FK,UK
    }
    MEMBERS {
        bigint id PK
    }
    TOURISM_CONTENTS {
        bigint id PK
    }
~~~

### DOM-04 평가·랭킹

~~~mermaid
erDiagram
    MEMBERS ||--o{ GUIDEBOOK_EVALUATIONS : evaluates
    GUIDEBOOKS ||--o| GUIDEBOOK_EVALUATIONS : prompted_for
    MEMBERS ||--o{ PLACE_RATINGS : rates
    TOURISM_CONTENTS ||--o{ PLACE_RATINGS : receives
    REGIONS o|--o{ RANKING_SNAPSHOTS : scopes
    RANKING_SNAPSHOTS ||--o{ RANKING_ENTRIES : contains
    TOURISM_CONTENTS ||--o{ RANKING_ENTRIES : ranked
    GUIDEBOOK_EVALUATIONS {
        bigint id PK
        varchar guidebook_id FK,UK
        bigint member_id FK,UK
        varchar status
    }
    PLACE_RATINGS {
        bigint id PK
        bigint member_id FK,UK
        bigint tourism_content_id FK,UK
        smallint score
    }
    RANKING_SNAPSHOTS {
        bigint id PK
        varchar period_type
        bigint region_id FK
        bigint scope_region_id UK
    }
    RANKING_ENTRIES {
        bigint id PK
        bigint ranking_snapshot_id FK,UK
        bigint content_id FK,UK
        int rank_position UK
    }
    MEMBERS {
        bigint id PK
    }
    GUIDEBOOKS {
        varchar id PK
    }
    TOURISM_CONTENTS {
        bigint id PK
    }
    REGIONS {
        bigint id PK
    }
~~~

### DOM-05 생성권·결제

~~~mermaid
erDiagram
    MEMBERS ||--|| CREDIT_WALLETS : owns
    CREDIT_WALLETS ||--o{ CREDIT_TRANSACTIONS : records
    MEMBERS ||--o{ ORDERS : places
    CREDIT_PRODUCTS ||--o{ ORDERS : purchased_as
    ORDERS ||--o{ PAYMENT_ATTEMPTS : attempts
    ORDERS o|--o{ CREDIT_TRANSACTIONS : causes
    GENERATION_JOBS o|--o| CREDIT_TRANSACTIONS : consumes
    CREDIT_WALLETS {
        bigint id PK
        bigint member_id FK,UK
        int credit_balance
    }
    CREDIT_TRANSACTIONS {
        bigint id PK
        bigint wallet_id FK
        bigint order_id FK
        varchar generation_job_id FK
        varchar idempotency_key UK
    }
    CREDIT_PRODUCTS {
        bigint id PK
        int credit_amount
        bigint price
        varchar status
    }
    ORDERS {
        bigint id PK
        varchar merchant_order_id UK
        bigint member_id FK
        bigint product_id FK
        varchar idempotency_key UK
    }
    PAYMENT_ATTEMPTS {
        bigint id PK
        bigint order_id FK
        varchar pg_provider UK
        varchar pg_transaction_id UK
    }
    MEMBERS {
        bigint id PK
    }
    GENERATION_JOBS {
        varchar id PK
    }
~~~

## 6. 테이블별 해체 분석

### members

회원 기준 행이다. OAuth 식별자, 프로필, 언어, 가입 상태와 푸시 설정을 관리한다.

- id는 OAuth 자연키를 다른 테이블에 전파하지 않는 내부 참조 기준이다.
- UNIQUE(provider, subject)는 동일 외부 계정의 중복 가입을 차단한다.
- profile_image_url은 공급자가 이미지를 주지 않을 수 있어 NULL이다.
- status는 삭제와 무관한 가입 진행 상태인 ONBOARDING, ACTIVE를 구분한다.
- 회원 탈퇴는 `deleted_at`으로 소프트 삭제한다. 값이 없으면 이용 가능한 회원이고, 값이 있으면 서비스 접근을 차단하며 파기·익명화 배치 기준으로 사용한다.
- status 인덱스는 운영 조회 후보이나 값 종류가 적어 선택도가 낮을 수 있다.

**[트레이드오프]** 회원당 OAuth 계정 하나만 지원한다. 멀티 연동 시 social_accounts로 분리한다.
**[변경 제안]** 실제 탈퇴 배치가 있다면 INDEX(deleted_at)이 필요한지 실행 계획으로 확인한다.

### auth_sessions

리프레시 토큰별 만료·폐기 상태를 저장한다.

- 원문 대신 해시를 저장해 DB 유출 위험을 낮춘다.
- 해시 UNIQUE는 중복 연결 방지와 조회를 함께 지원한다.
- revoked_at NULL은 활성, 값이 있으면 폐기 상태다.
- INDEX(member_id, revoked_at, expires_at)는 회원의 유효 세션 조회·일괄 폐기에 맞춘다.

**[트레이드오프]** 기기 식별자가 없어 특정 기기만 로그아웃하는 기능은 어렵다.
**[변경 제안]** SHA-256 hex로 확정하면 CHAR(64), 알고리즘 미정이면 VARCHAR(255)를 유지한다.

### member_preferences

회원이 선택한 대분류·중분류·여행 스타일을 행으로 저장한다.

- 여러 취향을 가지므로 members에 theme_1, theme_2 같은 반복 컬럼을 두지 않는다.
- BIGINT 대리키로 JPA 식별자 매핑을 단순화하고 UNIQUE(member_id, type, code)가 중복 선택을 막는다.
- 관리자 운영 데이터가 아니므로 옵션 테이블 없이 앱·서버 Enum으로 관리한다.
- 현재값만 필요해 selected_at이 없다.
- 대분류 1~3개와 중분류 종속은 여러 행을 봐야 하므로 서비스 트랜잭션에서 검증한다.

**[트레이드오프]** 코드와 상하위 관계를 FK로 보장할 수 없고 변경 시 앱·서버 동시 배포가 필요하다.
**[변경 제안]** 취향별 회원 역조회가 없다면 INDEX(type, code)는 제거 후보다.

### notifications

미읽은 인앱 알림만 저장한다.

- recipient_member_id가 소유권 검사 기준이다.
- type은 문구·아이콘·이동 로직을 결정하는 Enum이다.
- reference_type/id는 여러 화면 대상의 다형 참조이며 둘 중 하나만 존재하지 않도록 CHECK한다.
- INDEX(member_id, created_at DESC)는 최신 알림 목록을 지원한다.
- 읽으면 삭제하므로 read_at은 없다.

**[트레이드오프]** 다형 참조에 FK를 걸 수 없고 읽음 이력이 사라진다.
**[확장 조건]** 보관·대량 전송 요구가 생길 때 read_at, source_event_id, outbox/큐를 검토한다.

### regions

17개 광역 시·도의 기준정보다.

- “전체”는 지역이 아니라 필터 미적용 상태라 저장하지 않는다.
- administrative_code UNIQUE는 기준정보 갱신과 중복 방지에 사용한다.
- 콘텐츠·가이드북·랭킹이 같은 지역을 FK로 공유한다.

**[확장 조건]** 시·군·구가 필요하면 parent_id와 level을 추가한다.

### tourism_contents

행사·문화유산·명소의 공통 원본이며 검색, 지도, 일정, 평가, 랭킹이 참조한다.

- category는 콘텐츠당 하나이며 회원 취향과 다른 개념이다.
- UNIQUE(source_provider, source_content_id)는 재수집 upsert 기준이다.
- description은 길이 예측이 어려워 TEXT NULL이다.
- 좌표는 `POINT SRID 4326`로 저장해 MySQL 공간 함수와 SPATIAL INDEX로 반경·영역 조회를 지원한다.
- MySQL SPATIAL INDEX와 SRID 제약을 명확히 적용하기 위해 `location`은 NOT NULL로 둔다. 원본 좌표가 없으면 지오코딩 후 활성화하거나 수집·노출 대상에서 제외한다.
- `status`는 삭제가 아닌 노출 상태 `ACTIVE`, `INACTIVE`를 구분한다. 정책·관리자 삭제는 `deleted_at`으로 소프트 삭제한다.
- 비활성 콘텐츠는 검색·신규 생성 후보·신규 랭킹에서 제외하고, 기존 가이드북의 스냅샷과 평가 참조는 유지한다. 다시 수집되면 활성화할 수 있다.
- INDEX(region_id, category)는 필터 조합을 지원한다.

**[확장 조건]** 의미 검색 도입 시 원문은 유지하고 임베딩을 별도 파생 데이터로 저장한다.

### event_details

행사에만 있는 기간·운영 정보를 1:0..1로 분리한다.

- content_id가 PK이자 FK여서 콘텐츠당 상세 하나만 존재한다.
- 일반 명소에 의미 없는 날짜 NULL 컬럼을 두지 않는다.
- CHECK(start_date <= end_date)가 역전 기간을 차단한다.
- 월 필터는 행사 기간과 조회 월의 겹침으로 계산한다.

### content_images

복수 이미지와 표시 순서를 저장한다.

- 반복 데이터여서 JSON 배열 대신 행으로 둔다.
- URL은 `VARCHAR(2048)`로 저장하고 UNIQUE(content_id, image_url_hash)는 `utf8mb4` 인덱스 길이 제한 없이 재수집 중복을 막는다.
- INDEX(content_id, sort_order)는 상세 화면 정렬을 지원한다.

### favorite_contents

회원과 콘텐츠의 N:M 관심 관계다.

- BIGINT 대리키는 JPA 매핑과 후속 참조를 단순화하고 UNIQUE(member_id, content_id)가 중복 등록을 막는다.
- created_at은 등록순 정렬에 사용한다.
- PK와 복합 UNIQUE 인덱스를 모두 유지하는 쓰기·공간 비용을 감수한다.

### generation_jobs

AI 생성 입력, 처리 상태, 재시도, 오류와 결과 연결을 저장한다.

- 최초 생성 실패에는 가이드북이 없지만 작업 기록은 남아야 해 guidebooks와 분리한다.
- 백엔드가 job ID를 먼저 만들어 AI에 전달하며 프론트는 이 ID로 폴링한다.
- 최초 생성 중 guidebook_id는 NULL, 성공 시 결과 ID를 연결한다. 재생성은 시작부터 기존 ID를 가진다.
- request_payload는 날짜·동행·인원·취향의 요청 당시 스냅샷이다.
- idempotency_key UNIQUE는 네트워크 재전송의 중복 작업을 막는다.
- 진행 중일 때만 회원 ID를 갖는 `active_member_id` 생성 컬럼 UNIQUE는 회원별 진행 작업 하나만 허용한다.
- attempt_count CHECK는 무한 재시도를 막는다.

**[중요 예외]** AI 성공만으로 완료가 아니다. 가이드북·일정 저장, 작업 완료, 생성권 차감, 원장 기록이 모두 성공해야 한다.

### guidebooks

성공 결과의 소유권, 여행 조건, 현재 HTML과 버전을 저장한다.

- 최초 생성 성공 전에는 행을 만들지 않는다.
- 서비스 가이드북 ID는 AI가 아니라 백엔드가 만든다.
- title 15자는 화면 기획 제한을 DB에서도 보장한다.
- 날짜·인원·버전 CHECK가 비정상 AI 결과를 막는다.
- version은 재생성 성공으로 같은 ID의 내용이 바뀐 횟수다.
- `origin_guidebook_id`는 복사본이 거친 단계와 무관하게 최초 생성 원본을 가리킨다. 직접 생성한 가이드북은 NULL이다.
- `deleted_at`은 사용자 삭제를 소프트 삭제로 표현한다. 삭제 즉시 목록·상세·공유 링크 접근을 차단하지만, 이미 가져간 독립 복사본과 생성 작업·원장·평가 참조는 유지한다.

**[트레이드오프]** 최신 버전만 남기는 upsert라 이전 내용 복구는 불가능하다.
**[확장 조건]** 복원이 필요하면 guidebook_versions를 추가한다.

### itinerary_days / itinerary_items

- days는 실제 날짜별 묶음이며 가이드북 내 일차와 날짜 각각 UNIQUE다.
- day_number와 sequence는 작은 범위라 SMALLINT가 충분하다.
- item의 content_id NULL은 AI 장소가 내부 원본과 매칭되지 않은 경우를 허용한다.
- place_snapshot은 원본 변경 후에도 생성 당시 장소명·추천 이유를 보존한다.
- UNIQUE(day_id, sequence)는 하루 안 순서 중복을 막는다.
- content_id 인덱스는 일정 포함 여부, 지도 핀 색상과 평가 대상 조회에 사용한다.

**[트레이드오프]** 원본과 스냅샷이 달라질 수 있어 화면별 표시 우선순위가 필요하다.

### share_links / guidebook_imports

- 공유 토큰 원문 대신 해시를 저장하고 UNIQUE로 충돌을 막는다.
- expires_at NULL은 무기한 링크다.
- `root_guidebook_id`는 공유된 가이드북이 복사본이면 `origin_guidebook_id`를, 원본이면 해당 가이드북 ID를 저장한다.
- UNIQUE(imported_by_member_id, root_guidebook_id)는 공유 링크가 다르거나 복사본을 다시 공유해도 같은 회원이 같은 최초 원본을 두 번 가져오지 못하게 한다.
- imported_guidebook_id UNIQUE는 복사본 하나가 여러 사건에 연결되는 것을 막는다.
- 가져온 가이드북은 독립 복사본이라 원본 변경이 전파되지 않는다.

**[트레이드오프]** 최초 원본 자체가 후에 물리 삭제될 경우를 대비해 `root_guidebook_id` 보존 정책이 필요하다.

### guidebook_evaluations

여행 종료 후 모달과 최종 제출을 가이드북 단위로 관리한다.

- UNIQUE(guidebook_id, member_id)는 평가 진행 중복을 막는다.
- prompt_dismissed_at은 “다음에 하기”로 자동 모달만 중단한 시각이며 평가 포기가 아니다.
- 제출 전 장소 이동·점수는 프론트 상태이고 최종 제출 때 저장한다.

**[트레이드오프]** 앱 종료 시 작성 중 점수가 유실되고 여러 기기에서 이어 쓸 수 없다.

### place_ratings

회원·콘텐츠의 최신 별점 하나를 저장한다.

- UNIQUE(member_id, content_id)는 현재 평가 하나만 허용한다.
- SMALLINT 0~5만 유효한 정수 평가로 저장하고, NULL은 건너뛰기며 CHECK가 범위를 보장한다.
- INDEX(content_id, created_at)는 콘텐츠별 기간 집계를 지원한다.

**[핵심 트레이드오프]** 어떤 여행에서 나온 평가인지 추적할 수 없고 재평가 시 이전 값이 사라진다.
**[변경 제안]** 여행별 이력이 필요하면 evaluation_id 또는 itinerary_item_id를 추가하고 유일 제약을 바꾼다.

### ranking_snapshots / ranking_entries

- snapshot은 일·주·월, 전국·지역별 계산 묶음이며 요청마다 전체 평가를 다시 계산하지 않게 한다.
- region_id NULL은 전국이다.
- MySQL UNIQUE의 복수 NULL 허용을 피하기 위해 `scope_region_id = COALESCE(region_id, 0)` 생성 컬럼과 기간 조합에 UNIQUE를 둔다.
- entry의 snapshot+content UNIQUE는 콘텐츠 중복, snapshot+rank_position UNIQUE는 순위 중복을 막는다.
- weighted_score는 정렬 정밀도, raw_average는 설명용 평균, rating_count는 신뢰도·보조 정렬 근거다.

**[트레이드오프]** C, 최소 평가 수 m, 공식 버전을 저장하지 않아 과거 결과 완전 재현이 어렵다.
**[변경 제안]** 재현이 필요하면 계산 파라미터를 추가한다. 공동 순위를 허용하면 순위 UNIQUE는 제거한다.

### credit_wallets / credit_transactions

- wallet은 현재 잔액을 빠르게 조회하며 member_id UNIQUE로 회원당 하나다.
- CHECK(balance >= 0)은 음수 잔액을 막는다.
- reserved_count는 없고 활성 generation_job으로 논리 예약을 판단한다.
- transaction은 지급·사용·회수·조정을 수정·삭제하지 않는 원장으로 기록한다.
- delta는 증감량, balance_after는 직후 잔액이다.
- idempotency_key UNIQUE는 월 무료 지급, 결제 지급, 생성 차감의 재처리를 한 번만 반영한다.

**[중요 정합성]** 예약 컬럼이 없어도 지갑 행 잠금, 잔액 검사, 활성 작업 생성이 한 트랜잭션이어야 한다.
**[트레이드오프]** 잔액은 원장 합계의 캐시라 불일치 가능성이 있다. 모든 변경을 같은 트랜잭션으로 수행하고 원장으로 검산한다.

### credit_products / orders / payment_attempts

- product는 현재 판매 수량·가격을 관리하고 `status=INACTIVE`로 판매만 중지한다. 관리자 삭제는 `deleted_at`으로 소프트 삭제하고 과거 주문 FK는 유지한다.
- 금액 BIGINT는 원 단위 정수라 오차가 없고 currency CHAR(3)은 ISO 4217 코드다.
- order는 현재 상품이 바뀌어도 과거 구매를 설명하도록 수량·금액·통화를 복사한다.
- 내부 PK와 외부 merchant_order_id를 분리한다.
- 주문 멱등 키와 PG 주문번호는 목적이 달라 각각 UNIQUE다.
- payment_attempts는 네트워크 재시도로 주문 하나에 여러 번 생길 수 있어 1:N이다.
- UNIQUE(provider, transaction_id)는 동일 PG 승인의 중복 반영을 막는다.
- 승인 전 transaction_id와 approved_amount는 NULL일 수 있다.
- 요청·승인 금액을 모두 저장해 웹훅 금액 불일치를 탐지한다.

**[확정 범위]** 환불은 MVP에서 제외하므로 초기 DDL에 `REFUND`·`REFUNDED` 상태나 환불 테이블을 두지 않는다. 향후 정책·상태·이력·API를 함께 설계한다.

## 7. FK 삭제 정책 검토안

아래는 원본 정의서에 반영하지 않은 제안이다.

| 관계 | 후보 | 이유 |
| --- | --- | --- |
| 회원 → 세션·취향·미읽은 알림 | CASCADE 또는 탈퇴 서비스 삭제 | 개인정보 파기 시 종속 데이터 제거 |
| 회원 → 가이드북·평가 | RESTRICT 또는 익명화 | 여행·평가 보존 정책 필요 |
| 회원 → 지갑·주문·원장 | RESTRICT | 정산·감사 데이터 보존 |
| 콘텐츠 → 행사 상세·이미지 | CASCADE | 부모 없는 상세는 의미 없음 |
| 콘텐츠 → 일정 항목 | SET NULL 후보 | 스냅샷으로 기존 가이드북 표시 |
| 가이드북 → 일정 | CASCADE | 부모 없는 일정은 의미 없음 |
| 가이드북 → 생성 작업 | RESTRICT 또는 SET NULL | 작업 이력 보존 수준에 따라 결정 |
| 가이드북 → 공유 링크 | CASCADE 또는 RESTRICT | 삭제 후 링크 정책 필요 |
| 주문 → 결제 시도·원장 | RESTRICT | 결제·정산 이력 보존 |

## 8. 원자성과 동시성

최초 생성 성공 시 가이드북·일정 저장, 작업 COMPLETED, 지갑 1 감소, CONSUME 원장 삽입을 하나의 DB 트랜잭션으로 처리한다. 하나라도 실패하면 전체 롤백한다. AI 호출은 트랜잭션 밖에서 수행해 DB 잠금을 길게 잡지 않는다.

재생성은 새 결과 검증 후 기존 본문·일정을 교체하고 version을 올린다. 일정 삭제와 재삽입도 같은 트랜잭션 안에서 처리해 실패 시 기존 결과를 유지한다.

생성권 중복 차감은 다음을 함께 사용한다.

1. 활성 작업 부분 UNIQUE
2. 지갑 행 잠금
3. 원장 idempotency_key
4. 지갑 감소와 원장 삽입의 동일 트랜잭션

각 장치는 서로 다른 실패 지점을 막는다.

결제 멱등 기준:

| 단계 | 키 |
| --- | --- |
| 주문 생성 재전송 | orders.idempotency_key |
| 서비스 주문 식별 | merchant_order_id |
| 동일 PG 승인 | provider + transaction_id |
| 생성권 지급 | credit_transactions.idempotency_key |

## 9. 아직 결정하지 않은 변경 후보

1. members.status 단독 인덱스의 실제 쿼리 확인
2. 취향 역방향 인덱스 사용 여부
3. MySQL FULLTEXT 검색 도입 여부와 한국어 분석 한계
4. 알림 대상 삭제 시 처리와 읽음 이력 보존 여부
5. 가이드북 이전 버전 복구 여부
6. 장소 평가의 여행별 이력 필요 여부
7. 랭킹 계산 파라미터 보존 여부
8. 논리 예약 중 다른 잔액 감소 연산 제어 방식
9. FK별 ON DELETE와 탈퇴 후 보존·익명화
10. 소프트 삭제된 가이드북의 물리 삭제·개인정보 제거 시점
11. PDF 동기 생성의 비동기 전환 기준

## 10. 확장성

| 요구 변화 | 확장 방향 |
| --- | --- |
| 여러 소셜 계정 | social_accounts 분리 |
| 관리자 취향 편집 | 취향 그룹·옵션 테이블 |
| 시·군·구 필터 | regions 계층화 |
| 의미 검색 | 요구와 규모를 확인한 후 별도 검색 저장소 검토 |
| 가이드북 복원 | guidebook_versions |
| 평가 임시 저장 | 서버 초안·임시 점수 |
| 여행별 반복 평가 | 평가 세션/일정 FK |
| 랭킹 완전 재현 | C·m·공식 버전 저장 |
| 읽은 알림 보관 | read_at·보존 정책 |
| 대량 알림 | outbox·메시지 큐 |
| 긴 PDF 생성 | export_jobs·객체 저장소 |
| 부분 환불 | refunds·환불 시도 이력 |

확장성을 이유로 모든 테이블을 미리 만들지는 않는다. 요구가 생길 때 안전하게 마이그레이션할 경로를 남기는 수준이 MVP에 적합하다.

## 11. 원본 반영 전 체크리스트

- [ ] 변경이 어떤 요구사항에서 시작됐는지 적었다.
- [ ] 컬럼·상태명이 요구사항, API, ERD, 코드 Enum에서 같다.
- [ ] 모든 FK에 NULL 이유와 삭제 정책이 있다.
- [ ] 모든 UNIQUE가 막는 중복 상황을 설명할 수 있다.
- [ ] 모든 인덱스가 대응하는 API 조회를 설명할 수 있다.
- [ ] 문자열 길이가 화면 또는 외부 API 규격에 근거한다.
- [ ] 날짜·시각과 타임존 기준이 일관된다.
- [ ] 중복 요청, 실패, 재시도, 롤백을 검토했다.
- [ ] JSON 중 검색·조인·제약 값은 일반 컬럼으로 분리했다.
- [ ] MVP 이후 기능을 현재 구현 의무처럼 표현하지 않았다.
- [ ] 확정 후 요구사항 → 테이블·ERD → API → 테크스펙을 함께 갱신한다.

## 12. 결론

현행 구조는 MySQL 기준으로 회원, 콘텐츠 탐색, 비동기 AI 생성, 평가·랭킹, 생성권·결제의 핵심 정합성을 설명할 수 있는 수준이다. 특히 생성 작업과 결과 가이드북 분리, 콘텐츠 원본과 일정 스냅샷 병행, 지갑과 불변 원장 분리는 유지 근거가 분명하다.

데이터 길이와 인덱스는 절대적인 정답이 아니라 현재 가정이다. 화면 제한, 외부 API 규격, 실제 쿼리와 데이터량을 확인한 뒤 조정한다. 별점 정수화, 외부 URL `VARCHAR(2048)`, 관계 테이블의 대리키와 업무 조합 UNIQUE, 최초 원본 기준 중복 방지, 상태 기반 소프트 삭제, 환불 MVP 제외는 확정됐다. 남은 핵심 검토 항목은 FK 삭제·보존 정책, 평가 이력 범위, 랭킹 파라미터 보존 수준이다.
