---
name: devops-policy-template-maintainer
description: Maintain this reusable AI-assisted DevOps policy template. Use task-specific skills for issue, MR, and commit drafting.
license: MIT
---

# DevOps Policy Template Maintainer

Use this skill when editing this repository.

## Mission

Keep this repository reusable across projects.
Prefer fewer clearer files over more guidance.
Every changed line must support policy distribution, policy maintenance, or agent operation.

## Working Rules

- Treat this as a template repository, not a business system.
- Do not add application code or product-specific behavior.
- Reuse existing files before creating new files.
- Keep policy sentences short and declarative.
- Keep templates copy-ready for real teams.
- Write policy explanations and workflow outputs in Korean unless the user asks otherwise.
- Keep README focused on purpose, file roles, install, update, and application flow.
- Keep AGENTS focused on agent execution rules.
- Keep `AI_DEVELOPMENT_POLICY.md` focused on enforceable AI-assisted rules.
- Keep `CONTRIBUTING.md` focused on human Git workflow.
- Keep `.codex/agents/*.toml` as the active core execution definitions.
- Keep `.codex/agents/_archive/*.toml` as legacy references only.
- Keep `docs/agents/*.md` as short selection guidance.
- Keep task-specific details in `skills/*/SKILL.md`.

## Task Rule Loading

Do not assume repository-local `skills/*/SKILL.md` files are auto-loaded.
Read the matching task rule before creating issue, MR, or commit artifacts.
The user does not need to mention the skill name.

- Issue draft, update, or creation: `skills/write-issue/SKILL.md`
- MR draft, update, or creation: `skills/write-mr/SKILL.md`
- AI-assisted commit preparation: `skills/write-commit/SKILL.md`
- Spec clarification: `skills/spec/SKILL.md`
- Incremental build: `skills/build/SKILL.md`
- Review preparation: `skills/review/SKILL.md`

## Core Agent Workflow

- `spec`: `skills/spec/SKILL.md` + `issue-agent`.
- `build`: `skills/build/SKILL.md` + `backend-developer`.
- `review`: `skills/review/SKILL.md` + `code-reviewer`.
- `secure`: use `security-auditor`.
- `docs`: use `docs-agent`.

Keep `skills/spec/SKILL.md`, `skills/build/SKILL.md`, and `skills/review/SKILL.md` short AI execution aids.
Use archived agents only as references.
Do not add specialized agents unless the same need appears repeatedly and cannot be handled by the core set.

## Change Discipline

Before editing, identify:

1. the file role being clarified;
2. the duplicate rule being removed or linked;
3. the validation command or manual check.

Do not add speculative configuration, optional process branches, or broad refactors.
If two interpretations would produce different template behavior, ask first.

## Validation

For document-only changes, run a repository text check such as:

```bash
git diff --check
```

For script changes, run the changed script with a safe target or `--dry-run` when available.
