---
name: write-issue
description: Draft GitLab issues with the repository issue template and AI-assisted tracking fields.
license: MIT
---

# Write Issue

Use this repository-local rule file when drafting, updating, or creating a GitLab issue.
Do not assume this file is auto-loaded by Codex runtime.
Read it before producing the issue artifact.

## Rules

- Use `.gitlab/issue_templates/default.md`.
- Write all issue narrative fields in Korean.
- Keep labels, template field names, commands, paths, code identifiers, logs, API names, and proper nouns unchanged.
- Select exactly one `Type`.
- Select exactly one `Size`.
- Select exactly one `AI-Assisted` value.
- Mark `AI-Assisted: Yes` when AI is used for drafting, planning, analysis, coding, review, or validation preparation.
- Keep the issue short enough to copy into GitLab without cleanup.

## Size

- `Small (1)`: simple change or single file
- `Medium (2)`: feature-level change or multiple files
- `Large (3)`: structural change or multiple modules

## AI Usage

When `AI-Assisted: Yes`, record:

- usage type;
- subagent used: `No` or `Yes`;
- delegated scope when subagents are used;
- main author validation plan.

## Output

Return a completed issue draft with:

- summary;
- background;
- scope;
- acceptance criteria;
- validation plan;
- AI usage fields.
