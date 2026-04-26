CREATE TABLE tb_ai_rag_index_job (
  job_id         VARCHAR(100) PRIMARY KEY,
  object_type    VARCHAR(100),
  object_id      VARCHAR(150),
  document_id    VARCHAR(200),
  source_type    VARCHAR(100),
  status         VARCHAR(30) NOT NULL,
  current_step   VARCHAR(30),
  chunk_count    INT NOT NULL DEFAULT 0,
  embedded_count INT NOT NULL DEFAULT 0,
  indexed_count  INT NOT NULL DEFAULT 0,
  warning_count  INT NOT NULL DEFAULT 0,
  error_message  TEXT,
  created_at     TIMESTAMP NOT NULL,
  started_at     TIMESTAMP NULL,
  finished_at    TIMESTAMP NULL,
  duration_ms    BIGINT
);

CREATE INDEX idx_ai_rag_job_status_created
    ON tb_ai_rag_index_job(status, created_at);

CREATE INDEX idx_ai_rag_job_object
    ON tb_ai_rag_index_job(object_type, object_id);

CREATE INDEX idx_ai_rag_job_document
    ON tb_ai_rag_index_job(document_id);

CREATE TABLE tb_ai_rag_index_job_log (
  log_id     VARCHAR(100) PRIMARY KEY,
  job_id     VARCHAR(100) NOT NULL,
  log_level  VARCHAR(20) NOT NULL,
  step       VARCHAR(30),
  code       VARCHAR(100) NOT NULL,
  message    TEXT,
  detail     TEXT,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_rag_job_log_job_created
    ON tb_ai_rag_index_job_log(job_id, created_at);
