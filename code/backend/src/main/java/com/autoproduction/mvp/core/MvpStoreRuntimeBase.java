package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
abstract class MvpStoreRuntimeBase {
  protected static final long DEFAULT_SIM_SEED = MvpStoreRuntimeBaseConstantsSupport.DEFAULT_SIM_SEED, ORDER_MATERIAL_AVAILABILITY_BUDGET_MS = MvpStoreRuntimeBaseConstantsSupport.ORDER_MATERIAL_AVAILABILITY_BUDGET_MS, ORDER_MATERIAL_AVAILABILITY_REFRESH_BUDGET_MS = MvpStoreRuntimeBaseConstantsSupport.ORDER_MATERIAL_AVAILABILITY_REFRESH_BUDGET_MS;
  protected static final int DEFAULT_SIM_DAILY_SALES = MvpStoreRuntimeBaseConstantsSupport.DEFAULT_SIM_DAILY_SALES, ORDER_MATERIAL_AVAILABILITY_MAX_ORDERS = MvpStoreRuntimeBaseConstantsSupport.ORDER_MATERIAL_AVAILABILITY_MAX_ORDERS, ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE = MvpStoreRuntimeBaseConstantsSupport.ORDER_MATERIAL_INVENTORY_PARALLEL_CHUNK_SIZE, ORDER_MATERIAL_INVENTORY_PARALLEL_THREADS = MvpStoreRuntimeBaseConstantsSupport.ORDER_MATERIAL_INVENTORY_PARALLEL_THREADS;
  protected static final String DEFAULT_SIM_SCENARIO = MvpStoreRuntimeBaseConstantsSupport.DEFAULT_SIM_SCENARIO, STRATEGY_KEY_ORDER_FIRST = MvpStoreRuntimeBaseConstantsSupport.STRATEGY_KEY_ORDER_FIRST, STRATEGY_MAX_CAPACITY_FIRST = MvpStoreRuntimeBaseConstantsSupport.STRATEGY_MAX_CAPACITY_FIRST, STRATEGY_MIN_DELAY_FIRST = MvpStoreRuntimeBaseConstantsSupport.STRATEGY_MIN_DELAY_FIRST, LIVE_REPORT_VERSION_NO = MvpStoreRuntimeBaseConstantsSupport.LIVE_REPORT_VERSION_NO, LIVE_REPORT_STATUS = MvpStoreRuntimeBaseConstantsSupport.LIVE_REPORT_STATUS, WEEKEND_REST_MODE_NONE = MvpStoreRuntimeBaseConstantsSupport.WEEKEND_REST_MODE_NONE, WEEKEND_REST_MODE_SINGLE = MvpStoreRuntimeBaseConstantsSupport.WEEKEND_REST_MODE_SINGLE, WEEKEND_REST_MODE_DOUBLE = MvpStoreRuntimeBaseConstantsSupport.WEEKEND_REST_MODE_DOUBLE, DATE_SHIFT_MODE_REST = MvpStoreRuntimeBaseConstantsSupport.DATE_SHIFT_MODE_REST, DATE_SHIFT_MODE_DAY = MvpStoreRuntimeBaseConstantsSupport.DATE_SHIFT_MODE_DAY, DATE_SHIFT_MODE_NIGHT = MvpStoreRuntimeBaseConstantsSupport.DATE_SHIFT_MODE_NIGHT, DATE_SHIFT_MODE_BOTH = MvpStoreRuntimeBaseConstantsSupport.DATE_SHIFT_MODE_BOTH, DEFAULT_COMPANY_CODE = MvpStoreRuntimeBaseConstantsSupport.DEFAULT_COMPANY_CODE, WEEKLY_PLAN_SHEET_NAME = MvpStoreRuntimeBaseConstantsSupport.WEEKLY_PLAN_SHEET_NAME, WEEKLY_PLAN_TITLE_CN = MvpStoreRuntimeBaseConstantsSupport.WEEKLY_PLAN_TITLE_CN, MONTHLY_PLAN_SHEET_NAME = MvpStoreRuntimeBaseConstantsSupport.MONTHLY_PLAN_SHEET_NAME, MONTHLY_PLAN_TITLE_CN = MvpStoreRuntimeBaseConstantsSupport.MONTHLY_PLAN_TITLE_CN;
  protected static final ZoneId SIMULATION_ZONE = MvpStoreRuntimeBaseConstantsSupport.SIMULATION_ZONE;
  protected static final Map<String, Integer> BASE_WORKERS_BY_PROCESS = MvpStoreRuntimeBaseConstantsSupport.BASE_WORKERS_BY_PROCESS, BASE_MACHINES_BY_PROCESS = MvpStoreRuntimeBaseConstantsSupport.BASE_MACHINES_BY_PROCESS;
  protected static final Map<String, String> SCHEDULE_STRATEGY_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.SCHEDULE_STRATEGY_NAME_CN, PRODUCT_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.PRODUCT_NAME_CN, PROCESS_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.PROCESS_NAME_CN, STATUS_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.STATUS_NAME_CN, ACTION_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.ACTION_NAME_CN, EVENT_TYPE_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.EVENT_TYPE_NAME_CN, TOPIC_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.TOPIC_NAME_CN, SYSTEM_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.SYSTEM_NAME_CN, SCENARIO_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.SCENARIO_NAME_CN, SHIFT_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.SHIFT_NAME_CN, DEPENDENCY_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.DEPENDENCY_NAME_CN, ALERT_TYPE_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.ALERT_TYPE_NAME_CN, SEVERITY_NAME_CN = MvpStoreRuntimeBaseConstantsSupport.SEVERITY_NAME_CN;
  protected static final Set<String> ORDER_MATERIAL_PRIORITY_ORDER_NOS = MvpStoreRuntimeBaseConstantsSupport.ORDER_MATERIAL_PRIORITY_ORDER_NOS, DEPRECATED_MASTERDATA_CONFIG_FIELDS = MvpStoreRuntimeBaseConstantsSupport.DEPRECATED_MASTERDATA_CONFIG_FIELDS, CN_STATUTORY_HOLIDAY_DATE_SET = MvpStoreRuntimeBaseConstantsSupport.CN_STATUTORY_HOLIDAY_DATE_SET;
  protected static final String[] WEEKLY_PLAN_EXTRA_SHEETS = MvpStoreRuntimeBaseConstantsSupport.WEEKLY_PLAN_EXTRA_SHEETS, WEEKLY_PLAN_HEADERS_CN = MvpStoreRuntimeBaseConstantsSupport.WEEKLY_PLAN_HEADERS_CN, WEEKLY_PLAN_KEYS = MvpStoreRuntimeBaseConstantsSupport.WEEKLY_PLAN_KEYS, MONTHLY_PLAN_HEADERS_CN = MvpStoreRuntimeBaseConstantsSupport.MONTHLY_PLAN_HEADERS_CN, MONTHLY_PLAN_KEYS = MvpStoreRuntimeBaseConstantsSupport.MONTHLY_PLAN_KEYS;

