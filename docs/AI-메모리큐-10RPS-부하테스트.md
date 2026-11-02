# 단일 BE 메모리 큐: 10 RPS × 10분 부하테스트

실행일: 2026-10-30. 관련 요구사항: BR-GDE-16, FR-GDE-01/09, NFR-AVL-03.

## 조건

- 생성 POST 10 RPS × 600초, 실제 6,000건. 서로 다른 fixture 회원을 사용했다.
- CSRF GET이 요청당 한 번 추가돼 전체 HTTP 요청은 12,000건이다. 완료 폴링은 없다.
- 요청 발생 시각은 한국시간 약 11:31:17~11:41:17이다.
- BE는 현재 미커밋 작업 트리의 메모리 큐 버전이다. CPU 2개, 메모리 2GiB,
  Hikari 최대 10개, 실행 슬롯 3개다.
- AI는 콜백 지원 이미지의 fake LLM이며 생성 지연 60초, 워커 3개, 내부 큐 100개다.
  실제 LLM 비용은 발생하지 않는다.
- 별도 DB·Redis 네임스페이스·BE·AI·Prometheus·Grafana를 사용했다.
  기존 지역 246건·종로 콘텐츠 839건을 복사하고 fixture 6,001명을 준비했다.
  인증 우회는 OFF다. 기존 로컬 서버·업무 데이터는 변경하지 않았다.

## 결과

| 항목 | 결과 |
| --- | ---: |
| 실제 생성 요청 / BE 202 | 6,000 / 6,000 |
| 생성 요청 실제 RPS | 9.9998 |
| dropped iteration / BE 거절 | 0 / 0 |
| POST 평균 / p95 / p99 | 11.03 / 21.05 / 48.90ms |
| POST 최대 | 335.27ms |
| HTTP 오류 | 0 / 12,000 |
| 관측 최대 실행 슬롯 | 3 |
| 관측 최대 대기열 | 5,967 |
| Hikari 관측 최대 active / pending | 7 / 0 |
| Hikari 획득 timeout 증가 | 0 |
| JVM 힙 관측 최대 | 약 191.88MiB |
| BE 컨테이너 메모리: 시작 / 관측 최대 | 621.8 / 650.1MiB |
| AI queue_full | 0 |

큐·Hikari·힙은 약 5초, Docker 메모리는 약 30초 간격의 표본이다.
순간 최대치나 큐 단독 메모리 사용량을 의미하지 않는다. fixture 준비가 끝난 뒤
부하 전 메모리를 시작값으로 사용했으며, 워밍업·GC·다른 로컬 서비스의 영향이 있다.
단일 실행으로 운영 최대 처리량을 입증한 것은 아니다.

### 접수와 완료 구분

부하 종료 후 첫 집계는 COMPLETED 30, PROCESSING 3, PENDING 5,967이었다.
AI ID가 저장된 작업은 33건이고, 메모리 큐 대기는 5,967건이었다.

결과 수집·중지 사이 다음 3건이 추가 완료됐다. 테스트 서버 중지 후 보존된 DB는
COMPLETED 33, PROCESSING 3, PENDING 5,964, FAILED 0이다.
AI ID 36건, 생성권 차감 원장 33건, 완료 알림 33건을 확인했다.

**6,000건 모두 생성된 것이 아니라 BE가 접수한 것이다.** 종료 콜백 커밋마다 다음
작업을 전달했고 실행 슬롯은 3개를 넘지 않았다. AI 큐가 포화되는 대신 BE 대기열이
증가했다. 분당 약 3건의 처리량으로는 분당 600건의 유입을 감당할 수 없으므로
이 조건으로 무기한 운영하면 대기 시간과 대기열이 계속 증가한다.

## 실행 후 상태와 한계

- 테스트 전용 BE·AI는 중지했다. 따라서 메모리 큐는 소실됐고 미완료 DB 이력은 남았다.
  이 중지는 실험 종료이며 정상적인 복구·완료로 해석하지 않는다.
- Grafana는 localhost:3001, Prometheus는 localhost:9091에 유지했다.
  대시보드 UID는 queue-load-10rps이며 조회 시 테스트 시간대를 선택한다.
- 기존 서버와 테스트 DB는 삭제하지 않았다. 다중 BE, 재시작 복구, 콜백 유실,
  15분 실행 제한과 무제한 큐의 장기 메모리 고갈은 검증하지 않았다.
- 전체 완료 확인이 아니라 접수·큐 적재·실행 제한의 지속 부하 실험이다.

## 재현 자료

- 시나리오: scripts/load-test/guidebook-admission.js
- 원시 결과: results/load-test/queue10rps-1793327170597-k6.json
- 지표 표본: results/load-test/queue10rps-1793327170597-metrics.json
- 환경·최종 집계: results/load-test/queue10rps-1793327170597-manifest.json
- 임시 compose·Grafana·Prometheus·수집기: /private/tmp/kgb-queue-load.Spq8Vh
- 테스트 DB: kgb_queue_10rps_1793327170597
- JAR SHA-256: d21cb3151797c8f51105e06590667c9ad10d839fdb9f2734d8fd85c0a5aa4f5c

결과 JSON과 테스트 환경의 비밀값은 Git에 포함하지 않는다.
