# Policy Version

- Current Version: `v1.5.1`
- Effective Date: `2026-03-18`
- Last Updated: `2026-04-15`

## Versioning Rules
- 정책 파일 구조/규칙 변경 시 버전을 갱신한다.
- 권장 체계는 SemVer(`MAJOR.MINOR.PATCH`)를 사용한다.
  - `MAJOR`: 하위 호환이 깨지는 정책 변경
  - `MINOR`: 호환 가능한 정책/절차 추가
  - `PATCH`: 오탈자/표현 보정 등 의미 변화 없는 수정

## Scope
- 이 버전은 다음 정책 배포 파일 집합과 배포 절차 문서에 적용한다.
  - `AGENTS.md`
  - `AI_DEVELOPMENT_POLICY.md`
  - `CONTRIBUTING.md`
  - `SKILL.md`
  - `README.md`
  - `.gitmessage-ai-assisted.txt`
  - `.codex/config.toml`
  - `.codex/agents/*.toml`
  - `skills/*/SKILL.md`
  - `.gitlab/issue_templates/default.md`
  - `.gitlab/merge_request_templates/default.md`
  - `docs/agents/*.md`
  - `.vscode/java.code-snippets`
  - `docs/dev/vscode-snippets-guide.md`
  - `scripts/install-policy.sh`
  - `scripts/update-policy.sh`
  - `scripts/update-codex-subagents.sh`

## Template Repository Notes

- `CHANGELOG.md`는 템플릿 저장소 변경 이력이며 대상 프로젝트에 배포하지 않는다.
- 대상 프로젝트는 배포된 `POLICY_VERSION.md`로 정책 파일 세트의 기준 버전을 확인한다.
