# V1 취향 Enum 코드표

> V1-06 · BR-MEM-10/11 · API-MEM-07~09
> 개발용 초기 코드 목록이다. 사용자 요청에 따라 6개 대분류·각 6개 중분류·8개 스타일로 구체화했다. FE 라벨 및 AI 입력 매핑은 구현 전 함께 확인한다.

| preference_type | code | label | parent_code | sort_order |
|---|---|---|---|---|
| THEME | HEALING | 힐링 | null | 10 |
| DETAIL | QUIET_PLACE | 조용한 곳 | HEALING | 10 |
| DETAIL | HEALING_SPA | 온천·스파 | HEALING | 20 |
| DETAIL | HEALING_CAFE_REST | 카페에서 쉬기 | HEALING | 30 |
| DETAIL | HEALING_FOREST_WALK | 숲·산책로 | HEALING | 40 |
| DETAIL | HEALING_SEA_VIEW | 바다 보기 | HEALING | 50 |
| DETAIL | HEALING_LAKE_REST | 호수에서 쉬기 | HEALING | 60 |
| THEME | ACTIVITY | 액티비티 | null | 20 |
| DETAIL | ACTIVITY_HIKING | 등산 | ACTIVITY | 10 |
| DETAIL | ACTIVITY_WATER_SPORTS | 수상 스포츠 | ACTIVITY | 20 |
| DETAIL | ACTIVITY_CYCLING | 자전거 | ACTIVITY | 30 |
| DETAIL | ACTIVITY_LEISURE_SPORTS | 레저 스포츠 | ACTIVITY | 40 |
| DETAIL | ACTIVITY_CAMPING | 캠핑 | ACTIVITY | 50 |
| DETAIL | ACTIVITY_THEME_PARK | 테마파크 | ACTIVITY | 60 |
| THEME | FOOD | 미식 | null | 30 |
| DETAIL | FOOD_LOCAL_FOOD | 지역 음식 | FOOD | 10 |
| DETAIL | FOOD_TRADITIONAL_MARKET | 전통시장 먹거리 | FOOD | 20 |
| DETAIL | FOOD_SEAFOOD | 해산물 | FOOD | 30 |
| DETAIL | FOOD_DESSERT | 디저트 | FOOD | 40 |
| DETAIL | FOOD_LOCAL_DRINK | 지역 음료·주류 | FOOD | 50 |
| DETAIL | FOOD_FOOD_EXPERIENCE | 음식 만들기 체험 | FOOD | 60 |
| THEME | CULTURE | 문화 | null | 40 |
| DETAIL | CULTURE_HISTORY | 역사 탐방 | CULTURE | 10 |
| DETAIL | CULTURE_MUSEUM | 박물관 | CULTURE | 20 |
| DETAIL | CULTURE_GALLERY | 미술관·전시 | CULTURE | 30 |
| DETAIL | CULTURE_TRADITIONAL_CULTURE | 전통문화 체험 | CULTURE | 40 |
| DETAIL | CULTURE_PERFORMANCE | 공연 | CULTURE | 50 |
| DETAIL | CULTURE_ARCHITECTURE | 건축 탐방 | CULTURE | 60 |
| THEME | NATURE | 자연 | null | 50 |
| DETAIL | NATURE_MOUNTAIN | 산 풍경 | NATURE | 10 |
| DETAIL | NATURE_BEACH | 해변 | NATURE | 20 |
| DETAIL | NATURE_FOREST | 숲 | NATURE | 30 |
| DETAIL | NATURE_GARDEN | 정원·수목원 | NATURE | 40 |
| DETAIL | NATURE_RIVER_LAKE | 강·호수 | NATURE | 50 |
| DETAIL | NATURE_ECOLOGY | 생태 탐방 | NATURE | 60 |
| THEME | SHOPPING | 쇼핑 | null | 60 |
| DETAIL | SHOPPING_SOUVENIR | 기념품 | SHOPPING | 10 |
| DETAIL | SHOPPING_LOCAL_CRAFT | 지역 공예품 | SHOPPING | 20 |
| DETAIL | SHOPPING_MARKET | 시장 구경 | SHOPPING | 30 |
| DETAIL | SHOPPING_SELECT_SHOP | 편집숍 | SHOPPING | 40 |
| DETAIL | SHOPPING_OUTLET | 아울렛 | SHOPPING | 50 |
| DETAIL | SHOPPING_LOCAL_BRAND | 지역 브랜드 | SHOPPING | 60 |
| TRAVEL_STYLE | AVOID_CROWDS | 사람 많은 곳 피하기 | null | 10 |
| TRAVEL_STYLE | WALK_FRIENDLY | 많이 걸어도 좋아요 | null | 20 |
| TRAVEL_STYLE | CAR_TRAVEL | 차로 이동 | null | 30 |
| TRAVEL_STYLE | MORNING_PERSON | 아침형 | null | 40 |
| TRAVEL_STYLE | RELAXED | 여유롭게 | null | 50 |
| TRAVEL_STYLE | WITH_CHILDREN | 아이 동반 | null | 60 |
| TRAVEL_STYLE | BUDGET_FRIENDLY | 알뜰하게 | null | 70 |
| TRAVEL_STYLE | PHOTO_FOCUSED | 사진 위주 | null | 80 |

## 검증과 변경 주의사항

- THEME 1~3개, 선택 THEME 각각 DETAIL 1~3개, 스타일 0~8개. 스타일은 서로 다른 허용 코드만 받는다.
- API items 정렬은 THEME→DETAIL→TRAVEL_STYLE, 부모 대분류 순서→sort_order→code다. 사용자가 클릭한 순서는 저장하지 않는다.
- Java Enum 이름/문자열 코드를 저장하며 ordinal 숫자는 저장하지 않는다. DB는 VARCHAR이고 Enum registry가 코드·부모 관계를 검증한다.
- 코드·부모·중복·개수 오류는 422 PREFERENCE_INVALID. JSON 구조/타입 오류는 400 COMMON_VALIDATION_ERROR.
- 기본 취향 PUT은 전체 교체다. 삭제한 부모의 중분류를 FE에서 함께 빼고 BE는 부모 없는 중분류를 거절한다.
- 힐링의 SEA_VIEW와 자연의 BEACH처럼 비슷해도 여행 의도가 다르다. 콘텐츠 카테고리와 같은 Enum으로 합치지 않는다.
- WITH_CHILDREN/CAR_TRAVEL은 선호 정보다. 실제 아동 동행이나 차량 보유를 보증하지 않는다. 동행 유형·총인원을 자동 변경하지 않는다.
- 스타일 간 강제 상호 배타 규칙은 현재 없다. 생성 prompt의 상충 선호 처리는 AI와 확인한다.
- 옵션의 추가·폐기는 코드 배포와 FE/AI 매핑 변경이 필요하다. 기존 코드 의미를 바꾸거나 재사용하지 않는다. 삭제 시 기존 DB와 요청 snapshot의 호환 migration이 필요하다.
- AI 시트는 한글 라벨을 요구하지만 BE는 안정 코드를 저장한다. 작업 접수 snapshot과 매핑 버전을 기준으로 변환하며 뒤늦은 라벨 변경으로 재시도 입력을 바꾸지 않는다.
- 이 목록은 Enum을 문서로 정의한 것이며 실제 Java 구현은 아직 아니다. Figma 최신 옵션 전체와 재대조가 필요하다.
