# Staging GitOps CI/CD

## 변경 범위

백엔드 `dev` push 시 CI → GHCR 이미지 빌드·업로드 → 배포 저장소 이미지 태그 갱신을 실행한다.
Argo CD는 각 Application에 설정된 Auto Sync 또는 Manual 정책을 따른다. CI는 그 정책을 변경하지 않는다. 워크플로 성공은 **배포 설정 전달 성공**이며 EKS 기동·기능 검증 성공이 아니다.
AWS 인증, ECS 갱신, kubectl, Argo CD API 호출은 스테이징 CI에서 사용하지 않는다.
운영 `backend-cd.yml`의 ECS 배포와 AI 파이프라인은 변경하지 않는다.

기존에는 CI 실패와 무관하게 ECS 배포를 진행했다. 이제 CI가 성공해야 이미지 빌드와 GitOps 갱신이 실행된다.
애플리케이션의 도메인 규칙·API·DB 계약은 바뀌지 않는다. NFR-OPS-05~08의 상태 확인·관측은 실제 Sync 후 별도로 검증한다.

## 준비할 GitHub Secret

BE 저장소 `100-hours-a-week/KTB4-10th-BE`의 Settings → Secrets and variables → Actions에 다음 Secret을 등록한다.

- 이름: `KGB_GITOPS_TOKEN`
- 대상 저장소: `100-hours-a-week/KTB4-10th-Cloud`만 선택한 fine-grained PAT
- Repository permissions: **Contents: Read and write**, Metadata: Read
- 조직 승인이 필요한 토큰이면 승인 완료 후 사용한다. 토큰 소유자에게 대상 저장소 쓰기 권한이 있어야 한다.
- Cloud `main`의 보호 규칙이 직접 push를 허용하는지 확인한다. PR만 허용하는 정책이면 이 워크플로를 PR 생성 방식으로 바꿔야 한다.
- 기본 `GITHUB_TOKEN`은 다른 저장소 쓰기 인증을 대체하지 않는다. Argo CD의 읽기 전용 Deploy Key와도 별개다.
- 토큰 원문은 Git이나 문서에 저장하지 않는다.

## 자동 실행

1. dev push 후 Gradle Build·Checkstyle·SpotBugs가 성공한다.
2. `stag-<sha7>-<한국 날짜 YYYYMMDD>` 이미지를 빌드해 GHCR에 업로드한다.
3. Cloud `main`의 다음 값만 한 커밋으로 변경한다.
   - `environments/staging/auth.yaml` → `image.tag`
   - `environments/staging/guidebook.yaml` → `image.tag`
   - `environments/staging/scheduler.yaml` → `image.tag`
   - 기존 `helm/*/values.yaml`은 공통 기본값이므로 변경하지 않는다.
   - 대상 환경 파일이 빠졌으면 변경 전에 실패한다. 기존 경로로 되돌아가지 않는다.
   - `environments/production`은 변경하지 않는다.
4. AI Worker·일반 환경변수·리소스·Secret·Ingress는 갱신하지 않는다.
5. Auto Sync Application은 자동 반영되고 Manual Application은 직접 Sync한다. 이후 기동·기능을 검증한다.

스테이징 실행은 직렬화한다. GitOps 쓰기 직전에 더 최신 dev가 있으면 이전 실행의 갱신을 건너뛴다.
Cloud push 충돌 시 최신 main에 이미지 태그 변경만 다시 적용하고 최대 3회 push한다. 환경 폴더는 브랜치 대신 같은 main 내 디렉토리로 구분한다. force push는 사용하지 않는다.
GitHub concurrency는 모든 중간 커밋의 전달을 보장하지 않는다. 최신 변경을 반영하는 방식이다.

## EKS 검증 후 운영 승격용 tree 태그

기존 자동 `tag-verified` Job은 제거했다. GitOps 커밋만 성공한 이미지를 배포 검증 완료로 표시하지 않는다.
운영 워크플로가 사용하는 `tree-<tree12>` 형식은 유지하되, EKS 검증 후 별도 수동 워크플로에서 발급한다.

1. Auth·Guidebook·Scheduler를 해당 이미지로 Sync한다.
2. Healthy와 필요한 기능·스케줄러 동작을 검증한다.
3. Actions → **Verify staging image for promotion** → Run workflow에서 `dev`를 선택한다.
4. 검증한 전체 BE 커밋 SHA 40자리, 이미지 태그, EKS 검증 확인 체크를 입력한다.
5. 성공한 해당 SHA의 스테이징 워크플로가 있고 이미지 revision label이 일치할 때만 tree 태그를 발급한다.

새 workflow_dispatch 워크플로는 저장소의 기본 브랜치에도 파일이 있어야 GitHub UI에서 수동 실행할 수 있다.
따라서 운영 승격 전 `backend-staging-verify.yml`을 기본 브랜치(main)에도 반영해야 한다.
실행 브랜치는 dev를 선택하며 다른 브랜치에서는 Job이 실행되지 않는다.
이 확인은 운영자의 수동 EKS 검증을 전제로 한다. CI는 EKS에 접근하여 실제 배포 상태를 검사하지 않는다.
이전 이미지에는 revision label이 없으므로 이번 파이프라인으로 새로 빌드된 이미지부터 사용할 수 있다.

## 실패 및 되돌리기

- CI 실패: 이미지 빌드·GitOps 갱신이 실행되지 않는다.
- 이미지 push 실패: GitOps 태그를 변경하지 않는다.
- 토큰·브랜치 규칙·충돌로 GitOps 갱신 실패: Actions 실패 상태를 확인하고 문제를 해결한 뒤 재실행한다.
- Sync 후 장애: Cloud environments/staging의 image.tag를 이전 검증 버전으로 되돌리고 Argo CD 정책에 따라 반영한다.
- 이 변경은 기존 ECS 서비스를 삭제하거나 중지하지 않는다. 기존 ECS 스케줄러 중복 실행 점검은 별도 작업이다.

## 검증 범위

로컬에서 Actions 문법, 셸 문법, 태그 변경 스크립트의 실제 Helm 파일 처리·오류 시 무변경·멱등성을 확인한다.
GHCR 업로드·Cloud 쓰기·Argo CD Sync는 GitHub Secret 등록 및 변경 병합 후 첫 실행으로 확인한다.

## 공통 values 정리

Argo CD가 환경별 파일을 읽도록 전환을 완료했습니다. helm/*/values.yaml에는 공통 기본값만 둡니다.
스테이징 CI는 environments/staging의 백엔드 이미지 태그 3개만 갱신합니다.

이번 정리의 적용 순서:
1. 이 BE 변경(기존 chart values 미러 갱신 제거)을 dev에 먼저 병합합니다.
2. Cloud main에 공통 values 정리를 반영합니다.
3. 이전과 같은 환경 값으로 같은 리소스를 렌더링하는지 확인합니다.

운영은 environments/production에 별도 값을 작성하며 이번 CI 변경의 쓰기 대상에서 제외합니다.
운영 ECS CI/CD는 변경하지 않습니다.
