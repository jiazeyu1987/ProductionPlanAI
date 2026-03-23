-- P0 domain/fact schema extension (PostgreSQL)

BEGIN;

-- ---------------------------------------------------------------------------
-- 1) Extend existing baseline tables to align with data-dictionary fields
-- ---------------------------------------------------------------------------
ALTER TABLE rule_param_version
  ADD COLUMN IF NOT EXISTS priority_weights_json JSONB,
  ADD COLUMN IF NOT EXISTS replan_threshold_json JSONB,
  ADD COLUMN IF NOT EXISTS freeze_window_hours INT,
  ADD COLUMN IF NOT EXISTS shift_strategy_json JSONB;

ALTER TABLE order_pool
  ADD COLUMN IF NOT EXISTS source_plan_order_no VARCHAR(50),
  ADD COLUMN IF NOT EXISTS material_list_no VARCHAR(50),
  ADD COLUMN IF NOT EXISTS customer_priority NUMERIC(10,4),
  ADD COLUMN IF NOT EXISTS manual_weight NUMERIC(10,4);

ALTER TABLE employee_skill
  ADD COLUMN IF NOT EXISTS employee_name VARCHAR(100),
  ADD COLUMN IF NOT EXISTS team_code VARCHAR(50);

ALTER TABLE integration_inbox
  ADD COLUMN IF NOT EXISTS topic VARCHAR(100),
  ADD COLUMN IF NOT EXISTS biz_key VARCHAR(100),
  ADD COLUMN IF NOT EXISTS applied_at TIMESTAMPTZ;

ALTER TABLE integration_outbox
  ADD COLUMN IF NOT EXISTS topic VARCHAR(100),
  ADD COLUMN IF NOT EXISTS biz_key VARCHAR(100);

ALTER TABLE idempotency_ledger
  ADD COLUMN IF NOT EXISTS result_hash VARCHAR(128);

ALTER TABLE sync_checkpoint
  ADD COLUMN IF NOT EXISTS cursor_time TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS cursor_token VARCHAR(100);

