# AGENTS.md

## Repository Instruction Entry Point

When working in this repository, always follow the project rules in this order:

1. `AI_DEVELOPMENT_POLICY.md`
2. `CONTRIBUTING.md`
3. `SKILL.md`
4. `README.md`
5. `.gitlab/issue_templates/default.md`
6. `.gitlab/merge_request_templates/default.md`
7. `.gitmessage-ai-assisted.txt`

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

## Issue Rules

- Use the repository issue template: `.gitlab/issue_templates/default.md`
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

## Validation Rules

Every AI-assisted change must leave at least one executable validation record:
- build
- unit test
- smoke test
- manual verification if appropriate

Record validation commands and outcomes in the commit body or MR body.

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