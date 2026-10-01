# KGB Backend V1 현재 구현 문서

이 디렉터리는 KGB Backend V1의 **현재 코드와 DB migration에서 확인되는 사실**만 기록한다. 미래 버전 설계, 미구현 API, 개선 제안은 포함하지 않는다.

## 기준

- 기준 브랜치: `origin/dev`
- 기준 commit: `eba28f8`
- 기준일: 2026-10-01
- API 기준: Spring MVC Controller, Spring Security OAuth2 설정, API 테스트
- DB 기준: `src/main/resources/db/migration` 아래 Flyway migration `V1`~`V4`

## 문서

- [API 구현 현황과 유즈케이스](./API-%EA%B5%AC%ED%98%84-%ED%98%84%ED%99%A9.md)
- [V1 테이블 정의서](./%ED%85%8C%EC%9D%B4%EB%B8%94%EC%A0%95%EC%9D%98%EC%84%9C.md)

## 사용 원칙

- Issue 작성 전에 요청한 API가 현재 제공되는지 이 문서에서 먼저 확인한다.
- 새 migration이나 API가 `dev`에 병합되면 같은 PR에서 관련 V1 문서를 갱신한다.
- 문서와 코드가 다르면 Controller·Security 설정·Flyway migration을 우선해 현재 동작을 확인한다.