-- ---------------------------------------------------------------------------
-- 2) Domain master tables
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product (
  id BIGSERIAL PRIMARY KEY,
  product_code VARCHAR(50) NOT NULL UNIQUE,
  product_name VARCHAR(100),
  spec_model VARCHAR(100),
  uom VARCHAR(20),
  active_flag SMALLINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS process (
  id BIGSERIAL PRIMARY KEY,
  process_code VARCHAR(50) NOT NULL UNIQUE,
  process_name VARCHAR(100),
  process_type VARCHAR(30),
  critical_flag SMALLINT NOT NULL DEFAULT 0,
  active_flag SMALLINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS organization_unit (
  id BIGSERIAL PRIMARY KEY,
  unit_code VARCHAR(50) NOT NULL UNIQUE,
  unit_name VARCHAR(100) NOT NULL,
  unit_type VARCHAR(20) NOT NULL,
  parent_unit_code VARCHAR(50),
  active_flag SMALLINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_org_unit_parent'
  ) THEN
    ALTER TABLE organization_unit
      ADD CONSTRAINT fk_org_unit_parent
      FOREIGN KEY (parent_unit_code)
      REFERENCES organization_unit(unit_code)
      NOT VALID;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS employee (
  id BIGSERIAL PRIMARY KEY,
  employee_id VARCHAR(50) NOT NULL UNIQUE,
  employee_name VARCHAR(100) NOT NULL,
  team_code VARCHAR(50),
  status VARCHAR(20) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS product_route_header (
  id BIGSERIAL PRIMARY KEY,
  route_no VARCHAR(64) NOT NULL UNIQUE,
  product_code VARCHAR(50) NOT NULL,
  route_version VARCHAR(30),
  status VARCHAR(20) NOT NULL,
  effective_from TIMESTAMPTZ,
  effective_to TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_route_header_product
    FOREIGN KEY (product_code)
    REFERENCES product(product_code)
);

CREATE TABLE IF NOT EXISTS process_route (
  id BIGSERIAL PRIMARY KEY,
  route_no VARCHAR(64) NOT NULL,
  product_code VARCHAR(50) NOT NULL,
  process_code VARCHAR(50) NOT NULL,
  sequence_no INT NOT NULL,
  dependency_type VARCHAR(20) NOT NULL,
  parallel_group VARCHAR(50),
  std_cycle_time NUMERIC(18,6),
  capacity_per_shift NUMERIC(18,4),
  required_manpower_per_group INT,
  required_equipment_count INT,
  setup_time_minutes INT,
  enabled_flag SMALLINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_process_route UNIQUE (route_no, process_code),
  CONSTRAINT fk_process_route_route
    FOREIGN KEY (route_no)
    REFERENCES product_route_header(route_no),
  CONSTRAINT fk_process_route_product
    FOREIGN KEY (product_code)
    REFERENCES product(product_code),
  CONSTRAINT fk_process_route_process
    FOREIGN KEY (process_code)
    REFERENCES process(process_code)
);

CREATE TABLE IF NOT EXISTS route_step_dependency (
  id BIGSERIAL PRIMARY KEY,
  route_no VARCHAR(64) NOT NULL,
  from_process_code VARCHAR(50) NOT NULL,
  to_process_code VARCHAR(50) NOT NULL,
  dependency_type VARCHAR(20) NOT NULL,
  lag_minutes INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_route_step_dependency UNIQUE (route_no, from_process_code, to_process_code),
  CONSTRAINT fk_route_dep_route
    FOREIGN KEY (route_no)
    REFERENCES product_route_header(route_no),
  CONSTRAINT fk_route_dep_from_process
    FOREIGN KEY (from_process_code)
    REFERENCES process(process_code),
  CONSTRAINT fk_route_dep_to_process
    FOREIGN KEY (to_process_code)
    REFERENCES process(process_code)
);

CREATE TABLE IF NOT EXISTS process_resource_requirement (
  id BIGSERIAL PRIMARY KEY,
  process_code VARCHAR(50) NOT NULL UNIQUE,
  required_manpower_per_group INT NOT NULL DEFAULT 0,
  required_equipment_count INT NOT NULL DEFAULT 0,
  std_output_per_shift NUMERIC(18,4) NOT NULL DEFAULT 0,
  setup_time_minutes INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_process_resource_requirement_process
    FOREIGN KEY (process_code)
    REFERENCES process(process_code)
);

CREATE TABLE IF NOT EXISTS equipment_capacity (
  id BIGSERIAL PRIMARY KEY,
  equipment_code VARCHAR(50) NOT NULL UNIQUE,
  line_code VARCHAR(50),
  workshop_code VARCHAR(50),
  status VARCHAR(20) NOT NULL,
  capacity_per_shift NUMERIC(18,4),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS equipment_process_capability (
  id BIGSERIAL PRIMARY KEY,
  equipment_code VARCHAR(50) NOT NULL,
  process_code VARCHAR(50) NOT NULL,
  enabled_flag SMALLINT NOT NULL DEFAULT 1,
  capacity_factor NUMERIC(10,4) NOT NULL DEFAULT 1.0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_equipment_process_capability UNIQUE (equipment_code, process_code),
  CONSTRAINT fk_equipment_process_capability_equipment
    FOREIGN KEY (equipment_code)
    REFERENCES equipment_capacity(equipment_code),
  CONSTRAINT fk_equipment_process_capability_process
    FOREIGN KEY (process_code)
    REFERENCES process(process_code)
);

CREATE TABLE IF NOT EXISTS shift_calendar (
  id BIGSERIAL PRIMARY KEY,
  calendar_date DATE NOT NULL,
  shift_code VARCHAR(20) NOT NULL,
  shift_start_time TIMESTAMPTZ NOT NULL,
  shift_end_time TIMESTAMPTZ NOT NULL,
  open_flag SMALLINT NOT NULL DEFAULT 1,
  workshop_code VARCHAR(50),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_shift_calendar_date_shift_workshop
  ON shift_calendar (calendar_date, shift_code, COALESCE(workshop_code, ''));

CREATE TABLE IF NOT EXISTS material_availability (
  id BIGSERIAL PRIMARY KEY,
  material_code VARCHAR(50) NOT NULL,
  order_no VARCHAR(50),
  process_code VARCHAR(50),
  available_qty NUMERIC(18,4) NOT NULL DEFAULT 0,
  available_time TIMESTAMPTZ,
  ready_flag SMALLINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_material_availability_key
  ON material_availability (material_code, COALESCE(order_no, ''), COALESCE(process_code, ''));

CREATE TABLE IF NOT EXISTS plan_order (
  id BIGSERIAL PRIMARY KEY,
  plan_order_no VARCHAR(50) NOT NULL UNIQUE,
  source_sales_order_no VARCHAR(50),
  source_line_no VARCHAR(20),
  release_type VARCHAR(20) NOT NULL,
  release_status VARCHAR(20) NOT NULL,
  release_time TIMESTAMPTZ,
  last_update_time TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ
);

-- ---------------------------------------------------------------------------
-- 3) Scheduling execution and traceability tables
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schedule_task_resource (
  id BIGSERIAL PRIMARY KEY,
  version_no VARCHAR(30) NOT NULL,
  task_id BIGINT NOT NULL,
  resource_type VARCHAR(20) NOT NULL,
  resource_code VARCHAR(50) NOT NULL,
  plan_start_time TIMESTAMPTZ NOT NULL,
  plan_finish_time TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_schedule_task_resource_task
    FOREIGN KEY (task_id)
    REFERENCES schedule_task(id),
  CONSTRAINT fk_schedule_task_resource_version
    FOREIGN KEY (version_no)
    REFERENCES schedule_version(version_no)
);

CREATE TABLE IF NOT EXISTS schedule_task_change_log (
  id BIGSERIAL PRIMARY KEY,
  change_id VARCHAR(64) NOT NULL UNIQUE,
  version_no VARCHAR(30) NOT NULL,
  task_id BIGINT NOT NULL,
  change_type VARCHAR(30) NOT NULL,
  before_json JSONB,
  after_json JSONB,
  changed_by VARCHAR(50) NOT NULL,
  change_reason TEXT,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  request_id VARCHAR(64),
  CONSTRAINT fk_schedule_task_change_log_task
    FOREIGN KEY (task_id)
    REFERENCES schedule_task(id),
  CONSTRAINT fk_schedule_task_change_log_version
    FOREIGN KEY (version_no)
    REFERENCES schedule_version(version_no)
);

CREATE TABLE IF NOT EXISTS replan_job (
  id BIGSERIAL PRIMARY KEY,
  job_no VARCHAR(64) NOT NULL UNIQUE,
  trigger_type VARCHAR(30) NOT NULL,
  trigger_source_id VARCHAR(64),
  scope_type VARCHAR(20) NOT NULL,
  base_version_no VARCHAR(30),
  result_version_no VARCHAR(30),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ
);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_replan_job_base_version') THEN
    ALTER TABLE replan_job
      ADD CONSTRAINT fk_replan_job_base_version
      FOREIGN KEY (base_version_no)
      REFERENCES schedule_version(version_no)
      NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_replan_job_result_version') THEN
    ALTER TABLE replan_job
      ADD CONSTRAINT fk_replan_job_result_version
      FOREIGN KEY (result_version_no)
      REFERENCES schedule_version(version_no)
      NOT VALID;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS wip_lot (
  id BIGSERIAL PRIMARY KEY,
  wip_lot_id VARCHAR(64) NOT NULL UNIQUE,
  order_no VARCHAR(50) NOT NULL,
  process_code VARCHAR(50) NOT NULL,
  qty NUMERIC(18,4) NOT NULL,
  status VARCHAR(20) NOT NULL,
  source_report_id VARCHAR(64),
  create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_wip_lot_process
    FOREIGN KEY (process_code)
    REFERENCES process(process_code)
);

CREATE TABLE IF NOT EXISTS wip_lot_event (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  wip_lot_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(30) NOT NULL,
  from_process_code VARCHAR(50),
  to_process_code VARCHAR(50),
  qty NUMERIC(18,4) NOT NULL,
  event_time TIMESTAMPTZ NOT NULL,
  source_report_id VARCHAR(64),
  CONSTRAINT fk_wip_lot_event_lot
    FOREIGN KEY (wip_lot_id)
    REFERENCES wip_lot(wip_lot_id),
  CONSTRAINT fk_wip_lot_event_from_process
    FOREIGN KEY (from_process_code)
    REFERENCES process(process_code),
  CONSTRAINT fk_wip_lot_event_to_process
    FOREIGN KEY (to_process_code)
    REFERENCES process(process_code)
);

CREATE TABLE IF NOT EXISTS schedule_publish_log (
  id BIGSERIAL PRIMARY KEY,
  publish_id VARCHAR(64) NOT NULL UNIQUE,
  action_type VARCHAR(20) NOT NULL,
  from_version_no VARCHAR(30),
  to_version_no VARCHAR(30),
  operator VARCHAR(50) NOT NULL,
  request_id VARCHAR(64),
  result VARCHAR(20) NOT NULL,
  error_msg TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS equipment_downtime_event (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  equipment_code VARCHAR(50) NOT NULL,
  is_key_equipment SMALLINT NOT NULL DEFAULT 0,
  downtime_start_time TIMESTAMPTZ NOT NULL,
  downtime_end_time TIMESTAMPTZ,
  duration_minutes INT,
  reason_code VARCHAR(50),
  reason_desc TEXT,
  source VARCHAR(20) NOT NULL DEFAULT 'MES',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_equipment_downtime_equipment
    FOREIGN KEY (equipment_code)
    REFERENCES equipment_capacity(equipment_code)
);

CREATE TABLE IF NOT EXISTS employee_shift_availability (
  id BIGSERIAL PRIMARY KEY,
  availability_id VARCHAR(64) NOT NULL UNIQUE,
  employee_id VARCHAR(50) NOT NULL,
  calendar_date DATE NOT NULL,
  shift_code VARCHAR(20) NOT NULL,
  available_flag SMALLINT NOT NULL DEFAULT 1,
  availability_status VARCHAR(20) NOT NULL,
  effective_start_time TIMESTAMPTZ,
  effective_end_time TIMESTAMPTZ,
  source VARCHAR(20) NOT NULL DEFAULT 'HR',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_employee_shift_availability UNIQUE (employee_id, calendar_date, shift_code),
  CONSTRAINT fk_employee_shift_availability_employee
    FOREIGN KEY (employee_id)
    REFERENCES employee(employee_id)
);

CREATE TABLE IF NOT EXISTS alert_event (
  id BIGSERIAL PRIMARY KEY,
  alert_id VARCHAR(64) NOT NULL UNIQUE,
  alert_type VARCHAR(30) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  order_no VARCHAR(50),
  order_type VARCHAR(20),
  process_code VARCHAR(50),
  version_no VARCHAR(30),
  trigger_source VARCHAR(30),
  trigger_value NUMERIC(18,4),
  threshold_value NUMERIC(18,4),
  status VARCHAR(20) NOT NULL,
  ack_by VARCHAR(50),
  ack_time TIMESTAMPTZ,
  close_by VARCHAR(50),
  close_time TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_alert_event_process
    FOREIGN KEY (process_code)
    REFERENCES process(process_code),
  CONSTRAINT fk_alert_event_version
    FOREIGN KEY (version_no)
    REFERENCES schedule_version(version_no)
);

CREATE TABLE IF NOT EXISTS notification_delivery_log (
  id BIGSERIAL PRIMARY KEY,
  delivery_id VARCHAR(64) NOT NULL UNIQUE,
  alert_id VARCHAR(64) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  target VARCHAR(200) NOT NULL,
  message_id VARCHAR(100),
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  error_msg TEXT,
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_notification_delivery_alert
    FOREIGN KEY (alert_id)
    REFERENCES alert_event(alert_id)
);

-- ---------------------------------------------------------------------------
-- 4) Upstream fact landing tables
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sales_order_line_fact (
  id BIGSERIAL PRIMARY KEY,
  sales_order_no VARCHAR(50) NOT NULL,
  line_no VARCHAR(20) NOT NULL,
  customer_code VARCHAR(50),
  customer_name VARCHAR(100),
  product_code VARCHAR(50) NOT NULL,
  order_qty NUMERIC(18,4) NOT NULL,
  order_date TIMESTAMPTZ,
  expected_due_date TIMESTAMPTZ,
  promised_due_date TIMESTAMPTZ,
  requested_ship_date TIMESTAMPTZ,
  urgent_flag SMALLINT NOT NULL DEFAULT 0,
  order_status VARCHAR(20),
  last_update_time TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ,
  CONSTRAINT uq_sales_order_line_fact UNIQUE (sales_order_no, line_no)
);

CREATE TABLE IF NOT EXISTS production_order_fact (
  id BIGSERIAL PRIMARY KEY,
  production_order_no VARCHAR(50) NOT NULL UNIQUE,
  source_sales_order_no VARCHAR(50),
  source_line_no VARCHAR(20),
  source_plan_order_no VARCHAR(50),
  material_list_no VARCHAR(50),
  product_code VARCHAR(50),
  plan_qty NUMERIC(18,4),
  plan_start_date TIMESTAMPTZ,
  plan_finish_date TIMESTAMPTZ,
  production_status VARCHAR(20),
  last_update_time TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS schedule_control_fact (
  id BIGSERIAL PRIMARY KEY,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  review_passed_flag SMALLINT NOT NULL DEFAULT 0,
  frozen_flag SMALLINT NOT NULL DEFAULT 0,
  schedulable_flag SMALLINT NOT NULL DEFAULT 0,
  close_flag SMALLINT NOT NULL DEFAULT 0,
  promised_due_date TIMESTAMPTZ,
  last_update_time TIMESTAMPTZ,
  CONSTRAINT uq_schedule_control_fact UNIQUE (order_no, order_type)
);

CREATE TABLE IF NOT EXISTS mrp_result_link (
  id BIGSERIAL PRIMARY KEY,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  mrp_run_id VARCHAR(64) NOT NULL,
  run_time TIMESTAMPTZ,
  purchase_req_no VARCHAR(50),
  outsource_req_no VARCHAR(50),
  make_order_no VARCHAR(50),
  last_update_time TIMESTAMPTZ,
  CONSTRAINT uq_mrp_result_link UNIQUE (order_no, order_type, mrp_run_id)
);

CREATE TABLE IF NOT EXISTS production_material_list_item (
  id BIGSERIAL PRIMARY KEY,
  material_list_no VARCHAR(50) NOT NULL,
  production_order_no VARCHAR(50) NOT NULL,
  material_code VARCHAR(50) NOT NULL,
  required_qty NUMERIC(18,4) NOT NULL DEFAULT 0,
  issued_qty NUMERIC(18,4) NOT NULL DEFAULT 0,
  ready_flag SMALLINT NOT NULL DEFAULT 0,
  last_update_time TIMESTAMPTZ,
  CONSTRAINT uq_production_material_list_item UNIQUE (material_list_no, material_code)
);

CREATE TABLE IF NOT EXISTS delivery_progress (
  id BIGSERIAL PRIMARY KEY,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  warehoused_qty NUMERIC(18,4) NOT NULL DEFAULT 0,
  shipped_qty NUMERIC(18,4) NOT NULL DEFAULT 0,
  delivery_status VARCHAR(20),
  last_update_time TIMESTAMPTZ,
  CONSTRAINT uq_delivery_progress UNIQUE (order_no, order_type)
);

CREATE TABLE IF NOT EXISTS order_priority_factor (
  id BIGSERIAL PRIMARY KEY,
  version_no VARCHAR(30) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  customer_priority NUMERIC(10,4),
  manual_weight NUMERIC(10,4),
  due_urgency_score NUMERIC(10,4),
  urgent_score NUMERIC(10,4),
  order_time_score NUMERIC(10,4),
  total_score NUMERIC(10,4),
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_order_priority_factor UNIQUE (version_no, order_no, order_type),
  CONSTRAINT fk_order_priority_factor_version
    FOREIGN KEY (version_no)
    REFERENCES schedule_version(version_no)
);

-- ---------------------------------------------------------------------------
-- 5) Add FKs on existing tables that now have referenced domains
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_employee_skill_employee'
  ) THEN
    ALTER TABLE employee_skill
      ADD CONSTRAINT fk_employee_skill_employee
      FOREIGN KEY (employee_id)
      REFERENCES employee(employee_id)
      NOT VALID;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_employee_skill_process'
  ) THEN
    ALTER TABLE employee_skill
      ADD CONSTRAINT fk_employee_skill_process
      FOREIGN KEY (process_code)
      REFERENCES process(process_code)
      NOT VALID;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_schedule_task_process'
  ) THEN
    ALTER TABLE schedule_task
      ADD CONSTRAINT fk_schedule_task_process
      FOREIGN KEY (process_code)
      REFERENCES process(process_code)
      NOT VALID;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_reporting_fact_process'
  ) THEN
    ALTER TABLE reporting_fact
      ADD CONSTRAINT fk_reporting_fact_process
      FOREIGN KEY (process_code)
      REFERENCES process(process_code)
      NOT VALID;
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 6) Key indexes for newly introduced tables
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_process_route_product_seq
  ON process_route (product_code, sequence_no);

