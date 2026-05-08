# SqlQuery to MyBatis Migration Inventory

Issue: #441
Branch: `codex/sqlquery-to-mybatis-migration`
Baseline: `origin/2.x`
Date: 2026-05-08

## Summary

This inventory fixes the Phase 0 scope for migrating the legacy SqlQuery SQL mapper to MyBatis.

The migration target is the custom SqlQuery stack only:

- `studio.one.platform.data.sqlquery.*`
- `@SqlStatement`, `@SqlMapper`, `@SqlBoundStatement`, `@SqlMappedStatement`
- `sql/*-sqlset.xml`
- SqlQuery auto-configuration in `starter/studio-platform-starter`

General `JdbcTemplate` and `NamedParameterJdbcTemplate` repositories that do not depend on SqlQuery remain in scope only for regression validation, not conversion.

## Baseline Validation

Command:

```bash
./gradlew :studio-platform-data:build \
  :studio-platform-ai:build \
  :studio-platform-objecttype:build \
  :studio-platform-security:build \
  :starter:studio-platform-starter:build \
  :starter:studio-platform-starter-ai:build \
  :studio-application-modules:mail-service:build \
  :studio-application-modules:template-service:build \
  :studio-platform-workspace-default:build \
  :studio-platform-user-default:build \
  :studio-application-modules:wiki-service:build
```

Result: `BUILD SUCCESSFUL`.

## Current MyBatis Version State

`gradle.properties` currently contains:

```properties
mybatisVersion=3.5.19
mybatisSpringBootStarterVersion=3.0.5
```

For Spring Boot 3.5 / Java 17, the migration uses the `3.0.x` line of
`org.mybatis.spring.boot:mybatis-spring-boot-starter`. The legacy `mybatisSpringVersion` property was removed.

## SqlQuery Runtime and Auto-Configuration

The custom SqlQuery implementation was removed in Phase 6:

- `studio-platform-data/src/main/java/studio/one/platform/data/sqlquery`

The starter integration was also removed from both the canonical and deprecated typo packages:

- `starter/studio-platform-starter/src/main/java/studio/one/platform/autoconfigure/persistence/jdbc/sqlquery`
- `starter/studio-platform-starter/src/main/java/studio/one/platform/autoconfigure/perisitence/jdbc/sqlquery`

`JdbcAutoConfiguration` now registers only `JdbcTemplate` and `NamedParameterJdbcTemplate`.
It no longer creates `SqlQueryFactory`, scans `classpath*:sql/*-sqlset.xml`, or imports
`SqlQueryMapperAutoConfiguration`.

Status: production runtime and starter auto-configuration removal complete.

## Non-SQL SqlQuery Package Dependencies

The following production code depends on the SqlQuery implementation package without executing SQL:

| Area | File | Dependency |
| --- | --- | --- |
| Template Freemarker support | `studio-application-modules/template-service/src/main/java/studio/one/application/template/service/impl/FreemarkerTemplateBuilder.java` | `studio.one.platform.data.sqlquery.factory.impl.StaticModels` |

This helper must be moved to a non-SqlQuery package or replaced by a template-local utility before the
`studio.one.platform.data.sqlquery.*` implementation can be removed. It is not a MyBatis mapper conversion
target, but it is in scope for de-coupling.

Status: moved to `studio.one.platform.data.freemarker.StaticModels` during Phase 4 preparation. The legacy
`studio.one.platform.data.sqlquery.factory.impl.StaticModels` wrapper was removed with the SqlQuery runtime.

## Production SqlStatement Consumers

There are no remaining production `@SqlStatement` consumers after the AI vector conversion.

| Area | File | Count |
| --- | --- | ---: |
| none | n/a | 0 |

## Sqlset Resources

There are no remaining production `sql/*-sqlset.xml` resources after the AI vector conversion.

The AI vector SQL moved to:

- `starter/studio-platform-starter-ai/src/main/resources/mybatis/ai/PgVectorMapper.xml`

