# AI Development Policy

## Purpose

AI-assisted 변경의 책임, 검증, 기록, 보안 기준을 표준화한다.

## Scope

이 정책은 AI가 초안 작성, 분석, 계획, 코드/문서 수정, 리뷰, 검증 준비에 관여한 모든 변경에 적용한다.

## Rule Priority

1. `AGENTS.md`는 agent 실행 진입점이다.
2. 본 문서는 AI-assisted 작업의 최상위 정책 기준이다.
3. `CONTRIBUTING.md`는 Git 작업 절차를 설명한다.
4. `SKILL.md`는 이 템플릿 저장소 정비 방식에 적용한다.
5. `.codex/agents/*.toml`과 `docs/agents/*.md`는 명시적으로 선택한 subagent에만 적용한다.
6. `.codex/config.toml`은 선택적 메타데이터다.

더 구체적인 규칙이 있으면 그 규칙을 우선한다.
정책, 보안, 검증, issue/MR 템플릿, commit 형식은 본 문서가 우선한다.
Codex 앱 호환성을 위해 `.codex/config.toml`의 `agents.dir`는 활성화하지 않는다.

## Required Principles

- Human owner가 최종 책임을 가진다.
- AI 출력은 검토 없이 병합하지 않는다.
- 변경은 요청 범위 안에서 최소화한다.
- 관련 없는 리팩터링과 형식 변경을 섞지 않는다.
- 비밀정보, 토큰, 비밀번호, 개인정보를 프롬프트, 로그, 주석, 문서에 남기지 않는다.
- 민감값은 환경변수나 secret store를 사용한다.
- GitLab 자동화 토큰은 `.env.local` 또는 secret store에서 로드하고 저장소에 커밋하지 않는다.
- GitLab API 자동화는 전체 JSON 응답을 출력하지 않고 필요한 결과 필드만 남긴다.
- 동일 규칙은 여러 문서에 반복하지 않고 기준 문서로 연결한다.

## AI Workflow

- Write or update the spec before implementation.
- Build incrementally and keep changes traceable to the spec.
- Run `code-reviewer` or equivalent review before merge.
- Run `security-auditor` for auth, permission, token, secret, privacy, or data-handling changes.
- Update documentation when behavior, policy, templates, scripts, or validation procedures change.

## Issue Rules

- Issue는 `.gitlab/issue_templates/default.md`를 사용한다.
- `Type`은 정확히 하나만 선택한다.
- `Size`는 정확히 하나만 선택한다.
- `AI-Assisted`는 정확히 하나만 선택한다.
- AI를 사용한 작업은 `AI-Assisted: Yes`로 기록한다.
- Issue 생성이 불가능하면 사유와 배경을 commit body 또는 MR body에 남긴다.

Size 기준:

- `Small (1)`: 단순 수정 / 단일 파일
- `Medium (2)`: 기능 단위 변경 / 다중 파일
- `Large (3)`: 구조 변경 / 복수 모듈

## Branch Rules

작업 브랜치를 기본으로 사용한다.

- `feature/*`
- `bugfix/*`
- `hotfix/*`
- `refactor/*`

`main` 직접 변경은 예외다.
직접 변경 시 배경과 검증 기록을 더 명확히 남긴다.

## Commit Rules

AI-assisted commit 제목은 다음 형식을 사용한다.

```text
[ai-assisted] <type>(<scope>): <summary>
```

허용 타입:

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`

AI-assisted commit 작성 시 `.gitmessage-ai-assisted.txt`를 사용한다.

## Validation Rules

AI-assisted 변경은 최소 하나의 실행 가능한 검증 기록을 남긴다.

- build
- unit test
- smoke test
- manual verification
- analysis only, when no file changes were made

검증 명령과 결과는 commit body 또는 MR body에 기록한다.
실행할 수 없는 검증은 이유를 기록한다.

## Subagent Rules

- Subagent는 독립 검토가 가능한 명확한 하위 작업에만 사용한다.
- 위임 전 파일, 모듈, 작업 경계를 정한다.
- 즉시 필요한 핵심 판단은 main author가 수행한다.
- Main author가 subagent 산출물을 통합하고 최종 검증한다.
- Subagent를 사용한 경우 Issue 또는 MR에 위임 범위와 통합 후 검증을 기록한다.

## Merge Request Rules

- MR은 `.gitlab/merge_request_templates/default.md`를 사용한다.
- `Why`, `What`, `Validation`을 작성한다.
- Checklist를 완료한다.
- AI-assisted 변경은 merge 전 human review를 거친다.

## Review Rules

Merge 전 다음을 확인한다.

- commit message가 정책 형식을 따른다.
- issue template을 사용했거나 예외 사유를 기록했다.
- `AI-Assisted` 값이 맞다.
- MR template이 작성되었다.
- validation이 기록되었다.
- CI 또는 저장소 검증이 통과했다.
- unrelated change가 없다.

## Changelog Rules

다음 변경은 같은 작업 단위에서 `CHANGELOG.md`를 갱신한다.

- 정책/절차 변경
- issue/MR/commit 템플릿 변경
- 운영 스크립트 변경
- 검증 절차 변경
- 대상 프로젝트의 사용 방식 변경

오탈자처럼 의미 변화가 없는 문서 수정은 생략할 수 있다.

## Document Ownership

- `README.md`: 저장소 목적, 포함 파일, 설치/업데이트/적용 순서
- `AI_DEVELOPMENT_POLICY.md`: AI-assisted 강제 기준
- `CONTRIBUTING.md`: Git 작업 절차
- `SKILL.md`: 템플릿 저장소 정비 방식
- `POLICY_VERSION.md`: 배포 정책 파일 세트의 버전 기준
- `CHANGELOG.md`: 템플릿 저장소 변경 이력
