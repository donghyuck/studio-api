# Sanakan 로컬 적용

## 현재 적용 버전: 1.14.1 (2026-09-28)

이 프로젝트는 저장소 밖의 고정 Sanakan 런타임을 사용한다. 실행 위치/버전/ZIP digest는 `~/.config/sanakan/studio-api/runtime.json`에 기록했다. 기존 1.12.0 런타임과 모든 이전 실행 디렉터리·실패/리뷰 증거는 보존한다. 앱의 전역 플러그인 목록을 변경한 설치가 아니라 이 프로젝트용 독립 실행기 적용이다.

```sh
python3 "$HOME/.local/share/sanakan/studio-api/1.14.1/scripts/manage.py" --version
```

예상 출력은 `Sanakan 1.14.1`이다. 아래 기존 `python3 -m sanakan` 예시는 이 고정 launcher 뒤에 같은 하위 명령/인수를 붙여 실행할 수 있다. 새 작업에는 이 버전을 사용하고 이전 실행기의 파일을 제자리에서 덮어쓰지 않는다.

### 1.14.1 적용 범위와 새 작업 시작점

이번 요청의 SPEC은 최신 런타임·검증된 모델 정책·새 작업용 템플릿을 연결하고, 버전·스키마·정책·기존 파일 보존을 검사하는 것이다. 앱 코드, 기존 이슈 승인과 실행 기록, 자동 감시·원격 게시·전역 플러그인 설정은 이 적용 범위에 포함하지 않는다.

| 파일 | 용도 |
|---|---|
| `~/.config/sanakan/studio-api/runtime.json` | 현재 launcher, ZIP hash, 모델 정책 및 템플릿 위치 |
| `~/.config/sanakan/studio-api/model-policy-1.14.1.json` | 현재 CLI 계정에서 호출을 확인한 gpt-5.5 프로필 |
| `~/.config/sanakan/studio-api/project-v1.14.1.template.json` | model_policy를 포함한 새 이슈 설정의 출발점. expires_at=0인 실행 보류 템플릿 |

**runtime.json은 위치 안내 파일이며, 기존 이슈 설정에 모델 정책을 자동 주입하지 않는다.** 기존 project.json과 이슈별 설정은 보존했다. 새 이슈는 위 템플릿을 별도 이름으로 복사한 뒤 repo_path/baseline·이슈 범위·검증 명령·작성자/리뷰어·승인 기한을 실제 작업에 맞게 확인한다. 템플릿의 과거 baseline과 최초 실증용 파일 범위를 그대로 새 이슈의 승인으로 사용하지 않는다.

기본 모델은 모두 `gpt-5.5`이며 단순 구현·완료 보고는 medium, 계획·일반 구현·일반 리뷰는 high, 복잡한 구현·보안 구현/리뷰는 xhigh다. 단계 상향은 같은 모델의 추론 수준을 높인다. Codex CLI 0.139.0/현재 ChatGPT 계정에서 세 조합의 최소 구조화 응답을 확인했다. 모델 목록에 보인다는 이유만으로 지원을 가정하거나 실패 시 다른 모델로 자동 대체하지 않는다.

현재 미커밋 변경을 포함해야 하는 작업은 해당 변경을 반영한 승인된 별도 기준점이나 work_contract의 검토된 입력 패치·보존 파일·필수 회귀 조건을 준비한다. 원본 HEAD만 기준으로 삼아 기존 수정을 누락하지 않는다. 상세 설정은 고정 런타임의 `assets/MODEL_ROUTING.md`, `assets/WORK_CONTRACT.md`를 따른다.

기존 실행의 resume에는 원래 설정·모델 정책·이슈·실행 저장소를 유지한다. model_policy나 work_contract를 추가하는 변경은 새 설정과 새 실행에서 검증한다. repair_limit의 명시적 추가 수정은 동일 정책에서만 가능하며, 정책 거부를 다른 모델이나 과거 통과 결과로 우회하지 않는다. 환경 준비안은 inspect-environment의 --previous-plan으로 차이를 확인해도 새 기한을 자동 승인하지 않는다.

적용 검증은 ZIP의 SHA-256·59개 파일 bytes 대조, 설치된 launcher 버전/스키마/명령 옵션, 모델 정책과 난이도·보안 선택 검사, 템플릿의 실행 보류 검사로 한다. Sanakan 1.14.1의 도구 회귀 테스트 196개와 실제 임시 Git 작업의 고정/자동 선택 두 실행은 통과했다. 두 실행 모두 테스트 4개·독립 리뷰·ready와 동일 패치를 확인했으며, 이를 studio-api 전체 테스트나 미완료 보안 이슈 해결로 표시하지 않는다.

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
