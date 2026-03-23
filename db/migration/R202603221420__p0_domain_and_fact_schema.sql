BEGIN;

-- Drop FKs added on existing baseline tables first
ALTER TABLE IF EXISTS schedule_task DROP CONSTRAINT IF EXISTS fk_schedule_task_process;
ALTER TABLE IF EXISTS reporting_fact DROP CONSTRAINT IF EXISTS fk_reporting_fact_process;
ALTER TABLE IF EXISTS employee_skill DROP CONSTRAINT IF EXISTS fk_employee_skill_process;
ALTER TABLE IF EXISTS employee_skill DROP CONSTRAINT IF EXISTS fk_employee_skill_employee;

-- Drop newly created tables (reverse dependency order)
DROP TABLE IF EXISTS order_priority_factor;
DROP TABLE IF EXISTS notification_delivery_log;
DROP TABLE IF EXISTS alert_event;
DROP TABLE IF EXISTS employee_shift_availability;
DROP TABLE IF EXISTS equipment_downtime_event;
DROP TABLE IF EXISTS schedule_publish_log;
DROP TABLE IF EXISTS wip_lot_event;
DROP TABLE IF EXISTS wip_lot;
DROP TABLE IF EXISTS replan_job;
DROP TABLE IF EXISTS schedule_task_change_log;
DROP TABLE IF EXISTS schedule_task_resource;
DROP TABLE IF EXISTS delivery_progress;
DROP TABLE IF EXISTS production_material_list_item;
DROP TABLE IF EXISTS mrp_result_link;
DROP TABLE IF EXISTS schedule_control_fact;
DROP TABLE IF EXISTS production_order_fact;
DROP TABLE IF EXISTS sales_order_line_fact;
DROP TABLE IF EXISTS plan_order;
DROP TABLE IF EXISTS material_availability;
DROP TABLE IF EXISTS shift_calendar;
DROP TABLE IF EXISTS equipment_process_capability;
DROP TABLE IF EXISTS equipment_capacity;
DROP TABLE IF EXISTS process_resource_requirement;
DROP TABLE IF EXISTS route_step_dependency;
DROP TABLE IF EXISTS process_route;
DROP TABLE IF EXISTS product_route_header;
DROP TABLE IF EXISTS employee;
DROP TABLE IF EXISTS organization_unit;
DROP TABLE IF EXISTS process;
DROP TABLE IF EXISTS product;

-- Drop extension columns added to baseline tables
ALTER TABLE IF EXISTS sync_checkpoint
  DROP COLUMN IF EXISTS cursor_token,
  DROP COLUMN IF EXISTS cursor_time;

ALTER TABLE IF EXISTS idempotency_ledger
  DROP COLUMN IF EXISTS result_hash;

ALTER TABLE IF EXISTS integration_outbox
  DROP COLUMN IF EXISTS biz_key,
  DROP COLUMN IF EXISTS topic;

ALTER TABLE IF EXISTS integration_inbox
  DROP COLUMN IF EXISTS applied_at,
  DROP COLUMN IF EXISTS biz_key,
  DROP COLUMN IF EXISTS topic;

ALTER TABLE IF EXISTS employee_skill
  DROP COLUMN IF EXISTS team_code,
  DROP COLUMN IF EXISTS employee_name;

ALTER TABLE IF EXISTS order_pool
  DROP COLUMN IF EXISTS manual_weight,
  DROP COLUMN IF EXISTS customer_priority,
  DROP COLUMN IF EXISTS material_list_no,
  DROP COLUMN IF EXISTS source_plan_order_no;

ALTER TABLE IF EXISTS rule_param_version
  DROP COLUMN IF EXISTS shift_strategy_json,
  DROP COLUMN IF EXISTS freeze_window_hours,
  DROP COLUMN IF EXISTS replan_threshold_json,
  DROP COLUMN IF EXISTS priority_weights_json;

COMMIT;