CREATE INDEX IF NOT EXISTS idx_route_step_dependency_route
  ON route_step_dependency (route_no, from_process_code, to_process_code);

CREATE INDEX IF NOT EXISTS idx_schedule_task_resource_slot
  ON schedule_task_resource (resource_type, resource_code, plan_start_time, plan_finish_time);

CREATE INDEX IF NOT EXISTS idx_schedule_task_change_log_task_time
  ON schedule_task_change_log (task_id, changed_at);

CREATE INDEX IF NOT EXISTS idx_replan_job_status_created
  ON replan_job (status, created_at);

CREATE INDEX IF NOT EXISTS idx_wip_lot_order_process_status
  ON wip_lot (order_no, process_code, status);

CREATE INDEX IF NOT EXISTS idx_wip_lot_event_lot_time
  ON wip_lot_event (wip_lot_id, event_time);

CREATE INDEX IF NOT EXISTS idx_schedule_publish_log_action_created
  ON schedule_publish_log (action_type, created_at);

CREATE INDEX IF NOT EXISTS idx_equipment_downtime_equipment_start
  ON equipment_downtime_event (equipment_code, downtime_start_time);

CREATE INDEX IF NOT EXISTS idx_employee_shift_availability_shift
  ON employee_shift_availability (calendar_date, shift_code, available_flag);

