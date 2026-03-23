CREATE TABLE IF NOT EXISTS dispatch_command_approval (
  id BIGSERIAL PRIMARY KEY,
  approval_id VARCHAR(64) NOT NULL UNIQUE,
  command_id VARCHAR(64) NOT NULL,
  approver VARCHAR(50) NOT NULL,
  decision VARCHAR(20) NOT NULL,
  decision_reason TEXT,
  decision_time TIMESTAMPTZ NOT NULL,
  request_id VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS schedule_publish_log (
  id BIGSERIAL PRIMARY KEY,
  version_no VARCHAR(30) NOT NULL,
  action VARCHAR(20) NOT NULL,
  operator VARCHAR(50) NOT NULL,
  request_id VARCHAR(100) NOT NULL,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS integration_inbox (
  id BIGSERIAL PRIMARY KEY,
  message_id VARCHAR(100) NOT NULL UNIQUE,
  source VARCHAR(50) NOT NULL,
  target VARCHAR(50) NOT NULL,
  topic VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_msg TEXT,
  request_id VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS integration_outbox (
  id BIGSERIAL PRIMARY KEY,
  message_id VARCHAR(100) NOT NULL UNIQUE,
  source VARCHAR(50) NOT NULL,
  target VARCHAR(50) NOT NULL,
  topic VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_msg TEXT,
  request_id VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification_delivery_log (
  id BIGSERIAL PRIMARY KEY,
  notification_id VARCHAR(100) NOT NULL UNIQUE,
  channel VARCHAR(30) NOT NULL,
  target VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_msg TEXT,
  alert_id VARCHAR(64),
  request_id VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
