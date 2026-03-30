package com.autoproduction.mvp.core;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

final class MvpStoreRuntimeBaseConstantsSupport {
  private MvpStoreRuntimeBaseConstantsSupport() {}

  protected static final long DEFAULT_SIM_SEED = MvpStoreRuntimeBaseRuntimeConstantsSupport.DEFAULT_SIM_SEED;
  protected static final int DEFAULT_SIM_DAILY_SALES = MvpStoreRuntimeBaseRuntimeConstantsSupport.DEFAULT_SIM_DAILY_SALES;

  protected static final int ORDER_MATERIAL_AVAILABILITY_MAX_ORDERS =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.ORDER_MATERIAL_AVAILABILITY_MAX_ORDERS;
  protected static final long ORDER_MATERIAL_AVAILABILITY_BUDGET_MS =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.ORDER_MATERIAL_AVAILABILITY_BUDGET_MS;
  protected static final long ORDER_MATERIAL_AVAILABILITY_REFRESH_BUDGET_MS =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.ORDER_MATERIAL_AVAILABILITY_REFRESH_BUDGET_MS;
  protected static final int ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE;
  protected static final int ORDER_MATERIAL_INVENTORY_PARALLEL_THREADS =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.ORDER_MATERIAL_INVENTORY_PARALLEL_THREADS;
  protected static final Set<String> ORDER_MATERIAL_PRIORITY_ORDER_NOS =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.ORDER_MATERIAL_PRIORITY_ORDER_NOS;

  protected static final String DEFAULT_SIM_SCENARIO = MvpStoreRuntimeBaseRuntimeConstantsSupport.DEFAULT_SIM_SCENARIO;
  protected static final ZoneId SIMULATION_ZONE = MvpStoreRuntimeBaseRuntimeConstantsSupport.SIMULATION_ZONE;

  protected static final String STRATEGY_KEY_ORDER_FIRST = MvpStoreRuntimeBaseLocalizationConstantsSupport.STRATEGY_KEY_ORDER_FIRST;
  protected static final String STRATEGY_MAX_CAPACITY_FIRST = MvpStoreRuntimeBaseLocalizationConstantsSupport.STRATEGY_MAX_CAPACITY_FIRST;
  protected static final String STRATEGY_MIN_DELAY_FIRST = MvpStoreRuntimeBaseLocalizationConstantsSupport.STRATEGY_MIN_DELAY_FIRST;

  protected static final String LIVE_REPORT_VERSION_NO = MvpStoreRuntimeBaseRuntimeConstantsSupport.LIVE_REPORT_VERSION_NO;
  protected static final String LIVE_REPORT_STATUS = MvpStoreRuntimeBaseRuntimeConstantsSupport.LIVE_REPORT_STATUS;

  protected static final Map<String, String> SCHEDULE_STRATEGY_NAME_CN =
    MvpStoreRuntimeBaseLocalizationConstantsSupport.SCHEDULE_STRATEGY_NAME_CN;

  protected static final Map<String, Integer> BASE_WORKERS_BY_PROCESS = MvpStoreRuntimeBaseRuntimeConstantsSupport.BASE_WORKERS_BY_PROCESS;
  protected static final Map<String, Integer> BASE_MACHINES_BY_PROCESS = MvpStoreRuntimeBaseRuntimeConstantsSupport.BASE_MACHINES_BY_PROCESS;

  protected static final Set<String> DEPRECATED_MASTERDATA_CONFIG_FIELDS =
    MvpStoreRuntimeBaseRuntimeConstantsSupport.DEPRECATED_MASTERDATA_CONFIG_FIELDS;

  protected static final Map<String, String> PRODUCT_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.PRODUCT_NAME_CN;
  protected static final Map<String, String> PROCESS_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.PROCESS_NAME_CN;
  protected static final Map<String, String> STATUS_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.STATUS_NAME_CN;
  protected static final Map<String, String> ACTION_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.ACTION_NAME_CN;
  protected static final Map<String, String> EVENT_TYPE_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.EVENT_TYPE_NAME_CN;
  protected static final Map<String, String> TOPIC_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.TOPIC_NAME_CN;
  protected static final Map<String, String> SYSTEM_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.SYSTEM_NAME_CN;
  protected static final Map<String, String> SCENARIO_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.SCENARIO_NAME_CN;
  protected static final Map<String, String> SHIFT_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.SHIFT_NAME_CN;

