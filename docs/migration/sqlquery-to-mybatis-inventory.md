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
mybatisSpringVersion=2.1.2
mybatisVersion=3.5.16
```

For Spring Boot 3.5 / Java 17, the migration should introduce the latest `3.0.x` line of
`org.mybatis.spring.boot:mybatis-spring-boot-starter`. As of 2026-05-08, that patch version is `3.0.5`.
The migration should also stop using the legacy `mybatisSpringVersion` path.

## SqlQuery Runtime and Auto-Configuration

The core SqlQuery implementation is under:

- `studio-platform-data/src/main/java/studio/one/platform/data/sqlquery`

There are currently 60 Java source files in the SqlQuery implementation and its starter auto-configuration wrappers.

Starter integration is currently split across canonical and deprecated typo packages:

- `starter/studio-platform-starter/src/main/java/studio/one/platform/autoconfigure/persistence/jdbc/sqlquery`
- `starter/studio-platform-starter/src/main/java/studio/one/platform/autoconfigure/perisitence/jdbc/sqlquery`

`JdbcAutoConfiguration` also creates `SqlQueryFactory` and loads `classpath*:sql/*-sqlset.xml` by default.
`SqlQueryMapperAutoConfiguration` is also imported as starter auto-configuration and registers the annotation
injection/proxy layer:

- `SqlStatementBeanPostProcessor` for `@SqlStatement`, `@SqlBoundStatement`, and `@SqlMappedStatement`
- `@EnableSqlMappers` / `SqlMapperRegistrar` scanning for `@SqlMapper`

Both auto-configuration paths must be retired or made non-production before the SqlQuery migration is complete.

## Non-SQL SqlQuery Package Dependencies

The following production code depends on the SqlQuery implementation package without executing SQL:

| Area | File | Dependency |
| --- | --- | --- |
| Template Freemarker support | `studio-application-modules/template-service/src/main/java/studio/one/application/template/service/impl/FreemarkerTemplateBuilder.java` | `studio.one.platform.data.sqlquery.factory.impl.StaticModels` |

This helper must be moved to a non-SqlQuery package or replaced by a template-local utility before the
`studio.one.platform.data.sqlquery.*` implementation can be removed. It is not a MyBatis mapper conversion
target, but it is in scope for de-coupling.

Status: moved to `studio.one.platform.data.freemarker.StaticModels` during Phase 4 preparation. This removes
the non-SQL `StaticModels` dependency from `template-service`; broader starter/runtime references to the SqlQuery
implementation remain in scope for later phases. The legacy
`studio.one.platform.data.sqlquery.factory.impl.StaticModels` wrapper remains as a source/binary compatibility
shim until SqlQuery removal.

## Production SqlStatement Consumers

These production classes use `@SqlStatement` and must be converted to MyBatis mapper methods or equivalent MyBatis-backed adapters:

| Area | File | Count |
| --- | --- | ---: |
| AI vector store | `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/adapters/vector/PgVectorStoreAdapterV2.java` | 10 |
| Security audit | `studio-platform-security/src/main/java/studio/one/base/security/audit/persistence/jdbc/LoginFailureLogJdbcRepository.java` | 6 |
| Account lock | `studio-platform-security/src/main/java/studio/one/base/security/authentication/lock/persistence/jdbc/AccountLockJdbcRepository.java` | 6 |
| Refresh token | `studio-platform-security/src/main/java/studio/one/base/security/jwt/refresh/persistence/jdbc/RefreshTokenJdbcRepositoryV2.java` | 3 |
| Password reset token | `studio-platform-security/src/main/java/studio/one/base/security/jwt/reset/persistence/jdbc/PasswordResetTokenJdbcRepositoryV2.java` | 3 |
| Mail attachment | `studio-application-modules/mail-service/src/main/java/studio/one/application/mail/service/impl/JdbcMailAttachmentService.java` | 3 |
| Mail message | `studio-application-modules/mail-service/src/main/java/studio/one/application/mail/service/impl/JdbcMailMessageService.java` | 10 |
| Mail sync log | `studio-application-modules/mail-service/src/main/java/studio/one/application/mail/service/impl/JdbcMailSyncLogService.java` | 6 |
| Template | `studio-application-modules/template-service/src/main/java/studio/one/application/template/persistence/jdbc/TemplateJdbcRepository.java` | 10 |

## Sqlset Resources

The current production sqlset resources are:

- `studio-platform-ai/src/main/resources/sql/ai-sqlset.xml`
- `studio-platform-security/src/main/resources/sql/security-sqlset.xml`
- `studio-application-modules/mail-service/src/main/resources/sql/mail-sqlset.xml`
- `studio-application-modules/template-service/src/main/resources/sql/template-sqlset.xml`

These should move to MyBatis mapper XML under `classpath*:mybatis/**/*.xml` and should not remain as production `sql/*-sqlset.xml` resources after conversion.

Converted in Phase 4:

- Object type SQL injection was removed from `ObjectTypeJdbcRepository`.
  The migrated MyBatis path is `studio-platform-objecttype/src/main/resources/mybatis/objecttype/ObjectTypeMapper.xml`,
  while the legacy direct JDBC store remains as a compatibility implementation without SqlQuery.

## Test Coverage Tied to Sqlset Resources

The following tests load `sql/ai-sqlset.xml` directly and must either move with the MyBatis mapper resource or be
replaced with equivalent mapper contract coverage:

- `studio-platform-ai/src/test/java/studio/one/platform/ai/core/vector/VectorSqlSetContractTest.java`
- `starter/studio-platform-starter-ai/src/test/java/studio/one/platform/ai/adapters/vector/PgVectorStoreAdapterV2PostgresTest.java`

## Documentation References

The following documentation currently presents SqlQuery as a supported SQL mapper and must be rewritten or marked historical:

- `studio-platform-data/README.md`
- `studio-platform-data/JDBC_DEVELOPMENT_GUIDE.md`
- `studio-platform-data/docs/adr/0001-sql-mapper-xml.md`
- `studio-application-modules/mail-service/README.md`
- `studio-application-modules/template-service/README.md`

The broader configuration docs and starter docs should be updated after the final conversion so `mybatis` is the standard SQL mapper wording and `jdbc` is documented only where it remains an actual direct JDBC implementation or a deprecated alias.

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
