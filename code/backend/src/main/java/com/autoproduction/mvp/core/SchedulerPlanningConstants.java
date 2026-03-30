package com.autoproduction.mvp.core;

final class SchedulerPlanningConstants {
  static final double EPS = 1e-9d;

  static final String REASON_CAPACITY_MANPOWER = "CAPACITY_MANPOWER";
  static final String REASON_CAPACITY_MACHINE = "CAPACITY_MACHINE";
  static final String REASON_CAPACITY_UNKNOWN = "CAPACITY_UNKNOWN";
  static final String REASON_MATERIAL_SHORTAGE = "MATERIAL_SHORTAGE";
  static final String REASON_COMPONENT_SHORTAGE = "COMPONENT_SHORTAGE";
  static final String REASON_DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
  static final String REASON_TRANSFER_CONSTRAINT = "TRANSFER_CONSTRAINT";
  static final String REASON_FROZEN_BY_POLICY = "FROZEN_BY_POLICY";
  static final String REASON_LOCKED_PRESERVED = "LOCKED_PRESERVED";
  static final String REASON_URGENT_GUARANTEE = "URGENT_GUARANTEE";
  static final String REASON_LOCK_PREEMPTED = "LOCK_PREEMPTED_BY_URGENT";
  static final String REASON_BEFORE_EXPECTED_START = "BEFORE_EXPECTED_START";

  static final String DEPENDENCY_READY = "READY";
  static final String DEPENDENCY_WAIT_PREDECESSOR = "WAIT_PREDECESSOR";
  static final String DEPENDENCY_BLOCKED_PREDECESSOR = "BLOCKED_BY_PREDECESSOR";

  static final String TASK_STATUS_READY = "READY";
  static final String TASK_STATUS_PARTIALLY_ALLOCATED = "PARTIALLY_ALLOCATED";
  static final String TASK_STATUS_UNSCHEDULED = "UNSCHEDULED";
  static final String TASK_STATUS_PRESERVED_LOCKED = "PRESERVED_LOCKED";
  static final String TASK_STATUS_SKIPPED_FROZEN = "SKIPPED_FROZEN";

  static final double URGENT_MIN_SHARE_PER_PROCESS = 0.35d;
  static final double LOCKED_MAX_SHARE_WHEN_URGENT = 0.65d;
  static final double URGENT_MIN_DAILY_OUTPUT_RATIO = 0.12d;
  static final double URGENT_MIN_DAILY_OUTPUT_ABS = 60d;

  static final int MAX_ROUNDS_PER_SHIFT = 3;
  static final double ALLOCATION_CHUNK_RATIO = 0.45d;
  static final double CHANGEOVER_CAPACITY_FACTOR = 0.85d;
  static final double PRODUCT_MIX_CAPACITY_FACTOR = 0.92d;
  static final double NIGHT_SHIFT_CAPACITY_FACTOR = 0.90d;

  static final int DEFAULT_TRANSFER_BATCH = 60;
  static final int STERILE_TRANSFER_BATCH = 120;
  static final int DEFAULT_MIN_LOT = 30;
  static final int STERILE_MIN_LOT = 80;
  static final int STERILE_RELEASE_LAG_SHIFTS = 1;

  static final String COMPONENT_RAW_PREFIX = "RAW_";

  static final String STRATEGY_KEY_ORDER_FIRST = StrategyRuleRegistry.KEY_ORDER_FIRST;
  static final String STRATEGY_MAX_CAPACITY_FIRST = StrategyRuleRegistry.MAX_CAPACITY_FIRST;
  static final String STRATEGY_MIN_DELAY_FIRST = StrategyRuleRegistry.MIN_DELAY_FIRST;

  private SchedulerPlanningConstants() {}
}

