CREATE TABLE IF NOT EXISTS job_task (
  id BIGSERIAL PRIMARY KEY,
  job_no VARCHAR(64) NOT NULL UNIQUE,
  task_type VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  payload_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS idempotency_ledger (
  id BIGSERIAL PRIMARY KEY,
  request_id VARCHAR(100) NOT NULL,
  action VARCHAR(100) NOT NULL,
  response_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (request_id, action)
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  action VARCHAR(50) NOT NULL,
  operator VARCHAR(50) NOT NULL,
  request_id VARCHAR(100),
  reason TEXT,
  operate_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS replan_job (
  id BIGSERIAL PRIMARY KEY,
  job_no VARCHAR(64) NOT NULL UNIQUE,
  trigger_type VARCHAR(30) NOT NULL,
  scope_type VARCHAR(20) NOT NULL,
  base_version_no VARCHAR(30) NOT NULL,
  result_version_no VARCHAR(30),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS alert_event (
  id BIGSERIAL PRIMARY KEY,
  alert_id VARCHAR(64) NOT NULL UNIQUE,
  alert_type VARCHAR(30) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  order_no VARCHAR(50),
  process_code VARCHAR(50),
  version_no VARCHAR(30),
  trigger_value NUMERIC(18,4),
  threshold_value NUMERIC(18,4),
  status VARCHAR(20) NOT NULL,
  ack_by VARCHAR(50),
  ack_time TIMESTAMPTZ,
  close_by VARCHAR(50),
  close_time TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

