---
name: write-mr
description: Draft GitLab merge requests with Why, What, Validation, risk, and AI/subagent usage records.
license: MIT
---

# Write MR

Use this repository-local rule file when drafting, updating, or creating a GitLab Merge Request.
Do not assume this file is auto-loaded by Codex runtime.
Read it before producing the MR artifact.

## Required Template

When creating or updating an MR, use this exact section order:

1. `## Why`
2. `## What`
3. `## Related Issues`
4. `## Validation`
5. `## Risk / Rollback`
6. `## AI / Subagent Usage`
7. `## Checklist`

Do not replace the template with a free-form summary.

## Rules

- Use `.gitlab/merge_request_templates/default.md`.
- Keep the MR body copy-ready.
- Write `Why`, `What`, `Validation`, `Risk`, and `Rollback` in Korean.
- Keep commands, paths, code identifiers, logs, API names, and proper nouns unchanged.
- Fill `Why`, `What`, and `Validation`.
- Record linked issues or explain when no issue exists.
- Record AI and subagent usage accurately.
- Do not claim validation that was not performed.

## Validation

Record:

- command;
- result;
- reason when validation could not be run.

## Risk / Rollback

Keep this practical:

- name the main risk introduced by the change;
- state the rollback or revert path;
- write `N/A` only when there is no meaningful deployment or runtime risk.

## Checklist

Confirm:

- commit message follows policy;
- issue template was used or exception was recorded;
- `AI-Assisted` value is correct;
- validation is recorded;
- subagent usage is recorded when used;
- CI or repository verification passed;
- human review is required before merge;
- unrelated changes are excluded.
