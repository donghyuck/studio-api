# Design Guidance

## Purpose

Design work stays inside the lightweight core workflow.
This repository does not maintain active architecture specialist agents.

## Active routing

- Use `issue-agent` for spec, scope, and acceptance criteria.
- Use `backend-developer` after the implementation boundary is clear.
- Use `code-reviewer` for design-risk review before merge.
- Use `security-auditor` when the design affects trust boundaries or sensitive data.
- Use `docs-agent` for policy, README, changelog, or MR summary updates.

## Archived references

`system-designer` and `architect-reviewer` are archived references.
Do not use them as active execution agents by default.
