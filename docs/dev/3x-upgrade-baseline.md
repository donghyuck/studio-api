# 3.x framework upgrade baseline

## Purpose

This document fixes the reproducible baseline and validation gates for the
Spring Boot 4.1, Spring AI 2.0, Jackson 3, and MyBatis 4 migration.

The `2.x` line remains the Spring Boot 3 maintenance line. Breaking framework
changes are made only on `3.x`.

## Branch baseline

| Item | Value |
|---|---|
| `3.x` starting commit | `0ed2f040b52ccd8990ee2e87c5d0d18e06f3fc83` |
| `origin/2.x` comparison commit | `2a14f8f0dda79ed781dff8d76cd76381889e50f2` |
| 2.1 rollback tag | `v2.1.0-rc.1` (annotated, local until release push) |
| Tree comparison | Identical |
| Starting artifact version | `2.1.0-rc.1` |
| Target release candidate | `3.0.0-rc.1` |
| Java release | 17 |
| Gradle wrapper | 8.14.5 |

The two branch tips have different commit identities because the same release
candidate change was recorded independently. Their source trees were confirmed
to be identical before the upgrade started. The histories are not merged to
manufacture a common baseline.

## Target dependency baseline

| Component | 2.1 baseline | 3.x target |
|---|---|---|
| Spring Boot | 3.5.16 | 4.1.0 |
| Spring AI | 1.1.8 | 2.0.0 |
| MyBatis Spring Boot Starter | 3.0.5 | 4.0.1 |
| Jackson | 2.x | Boot-managed 3.x |
| Java bytecode | 17 | 17 |
| Gradle | 8.14.5 | 8.14.5 |

Java 21 bytecode and semantic-cache serving are explicitly outside this
upgrade. They require separate decisions after the framework migration is
stable.

## Baseline validation

Executed on 2026-07-26 before changing the artifact version:

```text
GRADLE_USER_HOME=<isolated-cache> \
  ./gradlew compileJava compileTestJava --continue --no-daemon

BUILD SUCCESSFUL in 3m 7s
150 actionable tasks: 150 executed
```

The non-database test baseline also passed after changing only the artifact
version:

```text
GRADLE_USER_HOME=<isolated-cache> ./gradlew test --continue --no-daemon

BUILD SUCCESSFUL in 2m 35s
239 actionable tasks: 90 executed, 149 up-to-date
```

The repository did not track `gradle/wrapper/gradle-wrapper.jar`, so
`./gradlew` initially failed with `ClassNotFoundException:
org.gradle.wrapper.GradleWrapperMain`. The wrapper was regenerated with the
configured Gradle 8.14.5 distribution and is tracked on `3.x`.

## Jackson mapper boundary

Jackson 3 migration uses the application-managed `ObjectMapper` at every
external boundary. HTTP clients and controllers, Redis payloads, and JDBC JSON
columns receive the mapper through constructor or bean-method injection.
Boundary components no longer create a fallback mapper when injection is
missing; an incomplete application context therefore fails at startup.

The only production-local mapper is `CatalogResourceLoader`. It parses the
versioned model catalog bundled in the same artifact and does not consume HTTP,
Redis, database, or user-provided payloads. This isolated parser intentionally
does not inherit application serialization customizations.

Spring AI's OpenAI and Google GenAI provider SDKs still bring their own Jackson
2 runtime (`jackson-databind` 2.21.4 through OpenAI Java 4.39.1 and Google GenAI
1.58.0 in the test adapter graph). This is an external provider implementation
detail. Studio production sources do not import its mapper API or expose it as
the application `ObjectMapper`; HTTP, Redis, and JDBC JSON boundaries remain on
Boot-managed Jackson 3.

Validation executed after the boundary cleanup:

```text
rg -n 'new ObjectMapper\(|JsonMapper\.builder\(\)\.build\(' \
  --glob '**/src/main/java/**/*.java'

studio-platform-ai-model-catalog/.../CatalogResourceLoader.java

GRADLE_USER_HOME=<isolated-cache> \
  ./gradlew compileJava compileTestJava --continue --no-daemon

BUILD SUCCESSFUL in 26s
150 actionable tasks: 3 executed, 147 up-to-date

GRADLE_USER_HOME=<isolated-cache> ./gradlew test --continue --no-daemon

BUILD SUCCESSFUL in 36s
239 actionable tasks: 1 executed, 238 up-to-date
```

## MyBatis and database compatibility

