-- PostgreSQL group member summary search optimization.
-- Supports lower(username|name|email) LIKE '%keyword%' through trigram expression indexes.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS IDX_APP_USER_USERNAME_TRGM
    ON TB_APPLICATION_USER USING GIN (lower(USERNAME) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS IDX_APP_USER_NAME_TRGM
    ON TB_APPLICATION_USER USING GIN (lower(NAME) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS IDX_APP_USER_EMAIL_TRGM
    ON TB_APPLICATION_USER USING GIN (lower(EMAIL) gin_trgm_ops);
