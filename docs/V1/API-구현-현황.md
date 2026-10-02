# V1 API 구현 현황과 유즈케이스

이 문서는 현재 코드에서 실제로 제공하는 API만 유즈케이스 관점으로 정리한다. 모든 경로의 기본 prefix는 `/api/v1`이다. Actuator 경로는 예외다.

## 1. 로그인과 세션

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 사용자가 카카오 로그인을 시작한다 | GET | `/auth/oauth/authorize/kakao` | 공개 | Spring Security OAuth2 인가 요청으로 redirect한다. | `KakaoOAuthStartTest` |
| 카카오 인증 결과로 가입·로그인한다 | GET | `/auth/oauth/callback/kakao` | 공개+로그인 시도 검증 | state·PKCE를 검증하고 회원·서비스 세션을 저장한다. | `KakaoOauthCallbackIntegrationTest`, `OauthLoginMysqlConcurrencyTest` |
| 브라우저가 상태 변경 API용 CSRF 토큰을 준비한다 | GET | `/auth/csrf` | 공개 | CSRF cookie와 요청 header 이름을 반환하고 cache를 금지한다. | `CsrfTokenLifecycleTest` |
| 로그인 실패 코드를 API 오류로 변환한다 | GET | `/auth/oauth/error` | 공개 | 허용된 OAuth 오류 코드를 공통 오류 응답으로 변환한다. | `KakaoOauthFailureHandlerTest` |
| 사용자가 현재 기기에서 로그아웃한다 | POST | `/auth/logout` | 세션+CSRF | 현재 서비스 세션을 폐기하고 session·CSRF cookie를 정리한다. | `SecurityAuthenticationTest`, `CsrfTokenLifecycleTest` |

## 2. 회원과 취향

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 로그인한 회원이 내 프로필과 상태를 확인한다 | GET | `/members/me` | 세션 | 닉네임, 이메일, 프로필 이미지, 회원 상태를 반환한다. | `MemberApiIntegrationTest` |
| 온보딩 화면이 선택 가능한 취향을 조회한다 | GET | `/preference-options` | 세션 | 서버 Enum에 정의된 취향 유형·코드를 반환한다. | `MemberPreferenceApiTest`, `PreferenceCodeTest` |
| 사용자가 현재 기본 취향을 확인한다 | GET | `/members/me/preferences` | 세션 | 회원이 저장한 취향 선택을 반환한다. | `MemberPreferenceApiTest` |
| 사용자가 기본 취향을 전체 저장한다 | PUT | `/members/me/preferences` | 세션+CSRF | 전달한 선택으로 기존 취향을 교체하고 조건을 충족하면 회원을 `ACTIVE`로 전환한다. | `MemberPreferenceApiTest` |
| 사용자가 언어·푸시 설정을 조회한다 | GET | `/members/me/settings` | 세션 | 현재 언어와 푸시 수신 여부를 반환한다. | `MemberSettingsApiTest` |
| 사용자가 푸시 수신 설정을 변경한다 | PATCH | `/members/me/settings` | 세션+CSRF | 푸시 수신 여부를 부분 수정한다. | `MemberSettingsApiTest` |
| 사용자가 회원을 탈퇴한다 | DELETE | `/members/me` | 세션+CSRF | 회원을 소프트 삭제하고 세션·생성 작업·V1 생성권 데이터를 정리한다. | `MemberWithdrawalApiTest`, `MemberLifecycleMysqlIntegrationTest` |

## 3. 정책과 알림

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 사용자가 제공 중인 정책 문서 목록을 확인한다 | GET | `/policies` | 공개 | 약관·개인정보 처리방침의 유형과 버전을 반환한다. | `PolicyApiTest` |
| 사용자가 특정 정책 전문을 읽는다 | GET | `/policies/{policyType}` | 공개 | classpath의 현재 정책 Markdown을 반환한다. | `PolicyApiTest` |
| 사용자가 미읽은 생성 완료 알림을 확인한다 | GET | `/notifications` | 세션 | 최신순 알림을 page·size로 조회한다. | `NotificationApiTest` |
| 사용자가 알림 하나를 읽고 지운다 | DELETE | `/notifications/{notificationId}` | 세션+CSRF | 본인 알림인 경우에만 삭제한다. | `NotificationApiTest` |
| 사용자가 알림을 모두 지운다 | DELETE | `/notifications` | 세션+CSRF | 본인의 알림을 한 번에 최대 20개 삭제한다. | `NotificationApiTest`, `NotificationMysqlConcurrencyTest` |