MyBatis resolves to `mybatis-spring-boot-starter` 4.0.1,
`mybatis-spring` 4.0.0, and MyBatis 3.5.19. The starter-focused tests pass
without a Spring Boot 3 compatibility bridge.

`DatabaseSchemaCompatibilityTest` uses Flyway and disposable target databases
to apply every module-owned migration in global version order. PostgreSQL uses
`pgvector/pgvector:pg16` with the `vector` extension enabled before migration;
MySQL uses 8.4 and MariaDB uses 11.4. The test found and corrected dialect files
that contained PostgreSQL-only types, comments, vector indexes, generated-column
referential actions, or unsupported `ADD COLUMN IF NOT EXISTS` syntax.

Validation executed on 2026-07-26:

```text
GRADLE_USER_HOME=<isolated-cache> \
  ./gradlew :starter:studio-platform-starter:test \
  -PrunDbTests=true --tests '*DatabaseSchemaCompatibilityTest' --no-daemon

BUILD SUCCESSFUL in 1m 6s
PostgreSQL: 72 migrations
MariaDB: 63 migrations
MySQL: 64 migrations
```

The seven PostgreSQL JPA integration tests changed from `create-drop` to
`create` and were rerun in their owning modules. They pass, and shutdown closes
the entity manager and pool without attempting DDL after the Testcontainers
database has stopped.

The corrected MySQL and MariaDB files were not executable as written on the
target database versions. Before the release candidate is promoted, any real
non-PostgreSQL Flyway history must be compared with these files. The application
must not run an automatic Flyway `repair`; an existing checksum requires an
explicit operator-reviewed migration decision.

## Spring AI 2 provider contract

Chat adapters use the Spring AI 2 model API and normalize provider model name,
token usage, latency, and prompt-cache usage into the Studio
`ChatResponseMetadata` contract. The sync and streaming paths share the same
metadata conversion.

Embedding requests now carry a typed `EmbeddingPurpose` separately from the
content `EmbeddingInputType`:

- indexing sends `INDEX`;
- retrieval sends `QUERY`;
- callers outside either flow retain `UNSPECIFIED`.

The model deployment's `EmbeddingSpaceContract.indexTaskType` and
`queryTaskType` are passed into the provider factory. This is required because
Spring AI 2.0.0 retains Google `taskType` in its options but does not forward it
to the Google SDK `EmbedContentConfig`. `GoogleGenAiEmbeddingAdapter` uses the
Spring AI connection details and sends the complete SDK request. It sends
`RETRIEVAL_DOCUMENT` for indexing and `RETRIEVAL_QUERY` for retrieval when
configured. `gemini-embedding-2` omits `taskType`; startup rejects an explicit
task contract for that model.

Focused validation:

```text
./gradlew \
  :starter:studio-platform-starter-ai:test \
  :starter:studio-platform-starter-ai-web:test \
  :studio-platform-ai:test --no-daemon

BUILD SUCCESSFUL in 20s
```

The provider-focused fixtures cover OpenAI, Google GenAI, Ollama, and TEI
registration/adapters, Google SDK request options, model/deployment identity,
sync/stream usage metadata, and embedding index/query purpose propagation.
Live provider calls remain part of the development-server compatibility matrix;
tests never require or log real provider credentials.

## Golden contract inventory

The following contracts must have fixtures before their implementation
dependencies are replaced:

- REST success and error JSON, including authentication and authorization;
- JWT login and refresh responses;
- RAG synchronous response and SSE `delta -> usage -> complete` sequence;
- model catalog and active chat/embedding deployment responses;
- Redis exact-answer cache payload and fail-open behavior;
- persisted JSON columns used by Markdown, SkillGraph, RAG, and retrieval
  policy stores;
- chat and embedding provider/model identity metadata.

Existing tests are reused where they already prove a contract. Missing
fixtures are added before the corresponding Jackson, Spring AI, or Boot
boundary is changed.

## Consumer compatibility matrix

| Consumer | Owner | Context | Security | Chat | Embedding | RAG sync | RAG SSE | Redis | Database |
|---|---|---|---|---|---|---|---|---|---|
| Development server | `studio-one-api-server` | Pass | Pass | Pass | Pass | Pass | Pass | Pass | Pass |

The development server was validated on 2026-07-26 from the isolated
`codex/3x-upgrade` worktree against the composite `studio-api-3x` build:

- a fresh PostgreSQL 16 + pgvector database applied all 71 migrations and
  passed Hibernate 7 schema validation;
