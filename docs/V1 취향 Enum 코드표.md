# V1 취향 Enum 코드표

> V1-06 · BR-MEM-10/11 · API-MEM-07~09
> TourAPI 관광 분류와 회원 취향을 일관되게 매핑하기 위해 확정한 V1 코드 목록이다. `THEME`과 `DETAIL`은 TourAPI 기반 관광 분류를 사용하고, `TRAVEL_STYLE`은 이동·일정 성향을 나타내는 별도 선택값이다.

| preference_type | code | label | parent_code | sort_order |
|---|---|---|---|---:|
| THEME | NATURE | 자연 | null | 10 |
| DETAIL | NATURE_MOUNTAIN | 산 | NATURE | 10 |
| DETAIL | NATURE_RIVER_SEA | 하천·해양 | NATURE | 20 |
| DETAIL | NATURE_ECOLOGY | 자연생태 | NATURE | 30 |
| DETAIL | NATURE_PARK | 공원 | NATURE | 40 |
| DETAIL | NATURE_ETC | 그 외 | NATURE | 50 |
| THEME | HISTORY | 역사 | null | 20 |
| DETAIL | HISTORY_HERITAGE_SITE | 역사 유적지 | HISTORY | 10 |
| DETAIL | HISTORY_RELIC | 역사 유물 | HISTORY | 20 |
| DETAIL | HISTORY_RELIGIOUS_SITE | 종교 성지 | HISTORY | 30 |
| DETAIL | HISTORY_SECURITY_SITE | 안보 관광지 | HISTORY | 40 |
| THEME | ATTRACTION | 관광 명소 | null | 30 |
| DETAIL | ATTRACTION_LANDMARK | 랜드마크 | ATTRACTION | 10 |
| DETAIL | ATTRACTION_THEME_PARK | 테마 공원 | ATTRACTION | 20 |
| DETAIL | ATTRACTION_URBAN_CULTURE | 도시·지역 문화 관광 | ATTRACTION | 30 |
| DETAIL | ATTRACTION_EXHIBITION | 전시 시설 | ATTRACTION | 40 |
| THEME | EXPERIENCE | 체험 | null | 40 |
| DETAIL | EXPERIENCE_TRADITION | 전통 체험 | EXPERIENCE | 10 |
| DETAIL | EXPERIENCE_CRAFT | 공예 체험 | EXPERIENCE | 20 |
| DETAIL | EXPERIENCE_RURAL | 농·산·어촌 체험 | EXPERIENCE | 30 |
| DETAIL | EXPERIENCE_TEMPLE_STAY | 산사 체험 | EXPERIENCE | 40 |
| DETAIL | EXPERIENCE_HEALING | 힐링 체험 | EXPERIENCE | 50 |
| DETAIL | EXPERIENCE_INDUSTRY | 산업 관광 | EXPERIENCE | 60 |
| DETAIL | EXPERIENCE_ETC | 그 외 체험 | EXPERIENCE | 70 |
| THEME | LEISURE_SPORTS | 레저 스포츠 | null | 50 |
| DETAIL | LEISURE_SPORTS_LAND | 육상 | LEISURE_SPORTS | 10 |
| DETAIL | LEISURE_SPORTS_WATER | 수상 | LEISURE_SPORTS | 20 |
| DETAIL | LEISURE_SPORTS_AIR | 항공 | LEISURE_SPORTS | 30 |
| DETAIL | LEISURE_SPORTS_COMPLEX | 복합 | LEISURE_SPORTS | 40 |
| THEME | EVENTS | 축제/공연/행사 | null | 60 |
| DETAIL | EVENTS_FESTIVAL | 축제 | EVENTS | 10 |
| DETAIL | EVENTS_CONCERT | 공연 | EVENTS | 20 |
| DETAIL | EVENTS_FAIR | 행사 | EVENTS | 30 |
| TRAVEL_STYLE | RELAXING | 여유롭게 | null | 20 |
| TRAVEL_STYLE | TIME_EFFICIENCY | 효율적으로 | null | 30 |
| TRAVEL_STYLE | WALK_FRIENDLY | 걷는거 좋아요 | null | 40 |
| TRAVEL_STYLE | CAR_TRAVEL | 차로 이동 | null | 50 |

## 검증과 변경 주의사항

- `THEME`은 1~3개, 선택한 각 `THEME`의 `DETAIL`은 1~3개를 필수로 선택한다. `TRAVEL_STYLE`은 선택 사항이며 서로 다른 허용 코드 0~4개를 선택할 수 있다.
- 대분류와 중분류의 관계는 고정 1:6이 아니라 1:N이다. 중분류 개수는 대분류별로 다르며 `parent_code`로 관계를 판정한다.
- API의 표시 순서는 위 표의 선언 순서를 그대로 따른다. 같은 부모 안에서는 `sort_order` 오름차순이며 사용자가 클릭한 순서는 저장하지 않는다.
- `THEME`과 `DETAIL`은 TourAPI 관광 분류를 기준으로 정의한다. 관광 콘텐츠 후보를 회원 취향에 매핑할 때 동일한 안정 코드를 사용하고, TourAPI 원본 코드와의 세부 변환은 관광 콘텐츠 수집 계층이 담당한다.
- `TRAVEL_STYLE`은 TourAPI 콘텐츠 분류가 아니라 이동·일정 구성 성향이다. 관광 콘텐츠의 원본 카테고리로 저장하지 않는다.
- Java Enum 이름과 문자열 코드를 저장하며 ordinal 숫자는 저장하지 않는다. DB는 VARCHAR이고 Enum registry가 코드·부모 관계를 검증한다.
- 코드·부모·중복·개수 오류는 422 `PREFERENCE_INVALID`, JSON 구조·타입 오류는 400 `COMMON_VALIDATION_ERROR`다.
- 기본 취향 PUT은 전체 교체다. 삭제한 부모의 중분류를 FE에서 함께 빼고 BE는 부모 없는 중분류를 거절한다.
- 옵션 추가·폐기는 코드 배포와 FE·AI·TourAPI 매핑 변경이 필요하다. 기존 코드의 의미를 바꾸거나 재사용하지 않으며, 삭제 시 기존 DB 선택과 생성 요청 snapshot의 호환 migration을 검토한다.
- BE는 안정 코드를 저장하고 API에서 한국어 라벨을 제공한다. 작업 접수 snapshot과 매핑 버전을 기준으로 AI 입력을 구성해 뒤늦은 라벨 변경이 재시도 입력을 바꾸지 않게 한다.
