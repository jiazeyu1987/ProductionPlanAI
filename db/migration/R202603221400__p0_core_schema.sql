BEGIN;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS sync_checkpoint;
DROP TABLE IF EXISTS idempotency_ledger;
DROP TABLE IF EXISTS integration_outbox;
DROP TABLE IF EXISTS integration_inbox;
DROP TABLE IF EXISTS dispatch_command_approval;
DROP TABLE IF EXISTS dispatch_command;
DROP TABLE IF EXISTS reporting_fact;
DROP TABLE IF EXISTS schedule_task;
DROP TABLE IF EXISTS order_pool;
DROP TABLE IF EXISTS schedule_version;
DROP TABLE IF EXISTS rule_param_version;

COMMIT;