- unauthenticated AI endpoints returned 401 and bootstrap authentication
  succeeded without moving the application's existing `@Cacheable` domain
  objects to Redis;
- the AI information endpoint returned 3 configured providers and 6 logical
  deployments, with `chat-default` and `humanities-text-v1` selected;
- with answer cache `none`, RAG sync returned the safe `NO_RAG_RESULTS`
  response, RAG SSE completed as `rag_status -> rag_status -> delta ->
  complete`, and no Redis v2 key was created;
- with answer cache `redis`, an indexed smoke document produced `MISS` then
  `HIT`, both with `INDEX_VALID` canonical content;
- changing the indexed revision and evidence produced a new `MISS` and a
  distinct v2 key;
- stopping Redis still returned HTTP 200 with `MISS/INDEX_VALID`; cache read
  and write failures logged only the exception type.

The live calls used an ephemeral database and smoke document. No credential,
token, provider response body, source text, or query is retained in this
repository. All required development-server matrix cells are complete for
`3.0.0-rc.1`.

## RC final verification

The following gates were rerun after changing the platform and consumer
versions to `3.0.0-rc.1` on 2026-07-26:

```text
./gradlew compileJava compileTestJava --continue --no-daemon
BUILD SUCCESSFUL in 1m 17s
150 actionable tasks

./gradlew test --continue --no-daemon
BUILD SUCCESSFUL in 3m 33s
239 actionable tasks

./gradlew test -PrunDbTests=true --continue --no-daemon
BUILD SUCCESSFUL in 4m 26s
239 actionable tasks

./gradlew dependencyCheckAnalyze --no-daemon
BUILD SUCCESSFUL in 2m 14s
24 analyzed modules, 0 vulnerabilities

./gradlew check --no-daemon
BUILD SUCCESSFUL in 1m 19s
verify3xRuntimeClasspath passed

./gradlew publishToMavenLocal --no-daemon
BUILD SUCCESSFUL in 53s
386 actionable tasks
```

The `studio-one-api-server` consumer test also passed against the RC composite
build with 147 actionable tasks. `git diff --check` passed in both repositories,
no pre-RC development version remains in release configuration or upgrade
documentation, and the only Jackson 2 runtime libraries are the documented
provider-SDK implementation boundary.

## Consumer-specific Boot 4 findings

Spring Boot 4 moved Flyway auto-configuration into
`spring-boot-starter-flyway`. Keeping only `flyway-core` leaves migrations
disabled and causes Hibernate validation to run against an empty schema. The
consumer now uses the starter together with
`flyway-database-postgresql`.

Adding `spring-boot-starter-data-redis` also makes Redis eligible as the global
Spring Cache backend. The consumer explicitly keeps `spring.cache.type` set to
`caffeine`; the RAG exact-answer cache uses `StringRedisTemplate` independently.
This avoids introducing a Redis serialization contract for existing cached
domain entities.

## Rollback baseline

- Code rollback uses annotated tag `v2.1.0-rc.1`; breaking commits are not
  back-merged. The tag currently remains local and must be pushed as part of
  the release operation.
- Database changes remain additive and readable by the rollback artifact.
- Redis exact cache uses a separate namespace after the Jackson 3 cutover.
- Set `studio.ai.rag.answer-cache.type=none` before rolling the application
  artifact back. Existing v2 keys may expire naturally or be deleted by prefix
  after the old application is stable.
- Keep the existing Caffeine application-cache selection during rollback; it
  is independent of the RAG Redis backend.
- Provider failures can disable the affected deployment without changing the
  canonical model identity of other deployments.

The rollback procedure was executed on 2026-07-26 rather than documented only:

1. A clean detached platform worktree was created from `v2.1.0-rc.1`.
2. Its full `test` gate passed with 239 executed tasks.
3. A clean 2.x consumer worktree used that platform through composite
   substitution and passed all 147 test tasks.
4. The 2.1 server was started with answer cache disabled against the same
   PostgreSQL schema previously validated by the 3.x server.
5. Flyway validated all 71 migrations, reported schema version 1615 as current,
   and JPA validation completed.
6. The public root and actuator mappings returned HTTP 200; an unauthenticated
   AI endpoint returned HTTP 401.

For a non-`public` PostgreSQL schema, both `DEFAULT_SCHEMA` and the JDBC
`currentSchema` parameter must identify that schema. Omitting `currentSchema`
causes unqualified JDBC repository SQL to use `public`, which is a deployment
configuration error rather than a migration incompatibility.