CREATE INDEX IF NOT EXISTS idx_alert_event_status_severity_time
  ON alert_event (status, severity, created_at);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_alert_channel_time
  ON notification_delivery_log (alert_id, channel, created_at);

CREATE INDEX IF NOT EXISTS idx_sales_order_line_fact_due_urgent_time
  ON sales_order_line_fact (expected_due_date, urgent_flag, last_update_time);

CREATE INDEX IF NOT EXISTS idx_production_order_fact_source_plan_time
  ON production_order_fact (source_plan_order_no, last_update_time);

CREATE INDEX IF NOT EXISTS idx_schedule_control_fact_flags_time
  ON schedule_control_fact (schedulable_flag, frozen_flag, close_flag, last_update_time);

CREATE INDEX IF NOT EXISTS idx_mrp_result_link_order_runtime
  ON mrp_result_link (order_no, order_type, run_time);

CREATE INDEX IF NOT EXISTS idx_production_material_list_item_order_ready
  ON production_material_list_item (production_order_no, ready_flag);

CREATE INDEX IF NOT EXISTS idx_delivery_progress_status_time
  ON delivery_progress (delivery_status, last_update_time);

CREATE INDEX IF NOT EXISTS idx_order_priority_factor_version_score
  ON order_priority_factor (version_no, total_score);

COMMIT;