  protected final Object lock = new Object();
  protected final ObjectMapper objectMapper = new ObjectMapper();
  protected final ErpDataManager erpDataManager;
  protected final AtomicInteger reportingSeq = new AtomicInteger(0), replanSeq = new AtomicInteger(0), alertSeq = new AtomicInteger(0),
    dispatchSeq = new AtomicInteger(0), dispatchApprovalSeq = new AtomicInteger(0), salesSeq = new AtomicInteger(0),
    productionSeq = new AtomicInteger(0), simulationEventSeq = new AtomicInteger(0);
  protected MvpDomain.State state = SeedDataFactory.build();
  protected final SimulationState simulationState = new SimulationState();
  protected final Map<String, Double> finalCompletedByOrderProductCache = new HashMap<>();
  protected final Map<String, CachedOrderPoolMaterials> orderPoolMaterialsCache = new HashMap<>(),
    materialChildrenByParentCache = new HashMap<>();
  protected int finalCompletedSyncCursor = 0;
  protected ManualSimulationSnapshot manualSimulationSnapshot;

  protected MvpStoreRuntimeBase(ErpDataManager erpDataManager) {
    this.erpDataManager = erpDataManager;
    resetSimulationState(DEFAULT_SIM_SEED, DEFAULT_SIM_SCENARIO, DEFAULT_SIM_DAILY_SALES);
  }


  protected abstract void resetSimulationState(long seed, String scenario, int dailySalesOrderCount);
  protected abstract MvpDomain.OrderBusinessData buildSimulationBusinessData(LocalDate startDate, LocalDate dueDate, String salesOrderNo, String productCode, double qty);
  protected abstract MvpDomain.OrderBusinessData businessData(MvpDomain.Order order);
  protected abstract MvpDomain.ScheduleVersion getScheduleEntity(String versionNo);
  protected abstract Map<String, Object> localizeRow(Map<String, Object> source);
  protected abstract MvpDomain.ProcessConfig processConfigByCode(String processCode);
  protected abstract boolean isDateInCurrentHorizon(LocalDate date);
  protected abstract boolean isShiftEnabledInCurrentSetting(String shiftCode);
  protected abstract OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshot(boolean refreshFromErp);
  protected abstract void applyOrderMaterialConstraintsForSchedule(MvpDomain.State scheduleState, Map<String, OrderMaterialConstraint> constraintByOrderNo);

