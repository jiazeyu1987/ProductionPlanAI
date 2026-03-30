package com.autoproduction.mvp.core;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

final class MvpStoreRuntimeBaseRuntimeConstantsSupport {
  private MvpStoreRuntimeBaseRuntimeConstantsSupport() {}

  static final long DEFAULT_SIM_SEED = 20260322L;
  static final int DEFAULT_SIM_DAILY_SALES = 20;

  static final int ORDER_MATERIAL_AVAILABILITY_MAX_ORDERS = 12;
  static final long ORDER_MATERIAL_AVAILABILITY_BUDGET_MS = 90_000L;
  static final long ORDER_MATERIAL_AVAILABILITY_REFRESH_BUDGET_MS = 120_000L;
  static final int ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE = 40;
  static final int ORDER_MATERIAL_INVENTORY_PARALLEL_THREADS = 6;
  static final Set<String> ORDER_MATERIAL_PRIORITY_ORDER_NOS = Set.of(
    "881MO091041",
    "881MO090957",
    "881MO090955",
    "881MO090954"
  );

  static final String DEFAULT_SIM_SCENARIO = "STABLE";
  static final ZoneId SIMULATION_ZONE = ZoneId.of("Asia/Shanghai");

  static final String LIVE_REPORT_VERSION_NO = "LIVE_STATE";
  static final String LIVE_REPORT_STATUS = "LIVE";

  static final Map<String, Integer> BASE_WORKERS_BY_PROCESS = Map.of(
    "PROC_TUBE", 8,
    "PROC_ASSEMBLY", 10,
    "PROC_BALLOON", 8,
    "PROC_STENT", 9,
    "PROC_STERILE", 6
  );

  static final Map<String, Integer> BASE_MACHINES_BY_PROCESS = Map.of(
    "PROC_TUBE", 3,
    "PROC_ASSEMBLY", 3,
    "PROC_BALLOON", 2,
    "PROC_STENT", 2,
    "PROC_STERILE", 2
  );

  static final Set<String> DEPRECATED_MASTERDATA_CONFIG_FIELDS = Set.of(
    "horizon_start_date",
    "horizonStartDate",
    "horizon_days",
    "horizonDays",
    "shifts_per_day",
    "shiftsPerDay",
    "shift_hours",
    "shiftHours",
    "skip_statutory_holidays",
    "skipStatutoryHolidays",
    "weekend_rest_mode",
    "weekendRestMode",
    "date_shift_mode_by_date",
    "dateShiftModeByDate",
    "section_leader_bindings",
    "sectionLeaderBindings",
    "resource_pool",
    "resourcePool"
  );

  static final String WEEKEND_REST_MODE_NONE = "NONE";
  static final String WEEKEND_REST_MODE_SINGLE = "SINGLE";
  static final String WEEKEND_REST_MODE_DOUBLE = "DOUBLE";

  static final String DATE_SHIFT_MODE_REST = "REST";
  static final String DATE_SHIFT_MODE_DAY = "DAY";
  static final String DATE_SHIFT_MODE_NIGHT = "NIGHT";
  static final String DATE_SHIFT_MODE_BOTH = "BOTH";

  static final String DEFAULT_COMPANY_CODE = "COMPANY-MAIN";
}

