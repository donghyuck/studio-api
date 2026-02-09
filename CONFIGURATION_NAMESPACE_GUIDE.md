# Configuration Namespace Guide

This guide defines YAML namespace conventions for the `studio-*` modules.

## Goal
- Keep module settings predictable across projects.
- Avoid long and duplicated keys.
- Separate feature wiring from runtime tuning.

## Namespace Model
Use two layers:

1. `studio.features.<module>.*`
- Purpose: feature wiring and exposure.
- Common keys:
  - `enabled`
  - `persistence.*`
  - `web.*`

2. `studio.<module>.*`
- Purpose: module runtime details and policy tuning.
- Examples:
  - `studio.user.password-policy.*`
  - `studio.objecttype.registry.cache.*`
  - `studio.realtime.stomp.*`

## What Belongs in `studio.features.<module>.*`
- `enabled`: enable/disable feature.
- `persistence.*`: repository/entity/tx binding, JPA/JDBC mode.
- `web.*`: base path, endpoint exposure, self/mgmt/public toggles.

Do not place deep policy values here (password policy, cache TTL, rate rules).

## What Belongs in `studio.<module>.*`
- Policy values.
- Cache and performance tuning.
- Runtime behavior that can vary by environment.

## Example
```yaml
studio:
  features:
    user:
      enabled: true
      persistence:
        type: jpa
      web:
        enabled: true
        base-path: /api/mgmt
        self:
          enabled: true
          path: /api/self

  user:
    password-policy:
      min-length: 12
      max-length: 64
      require-upper: true
      require-lower: true
      require-digit: true
      require-special: true
      allowed-specials: "!@#$%^&*"
      allow-whitespace: false
```

## Conflict and Override Rule
- Same semantic key must exist in only one namespace.
- If temporary compatibility requires both:
  - New key is source of truth.
  - Old key is fallback only.
  - Startup log must print deprecation warning for old key usage.

## Migration Rule
1. Introduce new key.
2. Keep old key as fallback for 1-2 releases.
3. Emit deprecation warning.
4. Remove old key after migration window.

## Module Checklist
For each new module:
- Define `studio.features.<module>.enabled`.
- Define `studio.features.<module>.persistence.*` if persistence exists.
- Define `studio.features.<module>.web.*` if endpoint exists.
- Define runtime detail keys under `studio.<module>.*`.
- Document default values and environment examples in module README.

## Current Recommendation for User Module
- Keep wiring keys:
  - `studio.features.user.enabled`
  - `studio.features.user.persistence.*`
  - `studio.features.user.web.*`
- Move/keep policy keys under:
  - `studio.user.password-policy.*`

## Key Mapping Table (Initial)
This table is the recommended migration baseline for mixed namespaces.

| Current key (legacy/mixed) | Recommended key (target) | Decision | Adoption status |
| --- | --- | --- | --- |
| `studio.features.user.enabled` | `studio.features.user.enabled` | keep | adopted |
| `studio.features.user.persistence.*` | `studio.features.user.persistence.*` | keep | adopted |
| `studio.features.user.web.*` | `studio.features.user.web.*` | keep | adopted |
| `studio.features.user.password-policy.*` | `studio.user.password-policy.*` | migrate | not started (current code uses legacy path) |
| `studio.security.enabled` | `studio.security.enabled` | keep (global infra) | adopted |
| `studio.security.jwt.*` | `studio.security.jwt.*` | keep (global infra) | adopted |
| `studio.persistence.*` | `studio.persistence.*` | keep (global infra) | adopted |
| `studio.features.objecttype.enabled` | `studio.features.objecttype.enabled` | keep | adopted |
| `studio.features.objecttype.web.*` | `studio.features.objecttype.web.*` | keep | adopted |
| `studio.objecttype.mode` | `studio.objecttype.mode` | keep (runtime detail) | adopted |
| `studio.objecttype.registry.cache.*` | `studio.objecttype.registry.cache.*` | keep (runtime detail) | adopted |
| `studio.objecttype.policy.cache.*` | `studio.objecttype.policy.cache.*` | keep (runtime detail) | adopted |
| `studio.features.realtime.enabled` | `studio.features.realtime.enabled` | keep | adopted |
| `studio.realtime.stomp.*` | `studio.realtime.stomp.*` | keep (runtime detail) | adopted |
| `studio.features.attachment.enabled` | `studio.features.attachment.enabled` | keep | adopted |
| `studio.features.attachment.web.*` | `studio.features.attachment.web.*` | keep | adopted |
| `studio.features.attachment.storage.*` | `studio.attachment.storage.*` | migrate (optional, if detail split is adopted) | not started |

### Migration Note
- For `migrate` rows: support both keys during transition.
- Read order: **target key first**, then legacy key fallback.
- Emit deprecation warning when legacy key is used.

### Adoption Status Legend
- `adopted`: key structure already aligned with target.
- `not started`: migration not yet implemented in code.
- `partial`: both key paths are supported, migration in progress.
