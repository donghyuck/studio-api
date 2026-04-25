-- MySQL group member summary search migration marker.
--
-- The optimized infix search path is PostgreSQL-specific and is implemented with
-- pg_trgm in schema/user/postgres/V301__optimize_group_member_summary_search.sql.
-- MySQL keeps the existing USERNAME and EMAIL unique indexes from V300. A normal
-- B-tree index does not optimize LIKE '%keyword%' reliably, so this migration is
-- intentionally schema-neutral to keep the user schema history explicit.

