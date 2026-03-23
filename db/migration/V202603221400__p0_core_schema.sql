-- P0 core schema baseline (PostgreSQL)

BEGIN;

CREATE TABLE IF NOT EXISTS rule_param_version (
  id BIGSERIAL PRIMARY KEY,
  rule_version_no VARCHAR(30) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  payload_json JSONB NOT NULL,
  effective_from TIMESTAMPTZ,
  effective_to TIMESTAMPTZ,
  created_by VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS schedule_version (
  id BIGSERIAL PRIMARY KEY,
  version_no VARCHAR(30) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  based_on_version VARCHAR(30),
  rule_version_no VARCHAR(30) NOT NULL,
  publish_time TIMESTAMPTZ,
  created_by VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  rollback_from VARCHAR(30),
  CONSTRAINT fk_schedule_version_rule
    FOREIGN KEY (rule_version_no)
    REFERENCES rule_param_version(rule_version_no)
);

CREATE TABLE IF NOT EXISTS order_pool (
  id BIGSERIAL PRIMARY KEY,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  line_no VARCHAR(20),
  product_code VARCHAR(50),
  order_qty NUMERIC(18,4) NOT NULL,
  expected_due_date TIMESTAMPTZ,
  promised_due_date TIMESTAMPTZ,
  requested_ship_date TIMESTAMPTZ,
  urgent_flag SMALLINT NOT NULL DEFAULT 0,
  review_passed_flag SMALLINT NOT NULL DEFAULT 0,
  schedulable_flag SMALLINT NOT NULL DEFAULT 0,
  close_flag SMALLINT NOT NULL DEFAULT 0,
  frozen_flag SMALLINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL,
  last_update_time TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ,
  deleted_flag SMALLINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_order_pool UNIQUE (order_no, order_type, line_no)
);

CREATE TABLE IF NOT EXISTS schedule_task (
  id BIGSERIAL PRIMARY KEY,
  version_no VARCHAR(30) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  process_code VARCHAR(50) NOT NULL,
  calendar_date DATE NOT NULL,
  shift_code VARCHAR(20) NOT NULL,
  plan_start_time TIMESTAMPTZ NOT NULL,
  plan_finish_time TIMESTAMPTZ NOT NULL,
  plan_qty NUMERIC(18,4) NOT NULL,
  lock_flag SMALLINT NOT NULL DEFAULT 0,
  priority INT,
  updated_at TIMESTAMPTZ,
  source VARCHAR(20) NOT NULL,
  CONSTRAINT fk_schedule_task_version
    FOREIGN KEY (version_no)
    REFERENCES schedule_version(version_no)
);

CREATE TABLE IF NOT EXISTS reporting_fact (
  id BIGSERIAL PRIMARY KEY,
  report_id VARCHAR(64) NOT NULL UNIQUE,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  process_code VARCHAR(50) NOT NULL,
  report_qty NUMERIC(18,4) NOT NULL,
  report_time TIMESTAMPTZ NOT NULL,
  shift_code VARCHAR(20) NOT NULL,
  team_code VARCHAR(50),
  operator_code VARCHAR(50),
  exception_code VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dispatch_command (
  id BIGSERIAL PRIMARY KEY,
  command_id VARCHAR(64) NOT NULL UNIQUE,
  command_type VARCHAR(30) NOT NULL,
  target_order_no VARCHAR(50) NOT NULL,
  target_order_type VARCHAR(20) NOT NULL,
  effective_time TIMESTAMPTZ NOT NULL,
  reason TEXT NOT NULL,
  created_by VARCHAR(50) NOT NULL,
  approved_flag SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dispatch_command_approval (
  id BIGSERIAL PRIMARY KEY,
  approval_id VARCHAR(64) NOT NULL UNIQUE,
  command_id VARCHAR(64) NOT NULL,
  approver VARCHAR(50) NOT NULL,
  decision VARCHAR(20) NOT NULL,
  decision_reason TEXT,
  decision_time TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_command_approval_command
    FOREIGN KEY (command_id)
    REFERENCES dispatch_command(command_id)
);

CREATE TABLE IF NOT EXISTS integration_inbox (
  id BIGSERIAL PRIMARY KEY,
  message_id VARCHAR(100) NOT NULL UNIQUE,
  source_system VARCHAR(20) NOT NULL,
  entity_name VARCHAR(50) NOT NULL,
  payload_json JSONB NOT NULL,
  status VARCHAR(20) NOT NULL,
  error_msg TEXT,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS integration_outbox (
  id BIGSERIAL PRIMARY KEY,
  message_id VARCHAR(100) NOT NULL UNIQUE,
  target_system VARCHAR(20) NOT NULL,
  entity_name VARCHAR(50) NOT NULL,
  payload_json JSONB NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  sent_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS idempotency_ledger (
  id BIGSERIAL PRIMARY KEY,
  request_id VARCHAR(100) NOT NULL,
  biz_key VARCHAR(200) NOT NULL,
  action VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_idempotency UNIQUE (request_id, biz_key, action)
);

CREATE TABLE IF NOT EXISTS sync_checkpoint (
  id BIGSERIAL PRIMARY KEY,
  source_system VARCHAR(20) NOT NULL,
  entity_name VARCHAR(50) NOT NULL,
  checkpoint_time TIMESTAMPTZ,
  checkpoint_token VARCHAR(200),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_sync_checkpoint UNIQUE (source_system, entity_name)
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(100) NOT NULL,
  action VARCHAR(50) NOT NULL,
  before_json JSONB,
  after_json JSONB,
  operator VARCHAR(50) NOT NULL,
  reason TEXT,
  request_id VARCHAR(100),
  client_ip VARCHAR(64),
  operate_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMIT;
