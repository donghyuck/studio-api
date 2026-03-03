# Flyway Versioning Policy

This project uses a single Flyway history table.

- History table: `flyway_schema_history`
- Database-specific migration locations remain separated by module
- Flyway version numbers must be globally unique across all loaded modules

## Core Rules

1. Each business module owns a fixed version range.
2. A module may only create migrations inside its assigned range.
3. Applied versioned migrations must never be renamed, removed, or reused.
4. New modules must be assigned a new unused range before adding SQL files.
5. `spring.flyway.out-of-order=true` is required when modules may be installed later.

## File Naming

Use versioned migrations for schema evolution.

- `V200__create_objecttype_tables.sql`
- `V201__add_objecttype_display_name.sql`
- `V500__create_acl_tables.sql`

Use clear names that describe the actual schema change.

## Module Version Ranges

| Module | Range |
| --- | --- |
| `data` | `100-199` |
| `objecttype` | `200-299` |
| `user` | `300-399` |
| `security` | `400-499` |
| `security-acl` | `500-599` |
| `ai` | `600-699` |
| `avatar` | `700-799` |
| `attachment` | `800-899` |
| `template` | `900-999` |
| `mail` | `1000-1099` |
| `forum` | `1100-1199` |
| `pages` | `1200-1299` |

## Adding a Migration

1. Find the next unused version inside the module's range.
2. Add the SQL file under `schema/{module}/{db}`.
3. Use the same version number for the same logical change per database only when each file belongs to the same module range.

Example:

- `schema/objecttype/postgres/V202__add_objecttype_code.sql`
- `schema/objecttype/mysql/V202__add_objecttype_code.sql`
- `schema/objecttype/mariadb/V202__add_objecttype_code.sql`

## Adding a New Module

1. Assign a new unused version range.
2. Document the range in this file.
3. Start the module's initial migration at the first number in that range.

Example:

- `schema/new-module/postgres/V1300__create_new_module_tables.sql`

## Do Not

- Do not use `V0` for every module in a single Flyway history table.
- Do not use another module's version range.
- Do not modify an already applied `V` migration file.
- Do not assume separated `locations` create separate Flyway version namespaces.
