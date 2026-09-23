# Sanakan 로컬 적용

## SPEC — 적용 범위와 완료 조건

- 목적: 기존 개발 규칙을 보존하면서 Sanakan으로 테스트 보강 작업 한 건을 로컬 실증한다.
- 범위: 저장소 목적 지침 정정, GitHub Issue/PR에 기존 서식 연결, 정책 버전/변경 이력 및 사용 안내.
- 실증 범위: 문서 메타데이터 기존 테스트 파일 한 개의 경계값 테스트 보강.
- 제외: 업무 코드/API/DB/의존성/CI 변경, 운영 실행, 자동 감시 활성화, 원격 이슈/PR 생성 및 push.
- 기존 미커밋 .omx 상태를 보존하고 원본 브랜치와 index를 임의 변경하지 않는다.
- 완료 조건: 실제 Codex의 구현·독립 검증·리뷰 통과, 테스트 patch 범위 확인, 원본에 승인된 변경만 반영.
- 이슈 예외: 사용자 요청에 따른 로컬 적용이므로 원격 이슈를 만들지 않고 로컬 작업 명세로 검증한다.

## 도구와 프로젝트의 역할

Sanakan은 별도 도구 저장소/플러그인에서 실행한다. 이 저장소 안에 runtime이나 credential을 복사하지 않는다.
설정은 저장소 밖 `~/.config/sanakan/studio-api/`에 둔다. 기존 AGENTS와 정책을 우선한다.
로컬 실행은 GitHub 토큰 없이 작업 JSON으로 진행하며 결과를 원격 게시할 수 없다.

## 기준과 검증

- 작업 브랜치: 3.x. 원격 기본 브랜치 main과 구분한다.
- Java: toolchain/release 17. Gradle wrapper: 8.14.5.
- 첫 범위: studio-platform-document-metadata의 기존 BuiltInDocumentMetadataSchemaRegistryTest.

```sh
bash ./gradlew --offline --no-daemon --console=plain --max-workers=1 \
  :studio-platform-document-metadata:test \
  --tests '*BuiltInDocumentMetadataSchemaRegistryTest'
```

실증 전 기준 테스트 7개가 별도 clone에서 통과했다. 현재 머신의 의존성 캐시에 의존하며 전체 모듈 검증이 아니다.
API/모듈 의존성 등이 바뀌는 작업에는 별도 관련 테스트와 scripts/verify-modular-consumers.sh를 검토한다.

## 적용 후 반복 사용

1. 지침 변경을 검토·commit한 뒤 원본 HEAD를 새 baseline으로 고정한다.
2. 외부 project.json의 repo_path를 이 저장소 절대 경로로, baseline을 새 SHA로 지정한다.
3. 작업 JSON의 목적·완료 조건·허용 테스트 파일을 확인하고 expires_at에 승인 만료 Unix 초를 지정한다.
4. Sanakan 도구 폴더에서 같은 설정과 로컬 작업 JSON으로 run을 실행한다.

```sh
python3 -m sanakan run \
  --config "$HOME/.config/sanakan/studio-api/project.json" \
  --issue https://github.com/donghyuck/studio-api/issues/1 \
  --issue-fixture "$HOME/.config/sanakan/studio-api/issue.json" \
  --runs "$HOME/.config/sanakan/studio-api/runs"
```

위 URL의 1은 로컬 식별자이며 실제 원격 이슈를 조회하지 않는다. --issue-fixture를 생략하지 않는다.
임시 작성자/리뷰어 값은 실제 GitHub 자동화 권한으로 사용하지 않는다.
초기 project.json은 만료 값 0으로 배치하며, 지침 commit과 실행 범위를 확인한 뒤 활성화한다.
첫 실증은 원본 commit을 만들지 않기 위해 지침 변경만 포함한 별도 snapshot과 pilot-project.json을 사용한다.

## 기록

2026-09-22 실증 결과:

- 원본 기준 c8457b601303338492431a3c60748d7a9e3996bd에 지침 수정만 포함한 별도 snapshot에서 실행했다.
- 실제 Codex가 기존 테스트 파일 하나에 summary/keyword code point 경계와 중복·개수 제한 테스트 3개를 추가했다.
- 독립 호스트 검증: tests=10, failures=0, errors=0, skipped=0. BUILD SUCCESSFUL (21초).
- 읽기 전용 완료 보고 completed, 독립 리뷰 pass / blocking_count=0, 최종 gate ready.
- patch SHA-256: f8643a978683c4ea820e905df00ea3b7fe135df2409bbcee7d49c06374def3da.
- 원본에 반영한 테스트 파일 bytes가 독립 검증 checkout과 일치함을 확인했다.
- 원본에서는 재빌드하지 않았으며 서버/DB/JAR를 갱신하지 않았다. 기존 .omx 변경은 보존했다.

실증 중 확인한 제약과 처리:

- 선택 BASELINE/PROJECT_MAP 및 .agent 복사본 부재가 정보 누락으로 보고되어 초기 실행이 차단됐다.
  Sanakan은 호스트가 제공한 baseline/issue를 우선하고 선택 문서 부재를 차단 사유로 사용하지 않도록 수정했다.
- 600초 개발 세션 제한에 도달한 기록을 보존하고 후속 실증에는 1200초를 사용했다.
- Worker sandbox가 Gradle lock/socket을 차단했다. sandbox를 해제하지 않고 implemented로 인계한 뒤
  호스트가 검증하고 읽기 전용 완료 보고/리뷰를 수행했다. implemented 자체는 완료나 게시 가능 상태가 아니다.

외부 설정의 project.json은 원본 지침 commit 후 갱신할 반복 사용용이며 만료 값 0으로 유지한다.
pilot-project-v4.json과 issue-v4.json은 이번 snapshot 실증 기록이다. 이전 실패/만료 기록도 지우지 않는다.
Docker 실행과 실제 GitHub Draft PR/reviewer 요청은 이 로컬 적용과 별개의 후속 실증이다.
