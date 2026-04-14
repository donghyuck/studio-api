# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build all modules
./gradlew clean build

# Build a specific module
./gradlew :studio-platform-user:build

# Run all tests in a module
./gradlew :studio-platform-user:test

# Run a single test class
./gradlew :studio-platform-user:test --tests GroupMgmtControllerTest

# Run a single test method
./gradlew :studio-platform-user:test --tests "GroupMgmtControllerTest.someMethodName"

# Build without tests
./gradlew build -x test

# Publish to local Maven cache
./gradlew publishToMavenLocal

# OWASP CVE dependency scan (fails at severity ≥ 7.0)
./gradlew dependencyCheckAnalyze
```

## Commit Message Format

All AI-assisted commits must use:
```
[ai-assisted] <type>(<scope>): <summary>
```
Allowed types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

Update `CHANGELOG.md` in the same commit when the change affects user behavior, API, DB schema/migrations, or operational scripts.

## Module Architecture

This is a **Spring Boot 3.5.9 / Java 17** multi-module Gradle project (`studio-one`). Modules are organized in three layers:

### 1. Platform Modules (`studio-platform-*`)
Core contracts and implementations. The dependency direction is strictly acyclic:

| Module | Purpose |
|---|---|
| `studio-platform` | Core web contracts: `ApiResponse<T>`, `ProblemDetails`, controller naming rules, domain events |
| `studio-platform-data` | JDBC pagination, SQL-XML mapper, file text extraction, DB-backed app properties |
| `studio-platform-autoconfigure` | Centralized Spring Boot auto-configuration, feature conditions, JPA/JDBC auditing |
| `studio-platform-objecttype` | ObjectType registry, policy resolution, runtime validation |
| `studio-platform-security` | Spring Security wrapper, JWT (Nimbus 9.37.3), login audit, account lockout |
| `studio-platform-security-acl` | ACL-based authorization with caching and metrics |
| `studio-platform-user` | User domain contracts only (DTOs, service interfaces, error types) |
| `studio-platform-user-default` | Concrete JPA/JDBC user management (entities, repos, services, REST controllers) |
| `studio-platform-ai` | AI abstraction: chat, embedding, chunking, RAG pipeline, vector store ports |
| `studio-platform-storage` | Object storage abstraction (filesystem / cloud) |
| `studio-platform-identity` | Identity/authentication abstraction |
| `studio-platform-realtime` | WebSocket abstraction |

### 2. Application Modules (`studio-application-modules/*`)
Feature services built on top of platform modules: `avatar-service`, `attachment-service`, `content-embedding-pipeline`, `template-service`, `mail-service`.

### 3. Starters (`starter/studio-*-starter-*`)
Spring Boot auto-configuration starters. Applications compose features by adding starters; each starter wires the relevant platform/application modules and respects `studio.features.*` flags.

## Key Patterns

### Response & Error Format
- All REST responses wrap data in `ApiResponse<T>` (from `studio-platform`).
- Errors use RFC 7807 `ProblemDetails` (`type`, `status`, `title`, `detail`, `instance`).

### Controller Naming
- `*MgmtController` — admin/management endpoints (authorization required)
- `*PublicController` — unauthenticated public endpoints
- `*MeController` — self-service endpoints for the authenticated user
- `*Controller` — standard REST

### Feature Flags & Configuration Namespace
All feature configuration lives under `studio.*` / `studio.features.*`:
```yaml
studio:
  features:
    user.enabled: true
    avatar-image:
      enabled: true
      persistence: jpa   # or jdbc
    ai.enabled: true
```
See `CONFIGURATION_NAMESPACE_GUIDE.md` for the full namespace spec.

### Persistence Mode
Many modules support dual-mode persistence (JPA or JDBC) switchable via config. The `studio-platform-autoconfigure` module provides the conditional beans.

### Database Migrations (Flyway)
SQL migration files live at:
```
<module>/src/main/resources/schema/<feature>/<database>/V<version>__<description>.sql
```
Supported databases: PostgreSQL (primary), MySQL, MariaDB. Version numbers are feature-scoped (e.g., avatar = V700, attachment = V800, mail = V1000).

### Security
JWT authentication via Nimbus JOSE/JWT. Jasypt (`studio-platform-starter-jasypt`) encrypts sensitive properties. ACL authorization via `studio-platform-security-acl`. Sensitive config values go in environment variables (`STUDIO_JWT_SECRET`, `JASYPT_ENCRYPTOR_PASSWORD`).

## Policy Documents

Before making changes, consult these files in priority order:
1. `AI_DEVELOPMENT_POLICY.md` — AI workflow, commit, and validation rules
2. `CONTRIBUTING.md` — Branch rules, changelog rules
3. `SKILL.md` — Behavioral guidelines (minimal changes, no speculative abstractions)
4. `AGENTS.md` — Full rule hierarchy and subagent workflow