  public abstract void reset(); public abstract List<Map<String, Object>> listOrders(); public abstract List<Map<String, Object>> listOrderPool(Map<String, String> filters); public abstract List<Map<String, Object>> listOrderPoolMaterials(String orderNo); public abstract List<Map<String, Object>> listOrderPoolMaterials(String orderNo, boolean refreshFromErp); public abstract List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode); public abstract List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode, boolean refreshFromErp); public abstract List<Map<String, Object>> listOrderMaterialAvailability(boolean refreshFromErp);
  public abstract Map<String, Object> upsertOrder(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> patchOrder(String orderNo, Map<String, Object> patch, String requestId, String operator); public abstract Map<String, Object> generateSchedule(Map<String, Object> options, String requestId, String operator); public abstract List<Map<String, Object>> listSchedules(); public abstract Map<String, Object> getLatestSchedule(); public abstract Map<String, Object> validateSchedule(String versionNo); public abstract Map<String, Object> publishSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> rollbackSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator);
  public abstract Map<String, Object> recordReporting(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> deleteReporting(String reportingId, String requestId, String operator); public abstract List<Map<String, Object>> listReportings(); public abstract List<Map<String, Object>> listReportingsForMes(Map<String, String> filters); public abstract Map<String, Object> writeScheduleResults(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> writeScheduleStatus(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> ingestWipLotEvent(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> triggerReplanJob(Map<String, Object> payload, String requestId, String operator);
  public abstract Map<String, Object> getReplanJob(String jobNo, String requestId); public abstract List<Map<String, Object>> listAlerts(Map<String, String> filters); public abstract Map<String, Object> ackAlert(String alertId, Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> closeAlert(String alertId, Map<String, Object> payload, String requestId, String operator); public abstract List<Map<String, Object>> listAuditLogs(Map<String, String> filters); public abstract List<Map<String, Object>> listIntegrationInbox(Map<String, String> filters); public abstract List<Map<String, Object>> listIntegrationOutbox(Map<String, String> filters); public abstract Map<String, Object> getSimulationState(String requestId);
  public abstract List<Map<String, Object>> listSimulationEvents(Map<String, String> filters); public abstract Map<String, Object> resetSimulation(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> runSimulation(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> addManualProductionOrder(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> advanceManualOneDay(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> resetManualSimulation(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> retryOutboxMessage(String messageId, String requestId, String operator); public abstract Map<String, Object> createDispatchCommand(Map<String, Object> payload, String requestId, String operator);
  public abstract Map<String, Object> approveDispatchCommand(String commandId, Map<String, Object> payload, String requestId, String operator); public abstract List<Map<String, Object>> listDispatchCommands(Map<String, String> filters); public abstract List<Map<String, Object>> listScheduleVersions(Map<String, String> filters); public abstract List<Map<String, Object>> listScheduleTasks(String versionNo); public abstract List<Map<String, Object>> listScheduleDailyProcessLoad(String versionNo); public abstract List<Map<String, Object>> listScheduleShiftProcessLoad(String versionNo); public abstract Map<String, Object> getScheduleAlgorithm(String versionNo, String requestId); public abstract Map<String, Object> getVersionDiff(String versionNo, String compareWith, String requestId);
  public abstract List<Map<String, Object>> listSalesOrderLines(); public abstract List<Map<String, Object>> listErpSalesOrderHeadersRaw(); public abstract List<Map<String, Object>> listErpSalesOrderLinesRaw(); public abstract List<Map<String, Object>> listPlanOrders(); public abstract List<Map<String, Object>> listPurchaseOrders(); public abstract List<Map<String, Object>> listProductionOrders(); public abstract List<Map<String, Object>> listErpProductionOrderHeadersRaw(); public abstract List<Map<String, Object>> listErpProductionOrderLinesRaw();
  public abstract List<Map<String, Object>> listWorkshopWeeklyPlanRows(); public abstract List<Map<String, Object>> listWorkshopWeeklyPlanRows(String versionNo); public abstract List<Map<String, Object>> listWorkshopMonthlyPlanRows(); public abstract List<Map<String, Object>> listWorkshopMonthlyPlanRows(String versionNo); public abstract byte[] exportWorkshopWeeklyPlanXlsx(); public abstract byte[] exportWorkshopWeeklyPlanXlsx(String versionNo); public abstract byte[] exportWorkshopMonthlyPlanXls(); public abstract byte[] exportWorkshopMonthlyPlanXls(String versionNo);
  public abstract List<Map<String, Object>> listScheduleControls(); public abstract List<Map<String, Object>> listMrpLinks(); public abstract List<Map<String, Object>> listDeliveryProgress(); public abstract List<Map<String, Object>> listMaterialAvailability(); public abstract List<Map<String, Object>> listEquipments(); public abstract List<Map<String, Object>> listProcessRoutes(); public abstract List<Map<String, Object>> listEquipmentProcessCapabilities(); public abstract List<Map<String, Object>> listEmployeeSkills();
  public abstract List<Map<String, Object>> listShiftCalendar(); public abstract Map<String, Object> getMasterdataConfig(String requestId); public abstract Map<String, Object> getScheduleCalendarRules(String requestId); public abstract Map<String, Object> saveMasterdataConfig(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> saveScheduleCalendarRules(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> createMasterdataRoute(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> updateMasterdataRoute(Map<String, Object> payload, String requestId, String operator); public abstract Map<String, Object> copyMasterdataRoute(Map<String, Object> payload, String requestId, String operator);
  public abstract Map<String, Object> deleteMasterdataRoute(Map<String, Object> payload, String requestId, String operator);

  protected static String firstString(Map<String, Object> row, String... keys) { return MvpStoreRuntimeBaseLookupSupport.firstString(row, keys); }

  protected static String productNameCn(String productCode) { return MvpStoreRuntimeBaseLookupSupport.nameCn(PRODUCT_NAME_CN, productCode); }

  protected static String scenarioNameCn(String scenario) { return MvpStoreRuntimeBaseLookupSupport.nameCn(SCENARIO_NAME_CN, scenario); }

  protected static String processNameCn(String processCode) { return MvpStoreRuntimeBaseLookupSupport.nameCn(PROCESS_NAME_CN, processCode); }

  protected static String shiftNameCn(String shiftCode) { return MvpStoreRuntimeBaseLookupSupport.nameCn(SHIFT_NAME_CN, shiftCode); }

  protected static String eventTypeNameCn(String eventType) { return MvpStoreRuntimeBaseLookupSupport.nameCn(EVENT_TYPE_NAME_CN, eventType); }

  protected static String normalizeWeekendRestMode(String modeText) { return MvpStoreRuntimeBaseDateSupport.normalizeWeekendRestMode(modeText, WEEKEND_REST_MODE_NONE, WEEKEND_REST_MODE_SINGLE, WEEKEND_REST_MODE_DOUBLE); }

  protected static String toDateTime(String date, String shiftCode, boolean start) { return MvpStoreRuntimeBaseDateSupport.toDateTime(date, shiftCode, start); }

  protected static final class OrderMaterialConstraintSnapshot {
    List<Map<String, Object>> rows; Map<String, OrderMaterialConstraint> constraintByOrderNo;
    OrderMaterialConstraintSnapshot(List<Map<String, Object>> rows, Map<String, OrderMaterialConstraint> constraintByOrderNo) {
      this.rows = rows == null ? List.of() : rows; this.constraintByOrderNo = constraintByOrderNo == null ? Map.of() : constraintByOrderNo;
    }
  }

  protected static final class OrderMaterialConstraint {
    double maxSchedulableQty; String dataStatus; String firstProcessCode;
  }

  protected static final class MaterialDemandRow {
    String materialCode; String materialNameCn; double requiredQty; double demandQty;
  }

  protected static final class MaterialUnionAggregate {
    String materialCode; String materialNameCn; double demandQty; double stockQty; boolean stockAssigned; boolean inventoryMissing; Set<String> orderNos = new HashSet<>();
  }

  protected static final class OrderMaterialWorkRow {
    String orderNo; String orderStatus; String productCode; String productNameCn; String firstProcessCode; String firstProcessNameCn; double totalOrderQty; double remainingOrderQty; List<MaterialDemandRow> materialRows = new ArrayList<>();
  }

  protected static final class CachedOrderPoolMaterials {
    List<Map<String, Object>> rows; String refreshedAt;
    CachedOrderPoolMaterials(List<Map<String, Object>> rows, String refreshedAt) { this.rows = rows == null ? List.of() : rows; this.refreshedAt = refreshedAt; }
  }

  protected static final class ReportVersionBinding {
    String versionNo; String status; Set<String> orderNos;
    ReportVersionBinding(String versionNo, String status, Set<String> orderNos) { this.versionNo = versionNo; this.status = status; this.orderNos = orderNos; }
  }

  protected static final class SimulationState {
    LocalDate currentDate; long seed; String scenario; int dailySalesOrderCount;
    List<Map<String, Object>> salesOrders = new ArrayList<>(); List<Map<String, Object>> events = new ArrayList<>(); Map<String, Object> lastRunSummary = new LinkedHashMap<>();
  }

  protected static final class ManualSimulationSnapshot {
    MvpDomain.State state; SimulationState simulationState; int reportingSeq; int replanSeq; int alertSeq; int dispatchSeq; int dispatchApprovalSeq; int salesSeq; int productionSeq; int simulationEventSeq; String snapshotAt;
  }

}
