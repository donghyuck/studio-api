# Contributing

## 기본 원칙

- 작은 단위로 변경한다.
- 변경 이유와 검증 결과를 남긴다.
- 관련 없는 변경은 분리한다.
- 정책성 규칙은 `AI_DEVELOPMENT_POLICY.md`에 둔다.
- README는 Studio API 목적, 모듈 구성, 실행과 적용 방법을 설명한다.

## 표준 흐름

Issue → Branch → Develop → Commit → Merge Request → Review → CI / validation → Merge

Issue 생성이 어려운 경우 commit body 또는 MR body에 사유를 기록한다.

## AI Workflow

- Start with a spec or issue draft before implementation.
- Build in small increments and keep changes scoped.
- Review before merge using `code-reviewer` or equivalent human review.
- Run security review for auth, permission, token, secret, privacy, or data-handling changes.
- Update documentation when behavior, policy, templates, scripts, or validation procedures change.

## Issue

- GitHub Issue도 `.gitlab/issue_templates/default.md`의 필수 항목을 사용한다.
- `Type`, `Size`, `AI-Assisted`를 각각 하나씩 선택한다.
- AI가 사용되면 `AI-Assisted: Yes`로 기록한다.
- Subagent를 사용하면 위임 범위와 main author 검증을 기록한다.

## Branch

작업 브랜치를 기본으로 사용한다.

- `feature/*`: 기능 추가/개선
- `bugfix/*`: 일반 버그 수정
- `hotfix/*`: 긴급 수정
- `refactor/*`: 동작 변경 없는 구조 개선

## Commit

- 커밋은 논리 단위로 나눈다.
- 허용 타입은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore`다.
- AI-assisted commit은 `[ai-assisted] <type>(<scope>): <summary>` 형식을 따른다.
- AI-assisted commit은 `.gitmessage-ai-assisted.txt` 사용을 권장한다.

## Merge Request

- GitHub PR도 `.gitlab/merge_request_templates/default.md`의 필수 항목을 사용한다.
- `Why`, `What`, `Validation`을 작성한다.
- Checklist를 완료한다.
- AI-assisted 변경은 human review 후 merge한다.

## Optional GitHub/GitLab Automation

- 기존 GitLab 도구는 `.env.local`을 사용할 수 있다. Sanakan은 별도 설정/환경변수 계약을 따른다.
- 기존 GitLab 도구의 변수명은 `GITLAB_TOKEN`, `GITLAB_BASE_URL`, `GITLAB_PROJECT_ID`이다.
- Sanakan의 로컬 적용 절차는 `docs/dev/sanakan.md`를 따른다. 사용자가 승인하지 않은 감시/원격 게시를 활성화하지 않는다.
- `.env.local`은 커밋하지 않는다.
- 토큰 값은 프롬프트, 로그, commit, MR에 남기지 않는다.
- API 응답 전체 JSON을 출력하지 않고 필요한 필드만 출력한다.
- Issue/MR 생성 결과는 `iid`, `web_url`, `state`만 남긴다.

## Validation

- 변경 범위에 맞는 검증을 최소 하나 수행한다.
- 실행한 명령과 결과를 commit body 또는 MR body에 남긴다.
- 검증을 실행할 수 없으면 이유를 남긴다.

## Changelog / Version

- 정책, 템플릿, 스크립트, 검증 절차가 바뀌면 `CHANGELOG.md`를 갱신한다.
- 배포 정책 파일 세트의 의미가 바뀌면 `POLICY_VERSION.md`를 갱신한다.
- `CHANGELOG.md`에는 Studio API와 해당 개발 정책 변경을 함께 기록한다.

## Application Repository Rules

- 새 문서보다 기존 문서 정리를 우선한다.
- Studio API의 모듈 경계와 실제 검증 명령을 기준으로 설명한다.
- 승인된 범위의 애플리케이션 코드와 테스트를 구현하며 관련 없는 기능은 추가하지 않는다.
- 스크립트 변경 시 사용법과 검증 방법을 함께 확인한다.
