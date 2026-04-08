# AGENTS.md

## Repository Instruction Entry Point

When working in this repository, always follow the project rules in this order:

1. `AI_DEVELOPMENT_POLICY.md`
2. `CONTRIBUTING.md`
3. `SKILL.md`
4. `README.md`
5. `.codex/config.toml`
6. `.codex/agents/*.toml`
7. `docs/agents/*.md`
8. `.gitlab/issue_templates/default.md`
9. `.gitlab/merge_request_templates/default.md`
10. `.gitmessage-ai-assisted.txt`

If rules conflict:
- Follow the more specific and narrower rule first.
- `SKILL.md` takes precedence over general coding behavior rules.
- `AI_DEVELOPMENT_POLICY.md` takes precedence for AI-assisted workflow, commit, validation, issue template, and review rules.

## Working Principle

- Do not assume requirements silently.
- State assumptions explicitly when needed.
- Keep changes minimal and in scope.
- Do not add speculative abstractions, configurability, or unrelated refactors.
- Respect existing patterns and design intent.
- If something is unclear, stop and ask.
- Every changed line should be traceable to the requested task.

## Required Workflow

Use this workflow unless the task clearly does not require all steps:

Issue
→ Branch
→ Develop
→ Commit
→ Merge Request
→ AI Review
→ Human Review
→ CI / validation
→ Merge

When subagents are used, apply this extension:

Issue
→ Branch
→ Delegation Plan (task split / ownership)
→ Develop (Main agent + Subagents)
→ Integration
→ Commit
→ Merge Request
→ AI Review
→ Human Review
→ CI / validation
→ Merge

## Skill Resolution

All agents must apply instructions in this order:

1. `AI_DEVELOPMENT_POLICY.md`
2. `CONTRIBUTING.md`
3. root `SKILL.md`
4. role-specific agent definition under `.codex/agents/*.toml`
5. role-specific guidance under `docs/agents/*.md`
6. `.codex/config.toml` (optional metadata only)

If rules conflict:
- Follow the narrower role-specific rule first.
- Policy, security, validation, and commit-format requirements in `AI_DEVELOPMENT_POLICY.md` always win.

If no subagent is specified, use the default main-agent flow with root `SKILL.md` only.

## Issue Rules

- Use the repository issue template: `.gitlab/issue_templates/default.md`
- Select exactly one `Type` value in the issue template.
- Select exactly one `Size` value in the issue template.
- `Size` 기준:
  - `Small (1)`: 단순 수정 / 단일 파일
  - `Medium (2)`: 기능 단위 변경 / 다중 파일
  - `Large (3)`: 구조 변경 / 복수 모듈
- The `AI-Assisted` field in the issue template must have exactly one value selected.
- If AI is used for issue drafting, planning, coding, or review preparation, set `AI-Assisted` to `Yes`.
- If Issue creation is not possible, record the reason and background in the commit body or MR body.

## Branch Rules

Use standard branches:
- `feature/*`
- `bugfix/*`
- `hotfix/*`
- `refactor/*`

Use a working branch by default.
Direct change to `main` is exceptional and must be explicitly justified with stronger background and validation records.

## Commit Rules

- Keep commits small and logically scoped.
- Separate unrelated changes into separate commits.
- AI-assisted commits must follow this format:

  `[ai-assisted] <type>(<scope>): <summary>`

- Allowed commit types:
  - `feat`
  - `fix`
  - `refactor`
  - `test`
  - `docs`
  - `chore`

- Use `.gitmessage-ai-assisted.txt` when preparing AI-assisted commits.

## Coding Rules

- Respect existing patterns and design intent.
- Make minimal, in-scope changes only.
- Do not add speculative features, abstractions, or configuration.
- Do not refactor unrelated code.
- If multiple interpretations exist, present them instead of choosing silently.
- Prefer verifiable goals over vague goals.
- For multi-step work, briefly state the plan and the verification point for each step.

## Subagent Rules

- Use subagents only for bounded, clearly scoped tasks that can be reviewed independently.
- Do not delegate immediate critical-path decisions that require main author judgment.
- Define ownership before delegation (file/module/task boundary) to avoid overlap.
- Main author is responsible for final integration quality and conflict resolution.
- Main author must review subagent outputs before merge.
- Record subagent usage summary in Issue/MR when subagents are used.
- Each role-specific agent must apply the required repository template for its stage.
- Prefer Codex-native agent definitions under `.codex/agents/` for reusable subagents.
- `.codex/config.toml` is optional metadata and does not replace repository policy documents.

## Validation Rules

Every AI-assisted change must leave at least one executable validation record:
- build
- unit test
- smoke test
- manual verification if appropriate

Record validation commands and outcomes in the commit body or MR body.
If subagents are used, include which validation was performed by the main author after integration.

## Merge Request Rules

Use the repository MR template: `.gitlab/merge_request_templates/default.md`

MR must include:
- `Why`
- `What`
- `Validation`

MR checklist must be completed.
AI-assisted changes must be reviewed by a human before merge.

## Review Rules

Before merge, confirm:
- policy-compliant commit message
- issue template used where applicable
- `AI-Assisted` value checked correctly in the issue template
- MR template completed
- validation recorded
- CI / repository verification passed
- no unrelated changes included

## Security Rules

- Never place secrets, tokens, passwords, or personal data in prompts, logs, code comments, or docs.
- Use environment variables or a secret store for sensitive values.

## Changelog Rules

Update `CHANGELOG.md` in the same work unit if the change affects:
- user behavior
- API
- DB schema / query / migration
- operational scripts
- development process rules
- regression-prevention tests or validation procedure
