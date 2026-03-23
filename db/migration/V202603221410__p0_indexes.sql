-- P0 index baseline (PostgreSQL)

BEGIN;

-- order pool lookup and scheduling queue scan
CREATE INDEX IF NOT EXISTS idx_order_pool_order_key
  ON order_pool (order_no, order_type);

CREATE INDEX IF NOT EXISTS idx_order_pool_schedulable_window
  ON order_pool (schedulable_flag, frozen_flag, close_flag, status, promised_due_date, expected_due_date);

CREATE INDEX IF NOT EXISTS idx_order_pool_product_status
  ON order_pool (product_code, status);

-- schedule task query paths
CREATE INDEX IF NOT EXISTS idx_schedule_task_version_order
  ON schedule_task (version_no, order_no, order_type);

CREATE INDEX IF NOT EXISTS idx_schedule_task_version_process_shift
  ON schedule_task (version_no, process_code, calendar_date, shift_code);

CREATE INDEX IF NOT EXISTS idx_schedule_task_calendar_shift
  ON schedule_task (calendar_date, shift_code);

CREATE INDEX IF NOT EXISTS idx_schedule_task_time_window
  ON schedule_task (plan_start_time, plan_finish_time);

-- execution / reporting
CREATE INDEX IF NOT EXISTS idx_reporting_fact_order_process_time
  ON reporting_fact (order_no, process_code, report_time);

CREATE INDEX IF NOT EXISTS idx_reporting_fact_created_at
  ON reporting_fact (created_at);

-- dispatch and approval
CREATE INDEX IF NOT EXISTS idx_dispatch_command_target_effective
  ON dispatch_command (target_order_no, target_order_type, effective_time);

CREATE INDEX IF NOT EXISTS idx_dispatch_command_created_at
  ON dispatch_command (created_at);

CREATE INDEX IF NOT EXISTS idx_dispatch_command_approval_command_time
  ON dispatch_command_approval (command_id, decision_time);

-- integration and retry
CREATE INDEX IF NOT EXISTS idx_integration_inbox_status_received
  ON integration_inbox (status, received_at);

CREATE INDEX IF NOT EXISTS idx_integration_inbox_source_entity_time
  ON integration_inbox (source_system, entity_name, received_at);

CREATE INDEX IF NOT EXISTS idx_integration_outbox_status_retry
  ON integration_outbox (status, next_retry_time);

CREATE INDEX IF NOT EXISTS idx_integration_outbox_target_entity_time
  ON integration_outbox (target_system, entity_name, created_at);

-- ledger / checkpoint / audit
CREATE INDEX IF NOT EXISTS idx_idempotency_created_at
  ON idempotency_ledger (created_at);

CREATE INDEX IF NOT EXISTS idx_sync_checkpoint_updated_at
  ON sync_checkpoint (updated_at);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity_operate_time
  ON audit_log (entity_type, entity_id, operate_time);

CREATE INDEX IF NOT EXISTS idx_audit_log_request_id
  ON audit_log (request_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_operator_operate_time
  ON audit_log (operator, operate_time);

CREATE INDEX IF NOT EXISTS idx_audit_log_operate_time
  ON audit_log (operate_time);

COMMIT;
