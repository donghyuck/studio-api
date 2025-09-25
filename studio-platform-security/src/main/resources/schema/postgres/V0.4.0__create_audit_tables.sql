-- =================================================
-- PACKAGE: SECURITY
-- CREATE : 2024.9.12
-- UPDATE :
-- =================================================

-- spring SECURITY audit for postgresql

CREATE TABLE IF NOT EXISTS tb_login_failure_log (
  id           BIGSERIAL PRIMARY KEY,
  username     VARCHAR(150) NOT NULL,
  remote_ip    inet,
  user_agent   VARCHAR(512),
  failure_type VARCHAR(128),
  message      VARCHAR(1000),
  occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_login_fail_user_time
  ON tb_login_failure_log (username, occurred_at DESC);

-- 대용량이면 BRIN 권장 (시간순 적재 가정)
CREATE INDEX IF NOT EXISTS ix_login_fail_time_brin
  ON tb_login_failure_log USING BRIN (occurred_at);
CREATE INDEX IF NOT EXISTS ix_login_fail_ip
  ON tb_login_failure_log (remote_ip);
