---
name: write-commit
description: Prepare AI-assisted commit messages using the repository commit template and validation record.
license: MIT
---

# Write Commit

Use this repository-local rule file when preparing an AI-assisted commit message.
Do not assume this file is auto-loaded by Codex runtime.
Read it before producing the commit artifact.

## Subject

Use this format:

```text
[ai-assisted] <type>(<scope>): <summary>
```

Use English for `<type>` and `<scope>`.
Write `<summary>` in Korean unless a product name or code identifier must stay unchanged.

Allowed types:

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`

## Body

Use `.gitmessage-ai-assisted.txt`.
Write commit body sections in Korean.

Include:

- `Issue`: issue reference or exception reason;
- `Why`: reason for the change;
- `What`: changed policy, template, script, or agent definition;
- `Validation`: command and result.

## Rules

- Keep the commit logically scoped.
- Do not mix unrelated policy, template, script, or agent changes.
- Record why validation could not be run when applicable.
- Do not include secrets, tokens, personal data, or `.env.local` values.

Example:

```text
[ai-assisted] docs(policy): 작업별 skill 언어 규칙 추가
```