Converted in Phase 4:

- Object type SQL injection was removed from `ObjectTypeJdbcRepository`.
  The migrated MyBatis path is `studio-platform-objecttype/src/main/resources/mybatis/objecttype/ObjectTypeMapper.xml`,
  while the legacy direct JDBC store remains as a compatibility implementation without SqlQuery.
- Security JDBC repositories now own their SQL locally without `@SqlStatement`, and
  `studio-platform-security/src/main/resources/sql/security-sqlset.xml` was removed.
  PostgreSQL fail-fast guards are limited to JDBC paths that still use PostgreSQL-only SQL/types;
  account-lock JDBC remains portable and is not guarded.
- Mail and template JDBC compatibility implementations now own their SQL locally without `@SqlStatement`, and
  `studio-application-modules/mail-service/src/main/resources/sql/mail-sqlset.xml` plus
  `studio-application-modules/template-service/src/main/resources/sql/template-sqlset.xml` were removed.
  Converted classes: `JdbcMailAttachmentService`, `JdbcMailMessageService`, `JdbcMailSyncLogService`, and
  `TemplateJdbcRepository`.
- AI vector store now prefers `PgVectorMapper` and `mybatis/ai/PgVectorMapper.xml`.
  `studio-platform-ai/src/main/resources/sql/ai-sqlset.xml` was removed. A direct JDBC fallback remains
  for existing `starter-ai` consumers that have `JdbcTemplate` but either have not added the MyBatis starter yet or
  have MyBatis configured without loading the AI `PgVectorMapper.xml` resource.

## Test Coverage Tied to Sqlset Resources

The previous tests that loaded `sql/ai-sqlset.xml` directly were replaced with equivalent MyBatis mapper coverage:

- `starter/studio-platform-starter-ai/src/test/java/studio/one/platform/ai/adapters/vector/mybatis/PgVectorMapperXmlContractTest.java`
- `starter/studio-platform-starter-ai/src/test/java/studio/one/platform/ai/adapters/vector/PgVectorStoreAdapterV2PostgresTest.java`

## Documentation References

Documentation cleanup status:

- `studio-platform-data/README.md` now documents JDBC utilities and points SQL mapper usage to MyBatis.
- `studio-platform-data/JDBC_DEVELOPMENT_GUIDE.md` was removed.
- `studio-platform-data/docs/adr/0001-sql-mapper-xml.md` is retained only as a deprecated historical ADR.
- Starter docs now document MyBatis as the SQL mapper convention and no longer expose a `sql-query.enabled` switch.

`jdbc` wording remains only where the implementation is an actual direct JDBC path or a compatibility alias.

## Out of Scope for This Migration

The following direct JDBC implementations are not automatically converted unless they also depend on SqlQuery:

- JPA/JDBC feature repositories that already use `JdbcTemplate` or `NamedParameterJdbcTemplate` directly
- DB utility or test-only `JdbcTemplate` usage
- Flyway migration scripts
- Existing JPA persistence paths

## Phase 0 Search Commands

```bash
rg -n "@SqlStatement|@SqlMappedStatement|@SqlBoundStatement|@SqlMapper|SqlQueryFactory|SqlQuery|studio\\.one\\.platform\\.data\\.sqlquery" \
  --glob '!**/build/**' \
  --glob '!**/.claude/**' \
  --glob '!**/.omx/**'

find . \
  -path './build' -prune -o \
  -path '*/build' -prune -o \
  -path './.claude' -prune -o \
  -path './.omx' -prune -o \
  -path './.gradle' -prune -o \
  -name '*-sqlset.xml' -print | sort

rg --files \
  -g 'gradle.properties' \
  -g 'settings.gradle.kts' \
  -g 'build.gradle.kts' \
  -g '!**/build/**' \
  -g '!**/.claude/**' \
  -g '!**/.omx/**' \
  -g '!**/.gradle/**' \
  | xargs rg -n "mybatis|mybatisSpring|mybatisVersion|mybatis-spring"
```
