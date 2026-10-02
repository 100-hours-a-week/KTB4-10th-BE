# TourAPI 초기 데이터 적재

## 목적

`areaBasedList2` 전량 JSON의 공통 관광 콘텐츠와 `searchFestival2` 전기간 JSON의 행사 기간을 로컬 MySQL에 한 번 적재한다. 이 실행기는 초기 데이터용이며 TourAPI 주기 동기화 스케줄러가 아니다.

## 적재 규칙

1. `(lDongRegnCd, lDongSignguCd)`별 주소의 최빈 `province`, `city`를 서비스 지역으로 결정한다.
2. `AdministrativeProvince`, `AdministrativeDistrict`에 존재하는 조합만 허용한다.
3. 일반 시 산하 구의 여러 TourAPI 코드는 하나의 서비스 `DISTRICT`로 연결한다.
4. 원본 지역 코드가 비어 있으면 검증된 주소의 `province`, `city`를 보조 기준으로 사용한다.
5. 세종특별자치시는 요청 계약과 동일하게 광역·2단계 지역명을 모두 `세종특별자치시`로 사용한다.
6. 공통 콘텐츠는 `(source_provider, source_content_id)`로 upsert한다.
7. 행사는 같은 외부 콘텐츠 ID의 `event_details`를 upsert한다.
8. 좌표가 없거나 코드·주소 모두로 지역을 매핑할 수 없는 콘텐츠는 제외하고 건수를 출력한다.

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
- `data/tourism/raw/2026-09-20` 경로의 `areaBasedList2`, `searchFestival2` JSON 파일

실행 시 Flyway가 먼저 V2 지역 계층과 V3 TourAPI 적재 컬럼을 적용한다. 이미 적용된 migration 파일은 수정하지 않는다.

## 실행 방법

프로젝트 루트에서 환경변수를 설정한 다음 절대 경로를 전달한다.

```bash
export DB_URL="jdbc:mysql://localhost:3306/kgb_local"
export DB_USERNAME="kgb"
export DB_PASSWORD="로컬 비밀번호"

./gradlew importTourApiData
```

기본 경로가 아닌 다른 스냅샷을 적재할 때는 절대 경로를 지정한다.

```bash
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

## 초기 데이터 덤프와 인스턴스 복원

Flyway가 스키마를 관리하므로 덤프에는 `regions`, `tourism_contents`, `event_details`의 데이터만 포함한다. 회원·세션·생성 작업·생성권 등 사용자 및 운영 데이터는 포함하지 않는다.

로컬 DB에서 덤프를 다시 만들려면 프로젝트 루트에서 실행한다. 기본적으로 `.env`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 사용한다.

```bash
./scripts/database/dump-tourism-data.sh
```

결과 파일은 `data/tourism/tourism-data.sql.gz`이다. 다른 환경 파일이나 출력 경로를 사용하려면 다음처럼 지정한다.

```bash
ENV_FILE=/absolute/path/db.env \
  ./scripts/database/dump-tourism-data.sh /absolute/path/tourism-data.sql.gz
```

인스턴스에서는 애플리케이션을 한 번 실행해 Flyway 최신 스키마를 먼저 만든 다음 복원한다. 복원 스크립트는 세 대상 테이블 중 하나라도 데이터가 있으면 중단하며 기존 데이터를 자동 삭제하지 않는다.

```bash
ENV_FILE=/absolute/path/instance-db.env \
  ./scripts/database/restore-tourism-data.sh
```

복원 후 스크립트가 테이블별 건수, 지역 FK 누락, 행사 FK 누락과 대분류별 건수를 출력한다. FK 누락 건수는 모두 `0`이어야 한다.

현재 저장소 덤프의 기준 건수는 다음과 같다.

| 테이블 | 건수 |
|---|---:|
| `regions` | 246 |
| `tourism_contents` | 48,728 |
| `event_details` | 905 |

## 최초 적재 성능 기준

2026-10-01 로컬 MySQL 8.4의 빈 `kgb_tour_bench` 스키마에서 JDK 21과
`--no-daemon`을 사용해 1회 측정한 결과다. `real`은 Gradle 작업, Spring Boot
기동, Flyway 5개 migration, JSON 파싱·정제, DB 적재와 정상 종료를 모두 포함한다.

```text
real: 27.31초
user: 20.90초
sys:   3.74초
```

| SQL 패턴 | DB 실행 횟수 | 처리 행 | DB 누적 시간 |
|---|---:|---:|---:|
| `tourism_contents` upsert | 48,728 | 48,728 | 14.960초 |
| `event_details` upsert | 905 | 905 | 0.046초 |
| `tour_api_region_mappings` upsert | 257 | 257 | 0.013초 |
| `regions` upsert | 246 | 246 | 0.030초 |

`JdbcTemplate.batchUpdate` 사용에도 이 측정의 MySQL `performance_schema`에서
`tourism_contents` INSERT는 48,728회로 관측됐다. 후속 최적화 시 JDBC 배치
재작성 옵션 또는 multi-row INSERT를 비교할 수 있는 기준값으로 사용한다.

같은 DB에 2026-10-01 `areaBasedList2` 49,623건을 재적재한 측정 결과다.
행사 스냅샷은 기존 2026-09-21 파일을 재사용했다.

```text
real: 19.69초
user: 22.37초
sys:   3.41초
```

| SQL 패턴 | DB 실행 횟수 | MySQL 영향 행 | DB 누적 시간 |
|---|---:|---:|---:|
| `tourism_contents` upsert | 48,675 | 97,275 | 7.126초 |
| `event_details` upsert | 844 | 1,688 | 0.055초 |

`modifiedtime` 비교를 적용하기 전 `tourism_contents` 최종 건수는 48,728건에서
48,803건으로 75건 증가했다.
MySQL 영향 행 97,275는 신규 INSERT 75건과 기존 행 UPDATE 48,600건을
각각 1건과 2건으로 집계한 결과와 일치한다. 개선 전 importer는
`modifiedtime` 변경 1,238건만 고르지 않고, 스냅샷의 유효 콘텐츠 전체를
upsert했던 기준값이다.

`source_modified_at`과 활성·삭제 상태를 비교하도록 개선한 후 같은 조건으로
재측정한 결과다. 행사는 시작일·종료일이 다른 경우만 갱신한다.

```text
real: 9.75초
user: 19.26초
sys:   1.51초
```

| SQL 패턴 | DB 실행 횟수 | DB 누적 시간 |
|---|---:|---:|
| 기존 콘텐츠 수정 시각·상태 조회 | 1 | 0.086초 |
| `tourism_contents` 증분 upsert | 1,310 | 0.402초 |
| 기존 행사 기간 조회 | 1 | 0.002초 |
| `event_details` 증분 upsert | 0 | 0초 |

신규 75건과 원본 `modifiedtime` 변경 행 중 좌표·지역 검증을 통과한
1,235건만 upsert해 콘텐츠 SQL이 48,675회에서 1,310회로 감소했다.
이전 스냅샷에서 사라진 130건의 `INACTIVE` 전환은 아직 구현되지 않았다.

## 현재 제외 범위

- TourAPI 주기 호출과 `@Scheduled`
- `detailCommon2`를 통한 소개·프로그램·요금 수집
- 좌표 누락 콘텐츠의 지오코딩
- 정보·지도 조회 API 변경
- AI 요청 DTO에 후보 콘텐츠 조립
