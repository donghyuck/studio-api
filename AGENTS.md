# AGENTS.md

## Scope

This repository is a reusable AI-assisted development policy template.
Work only on policy documents, templates, scripts, Codex agent definitions, and related guidance.
Do not add application code, business features, or project-specific runtime behavior.

## Instruction Order

Apply repository rules in this order:

1. `AI_DEVELOPMENT_POLICY.md`
2. `CONTRIBUTING.md`
3. `SKILL.md`
4. task-specific `skills/*/SKILL.md` when the matching task is requested
5. role-specific `.codex/agents/*.toml` when a subagent is explicitly used
6. role-specific `docs/agents/*.md` when a subagent is explicitly used
7. `.codex/config.toml` as optional metadata only

If rules conflict, follow the narrower rule first.
Policy, security, validation, issue template, MR template, and commit format rules in `AI_DEVELOPMENT_POLICY.md` win.
Do not enable `agents.dir` in `.codex/config.toml`; it can break Codex desktop app loading.

## Working Rules

- Keep changes minimal and traceable to the requested task.
- Reuse existing files before creating new ones.
- Prefer short, declarative policy text.
- Write Issue, MR, and commit body content in Korean.
- Keep commit type/scope, commands, paths, code identifiers, logs, API names, and proper nouns in their original form.
- Do not add speculative abstractions, options, or workflows.
- State assumptions when requirements are unclear.
- Stop and ask if multiple interpretations would change the outcome.
- Do not revert unrelated user changes.

## Required Flow

Use this flow when the task involves repository changes:

Issue → Branch → Develop → Commit → Merge Request → AI Review → Human Review → CI / validation → Merge

If an issue cannot be created, record the reason in the commit body or MR body.

Use a working branch by default:

- `feature/*`
- `bugfix/*`
- `hotfix/*`
- `refactor/*`

Direct changes to `main` require explicit justification and stronger validation notes.

## AI-Assisted Records

- Use `.gitlab/issue_templates/default.md` for issues.
- Select exactly one `Type`, one `Size`, and one `AI-Assisted` value.
- Mark `AI-Assisted: Yes` when AI is used for drafting, planning, coding, review, or validation preparation.
- Use `.gitlab/merge_request_templates/default.md` for MRs.
- Record validation command and result in the commit body or MR body.
- AI-assisted commits must use:

```text
[ai-assisted] <type>(<scope>): <summary>
```

Allowed types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.

## Task-Specific Rule Files

Repository-local `skills/*/SKILL.md` files are required task rule files.
They may not be auto-loaded by Codex runtime.
When a task matches, read the matching file before acting.
The user does not need to mention the skill name.

- Issue draft, update, or creation: `skills/write-issue/SKILL.md`
- MR draft, update, or creation: `skills/write-mr/SKILL.md`
- AI-assisted commit preparation: `skills/write-commit/SKILL.md`

If the matching file is missing, stop and report the missing policy file.

## Subagents

Use subagents only for bounded work that can be reviewed independently.
Define ownership before delegation.
The main author owns final integration and validation.
Record subagent usage in the Issue or MR when used.

## Changelog

Update `CHANGELOG.md` in the same work unit when the change affects policy behavior, templates, scripts, validation procedure, or development process rules.
