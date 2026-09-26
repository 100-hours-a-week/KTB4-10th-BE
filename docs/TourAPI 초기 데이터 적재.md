# TourAPI 초기 데이터 적재

## 목적

`areaBasedList2` 전량 JSON의 공통 관광 콘텐츠와 `searchFestival2` 전기간 JSON의 행사 기간을 로컬 MySQL에 한 번 적재한다. 이 실행기는 초기 데이터용이며 TourAPI 주기 동기화 스케줄러가 아니다.

## 적재 규칙

1. `(lDongRegnCd, lDongSignguCd)`별 주소의 최빈 `province`, `city`를 서비스 지역으로 결정한다.
2. `AdministrativeProvince`, `AdministrativeDistrict`에 존재하는 조합만 허용한다.
3. 일반 시 산하 구의 여러 TourAPI 코드는 하나의 서비스 `DISTRICT`로 연결한다.
4. 공통 콘텐츠는 `(source_provider, source_content_id)`로 upsert한다.
5. 행사는 같은 외부 콘텐츠 ID의 `event_details`를 upsert한다.
6. 좌표가 없거나 지역을 매핑할 수 없는 콘텐츠는 제외하고 건수를 출력한다.

예를 들어 TourAPI의 충청북도 청주시 구 코드는 모두 서비스의 청주시로 연결된다.

```text
43 + 111 ┐
43 + 112 ├─→ 충청북도 / 청주시
43 + 113 ┤
43 + 114 ┘
```

## 사전 조건

- JDK 21
- MySQL 8.4
- 로컬 프로필에 필요한 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수
- `areaBasedList2`, `searchFestival2` JSON 파일

실행 시 Flyway가 먼저 V2 지역 계층과 V3 TourAPI 적재 컬럼을 적용한다. 이미 적용된 migration 파일은 수정하지 않는다.

## 실행 방법

프로젝트 루트에서 환경변수를 설정한 다음 절대 경로를 전달한다.

```bash
export DB_URL="jdbc:mysql://localhost:3306/kgb_local"
export DB_USERNAME="kgb"
export DB_PASSWORD="로컬 비밀번호"

./gradlew importTourApiData \
  -PareaFile="/absolute/path/areaBasedList2.json" \
  -PfestivalFile="/absolute/path/searchFestival2.json"
```

성공하면 공통 원본·콘텐츠 upsert·좌표 누락·지역 매핑 실패·행사 upsert·잘못된 행사 건수를 출력한다. 같은 파일로 다시 실행해도 동일 콘텐츠와 행사 상세가 중복 생성되지 않는다.

## 적재 후 확인

```sql
SELECT COUNT(*) FROM tourism_contents WHERE source_provider = 'TOUR_API';
SELECT COUNT(*) FROM event_details;

SELECT province.name AS province, district.name AS city, COUNT(*) AS contents
FROM tourism_contents content
JOIN regions district ON district.id = content.region_id
JOIN regions province ON province.id = district.parent_id
WHERE content.source_provider = 'TOUR_API'
GROUP BY province.id, province.name, district.id, district.name
ORDER BY province.name, district.name;
```

## 현재 제외 범위

- TourAPI 주기 호출과 `@Scheduled`
- `detailCommon2`를 통한 소개·프로그램·요금 수집
- 좌표 누락 콘텐츠의 지오코딩
- 정보·지도 조회 API 변경
- AI 요청 DTO에 후보 콘텐츠 조립