## 4. 지도와 관심 장소

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 사용자가 현재 지도 영역의 관광 콘텐츠를 본다 | GET | `/map/contents` | 세션 | 남·서·북·동 경계와 zoom을 받아 cluster 또는 개별 콘텐츠를 반환한다. | `MapContentApiTest`, `MapContentQueryTest` |
| 사용자가 장소를 관심 목록에 추가한다 | PUT | `/members/me/favorites/{contentId}` | 세션+CSRF | `ACTIVE` 관광 콘텐츠를 중복 없이 관심 목록에 추가한다. | `FavoriteContentApiTest`, `FavoriteContentMysqlIntegrationTest` |
| 사용자가 관심 장소를 해제한다 | DELETE | `/members/me/favorites/{contentId}` | 세션+CSRF | 본인의 관심 관계를 삭제한다. | `FavoriteContentApiTest` |

## 5. 가이드북 생성과 조회

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 사용자가 AI 가이드북 생성을 접수한다 | POST | `/guidebook-generations` | `ACTIVE` 세션+CSRF | `Idempotency-Key`와 여행 조건을 받아 생성 작업을 저장하고 AI 접수 이벤트를 발행한다. | `GuidebookGenerationControllerTest`, `GuidebookGenerationServiceTest` |
| 사용자가 생성 진행 상태를 확인한다 | GET | `/guidebook-generations/{jobId}` | 세션 | 본인 작업의 현재 상태를 조회하고 필요하면 AI 상태를 동기화한다. | `GenerationStatusApiTest` |
| 실패한 생성 작업을 사용자가 다시 시도한다 | POST | `/guidebook-generations/{jobId}/retry` | `ACTIVE` 세션+CSRF | 본인의 실패 작업을 최대 시도 횟수 안에서 다시 접수한다. | `GuidebookGenerationControllerTest`, `GenerationJobTest` |
| 사용자가 내 가이드북 목록을 본다 | GET | `/guidebooks` | `ACTIVE` 세션 | 삭제하지 않은 본인 가이드북을 cursor·size로 조회한다. | `GuidebookListApiTest` |
| 사용자가 가이드북 일정과 상세를 본다 | GET | `/guidebooks/{guidebookId}` | `ACTIVE` 세션 | 본인이 보관 중인 가이드북과 일정·장소 snapshot을 반환한다. | `GuidebookDetailApiTest` |
| 뷰어가 가이드북 HTML 원문을 표시한다 | GET | `/guidebooks/{guidebookId}/viewer` | `ACTIVE` 세션 | 본인 가이드북의 `content_html`을 반환한다. | `GuidebookDetailApiTest` |
| 사용자가 내 목록에서 가이드북을 지운다 | DELETE | `/guidebooks/{guidebookId}` | `ACTIVE` 세션+CSRF | `member_guidebooks`의 보관 관계를 소프트 삭제한다. | `GuidebookDeleteApiTest` |

## 6. 생성권

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 사용자가 남은 가이드북 생성권을 확인한다 | GET | `/credits/wallet` | `ACTIVE` 세션 | 본인 지갑의 현재 생성권 잔액을 반환한다. | `CreditWalletQueryServiceTest` |
| 운영자가 제공한 쿠폰으로 생성권을 받는다 | POST | `/credits/coupons/redeem` | `ACTIVE` 세션+CSRF | 허용된 쿠폰 코드의 설정 지급량만큼 잔액을 늘리고 새 원장을 기록한다. | `CouponRedemptionServiceTest` |

## 7. 운영 경로

| 유즈케이스 | Method | Path | 접근 | 현재 동작 | 관련 검증 |
| --- | --- | --- | --- | --- | --- |
| 로드밸런서가 서버 생존 상태를 확인한다 | GET | `:8081/actuator/health` | 내부 관리 포트 | Spring Boot Actuator health를 제공한다. | `PrometheusMetricsIntegrationTest` |
| Prometheus가 애플리케이션 지표를 수집한다 | GET | `:8081/actuator/prometheus` | 내부 관리 포트 | Micrometer Prometheus metric을 노출하며 업무 API 포트에는 노출하지 않는다. | `PrometheusMetricsIntegrationTest` |
