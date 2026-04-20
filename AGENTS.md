# AGENTS.md

Operational guide for AI agents using this policy template in real projects.

## Repository Purpose

This repository is policy-first.
It provides GitLab-oriented AI-assisted development rules, templates, commit policy, lightweight workflow skills, and Codex subagent definitions.
Do not turn it into an application repository, a generic skill catalog, or a second AI framework.

## Rule Order

Apply rules in this order:

1. `AI_DEVELOPMENT_POLICY.md`
2. `CONTRIBUTING.md`
3. root `SKILL.md`
4. matching `skills/*/SKILL.md`
5. active `.codex/agents/*.toml` when a Codex subagent is explicitly used
6. `docs/agents/*.md` when extra agent guidance is needed
7. `.codex/config.toml` as optional metadata only

If rules conflict, follow the narrower rule.
Policy, security, validation, Issue/MR template, and commit format rules in `AI_DEVELOPMENT_POLICY.md` win.
Do not enable `agents.dir` in `.codex/config.toml`.

## Core Operating Principles

- Clarify requirements before implementation.
- Do not implement immediately when the task is ambiguous.
- If no spec exists, create a minimal one first.
- Keep every change traceable to the request or spec.
- Work incrementally; avoid large unreviewable changes.
- Use existing files before creating new ones.
- Do not add speculative workflows, options, or abstractions.
- Do not revert unrelated user changes.
- Do not finish until the change is review-ready.

## Default Workflow

Use this internal lifecycle for repository work:

1. `SPEC`: define outcome, scope, constraints, acceptance criteria, and ambiguity.
2. `BUILD`: implement the smallest coherent change.
3. `REVIEW`: check correctness, risk, maintainability, validation, and policy compliance.
4. `SECURITY`: add security review when auth, permissions, secrets, external input, privacy, or sensitive data are affected.
5. `DOCS`: update docs when behavior, policy, workflow, templates, scripts, or API usage changes.

Do not require slash commands.
Do not require explicit subagent selection for simple work.
The main agent must still follow this lifecycle internally.

## Intent Mapping

- Issue draft, scope, acceptance criteria → `SPEC`
- Implementation or bug fix → `SPEC` then `BUILD`
- Code review, regression risk, validation gap → `REVIEW`
- Auth, permissions, secrets, external input, privacy, sensitive data → `SECURITY`
- README, changelog, policy, templates, MR summary → `DOCS`
- Commit message, Issue, MR artifact → use the matching `skills/write-*` rule

## Role Mapping

- `issue-agent` → `SPEC`
- `backend-developer` → `BUILD`
- `code-reviewer` → `REVIEW`
- `security-auditor` → `SECURITY`
- `docs-agent` → `DOCS`

Archived agents under `.codex/agents/_archive/` are legacy references only.
Do not use archived agents as active execution choices.
Do not add specialized agents unless repeated real project work proves the core set is insufficient.

## Default Mode

Use default mode when no subagent is explicitly requested.
The main agent owns the full lifecycle:

- read the relevant policy files;
- read matching `skills/*/SKILL.md` only when needed;
- make a minimal spec before code changes;
- implement incrementally;
- prepare validation evidence;
- prepare review-ready output.

## Role-Specific Mode

Use role-specific Codex subagents only for bounded work that can be reviewed independently.
Define ownership before delegation.
Subagents complement the lifecycle; they do not bypass it.
The main author remains responsible for integration, validation, and final quality.
Record subagent usage in the Issue or MR when used.

## Task-Specific Skills

Repository-local skills are execution aids, not an auto-loaded framework.
When the task matches, read the matching file before producing the artifact:

- Spec clarification: `skills/spec/SKILL.md`
- Incremental build: `skills/build/SKILL.md`
- Review preparation: `skills/review/SKILL.md`
- Issue draft/update/create: `skills/write-issue/SKILL.md`
- MR draft/update/create: `skills/write-mr/SKILL.md`
- AI-assisted commit message: `skills/write-commit/SKILL.md`

If a required skill file is missing, stop and report it.

## GitLab and Commit Records

- Use `.gitlab/issue_templates/default.md` for Issues.
- Use `.gitlab/merge_request_templates/default.md` for MRs.
- Use `.gitmessage-ai-assisted.txt` for AI-assisted commits.
- Select exactly one `Type`, `Size`, and `AI-Assisted` value in Issues.
- Mark `AI-Assisted: Yes` when AI is used for drafting, planning, coding, review, or validation preparation.
- Record validation command and result in the commit body or MR body.
- If no Issue exists, record the exception reason in the commit body or MR body.
- Use `[ai-assisted] <type>(<scope>): <summary>` for AI-assisted commits.

Allowed commit types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.

## Anti-Rationalization Rules

Reject these shortcuts:

- "This is too small to analyze first."
- "I can quickly patch this."
- "A tiny change does not need review."
- "The template is optional for this artifact."
- "Security review is unnecessary because the change is small."

Correct behavior:

- define the minimum spec;
- make the smallest scoped change;
- verify it;
- review it;
- record the required policy evidence.

## Practical Execution

- Start by identifying the lifecycle stage: `SPEC`, `BUILD`, `REVIEW`, `SECURITY`, or `DOCS`.
- Stay in main-agent mode unless a bounded subagent task is useful.
- Ask only when ambiguity changes the outcome.
- Keep Issue, MR, and commit bodies in Korean.
- Keep commands, paths, code identifiers, logs, API names, and proper nouns unchanged.
- Never expose secrets, tokens, passwords, or personal data.
- Update `CHANGELOG.md` when policy behavior, templates, scripts, validation procedure, or development process changes.