  protected static final String WEEKEND_REST_MODE_NONE = MvpStoreRuntimeBaseRuntimeConstantsSupport.WEEKEND_REST_MODE_NONE;
  protected static final String WEEKEND_REST_MODE_SINGLE = MvpStoreRuntimeBaseRuntimeConstantsSupport.WEEKEND_REST_MODE_SINGLE;
  protected static final String WEEKEND_REST_MODE_DOUBLE = MvpStoreRuntimeBaseRuntimeConstantsSupport.WEEKEND_REST_MODE_DOUBLE;

  protected static final String DATE_SHIFT_MODE_REST = MvpStoreRuntimeBaseRuntimeConstantsSupport.DATE_SHIFT_MODE_REST;
  protected static final String DATE_SHIFT_MODE_DAY = MvpStoreRuntimeBaseRuntimeConstantsSupport.DATE_SHIFT_MODE_DAY;
  protected static final String DATE_SHIFT_MODE_NIGHT = MvpStoreRuntimeBaseRuntimeConstantsSupport.DATE_SHIFT_MODE_NIGHT;
  protected static final String DATE_SHIFT_MODE_BOTH = MvpStoreRuntimeBaseRuntimeConstantsSupport.DATE_SHIFT_MODE_BOTH;

  protected static final String DEFAULT_COMPANY_CODE = MvpStoreRuntimeBaseRuntimeConstantsSupport.DEFAULT_COMPANY_CODE;

  protected static final Set<String> CN_STATUTORY_HOLIDAY_DATE_SET = MvpStoreRuntimeBaseCalendarConstantsSupport.CN_STATUTORY_HOLIDAY_DATE_SET;
  protected static final Map<String, String> DEPENDENCY_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.DEPENDENCY_NAME_CN;

  protected static final String WEEKLY_PLAN_SHEET_NAME = MvpStoreRuntimeBaseExportTemplateConstantsSupport.WEEKLY_PLAN_SHEET_NAME;
  protected static final String WEEKLY_PLAN_TITLE_CN = MvpStoreRuntimeBaseExportTemplateConstantsSupport.WEEKLY_PLAN_TITLE_CN;
  protected static final String[] WEEKLY_PLAN_EXTRA_SHEETS = MvpStoreRuntimeBaseExportTemplateConstantsSupport.WEEKLY_PLAN_EXTRA_SHEETS;
  protected static final String MONTHLY_PLAN_SHEET_NAME = MvpStoreRuntimeBaseExportTemplateConstantsSupport.MONTHLY_PLAN_SHEET_NAME;
  protected static final String MONTHLY_PLAN_TITLE_CN = MvpStoreRuntimeBaseExportTemplateConstantsSupport.MONTHLY_PLAN_TITLE_CN;
  protected static final String[] WEEKLY_PLAN_HEADERS_CN = MvpStoreRuntimeBaseExportTemplateConstantsSupport.WEEKLY_PLAN_HEADERS_CN;
  protected static final String[] WEEKLY_PLAN_KEYS = MvpStoreRuntimeBaseExportTemplateConstantsSupport.WEEKLY_PLAN_KEYS;
  protected static final String[] MONTHLY_PLAN_HEADERS_CN = MvpStoreRuntimeBaseExportTemplateConstantsSupport.MONTHLY_PLAN_HEADERS_CN;
  protected static final String[] MONTHLY_PLAN_KEYS = MvpStoreRuntimeBaseExportTemplateConstantsSupport.MONTHLY_PLAN_KEYS;

  protected static final Map<String, String> ALERT_TYPE_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.ALERT_TYPE_NAME_CN;
  protected static final Map<String, String> SEVERITY_NAME_CN = MvpStoreRuntimeBaseLocalizationConstantsSupport.SEVERITY_NAME_CN;
}

