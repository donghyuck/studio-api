# Review Agents

## Purpose

Use review agents when pending changes need defect analysis, policy checks, or security review.

## Active agents

- `code-reviewer`: review bugs, regressions, missing validation, and policy compliance.
- `security-auditor`: review auth, permission, token, secret, privacy, and data-handling risk.

## Archived references

`architect-reviewer` and `code-mapper` are archived references.
Route active review work through `code-reviewer` or `security-auditor`.

## Expected output

- Findings ordered by severity
- Policy checklist result
- Required fixes before merge
