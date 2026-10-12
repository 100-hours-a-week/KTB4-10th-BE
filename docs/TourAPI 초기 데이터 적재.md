# TourAPI 초기 데이터 적재

## 목적

`areaBasedList2` 전량 JSON의 공통 관광 콘텐츠와 `searchFestival2` 조회 결과의 행사 기간을 검증하고 적재한다. 수집과 적재는 향후 주기 동기화 스케줄러에서 사용한다.

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
- TourAPI 서비스 키와 스냅샷 저장 위치 설정

실행 시 Flyway가 먼저 V2 지역 계층과 V3 TourAPI 적재 컬럼을 적용한다. 이미 적용된 migration 파일은 수정하지 않는다.

## 실행 방식

별도의 수동 실행기는 제공하지 않는다. 스케줄러가
`TourApiSyncService.synchronize()`를 호출하면 API 수집, 응답 검증, 스냅샷 저장,
DB 적재가 순서대로 실행된다. 같은 데이터를 다시 적재해도 동일 콘텐츠와 행사
상세가 중복 생성되지 않는다.

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
이 측정 당시 이전 스냅샷에서 사라진 130건의 `INACTIVE` 전환은 구현 전이었다.

## TourAPI 전국 목록 수집

`areaBasedList2`를 `pageNo=1`, `numOfRows=60000`으로 한 번 호출한다. 응답의
`totalCount`와 중복을 제거한 `contentid` 수가 일치할 때만 원본 JSON을 저장하고
위의 증분 적재 로직으로 전달한다. 불완전한 응답은 MySQL을 변경하지 않는다.

수집 진입점은 `TourApiSyncService.synchronize()`이다. 로컬에서는 스냅샷 위치에
파일 경로를, 운영에서는 S3 URI를 설정한다.

```bash
export TOUR_API_SNAPSHOT_LOCATION='s3://<bucket>/<prefix>'
```

스냅샷은 `<location>/<Asia/Seoul 기준 날짜>/<UUID>/areaBasedList2.json`에 저장된다.
이 실행기는 `areaBasedList2`와 `searchFestival2`를 함께 수집하고 두 응답이 모두
완전한 경우에만 한 트랜잭션으로 증분 적재한다. 행사 조회 기간은 `Asia/Seoul`
기준 실행일 90일 전부터 2년 후까지이며, 행사 응답에서 사라진 항목은 조회 기간
밖으로 이동했을 수 있으므로 콘텐츠 비활성화 근거로 사용하지 않는다.

## 정기 동기화와 실패 복구

`TOUR_API_SCHEDULER_ENABLED=true`인 환경에서는 기본적으로 Asia/Seoul 기준 매일
00:00에 동기화한다. `TOUR_API_SCHEDULER_CRON`으로 실행 시각을 변경할 수 있다.
애플리케이션 기동 시에도 당일 성공 이력이 없으면 즉시 실행해 서버 중단 중 놓친
작업을 보충한다.

`tour_api_sync_runs.sync_date`의 유일 키가 기준일별 분산 잠금 역할을 한다. 이미
성공했거나 다른 인스턴스가 실행 중이면 건너뛰고, `RUNNING` 상태가 기본 2시간보다
오래되면 중단된 실행으로 판단해 새 실행이 인계한다. 재시도 가능한 오류는 기본
3회까지 재시도하며, 최종 상태·시도 횟수·처리 건수·실패 단계와 사유를 실행 이력에
기록한다.

V1은 동기화 한 회가 2시간 안에 종료된다는 운영 전제를 둔다. 오래된 작업
인계에는 fencing token을 적용하지 않으므로, 실제로 살아 있는 기존 작업이 2시간을
넘기면 새 작업과 DB 적재·캐시 갱신이 겹칠 수 있다. 동기화 소요 시간은 운영에서
관찰하고 2시간에 근접하면 임계값을 단순히 늘리기보다 소유권 확인·fencing을 도입한다.

기동 보충 실행은 `ApplicationReadyEvent` 리스너에서 동기로 수행한다. V1에서는
별도 실행기를 두지 않으며, 배포 시작 지연이 운영 문제로 확인되면 전용 실행기로
분리한다.

완전한 전국 목록을 적재한 경우에만 목록에서 사라진 기존 콘텐츠를 `INACTIVE`로
전환한다. 다시 나타난 콘텐츠는 기존 증분 UPSERT가 `ACTIVE`로 복구한다. DB 반영이
끝나면 지도 공통 캐시 전체를 현재 데이터로 다시 구성한다.

## 현재 제외 범위

- `detailCommon2`를 통한 소개·프로그램·요금 수집
- 좌표 누락 콘텐츠의 지오코딩
- 정보·지도 조회 API 변경
- AI 요청 DTO에 후보 콘텐츠 조립
