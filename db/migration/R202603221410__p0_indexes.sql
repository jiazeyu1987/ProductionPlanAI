BEGIN;

DROP INDEX IF EXISTS idx_audit_log_operate_time;
DROP INDEX IF EXISTS idx_audit_log_operator_operate_time;
DROP INDEX IF EXISTS idx_audit_log_request_id;
DROP INDEX IF EXISTS idx_audit_log_entity_operate_time;
DROP INDEX IF EXISTS idx_sync_checkpoint_updated_at;
DROP INDEX IF EXISTS idx_idempotency_created_at;

DROP INDEX IF EXISTS idx_integration_outbox_target_entity_time;
DROP INDEX IF EXISTS idx_integration_outbox_status_retry;
DROP INDEX IF EXISTS idx_integration_inbox_source_entity_time;
DROP INDEX IF EXISTS idx_integration_inbox_status_received;

DROP INDEX IF EXISTS idx_dispatch_command_approval_command_time;
DROP INDEX IF EXISTS idx_dispatch_command_created_at;
DROP INDEX IF EXISTS idx_dispatch_command_target_effective;

DROP INDEX IF EXISTS idx_reporting_fact_created_at;
DROP INDEX IF EXISTS idx_reporting_fact_order_process_time;

DROP INDEX IF EXISTS idx_schedule_task_time_window;
DROP INDEX IF EXISTS idx_schedule_task_calendar_shift;
DROP INDEX IF EXISTS idx_schedule_task_version_process_shift;
DROP INDEX IF EXISTS idx_schedule_task_version_order;

DROP INDEX IF EXISTS idx_order_pool_product_status;
DROP INDEX IF EXISTS idx_order_pool_schedulable_window;
DROP INDEX IF EXISTS idx_order_pool_order_key;

COMMIT;
