package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class MvpStoreService {
  private static final long DEFAULT_SIM_SEED = 20260322L;
  private static final int DEFAULT_SIM_DAILY_SALES = 20;
  private static final String DEFAULT_SIM_SCENARIO = "STABLE";
  private static final ZoneId SIMULATION_ZONE = ZoneId.of("Asia/Shanghai");
  private static final String STRATEGY_KEY_ORDER_FIRST = "KEY_ORDER_FIRST";
  private static final String STRATEGY_MAX_CAPACITY_FIRST = "MAX_CAPACITY_FIRST";
  private static final String STRATEGY_MIN_DELAY_FIRST = "MIN_DELAY_FIRST";
  private static final String LIVE_REPORT_VERSION_NO = "LIVE_STATE";
  private static final String LIVE_REPORT_STATUS = "LIVE";
  private static final Map<String, String> SCHEDULE_STRATEGY_NAME_CN = Map.ofEntries(
    Map.entry(STRATEGY_KEY_ORDER_FIRST, "关键订单优先"),
    Map.entry(STRATEGY_MAX_CAPACITY_FIRST, "最大产能优先"),
    Map.entry(STRATEGY_MIN_DELAY_FIRST, "交期最小延期优先")
  );
  private static final Map<String, Integer> BASE_WORKERS_BY_PROCESS = Map.of(
    "PROC_TUBE", 8,
    "PROC_ASSEMBLY", 10,
    "PROC_BALLOON", 8,
    "PROC_STENT", 9,
    "PROC_STERILE", 6
  );
  private static final Map<String, Integer> BASE_MACHINES_BY_PROCESS = Map.of(
    "PROC_TUBE", 3,
    "PROC_ASSEMBLY", 3,
    "PROC_BALLOON", 2,
    "PROC_STENT", 2,
    "PROC_STERILE", 2
  );
  private static final Map<String, String> PRODUCT_NAME_CN = Map.ofEntries(
    Map.entry("PROD_CATH", "导管"),
    Map.entry("PROD_BALLOON", "球囊"),
    Map.entry("PROD_STENT", "支架"),
    Map.entry("PROD_ANGIO_CATH", "造影导管"),
    Map.entry("PROD_UNKNOWN", "未知产品")
  );
  private static final Map<String, String> PROCESS_NAME_CN = Map.ofEntries(
    Map.entry("PROC_TUBE", "制管"),
    Map.entry("PROC_ASSEMBLY", "装配"),
    Map.entry("PROC_BALLOON", "球囊成型"),
    Map.entry("PROC_STENT", "支架成型"),
    Map.entry("PROC_STERILE", "灭菌"),
    Map.entry("Z470", "造影导管切导管"),
    Map.entry("Z3910", "造影导管扩孔"),
    Map.entry("Z3920", "造影导管磨削"),
    Map.entry("Z390", "造影导管搭接"),
    Map.entry("Z340", "造影导管全检导管"),
    Map.entry("Z410", "造影导管融飞边"),
    Map.entry("Z460", "造影导管穿白边"),
    Map.entry("Z420", "造影导管焊接"),
    Map.entry("Z320", "造影导管修飞边"),
    Map.entry("Z310", "造影导管烫飞边"),
    Map.entry("Z350", "造影导管检验白色端"),
    Map.entry("Z480", "造影导管毛化"),
    Map.entry("Z370", "造影导管手柄点胶"),
    Map.entry("Z380", "造影导管手柄上护套"),
    Map.entry("Z290", "造影导管清洗、吹水"),
    Map.entry("Z450", "造影导管穿衬芯"),
    Map.entry("Z360", "造影导管塑型"),
    Map.entry("Z270", "造影导管检漏"),
    Map.entry("Z4830", "造影导管包装"),
    Map.entry("Z500", "造影导管全检（包装）"),
    Map.entry("W130", "W贴产品标签（大标）"),
    Map.entry("W140", "W贴产品标签（小标）"),
    Map.entry("W160", "W全检导管"),
    Map.entry("W150", "W导管中盒(说明书)"),
    Map.entry("W030", "W包装打包")
  );
  private static final Map<String, String> STATUS_NAME_CN = Map.ofEntries(
    Map.entry("OPEN", "待处理"),
    Map.entry("IN_PROGRESS", "进行中"),
    Map.entry("DONE", "已完成"),
    Map.entry("DELAY", "延期"),
    Map.entry("DRAFT", "草稿"),
    Map.entry("PUBLISHED", "已发布"),
    Map.entry("SUPERSEDED", "已替代"),
    Map.entry("ROLLED_BACK", "已回滚"),
    Map.entry("RUNNING", "运行中"),
    Map.entry("ACKED", "已确认"),
    Map.entry("CLOSED", "已关闭"),
    Map.entry("SUCCESS", "成功"),
    Map.entry("FAILED", "失败"),
    Map.entry("PARTIAL", "部分成功"),
    Map.entry("AVAILABLE", "可用"),
    Map.entry("RELEASED", "已下发")
  );
  private static final Map<String, String> ACTION_NAME_CN = Map.ofEntries(
    Map.entry("CREATE_ORDER", "创建订单"),
    Map.entry("UPDATE_ORDER", "更新订单"),
    Map.entry("PATCH_ORDER", "修改订单"),
    Map.entry("GENERATE_SCHEDULE", "生成排产"),
    Map.entry("AUTO_REPLAN_SCHEDULE", "自动重排"),
    Map.entry("PUBLISH_VERSION", "发布版本"),
    Map.entry("ROLLBACK_VERSION", "回滚版本"),
    Map.entry("CREATE_REPORTING", "创建报工"),
    Map.entry("WRITE_SCHEDULE_RESULTS", "回写排产结果"),
    Map.entry("WRITE_SCHEDULE_STATUS", "回写排产状态"),
    Map.entry("INGEST_WIP_EVENT", "写入在制品事件"),
    Map.entry("TRIGGER_REPLAN", "触发重排"),
    Map.entry("RESET_SIMULATION", "重置仿真"),
    Map.entry("RUN_SIMULATION", "运行仿真"),
    Map.entry("RETRY_OUTBOX", "重试出站消息"),
    Map.entry("CREATE_DISPATCH_COMMAND", "创建调度指令"),
    Map.entry("APPROVE_DISPATCH_COMMAND", "批准调度指令"),
    Map.entry("REJECT_DISPATCH_COMMAND", "驳回调度指令"),
    Map.entry("ACK_ALERT", "确认预警"),
    Map.entry("CLOSE_ALERT", "关闭预警"),
    Map.entry("APPROVED", "已批准"),
    Map.entry("REJECTED", "已驳回"),
    Map.entry("INSERT", "插单"),
    Map.entry("LOCK", "锁单"),
    Map.entry("UNLOCK", "解锁"),
    Map.entry("PRIORITY", "提优先级"),
    Map.entry("FREEZE", "冻结"),
    Map.entry("UNFREEZE", "解冻")
  );
  private static final Map<String, String> EVENT_TYPE_NAME_CN = Map.ofEntries(
    Map.entry("SALES_RECEIVED", "接收销售订单"),
    Map.entry("ORDER_CONVERTED", "转换生产订单"),
    Map.entry("CAPACITY_CHANGED", "产能变化"),
    Map.entry("SCHEDULE_GENERATED", "生成排产版本"),
    Map.entry("SCHEDULE_PUBLISHED", "发布排产版本"),
    Map.entry("EXECUTION_PROGRESS", "执行进度同步"),
    Map.entry("SIM_RESET", "重置仿真"),
    Map.entry("SIM_RUN_DONE", "仿真运行完成")
  );
  private static final Map<String, String> TOPIC_NAME_CN = Map.ofEntries(
    Map.entry("MES_REPORTING", "MES报工"),
    Map.entry("MES_WIP_EVENT", "MES在制品事件"),
    Map.entry("ERP_SCHEDULE_RESULTS", "ERP排产结果"),
    Map.entry("ERP_SCHEDULE_STATUS", "ERP排产状态")
  );
  private static final Map<String, String> SYSTEM_NAME_CN = Map.ofEntries(
    Map.entry("MES", "MES系统"),
    Map.entry("ERP", "ERP系统"),
    Map.entry("SCHEDULER", "排产系统")
  );
  private static final Map<String, String> SCENARIO_NAME_CN = Map.ofEntries(
    Map.entry("STABLE", "稳定"),
    Map.entry("TIGHT", "紧张"),
    Map.entry("BREAKDOWN", "故障")
  );
  private static final Map<String, String> SHIFT_NAME_CN = Map.ofEntries(
    Map.entry("DAY", "白班"),
    Map.entry("NIGHT", "夜班"),
    Map.entry("D", "白班"),
    Map.entry("N", "夜班")
  );
  private static final String WEEKEND_REST_MODE_NONE = "NONE";
  private static final String WEEKEND_REST_MODE_SINGLE = "SINGLE";
  private static final String WEEKEND_REST_MODE_DOUBLE = "DOUBLE";
  private static final String DATE_SHIFT_MODE_REST = "REST";
  private static final String DATE_SHIFT_MODE_DAY = "DAY";
  private static final String DATE_SHIFT_MODE_NIGHT = "NIGHT";
  private static final String DATE_SHIFT_MODE_BOTH = "BOTH";
  private static final Set<String> CN_STATUTORY_HOLIDAY_DATE_SET = Set.of(
    "2024-01-01",
    "2024-02-10",
    "2024-02-11",
    "2024-02-12",
    "2024-02-13",
    "2024-02-14",
    "2024-02-15",
    "2024-02-16",
    "2024-02-17",
    "2024-04-04",
    "2024-04-05",
    "2024-04-06",
    "2024-05-01",
    "2024-05-02",
    "2024-05-03",
    "2024-05-04",
    "2024-05-05",
    "2024-06-08",
    "2024-06-09",
    "2024-06-10",
    "2024-09-15",
    "2024-09-16",
    "2024-09-17",
    "2024-10-01",
    "2024-10-02",
    "2024-10-03",
    "2024-10-04",
    "2024-10-05",
    "2024-10-06",
    "2024-10-07",
    "2025-01-01",
    "2025-01-28",
    "2025-01-29",
    "2025-01-30",
    "2025-01-31",
    "2025-02-01",
    "2025-02-02",
    "2025-02-03",
    "2025-02-04",
    "2025-04-04",
    "2025-04-05",
    "2025-04-06",
    "2025-05-01",
    "2025-05-02",
    "2025-05-03",
    "2025-05-04",
    "2025-05-05",
    "2025-05-31",
    "2025-06-01",
    "2025-06-02",
    "2025-10-01",
    "2025-10-02",
    "2025-10-03",
    "2025-10-04",
    "2025-10-05",
    "2025-10-06",
    "2025-10-07",
    "2025-10-08",
    "2026-01-01",
    "2026-01-02",
    "2026-01-03",
    "2026-02-15",
    "2026-02-16",
    "2026-02-17",
    "2026-02-18",
    "2026-02-19",
    "2026-02-20",
    "2026-02-21",
    "2026-02-22",
    "2026-02-23",
    "2026-04-04",
    "2026-04-05",
    "2026-04-06",
    "2026-05-01",
    "2026-05-02",
    "2026-05-03",
    "2026-05-04",
    "2026-05-05",
    "2026-06-19",
    "2026-06-20",
    "2026-06-21",
    "2026-09-25",
    "2026-09-26",
    "2026-09-27",
    "2026-10-01",
    "2026-10-02",
    "2026-10-03",
    "2026-10-04",
    "2026-10-05",
    "2026-10-06",
    "2026-10-07"
  );
  private static final Map<String, String> DEPENDENCY_NAME_CN = Map.ofEntries(
    Map.entry("FS", "完成-开始"),
    Map.entry("SS", "开始-开始")
  );

  private static final String WEEKLY_PLAN_SHEET_NAME = "周计划（3.16-3.22）";
  private static final String WEEKLY_PLAN_TITLE_CN = "周计划（3.16-3.22）";
  private static final String[] WEEKLY_PLAN_EXTRA_SHEETS = { "Sheet2", "Sheet3" };
  private static final String MONTHLY_PLAN_SHEET_NAME = "生产计划";
  private static final String MONTHLY_PLAN_TITLE_CN = "2月订单明细表";
  private static final String[] WEEKLY_PLAN_HEADERS_CN = {
    "生产订单号",
    "客户备注",
    "产品名称",
    "规格型号",
    "生产批号",
    "订单数量",
    "包装形式",
    "销售单号",
    "车间外围包装日期",
    "备注(工序排产)"
  };
  private static final String[] WEEKLY_PLAN_KEYS = {
    "production_order_no",
    "customer_remark",
    "product_name",
    "spec_model",
    "production_batch_no",
    "order_qty",
    "packaging_form",
    "sales_order_no",
    "workshop_outer_packaging_date",
    "process_schedule_remark"
  };
  private static final String[] MONTHLY_PLAN_HEADERS_CN = {
    "下单日期",
    "生产订单号",
    "客户备注",
    "产品名称",
    "规格型号",
    "生产批号",
    "计划完工日期2",
    "生产日期\n（外贸）",
    "订单数量",
    "包装形式",
    "销售单号",
    "采购交期",
    "注塑交期",
    "市场备注信息",
    "市场需求",
    "计划完工日期1",
    "半成品代码",
    "半成品库存",
    "半成品需求",
    "半成品在制",
    "需下单",
    "待入库",
    "周度/月度计划(工序排产)",
    "车间外围包装日期",
    "备注",
    "完工数量(车间)",
    "完工时间（车间）",
    "完工数量（外围）",
    "完工时间（外围）",
    "匹配"
  };
  private static final String[] MONTHLY_PLAN_KEYS = {
    "order_date",
    "production_order_no",
    "customer_remark",
    "product_name",
    "spec_model",
    "production_batch_no",
    "planned_finish_date_2",
    "production_date_foreign_trade",
    "order_qty",
    "packaging_form",
    "sales_order_no",
    "purchase_due_date",
    "injection_due_date",
    "market_remark_info",
    "market_demand",
    "planned_finish_date_1",
    "semi_finished_code",
    "semi_finished_inventory",
    "semi_finished_demand",
    "semi_finished_wip",
    "need_order_qty",
    "pending_inbound_qty",
    "weekly_monthly_process_plan",
    "workshop_outer_packaging_date",
    "note",
    "workshop_completed_qty",
    "workshop_completed_time",
    "outer_completed_qty",
    "outer_completed_time",
    "match_status"
  };

  private static final Map<String, String> ALERT_TYPE_NAME_CN = Map.ofEntries(
    Map.entry("PROGRESS_GAP", "\u8fdb\u5ea6\u504f\u5dee"),
    Map.entry("EQUIPMENT_DOWN", "\u8bbe\u5907\u6545\u969c")
  );
  private static final Map<String, String> SEVERITY_NAME_CN = Map.ofEntries(
    Map.entry("CRITICAL", "\u4e25\u91cd"),
    Map.entry("WARN", "\u8b66\u544a"),
    Map.entry("INFO", "\u63d0\u793a")
  );

  private final Object lock = new Object();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ErpDataManager erpDataManager;
  private final AtomicInteger reportingSeq = new AtomicInteger(0);
  private final AtomicInteger replanSeq = new AtomicInteger(0);
  private final AtomicInteger alertSeq = new AtomicInteger(0);
  private final AtomicInteger dispatchSeq = new AtomicInteger(0);
  private final AtomicInteger dispatchApprovalSeq = new AtomicInteger(0);
  private final AtomicInteger salesSeq = new AtomicInteger(0);
  private final AtomicInteger productionSeq = new AtomicInteger(0);
  private final AtomicInteger simulationEventSeq = new AtomicInteger(0);
  private MvpDomain.State state = SeedDataFactory.build();
  private final SimulationState simulationState = new SimulationState();
  private final Map<String, Double> finalCompletedByOrderProductCache = new HashMap<>();
  private final Map<String, CachedOrderPoolMaterials> orderPoolMaterialsCache = new HashMap<>();
  private final Map<String, CachedOrderPoolMaterials> materialChildrenByParentCache = new HashMap<>();
  private int finalCompletedSyncCursor = 0;
  private ManualSimulationSnapshot manualSimulationSnapshot;

  public MvpStoreService(ErpDataManager erpDataManager) {
    this.erpDataManager = erpDataManager;
    resetSimulationState(DEFAULT_SIM_SEED, DEFAULT_SIM_SCENARIO, DEFAULT_SIM_DAILY_SALES);
  }

  public void reset() {
    synchronized (lock) {
      state = SeedDataFactory.build();
      reportingSeq.set(0);
      replanSeq.set(0);
      alertSeq.set(0);
      dispatchSeq.set(0);
      dispatchApprovalSeq.set(0);
      salesSeq.set(0);
      productionSeq.set(0);
      simulationEventSeq.set(0);
      manualSimulationSnapshot = null;
      finalCompletedByOrderProductCache.clear();
      orderPoolMaterialsCache.clear();
      materialChildrenByParentCache.clear();
      finalCompletedSyncCursor = 0;
      resetSimulationState(DEFAULT_SIM_SEED, DEFAULT_SIM_SCENARIO, DEFAULT_SIM_DAILY_SALES);
    }
  }

  public List<Map<String, Object>> listOrders() {
    synchronized (lock) {
      syncCompletedQtyFromFinalProcessReports();
      refreshOrderStatuses(simulationState.currentDate == null ? state.startDate : simulationState.currentDate);
      return state.orders.stream().map(this::toOrderMap).toList();
    }
  }

  public List<Map<String, Object>> listOrderPool(Map<String, String> filters) {
    synchronized (lock) {
      syncCompletedQtyFromFinalProcessReports();
      refreshOrderStatuses(simulationState.currentDate == null ? state.startDate : simulationState.currentDate);
      Map<String, Map<String, Object>> rowByOrderNo = new LinkedHashMap<>();
      for (MvpDomain.Order order : state.orders) {
        Map<String, Object> row = toOrderPoolItem(order);
        String orderNo = string(row, "order_no", null);
        if (orderNo != null && !orderNo.isBlank()) {
          rowByOrderNo.put(orderNo, row);
        }
      }

      List<Map<String, Object>> erpRows = erpDataManager.getProductionOrders();
      for (Map<String, Object> erpRow : erpRows) {
        Map<String, Object> row = toOrderPoolItemFromErp(erpRow);
        String orderNo = string(row, "order_no", null);
        if (orderNo == null || orderNo.isBlank()) {
          continue;
        }
        rowByOrderNo.putIfAbsent(orderNo, row);
      }

      return rowByOrderNo.values().stream()
        .filter(row -> filterOrderPoolRow(row, filters))
        .toList();
    }
  }

  public List<Map<String, Object>> listOrderPoolMaterials(String orderNo) {
    return listOrderPoolMaterials(orderNo, false);
  }

  public List<Map<String, Object>> listOrderPoolMaterials(String orderNo, boolean refreshFromErp) {
    synchronized (lock) {
      String normalizedOrderNo = orderNo == null ? "" : orderNo.trim();
      if (normalizedOrderNo.isBlank()) {
        throw badRequest("orderNo is required.");
      }

      if (!refreshFromErp) {
        CachedOrderPoolMaterials cached = orderPoolMaterialsCache.get(normalizedOrderNo);
        if (cached != null) {
          return copyMaterialRows(cached.rows);
        }
      }

      Map<String, Object> orderRow = erpDataManager.getProductionOrders().stream()
        .filter(row -> normalizedOrderNo.equals(string(row, "production_order_no", null)))
        .findFirst()
        .orElse(null);
      if (orderRow == null) {
        orderPoolMaterialsCache.put(normalizedOrderNo, new CachedOrderPoolMaterials(List.of(), OffsetDateTime.now(ZoneOffset.UTC).toString()));
        return List.of();
      }
      String materialListNo = string(orderRow, "material_list_no", null);
      String productCode = string(orderRow, "product_code", null);
      List<Map<String, Object>> rawRows = erpDataManager.getProductionMaterialIssuesByOrder(
        normalizedOrderNo,
        materialListNo,
        refreshFromErp
      );
      boolean hasExpandedBomRows = rawRows.stream()
        .anyMatch(row -> "ERP_API_BOM_VIEW_ENTRY".equalsIgnoreCase(string(row, "erp_source_table", "")));
      if (!hasExpandedBomRows) {
        String ppBomNo = rawRows.stream()
          .map(row -> string(row, "pick_material_bill_no", string(row, "source_bill_no", null)))
          .filter(value -> value != null && value.startsWith("PPBOM"))
          .findFirst()
          .orElse(null);
        if (ppBomNo != null) {
          List<Map<String, Object>> ppBomRows = erpDataManager.getProductionMaterialIssuesByOrder(null, ppBomNo, refreshFromErp);
          if (!ppBomRows.isEmpty()) {
            rawRows = ppBomRows;
          }
        }
      }
      String normalizedProductCode = normalizeMaterialCode(productCode);
      String normalizedMaterialListNo = normalizeMaterialCode(materialListNo);
      String normalizedMaterialListBaseCode = materialListBaseCode(normalizedMaterialListNo);
      List<Map<String, Object>> rows = new ArrayList<>();
      Set<String> seenCodes = new HashSet<>();
      for (Map<String, Object> rawRow : rawRows) {
        String code = normalizeMaterialCode(string(rawRow, "child_material_code", null));
        if (code.isBlank() || "UNKNOWN".equals(code) || !seenCodes.add(code)) {
          continue;
        }
        if (
          isSameMaterialCode(code, normalizedProductCode)
            || isSameMaterialCode(code, normalizedMaterialListNo)
            || isSameMaterialCode(code, normalizedMaterialListBaseCode)
        ) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", normalizedOrderNo);
        row.put("material_list_no", materialListNo);
        row.put("child_material_code", code);
        row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
        row.put("required_qty", number(rawRow, "required_qty", 0d));
        row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
        row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
        row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
        row.put("issue_date", string(rawRow, "issue_date", ""));
        Map<String, Object> supplyInfo = erpDataManager.getMaterialSupplyInfo(code);
        row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
        row.put("child_material_supply_type_name_cn", string(supplyInfo, "supply_type_name_cn", "\u672A\u77E5"));
        rows.add(localizeRow(row));
      }
      rows.sort(Comparator.comparing(row -> string(row, "child_material_code", "")));
      List<Map<String, Object>> cachedRows = freezeMaterialRows(rows);
      orderPoolMaterialsCache.put(
        normalizedOrderNo,
        new CachedOrderPoolMaterials(cachedRows, OffsetDateTime.now(ZoneOffset.UTC).toString())
      );
      return copyMaterialRows(cachedRows);
    }
  }

  public List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode) {
    return listMaterialChildrenByParentCode(parentMaterialCode, false);
  }

  public List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode, boolean refreshFromErp) {
    synchronized (lock) {
      String normalizedParentCode = normalizeMaterialCode(parentMaterialCode);
      if (normalizedParentCode.isBlank()) {
        throw badRequest("parentMaterialCode is required.");
      }

      if (!refreshFromErp) {
        CachedOrderPoolMaterials cached = materialChildrenByParentCache.get(normalizedParentCode);
        if (cached != null) {
          return copyMaterialRows(cached.rows);
        }
      }

      List<Map<String, Object>> rawRows = erpDataManager.getProductionMaterialIssuesByOrder(
        null,
        normalizedParentCode,
        refreshFromErp
      );
      String normalizedParentBaseCode = materialListBaseCode(normalizedParentCode);
      List<Map<String, Object>> rows = new ArrayList<>();
      Set<String> seenCodes = new HashSet<>();
      for (Map<String, Object> rawRow : rawRows) {
        String code = normalizeMaterialCode(string(rawRow, "child_material_code", null));
        if (code.isBlank() || "UNKNOWN".equals(code) || !seenCodes.add(code)) {
          continue;
        }
        if (
          isSameMaterialCode(code, normalizedParentCode)
            || isSameMaterialCode(code, normalizedParentBaseCode)
        ) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parent_material_code", normalizedParentCode);
        row.put("child_material_code", code);
        row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
        row.put("required_qty", number(rawRow, "required_qty", 0d));
        row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
        row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
        row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
        row.put("issue_date", string(rawRow, "issue_date", ""));
        Map<String, Object> supplyInfo = erpDataManager.getMaterialSupplyInfo(code);
        row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
        row.put("child_material_supply_type_name_cn", string(supplyInfo, "supply_type_name_cn", "\u672A\u77E5"));
        rows.add(localizeRow(row));
      }
      rows.sort(Comparator.comparing(row -> string(row, "child_material_code", "")));
      List<Map<String, Object>> cachedRows = freezeMaterialRows(rows);
      materialChildrenByParentCache.put(
        normalizedParentCode,
        new CachedOrderPoolMaterials(cachedRows, OffsetDateTime.now(ZoneOffset.UTC).toString())
      );
      return copyMaterialRows(cachedRows);
    }
  }

  public Map<String, Object> upsertOrder(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      String orderNo = string(payload, "orderNo", string(payload, "order_no", null));
      if (orderNo == null || orderNo.isBlank()) {
        throw badRequest("orderNo is required.");
      }
      MvpDomain.Order found = state.orders.stream().filter(o -> o.orderNo.equals(orderNo)).findFirst().orElse(null);
      if (found == null) {
        MvpDomain.Order created = fromOrderPayload(payload);
        state.orders.add(created);
        appendAudit("ORDER", created.orderNo, "CREATE_ORDER", operator, requestId, null);
        return toOrderMap(created);
      }
      applyOrderPatch(found, payload);
      appendAudit("ORDER", found.orderNo, "UPDATE_ORDER", operator, requestId, null);
      return toOrderMap(found);
    }
  }

  public Map<String, Object> patchOrder(String orderNo, Map<String, Object> patch, String requestId, String operator) {
    synchronized (lock) {
      MvpDomain.Order found = findOrder(orderNo);
      applyOrderPatch(found, patch);
      appendAudit("ORDER", found.orderNo, "PATCH_ORDER", operator, requestId, null);
      return toOrderMap(found);
    }
  }

  public Map<String, Object> generateSchedule(Map<String, Object> options, String requestId, String operator) {
    synchronized (lock) {
      long totalStart = System.nanoTime();
      Map<String, Long> phaseDurationMs = new LinkedHashMap<>();

      long phaseStart = System.nanoTime();
      syncCompletedQtyFromFinalProcessReports();
      phaseDurationMs.put("sync_completed_qty", elapsedMillis(phaseStart));

      phaseStart = System.nanoTime();
      boolean autoReplan = bool(options, "autoReplan", false);
      String strategyCode = normalizeScheduleStrategy(
        string(
          options,
          "strategy_code",
          string(options, "schedule_strategy", string(options, "strategy", null))
        )
      );
      String baseVersionNo = string(options, "base_version_no", null);
      MvpDomain.ScheduleVersion baseVersion = null;
      if (baseVersionNo != null && !baseVersionNo.isBlank()) {
        baseVersion = state.schedules.stream().filter(item -> item.versionNo.equals(baseVersionNo)).findFirst().orElse(null);
      }

      List<String> lockedOrders = new ArrayList<>();
      List<MvpDomain.Order> orders = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        if (!hasRemainingQty(order)) {
          continue;
        }
        if (order.lockFlag) {
          lockedOrders.add(order.orderNo);
        }
        orders.add(order);
      }
      phaseDurationMs.put("prepare_input", elapsedMillis(phaseStart));

      phaseStart = System.nanoTime();
      String versionNo = "V%03d".formatted(state.schedules.size() + 1);
      MvpDomain.ScheduleVersion schedule = SchedulerEngine.generate(
        state,
        orders,
        requestId,
        versionNo,
        baseVersion,
        new HashSet<>(lockedOrders),
        strategyCode
      );
      phaseDurationMs.put("engine_generate", elapsedMillis(phaseStart));

      phaseStart = System.nanoTime();
      schedule.status = "DRAFT";
      schedule.basedOnVersion = baseVersionNo;
      schedule.ruleVersionNo = "RULE-P0-BASE";
      schedule.publishTime = null;
      schedule.createdBy = operator;
      schedule.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
      schedule.metadata.put("autoReplan", autoReplan);
      schedule.metadata.put("excludedLockedOrders", List.of());
      schedule.metadata.put("preservedLockedOrders", lockedOrders);
      schedule.metadata.put("baseVersionResolved", baseVersion != null);
      schedule.metadata.put("schedule_strategy_code", strategyCode);
      schedule.metadata.put("schedule_strategy_name_cn", scheduleStrategyNameCn(strategyCode));
      schedule.metadata.put("schedule_generate_phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));

      long candidateOrderCount = orders.stream().map(order -> order.orderNo).filter(Objects::nonNull).distinct().count();
      long candidateTaskCount = schedule.tasks.size();
      schedule.metrics.putAll(buildScheduleObservabilityMetrics(schedule, candidateOrderCount, candidateTaskCount));

      state.schedules.add(schedule);
      phaseDurationMs.put("persist_and_metrics", elapsedMillis(phaseStart));

      long totalDurationMs = elapsedMillis(totalStart);
      schedule.metrics.put("schedule_generate_duration_ms", totalDurationMs);
      schedule.metrics.put("schedule_generate_phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
      schedule.metadata.put("schedule_generate_duration_ms", totalDurationMs);

      Map<String, Object> perfContext = new LinkedHashMap<>();
      perfContext.put("request_id", requestId);
      perfContext.put("version_no", schedule.versionNo);
      perfContext.put("phase", "schedule_generate");
      perfContext.put("duration_ms", totalDurationMs);
      perfContext.put("phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
      perfContext.put("strategy_code", strategyCode);
      perfContext.put("strategy_name_cn", scheduleStrategyNameCn(strategyCode));
      appendAudit(
        "SCHEDULE_VERSION",
        schedule.versionNo,
        autoReplan ? "AUTO_REPLAN_SCHEDULE" : "GENERATE_SCHEDULE",
        operator,
        requestId,
        string(options, "reason", null),
        perfContext
      );

      boolean compactResponse = bool(
        options,
        "compact_response",
        bool(options, "compactResponse", false)
      );
      if (compactResponse) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_id", requestId);
        out.put("version_no", schedule.versionNo);
        out.put("versionNo", schedule.versionNo);
        out.put("status", schedule.status);
        out.put("generated_at", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
        out.put("generatedAt", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
        out.put(
          "schedule_completion_rate",
          number(schedule.metrics, "schedule_completion_rate", number(schedule.metrics, "scheduleCompletionRate", 0d))
        );
        out.put("unscheduled_task_count", schedule.unscheduled == null ? 0 : schedule.unscheduled.size());
        return out;
      }
      return toScheduleMap(schedule);
    }
  }

  public List<Map<String, Object>> listSchedules() {
    synchronized (lock) {
      return state.schedules.stream().map(this::toScheduleMap).toList();
    }
  }

  public Map<String, Object> getLatestSchedule() {
    synchronized (lock) {
      if (state.schedules.isEmpty()) {
        throw badRequest("No schedule generated.");
      }
      return toScheduleMap(state.schedules.get(state.schedules.size() - 1));
    }
  }

  public Map<String, Object> validateSchedule(String versionNo) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);
      Map<String, Object> validation = SchedulerEngine.validate(state, schedule);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("versionNo", schedule.versionNo);
      response.putAll(validation);
      return response;
    }
  }

  public Map<String, Object> publishSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "PUBLISH#" + versionNo, () -> {
        MvpDomain.ScheduleVersion target = getScheduleEntity(versionNo);
        if (state.publishedVersionNo != null && !state.publishedVersionNo.equals(versionNo)) {
          MvpDomain.ScheduleVersion previous = getScheduleEntity(state.publishedVersionNo);
          previous.status = "SUPERSEDED";
        }
        state.publishedVersionNo = versionNo;
        target.status = "PUBLISHED";
        target.publishTime = OffsetDateTime.now(ZoneOffset.UTC);
        appendAudit("SCHEDULE_VERSION", versionNo, "PUBLISH_VERSION", operator, requestId, string(payload, "reason", null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_id", requestId);
        out.put("success", true);
        out.put("message", "Version %s published.".formatted(versionNo));
        out.put("version_no", versionNo);
        out.put("versionNo", versionNo);
        out.put("publishedAt", target.publishTime.toString());
        out.put("status", target.status);
        return out;
      });
    }
  }

  public Map<String, Object> rollbackSchedule(String versionNo, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "ROLLBACK#" + versionNo, () -> {
        MvpDomain.ScheduleVersion target = getScheduleEntity(versionNo);
        if (state.publishedVersionNo != null && !state.publishedVersionNo.equals(versionNo)) {
          MvpDomain.ScheduleVersion current = getScheduleEntity(state.publishedVersionNo);
          current.status = "ROLLED_BACK";
          target.rollbackFrom = current.versionNo;
        }
        target.status = "PUBLISHED";
        target.publishTime = OffsetDateTime.now(ZoneOffset.UTC);
        state.publishedVersionNo = versionNo;
        appendAudit("SCHEDULE_VERSION", versionNo, "ROLLBACK_VERSION", operator, requestId, string(payload, "reason", null));
        return Map.of(
          "request_id", requestId,
          "success", true,
          "message", "Rollback to %s completed.".formatted(versionNo),
          "version_no", versionNo
        );
      });
    }
  }

  public Map<String, Object> recordReporting(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      String orderNo = string(payload, "orderNo", string(payload, "order_no", null));
      MvpDomain.Order order = findOrder(orderNo);
      String productCode = string(payload, "productCode", string(payload, "product_code", null));
      if (productCode == null && !order.items.isEmpty()) {
        productCode = order.items.get(0).productCode;
      }
      MvpDomain.OrderItem item = null;
      for (MvpDomain.OrderItem current : order.items) {
        if (Objects.equals(current.productCode, productCode)) {
          item = current;
          break;
        }
      }
      if (item == null) {
        throw badRequest("Product %s not found in order %s.".formatted(productCode, orderNo));
      }
      double reportQtyRaw = number(payload, "reportQty", number(payload, "report_qty", -1d));
      if (reportQtyRaw <= 0d) {
        throw badRequest("reportQty must be > 0.");
      }
      double reportQty = Math.round(reportQtyRaw);
      if (reportQty <= 0d) {
        throw badRequest("reportQty must be >= 1.");
      }

      MvpDomain.Reporting reporting = new MvpDomain.Reporting();
      reporting.reportingId = "RPT-%05d".formatted(reportingSeq.incrementAndGet());
      reporting.requestId = requestId;
      reporting.orderNo = orderNo;
      reporting.productCode = productCode;
      reporting.processCode = string(payload, "processCode", string(payload, "process_code", "UNKNOWN"));
      reporting.reportQty = reportQty;
      reporting.reportTime = OffsetDateTime.now(ZoneOffset.UTC);

      if (isFinalProcessForProduct(item.productCode, reporting.processCode)) {
        item.completedQty = Math.min(item.qty, item.completedQty + reportQty);
      }
      updateOrderProgressFacts(order);

      state.reportings.add(reporting);
      appendAudit("REPORTING", reporting.reportingId, "CREATE_REPORTING", operator, requestId, null);
      appendInbox("MES_REPORTING", reporting.reportingId, requestId, "SUCCESS", null);
      erpDataManager.refreshTriggered("MES_REPORTING", requestId, "reporting created");

      Map<String, Object> triggered = maybeTriggerProgressGapReplan(reporting, requestId, operator);
      if (triggered != null) {
        reporting.triggeredReplanJobNo = string(triggered, "job_no", null);
        reporting.triggeredAlertId = string(triggered, "alert_id", null);
      }

      return toReportingMap(reporting);
    }
  }

  public Map<String, Object> deleteReporting(String reportingId, String requestId, String operator) {
    synchronized (lock) {
      String normalizedReportingId = normalizeCode(reportingId);
      if (normalizedReportingId.isBlank()) {
        throw badRequest("reportingId is required.");
      }

      int targetIndex = -1;
      for (int i = 0; i < state.reportings.size(); i += 1) {
        MvpDomain.Reporting current = state.reportings.get(i);
        if (normalizeCode(current.reportingId).equals(normalizedReportingId)) {
          targetIndex = i;
          break;
        }
      }
      if (targetIndex < 0) {
        throw notFound("Reporting %s not found.".formatted(reportingId));
      }

      MvpDomain.Reporting removed = state.reportings.remove(targetIndex);
      if (isFinalProcessForProduct(removed.productCode, removed.processCode)) {
        try {
          MvpDomain.Order order = findOrder(removed.orderNo);
          for (MvpDomain.OrderItem item : order.items) {
            if (!Objects.equals(item.productCode, removed.productCode)) {
              continue;
            }
            item.completedQty = Math.max(0d, item.completedQty - removed.reportQty);
          }
          updateOrderProgressFacts(order);
        } catch (MvpServiceException ignore) {
          // Order may have been removed by other flows; keep deletion successful.
        }
      }

      if (state.reportings.size() < finalCompletedSyncCursor) {
        finalCompletedByOrderProductCache.clear();
        finalCompletedSyncCursor = 0;
      }

      appendAudit("REPORTING", removed.reportingId, "DELETE_REPORTING", operator, requestId, null);
      erpDataManager.refreshTriggered("MES_REPORTING", requestId, "reporting deleted");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("request_id", requestId);
      result.put("report_id", removed.reportingId);
      result.put("order_no", removed.orderNo);
      result.put("product_code", removed.productCode);
      result.put("process_code", removed.processCode);
      result.put("deleted", true);
      return result;
    }
  }

  public List<Map<String, Object>> listReportings() {
    synchronized (lock) {
      return state.reportings.stream().map(this::toReportingMap).toList();
    }
  }

  public List<Map<String, Object>> listReportingsForMes(Map<String, String> filters) {
    synchronized (lock) {
      OffsetDateTime startTime = parseOffsetDateTimeFilter(filters, "start_time", "report_time_start");
      OffsetDateTime endTime = parseOffsetDateTimeFilter(filters, "end_time", "report_time_end");
      return state.reportings.stream()
        .filter(reporting -> startTime == null || !reporting.reportTime.isBefore(startTime))
        .filter(reporting -> endTime == null || !reporting.reportTime.isAfter(endTime))
        .map(reporting -> {
        String shiftCode = reporting.reportTime.getHour() >= 20 || reporting.reportTime.getHour() < 8 ? "NIGHT" : "DAY";
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("report_id", reporting.reportingId);
        row.put("order_no", reporting.orderNo);
        row.put("order_type", "production");
        row.put("product_code", reporting.productCode);
        row.put("product_name_cn", productNameCn(reporting.productCode));
        row.put("process_code", reporting.processCode);
        row.put("process_name_cn", processNameCn(reporting.processCode));
        row.put("report_qty", reporting.reportQty);
        row.put("report_time", reporting.reportTime.toString());
        row.put("shift_code", shiftCode);
        row.put("shift_name_cn", shiftNameCn(shiftCode));
        row.put("team_code", "TEAM-A");
        row.put("operator_code", "OP-A");
        row.put("last_update_time", reporting.reportTime.toString());
        return row;
      })
      .toList();
    }
  }

  public Map<String, Object> writeScheduleResults(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "WRITE_SCHEDULE_RESULTS", () -> {
        String scheduleVersion = string(payload, "schedule_version", null);
        List<Map<String, Object>> items = maps(payload.get("items"));
        if (scheduleVersion == null || items.isEmpty()) {
          throw badRequest("schedule_version and items are required.");
        }
        int successCount = 0;
        int failedCount = 0;
        for (Map<String, Object> row : items) {
          String orderNo = string(row, "order_no", string(row, "orderNo", null));
          MvpDomain.Order order = state.orders.stream().filter(o -> o.orderNo.equals(orderNo)).findFirst().orElse(null);
          if (order == null) {
            failedCount += 1;
            continue;
          }
          if (row.containsKey("lock_flag")) {
            order.lockFlag = number(row, "lock_flag", 0d) == 1d;
          }
          successCount += 1;
        }
        state.scheduleResultWrites.add(Map.of(
          "request_id", requestId,
          "schedule_version", scheduleVersion,
          "item_count", items.size(),
          "created_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
        appendAudit("ERP_WRITEBACK", scheduleVersion, "WRITE_SCHEDULE_RESULTS", operator, requestId, null);
        appendOutbox("ERP_SCHEDULE_RESULTS", scheduleVersion, requestId, failedCount == 0 ? "SUCCESS" : "PARTIAL", null);
        erpDataManager.refreshTriggered("ERP_SCHEDULE_RESULTS", requestId, "schedule result writeback");
        return Map.of("request_id", requestId, "success_count", successCount, "failed_count", failedCount);
      });
    }
  }

  public Map<String, Object> writeScheduleStatus(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "WRITE_SCHEDULE_STATUS", () -> {
        String scheduleVersion = string(payload, "schedule_version", null);
        List<Map<String, Object>> items = maps(payload.get("items"));
        if (scheduleVersion == null || items.isEmpty()) {
          throw badRequest("schedule_version and items are required.");
        }
        int successCount = 0;
        int failedCount = 0;
        for (Map<String, Object> row : items) {
          String orderNo = string(row, "order_no", string(row, "orderNo", null));
          String status = string(row, "status", null);
          MvpDomain.Order order = state.orders.stream().filter(o -> o.orderNo.equals(orderNo)).findFirst().orElse(null);
          if (order == null || status == null) {
            failedCount += 1;
            continue;
          }
          order.status = status;
          successCount += 1;
        }
        state.scheduleStatusWrites.add(Map.of(
          "request_id", requestId,
          "schedule_version", scheduleVersion,
          "item_count", items.size(),
          "created_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
        appendAudit("ERP_WRITEBACK", scheduleVersion, "WRITE_SCHEDULE_STATUS", operator, requestId, null);
        appendOutbox("ERP_SCHEDULE_STATUS", scheduleVersion, requestId, failedCount == 0 ? "SUCCESS" : "PARTIAL", null);
        erpDataManager.refreshTriggered("ERP_SCHEDULE_STATUS", requestId, "schedule status writeback");
        return Map.of("request_id", requestId, "success_count", successCount, "failed_count", failedCount);
      });
    }
  }

  public Map<String, Object> ingestWipLotEvent(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "INGEST_WIP_EVENT", () -> {
        String wipLotId = string(payload, "wip_lot_id", null);
        String orderNo = string(payload, "order_no", null);
        String processCode = string(payload, "process_code", null);
        double qty = number(payload, "qty", -1d);
        String eventTime = string(payload, "event_time", null);
        if (wipLotId == null || orderNo == null || processCode == null || qty < 0 || eventTime == null) {
          throw badRequest("wip lot event payload is invalid.");
        }
        Map<String, Object> lot = state.wipLots.stream()
          .filter(row -> Objects.equals(row.get("wip_lot_id"), wipLotId))
          .findFirst()
          .orElse(null);
        if (lot == null) {
          lot = new LinkedHashMap<>();
          lot.put("wip_lot_id", wipLotId);
          lot.put("order_no", orderNo);
          lot.put("process_code", processCode);
          lot.put("qty", 0d);
          lot.put("status", "ACTIVE");
          lot.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
          state.wipLots.add(lot);
        }
        lot.put("qty", number(lot, "qty", 0d) + qty);
        lot.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());

        state.wipLotEvents.add(Map.of(
          "event_id", "WIP-EVT-%05d".formatted(state.wipLotEvents.size() + 1),
          "wip_lot_id", wipLotId,
          "order_no", orderNo,
          "process_code", processCode,
          "qty", qty,
          "event_time", eventTime,
          "request_id", requestId
        ));
        appendAudit("WIP_LOT", wipLotId, "INGEST_WIP_EVENT", operator, requestId, null);
        appendInbox("MES_WIP_EVENT", wipLotId, requestId, "SUCCESS", null);
        erpDataManager.refreshTriggered("MES_WIP_EVENT", requestId, "wip lot event accepted");
        return Map.of("request_id", requestId, "accepted", true, "message", "WIP event accepted.");
      });
    }
  }

  public Map<String, Object> triggerReplanJob(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "TRIGGER_REPLAN", () -> doCreateReplanJob(payload, requestId, operator));
    }
  }

  public Map<String, Object> getReplanJob(String jobNo, String requestId) {
    synchronized (lock) {
      Map<String, Object> found = state.replanJobs.stream()
        .filter(row -> Objects.equals(row.get("job_no"), jobNo))
        .findFirst()
        .orElseThrow(() -> notFound("Replan job %s not found.".formatted(jobNo)));
      Map<String, Object> out = new LinkedHashMap<>(found);
      out.put("request_id", requestId);
      return out;
    }
  }

  public List<Map<String, Object>> listAlerts(Map<String, String> filters) {
    synchronized (lock) {
      return state.alerts.stream().filter(row -> {
        if (filters == null) {
          return true;
        }
        if (filters.containsKey("status") && !Objects.equals(filters.get("status"), row.get("status"))) {
          return false;
        }
        if (filters.containsKey("severity") && !Objects.equals(filters.get("severity"), row.get("severity"))) {
          return false;
        }
        return true;
      }).map(this::enrichAlertMetrics).map(this::localizeRow).toList();
    }
  }

  public Map<String, Object> ackAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return updateAlert(alertId, payload, requestId, operator, "ACKED");
    }
  }

  public Map<String, Object> closeAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return updateAlert(alertId, payload, requestId, operator, "CLOSED");
    }
  }

  public List<Map<String, Object>> listAuditLogs(Map<String, String> filters) {
    synchronized (lock) {
      return state.auditLogs.stream().filter(row -> {
        if (filters == null) {
          return true;
        }
        if (filters.containsKey("entity_type") && !Objects.equals(filters.get("entity_type"), row.get("entity_type"))) {
          return false;
        }
        if (filters.containsKey("request_id") && !Objects.equals(filters.get("request_id"), row.get("request_id"))) {
          return false;
        }
        return true;
      }).map(this::localizeRow).toList();
    }
  }

  public List<Map<String, Object>> listIntegrationInbox(Map<String, String> filters) {
    synchronized (lock) {
      return state.integrationInbox.stream()
        .filter(row -> matchIntegrationFilters(row, filters))
        .map(this::localizeRow)
        .toList();
    }
  }

  public List<Map<String, Object>> listIntegrationOutbox(Map<String, String> filters) {
    synchronized (lock) {
      return state.integrationOutbox.stream()
        .filter(row -> matchIntegrationFilters(row, filters))
        .map(this::localizeRow)
        .toList();
    }
  }

  public Map<String, Object> getSimulationState(String requestId) {
    synchronized (lock) {
      alignSimulationDateToToday();
      return buildSimulationStateResponse(requestId);
    }
  }

  private void alignSimulationDateToToday() {
    LocalDate today = LocalDate.now(SIMULATION_ZONE);
    if (simulationState.currentDate == null || simulationState.currentDate.isBefore(today)) {
      simulationState.currentDate = today;
      refreshOrderStatuses(today);
    }
  }

  public List<Map<String, Object>> listSimulationEvents(Map<String, String> filters) {
    synchronized (lock) {
      return simulationState.events.stream()
        .filter(row -> {
          if (filters == null) {
            return true;
          }
          if (filters.containsKey("event_type") && !Objects.equals(filters.get("event_type"), row.get("event_type"))) {
            return false;
          }
          if (filters.containsKey("event_date") && !Objects.equals(filters.get("event_date"), row.get("event_date"))) {
            return false;
          }
          return true;
        })
        .map(this::localizeRow)
        .toList();
    }
  }

  public Map<String, Object> resetSimulation(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "SIM_RESET", () -> {
        long seed = (long) number(payload, "seed", DEFAULT_SIM_SEED);
        String scenario = normalizeScenario(string(payload, "scenario", DEFAULT_SIM_SCENARIO));
        int dailySales = clampInt((int) number(payload, "daily_sales_order_count", DEFAULT_SIM_DAILY_SALES), 1, 500);
        resetSimulationState(seed, scenario, dailySales);
        appendSimulationEvent(
          simulationState.currentDate,
          "SIM_RESET",
          "\u4eff\u771f\u72b6\u6001\u5df2\u91cd\u7f6e\u3002",
          requestId,
          Map.of()
        );
        appendAudit("SIMULATION", "SIMULATION_STATE", "RESET_SIMULATION", operator, requestId, null);
        Map<String, Object> out = buildSimulationStateResponse(requestId);
        out.put("message", "娴犺法婀￠悩鑸碘偓浣稿嚒闁插秶鐤嗛妴");
        return out;
      });
    }
  }

  public Map<String, Object> runSimulation(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "SIM_RUN", () -> {
        int days = clampInt((int) number(payload, "days", 7d), 1, 30);
        int dailySales = clampInt(
          (int) number(payload, "daily_sales_order_count", simulationState.dailySalesOrderCount),
          1,
          500
        );
        String scenario = normalizeScenario(string(payload, "scenario", simulationState.scenario));
        long seed = simulationState.seed;
        if (payload != null && payload.containsKey("seed")) {
          seed = (long) number(payload, "seed", seed);
        }

        simulationState.seed = seed;
        simulationState.scenario = scenario;
        simulationState.dailySalesOrderCount = dailySales;

        LocalDate startDate = simulationState.currentDate;
        int totalSales = 0;
        int totalConverted = 0;
        int totalVersions = 0;
        int totalReportings = 0;
        int delayedOrders = 0;
        double capacityFactorSum = 0d;
        List<Map<String, Object>> dailySummaries = new ArrayList<>();

        for (int i = 0; i < days; i += 1) {
          LocalDate businessDate = simulationState.currentDate;
          Random dayRandom = new Random(seed + businessDate.toEpochDay() * 997L + i * 131L);

          int generated = generateDailySalesAndProductionOrders(businessDate, dailySales, dayRandom, requestId + ":sales:" + i);
          totalSales += generated;
          totalConverted += generated;

          Map<String, Object> capacity = rebuildPlanningHorizon(businessDate, scenario, dayRandom, requestId + ":capacity:" + i);
          double capacityFactor = number(capacity, "capacity_factor", 1d);
          capacityFactorSum += capacityFactor;

          String baseVersionNo = state.schedules.isEmpty() ? null : state.schedules.get(state.schedules.size() - 1).versionNo;
          Map<String, Object> generatePayload = new LinkedHashMap<>();
          generatePayload.put("base_version_no", baseVersionNo);
          generatePayload.put("autoReplan", false);
          generatePayload.put("reason", "SIM_AUTO_DAILY");
          generatePayload.put("request_id", requestId + ":generate:" + i);
          generatePayload.put("compact_response", true);
          Map<String, Object> schedule = generateSchedule(generatePayload, requestId + ":generate:" + i, "simulator");
          String versionNo = string(schedule, "version_no", string(schedule, "versionNo", null));
          totalVersions += 1;
          appendSimulationEvent(
            businessDate,
            "SCHEDULE_GENERATED",
            "\u5df2\u751f\u6210\u6392\u4ea7\u7248\u672c\u3002",
            requestId,
            Map.of("version_no", versionNo, "base_version_no", baseVersionNo == null ? "" : baseVersionNo)
          );

          Map<String, Object> publishPayload = new LinkedHashMap<>();
          publishPayload.put("request_id", requestId + ":publish:" + i);
          publishPayload.put("operator", "simulator");
          publishPayload.put("reason", "SIM_AUTO_DAILY");
          publishSchedule(versionNo, publishPayload, requestId + ":publish:" + i, "simulator");
          appendSimulationEvent(
            businessDate,
            "SCHEDULE_PUBLISHED",
            "\u5df2\u53d1\u5e03\u6392\u4ea7\u7248\u672c\u3002",
            requestId,
            Map.of("version_no", versionNo)
          );

          String breakdownProcess = string(capacity, "breakdown_process", null);
          if (breakdownProcess != null) {
            createAlert("EQUIPMENT_DOWN", "CRITICAL", "", breakdownProcess, versionNo, 1d, 1d, null, null);
          }

          int reportingCount = simulateDailyReporting(businessDate, versionNo, scenario, dayRandom, requestId + ":report:" + i);
          totalReportings += reportingCount;

          refreshOrderStatuses(businessDate.plusDays(1));
          delayedOrders = countDelayedOrders(businessDate.plusDays(1));

          Map<String, Object> daySummary = new LinkedHashMap<>();
          daySummary.put("date", businessDate.toString());
          daySummary.put("sales_orders", generated);
          daySummary.put("converted_orders", generated);
          daySummary.put("version_no", versionNo);
          daySummary.put("reportings", reportingCount);
          daySummary.put("capacity_factor", round2(capacityFactor));
          daySummary.put("delayed_orders", delayedOrders);
          dailySummaries.add(daySummary);

          simulationState.currentDate = businessDate.plusDays(1);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("request_id", requestId);
        summary.put("days", days);
        summary.put("scenario", scenario);
        summary.put("seed", seed);
        summary.put("daily_sales_order_count", dailySales);
        summary.put("start_date", startDate.toString());
        summary.put("end_date", simulationState.currentDate.toString());
        summary.put("new_sales_orders", totalSales);
        summary.put("new_production_orders", totalConverted);
        summary.put("generated_versions", totalVersions);
        summary.put("reporting_count", totalReportings);
        summary.put("delayed_orders", delayedOrders);
        summary.put("avg_capacity_factor", days > 0 ? round2(capacityFactorSum / days) : 1d);
        summary.put("daily_kpis", dailySummaries);
        simulationState.lastRunSummary = deepCopyMap(summary);

        appendAudit("SIMULATION", "SIM-RUN", "RUN_SIMULATION", operator, requestId, "娴犺法婀℃潻鎰攽鐎瑰本鍨氶妴");
        appendSimulationEvent(
          simulationState.currentDate.minusDays(1),
          "SIM_RUN_DONE",
          "\u4eff\u771f\u8fd0\u884c\u5b8c\u6210\u3002",
          requestId,
          Map.of(
            "days", days,
            "new_sales_orders", totalSales,
            "generated_versions", totalVersions
          )
        );

        Map<String, Object> out = new LinkedHashMap<>(summary);
        out.put("state", buildSimulationStateResponse(requestId));
        return out;
      });
    }
  }

  public Map<String, Object> addManualProductionOrder(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "MANUAL_SIM_ADD_ORDER", () -> {
        ensureManualSimulationSnapshot();
        LocalDate businessDate = simulationState.currentDate;
        Random random = new Random(simulationState.seed + businessDate.toEpochDay() * 1103L + productionSeq.get() * 31L);
        String productCode = pickProductCode(random);
        double qty = (2 + random.nextInt(10)) * 100d;
        boolean urgent = bool(payload, "urgent_flag", false);
        LocalDate dueDate = businessDate.plusDays(urgent ? 1 + random.nextInt(2) : 2 + random.nextInt(5));

        String salesOrderNo = "SO-MANUAL-%05d".formatted(salesSeq.incrementAndGet());
        String productionOrderNo = "MO-MANUAL-%05d".formatted(productionSeq.incrementAndGet());

        MvpDomain.OrderBusinessData data = buildSimulationBusinessData(businessDate, dueDate, salesOrderNo, productCode, qty);
        data.customerRemark = "閹靛濮╁Ο鈩冨珯鐠併垹宕";
        data.weeklyMonthlyPlanRemark = "閹靛濮╁Ο鈩冨珯";
        data.note = "MANUAL_SIM";

        MvpDomain.Order order = new MvpDomain.Order(
          productionOrderNo,
          "production",
          dueDate,
          businessDate,
          urgent,
          false,
          false,
          "OPEN",
          List.of(new MvpDomain.OrderItem(productCode, qty, 0d)),
          data
        );
        state.orders.add(order);

        appendSimulationEvent(
          businessDate,
          "ORDER_CONVERTED",
          "\u5df2\u5c06\u9500\u552e\u8ba2\u5355\u8f6c\u6362\u4e3a\u751f\u4ea7\u8ba2\u5355\u3002",
          requestId,
          Map.of(
            "production_order_no", productionOrderNo,
            "sales_order_no", salesOrderNo,
            "product_code", productCode,
            "product_name_cn", productNameCn(productCode),
            "order_qty", qty
          )
        );
        appendAudit("SIMULATION", productionOrderNo, "CREATE_ORDER", operator, requestId, "閹靛濮╁Ο鈩冨珯閺傛澘顤冮悽鐔堕獓鐠併垹宕");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request_id", requestId);
        out.put("production_order_no", productionOrderNo);
        out.put("sales_order_no", salesOrderNo);
        out.put("product_code", productCode);
        out.put("product_name_cn", productNameCn(productCode));
        out.put("order_qty", qty);
        out.put("due_date", dueDate.toString());
        out.put("state", buildSimulationStateResponse(requestId));
        return out;
      });
    }
  }

  public Map<String, Object> advanceManualOneDay(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "MANUAL_SIM_ADVANCE_DAY", () -> {
        ensureManualSimulationSnapshot();
        long totalStart = System.nanoTime();
        Map<String, Long> phaseDurationMs = new LinkedHashMap<>();
        long phaseStart = System.nanoTime();

        String scenario = normalizeScenario(string(payload, "scenario", simulationState.scenario));
        long seed = payload != null && payload.containsKey("seed")
          ? (long) number(payload, "seed", simulationState.seed)
          : simulationState.seed;
        int dailySales = clampInt(
          (int) number(payload, "daily_sales_order_count", simulationState.dailySalesOrderCount),
          1,
          500
        );
        simulationState.seed = seed;
        simulationState.scenario = scenario;
        simulationState.dailySalesOrderCount = dailySales;
        phaseDurationMs.put("prepare_input", elapsedMillis(phaseStart));

        LocalDate businessDate = simulationState.currentDate == null ? state.startDate : simulationState.currentDate;
        String clientDateText = string(payload, "client_date", string(payload, "clientDate", null));
        LocalDate clientDate = parseLocalDateFlexible(clientDateText, null);
        LocalDate baseClientDate = clientDate == null ? LocalDate.now() : clientDate;
        LocalDate nextDayFromClientDate = baseClientDate.plusDays(1);
        if (businessDate.isBefore(nextDayFromClientDate)) {
          businessDate = nextDayFromClientDate;
        }
        Random dayRandom = new Random(seed + businessDate.toEpochDay() * 997L + 17L);
        phaseStart = System.nanoTime();
        Map<String, Object> capacity = rebuildPlanningHorizon(businessDate, scenario, dayRandom, requestId + ":manual:capacity");
        double capacityFactor = number(capacity, "capacity_factor", 1d);
        phaseDurationMs.put("rebuild_planning_horizon", elapsedMillis(phaseStart));

        String baseVersionNo = state.schedules.isEmpty() ? null : state.schedules.get(state.schedules.size() - 1).versionNo;
        Map<String, Object> generatePayload = new LinkedHashMap<>();
        generatePayload.put("base_version_no", baseVersionNo);
        generatePayload.put("autoReplan", false);
        generatePayload.put("reason", "MANUAL_SIM_ADVANCE");
        generatePayload.put("request_id", requestId + ":manual:generate");
        generatePayload.put("compact_response", true);
        phaseStart = System.nanoTime();
        Map<String, Object> schedule = generateSchedule(generatePayload, requestId + ":manual:generate", "simulator");
        String versionNo = string(schedule, "version_no", string(schedule, "versionNo", null));
        phaseDurationMs.put("generate_schedule", elapsedMillis(phaseStart));

        Map<String, Object> publishPayload = new LinkedHashMap<>();
        publishPayload.put("request_id", requestId + ":manual:publish");
        publishPayload.put("operator", "simulator");
        publishPayload.put("reason", "MANUAL_SIM_ADVANCE");
        phaseStart = System.nanoTime();
        publishSchedule(versionNo, publishPayload, requestId + ":manual:publish", "simulator");
        phaseDurationMs.put("publish_schedule", elapsedMillis(phaseStart));

        phaseStart = System.nanoTime();
        int reportingCount = simulateDailyReporting(
          businessDate,
          versionNo,
          scenario,
          dayRandom,
          requestId + ":manual:report"
        );
        phaseDurationMs.put("simulate_reporting", elapsedMillis(phaseStart));

        phaseStart = System.nanoTime();
        refreshOrderStatuses(businessDate.plusDays(1));
        int delayedOrders = countDelayedOrders(businessDate.plusDays(1));
        simulationState.currentDate = businessDate.plusDays(1);
        phaseDurationMs.put("refresh_order_status", elapsedMillis(phaseStart));
        MvpDomain.ScheduleVersion generatedSchedule = getScheduleEntity(versionNo);
        Map<String, Object> generatedObservability = buildScheduleObservabilityMetrics(generatedSchedule, null, null);
        long manualAdvanceDurationMs = elapsedMillis(totalStart);

        Map<String, Object> dailySummary = new LinkedHashMap<>();
        dailySummary.put("date", businessDate.toString());
        dailySummary.put("sales_orders", 0);
        dailySummary.put("converted_orders", 0);
        dailySummary.put("version_no", versionNo);
        dailySummary.put("reportings", reportingCount);
        dailySummary.put("capacity_factor", round2(capacityFactor));
        dailySummary.put("delayed_orders", delayedOrders);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("request_id", requestId);
        summary.put("days", 1);
        summary.put("scenario", scenario);
        summary.put("seed", seed);
        summary.put("daily_sales_order_count", dailySales);
        summary.put("start_date", businessDate.toString());
        summary.put("end_date", simulationState.currentDate.toString());
        summary.put("new_sales_orders", 0);
        summary.put("new_production_orders", 0);
        summary.put("generated_versions", 1);
        summary.put("reporting_count", reportingCount);
        summary.put("delayed_orders", delayedOrders);
        summary.put("avg_capacity_factor", round2(capacityFactor));
        summary.put("daily_kpis", List.of(dailySummary));
        summary.put("manual_advance_duration_ms", manualAdvanceDurationMs);
        summary.put("manual_advance_phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
        summary.put(
          "schedule_generate_duration_ms",
          (long) number(generatedSchedule.metrics, "schedule_generate_duration_ms", 0d)
        );
        summary.put(
          "schedule_generate_phase_duration_ms",
          generatedSchedule.metrics.get("schedule_generate_phase_duration_ms") instanceof Map<?, ?> phases
            ? deepCopyMap((Map<String, Object>) phases)
            : new LinkedHashMap<String, Object>()
        );
        summary.put("unscheduled_task_count", (int) number(generatedObservability, "unscheduled_task_count", 0d));
        summary.put(
          "unscheduled_reason_distribution",
          generatedObservability.get("unscheduled_reason_distribution") instanceof Map<?, ?> reasons
            ? deepCopyMap((Map<String, Object>) reasons)
            : new LinkedHashMap<String, Object>()
        );
        summary.put(
          "schedule_completion_rate",
          number(generatedObservability, "schedule_completion_rate", number(generatedSchedule.metrics, "scheduleCompletionRate", 0d))
        );
        summary.put("locked_or_frozen_impact_count", (int) number(generatedObservability, "locked_or_frozen_impact_count", 0d));
        summary.put("publish_count", (int) number(generatedObservability, "publish_count", 0d));
        summary.put("rollback_count", (int) number(generatedObservability, "rollback_count", 0d));
        summary.put("publish_rollback_count", (int) number(generatedObservability, "publish_rollback_count", 0d));
        summary.put("replan_failure_rate", number(generatedObservability, "replan_failure_rate", 0d));
        summary.put("api_error_rate", number(generatedObservability, "api_error_rate", 0d));
        simulationState.lastRunSummary = deepCopyMap(summary);

        appendSimulationEvent(
          businessDate,
          "SIM_RUN_DONE",
          "\u4eff\u771f\u8fd0\u884c\u5b8c\u6210\u3002",
          requestId,
          Map.of(
            "version_no",
            versionNo,
            "days",
            1,
            "reporting_count",
            reportingCount,
            "manual_advance_duration_ms",
            manualAdvanceDurationMs,
            "manual_advance_phase_duration_ms",
            new LinkedHashMap<>(phaseDurationMs)
          )
        );
        Map<String, Object> perfContext = new LinkedHashMap<>();
        perfContext.put("request_id", requestId);
        perfContext.put("version_no", versionNo);
        perfContext.put("phase", "manual_advance_day");
        perfContext.put("duration_ms", manualAdvanceDurationMs);
        perfContext.put("phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
        appendAudit(
          "SIMULATION",
          "SIM-MANUAL-ADVANCE",
          "RUN_SIMULATION",
          operator,
          requestId,
          "MANUAL_ADVANCE_DAY",
          perfContext
        );

        Map<String, Object> out = new LinkedHashMap<>(summary);
        out.put("version_no", versionNo);
        out.put("state", buildSimulationStateResponse(requestId));
        return out;
      });
    }
  }

  public Map<String, Object> resetManualSimulation(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "MANUAL_SIM_RESET", () -> {
        if (manualSimulationSnapshot == null) {
          Map<String, Object> out = buildSimulationStateResponse(requestId);
          out.put("message", "瑜版挸澧犲▽鈩冩箒閸欘垶鍣哥純顔炬畱閹靛濮╁Ο鈩冨珯娴兼俺鐦介妴");
          return out;
        }
        restoreManualSimulationSnapshot();
        Map<String, Object> out = buildSimulationStateResponse(requestId);
        out.put("message", "瀹稿弶浠径宥呭煂閹靛濮╁Ο鈩冨珯閸撳秶娈戦弫鐗堝祦閻樿埖鈧降鈧");
        return out;
      });
    }
  }

  public Map<String, Object> retryOutboxMessage(String messageId, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "RETRY_OUTBOX#" + messageId, () -> {
        Map<String, Object> message = state.integrationOutbox.stream()
          .filter(row -> Objects.equals(row.get("message_id"), messageId))
          .findFirst()
          .orElseThrow(() -> notFound("Outbox message %s not found.".formatted(messageId)));
        int retryCount = (int) number(message, "retry_count", 0d) + 1;
        message.put("retry_count", retryCount);
        message.put("status", "SUCCESS");
        message.put("error_msg", "");
        message.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        appendAudit("INTEGRATION_OUTBOX", messageId, "RETRY_OUTBOX", operator, requestId, null);
        return Map.of(
          "request_id", requestId,
          "success", true,
          "message", "Outbox message %s retried.".formatted(messageId)
        );
      });
    }
  }

  public Map<String, Object> createDispatchCommand(Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "CREATE_DISPATCH", () -> {
        String commandType = string(payload, "command_type", null);
        String targetOrderNo = string(payload, "target_order_no", null);
        if (commandType == null || targetOrderNo == null) {
          throw badRequest("dispatch command payload is invalid.");
        }
        String commandId = "CMD-%05d".formatted(dispatchSeq.incrementAndGet());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("command_id", commandId);
        row.put("command_type", commandType);
        row.put("target_order_no", targetOrderNo);
        row.put("target_order_type", string(payload, "target_order_type", "production"));
        row.put("effective_time", string(payload, "effective_time", OffsetDateTime.now(ZoneOffset.UTC).toString()));
        row.put("reason", string(payload, "reason", ""));
        row.put("created_by", string(payload, "created_by", operator));
        row.put("approved_flag", 0);
        row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        state.dispatchCommands.add(row);
        appendAudit("DISPATCH_COMMAND", commandId, "CREATE_DISPATCH_COMMAND", operator, requestId, string(payload, "reason", null));
        return Map.of("request_id", requestId, "accepted", true, "message", "Dispatch command accepted.", "command_id", commandId);
      });
    }
  }

  public Map<String, Object> approveDispatchCommand(String commandId, Map<String, Object> payload, String requestId, String operator) {
    synchronized (lock) {
      return runIdempotent(requestId, "APPROVE_DISPATCH#" + commandId, () -> {
        Map<String, Object> command = state.dispatchCommands.stream()
          .filter(row -> Objects.equals(row.get("command_id"), commandId))
          .findFirst()
          .orElseThrow(() -> notFound("Dispatch command %s not found.".formatted(commandId)));
        String decision = string(payload, "decision", null);
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
          throw badRequest("decision must be APPROVED or REJECTED.");
        }
        state.dispatchApprovals.add(Map.of(
          "approval_id", "APP-%05d".formatted(dispatchApprovalSeq.incrementAndGet()),
          "command_id", commandId,
          "approver", string(payload, "approver", operator),
          "decision", decision,
          "decision_reason", string(payload, "decision_reason", ""),
          "decision_time", string(payload, "decision_time", OffsetDateTime.now(ZoneOffset.UTC).toString()),
          "request_id", requestId
        ));
        if ("APPROVED".equals(decision)) {
          command.put("approved_flag", 1);
          applyDispatchCommand(command);
        }
        appendAudit(
          "DISPATCH_COMMAND",
          commandId,
          "APPROVED".equals(decision) ? "APPROVE_DISPATCH_COMMAND" : "REJECT_DISPATCH_COMMAND",
          operator,
          requestId,
          string(payload, "decision_reason", null)
        );
        return Map.of("request_id", requestId, "success", true, "message", "Dispatch command %s.".formatted(decision.toLowerCase()));
      });
    }
  }

  public List<Map<String, Object>> listDispatchCommands(Map<String, String> filters) {
    synchronized (lock) {
      return state.dispatchCommands.stream()
        .filter(row -> filters == null || !filters.containsKey("command_type")
          || Objects.equals(filters.get("command_type"), row.get("command_type")))
        .map(this::localizeRow)
        .toList();
    }
  }

  public List<Map<String, Object>> listScheduleVersions(Map<String, String> filters) {
    synchronized (lock) {
      return state.schedules.stream()
        .sorted(Comparator.comparing(v -> v.versionNo))
        .map(version -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("version_no", version.versionNo);
          row.put("status", version.status);
          row.put("based_on_version", version.basedOnVersion);
          row.put("rule_version_no", version.ruleVersionNo);
          String strategyCode = normalizeScheduleStrategy(
            firstString(version.metadata, "schedule_strategy_code", "scheduleStrategyCode", "strategy_code", "strategyCode")
          );
          row.put("strategy_code", strategyCode);
          row.put("strategy_name_cn", scheduleStrategyNameCn(strategyCode));
          row.put("publish_time", version.publishTime == null ? null : version.publishTime.toString());
          row.put("created_by", version.createdBy);
          row.put("created_at", version.createdAt == null ? null : version.createdAt.toString());
          return localizeRow(row);
        })
        .filter(row -> filters == null || !filters.containsKey("status") || Objects.equals(filters.get("status"), row.get("status")))
        .toList();
    }
  }

  public List<Map<String, Object>> listScheduleTasks(String versionNo) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);
      List<Map<String, Object>> tasks = new ArrayList<>();
      Map<String, MvpDomain.ScheduleTask> taskByTaskKey = new HashMap<>();
      for (MvpDomain.ScheduleTask task : schedule.tasks) {
        taskByTaskKey.put(task.taskKey, task);
      }
      Map<String, Map<String, Object>> unscheduledByTaskKey = new HashMap<>();
      for (Map<String, Object> item : schedule.unscheduled) {
        String taskKey = firstString(item, "taskKey", "task_key");
        if (taskKey == null || taskKey.isBlank()) {
          continue;
        }
        unscheduledByTaskKey.put(taskKey, item);
      }
      Set<String> scheduledTaskKeys = new HashSet<>();
      int id = 1;
      for (MvpDomain.Allocation allocation : schedule.allocations) {
        scheduledTaskKeys.add(allocation.taskKey);
        MvpDomain.ScheduleTask task = taskByTaskKey.get(allocation.taskKey);
        Map<String, Object> unscheduled = unscheduledByTaskKey.get(allocation.taskKey);
        String reasonCode = resolveUnscheduledReasonCode(unscheduled);
        String dependencyStatus = resolveTaskDependencyStatus(task, unscheduled);
        String lastBlockReason = resolveTaskLastBlockReason(task, unscheduled, reasonCode);
        String taskStatus = resolveTaskStatus(task, unscheduled, true);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id++);
        row.put("version_no", schedule.versionNo);
        row.put("order_no", allocation.orderNo);
        row.put("order_type", "production");
        row.put("process_code", allocation.processCode);
        row.put("calendar_date", allocation.date);
        row.put("shift_code", "D".equals(allocation.shiftCode) ? "DAY" : "NIGHT");
        row.put("plan_start_time", toDateTime(allocation.date, allocation.shiftCode, true));
        row.put("plan_finish_time", toDateTime(allocation.date, allocation.shiftCode, false));
        row.put("plan_qty", allocation.scheduledQty);
        row.put("lock_flag", findOrder(allocation.orderNo).lockFlag ? 1 : 0);
        row.put("priority", findOrder(allocation.orderNo).urgent ? 1 : 0);
        row.put("dependency_status", dependencyStatus);
        row.put("task_status", taskStatus);
        row.put("last_block_reason", lastBlockReason);
        row.put("unscheduled_reason_code", reasonCode);
        row.put("unscheduled_reason_cn", reasonCode == null ? null : scheduleReasonNameCn(reasonCode));
        tasks.add(localizeRow(row));
      }
      for (MvpDomain.ScheduleTask task : schedule.tasks) {
        if (scheduledTaskKeys.contains(task.taskKey)) {
          continue;
        }
        Map<String, Object> unscheduled = unscheduledByTaskKey.get(task.taskKey);
        if (unscheduled == null) {
          continue;
        }
        String reasonCode = resolveUnscheduledReasonCode(unscheduled);
        String dependencyStatus = resolveTaskDependencyStatus(task, unscheduled);
        String lastBlockReason = resolveTaskLastBlockReason(task, unscheduled, reasonCode);
        String taskStatus = resolveTaskStatus(task, unscheduled, false);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id++);
        row.put("version_no", schedule.versionNo);
        row.put("order_no", task.orderNo);
        row.put("order_type", "production");
        row.put("process_code", task.processCode);
        row.put("calendar_date", null);
        row.put("shift_code", null);
        row.put("plan_start_time", null);
        row.put("plan_finish_time", null);
        row.put("plan_qty", 0d);
        row.put("lock_flag", findOrder(task.orderNo).lockFlag ? 1 : 0);
        row.put("priority", findOrder(task.orderNo).urgent ? 1 : 0);
        row.put("dependency_status", dependencyStatus);
        row.put("task_status", taskStatus);
        row.put("last_block_reason", lastBlockReason);
        row.put("unscheduled_reason_code", reasonCode);
        row.put("unscheduled_reason_cn", reasonCode == null ? null : scheduleReasonNameCn(reasonCode));
        tasks.add(localizeRow(row));
      }
      return tasks;
    }
  }

  public List<Map<String, Object>> listScheduleDailyProcessLoad(String versionNo) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);

      Map<String, MvpDomain.ProcessConfig> processByCode = new HashMap<>();
      List<String> processOrder = new ArrayList<>();
      for (MvpDomain.ProcessConfig process : state.processes) {
        String processCode = normalizeCode(process.processCode);
        processByCode.put(processCode, process);
        processOrder.add(processCode);
      }

      Map<String, Integer> workersByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.workerPools) {
        workersByDateShiftProcess.put(
          row.date + "#" + row.shiftCode + "#" + normalizeCode(row.processCode),
          row.available
        );
      }
      Map<String, Integer> machinesByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.machinePools) {
        machinesByDateShiftProcess.put(
          row.date + "#" + row.shiftCode + "#" + normalizeCode(row.processCode),
          row.available
        );
      }
      Map<String, Integer> occupiedWorkersByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.initialWorkerOccupancy) {
        occupiedWorkersByDateShiftProcess.put(
          row.date + "#" + row.shiftCode + "#" + normalizeCode(row.processCode),
          Math.max(0, row.available)
        );
      }
      Map<String, Integer> occupiedMachinesByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.initialMachineOccupancy) {
        occupiedMachinesByDateShiftProcess.put(
          row.date + "#" + row.shiftCode + "#" + normalizeCode(row.processCode),
          Math.max(0, row.available)
        );
      }

      Set<String> scheduleDates = new HashSet<>();
      for (Map<String, Object> shift : schedule.shifts) {
        String date = string(shift, "date", null);
        if (date != null && !date.isBlank()) {
          scheduleDates.add(date);
        }
      }
      if (scheduleDates.isEmpty()) {
        for (MvpDomain.ShiftRow shift : state.shiftCalendar) {
          scheduleDates.add(shift.date.toString());
        }
      }

      Map<String, Double> scheduledQtyByDateProcess = new HashMap<>();
      for (MvpDomain.Allocation allocation : schedule.allocations) {
        if (allocation.date == null || allocation.date.isBlank()) {
          continue;
        }
        String date = allocation.date;
        if (!scheduleDates.contains(date)) {
          continue;
        }
        String processCode = normalizeCode(allocation.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        scheduledQtyByDateProcess.merge(date + "#" + processCode, allocation.scheduledQty, Double::sum);
      }

      Map<String, Double> maxCapacityByDateProcess = new HashMap<>();
      Map<String, Integer> openShiftCountByDateProcess = new HashMap<>();
      for (MvpDomain.ShiftRow shift : state.shiftCalendar) {
        String date = shift.date.toString();
        if (!scheduleDates.contains(date) || !shift.open) {
          continue;
        }
        for (String processCode : processOrder) {
          MvpDomain.ProcessConfig process = processByCode.get(processCode);
          if (process == null) {
            continue;
          }
          String usageKey = date + "#" + shift.shiftCode + "#" + processCode;
          int workersAvailable = workersByDateShiftProcess.getOrDefault(usageKey, 0);
          int machinesAvailable = machinesByDateShiftProcess.getOrDefault(usageKey, 0);
          int occupiedWorkers = occupiedWorkersByDateShiftProcess.getOrDefault(usageKey, 0);
          int occupiedMachines = occupiedMachinesByDateShiftProcess.getOrDefault(usageKey, 0);
          int effectiveWorkers = Math.max(0, workersAvailable - occupiedWorkers);
          int effectiveMachines = Math.max(0, machinesAvailable - occupiedMachines);
          int groupsByWorkers = effectiveWorkers / Math.max(1, process.requiredWorkers);
          int groupsByMachines = effectiveMachines / Math.max(1, process.requiredMachines);
          int maxGroups = Math.min(groupsByWorkers, groupsByMachines);
          double shiftCapacity = Math.max(0, maxGroups) * process.capacityPerShift;
          String dayProcessKey = date + "#" + processCode;
          maxCapacityByDateProcess.merge(dayProcessKey, shiftCapacity, Double::sum);
          openShiftCountByDateProcess.merge(dayProcessKey, 1, Integer::sum);
        }
      }

      List<String> dates = new ArrayList<>(scheduleDates);
      dates.sort(String::compareTo);

      List<Map<String, Object>> rows = new ArrayList<>();
      for (String date : dates) {
        for (String processCode : processOrder) {
          String key = date + "#" + processCode;
          double scheduledQty = round2(scheduledQtyByDateProcess.getOrDefault(key, 0d));
          double maxCapacityQty = round2(maxCapacityByDateProcess.getOrDefault(key, 0d));
          if (scheduledQty <= 1e-9 && maxCapacityQty <= 1e-9) {
            continue;
          }
          double loadRate = maxCapacityQty > 1e-9
            ? round2(Math.max(0d, (scheduledQty / maxCapacityQty) * 100d))
            : (scheduledQty > 1e-9 ? 100d : 0d);

          Map<String, Object> row = new LinkedHashMap<>();
          row.put("version_no", schedule.versionNo);
          row.put("calendar_date", date);
          row.put("process_code", processCode);
          row.put("scheduled_qty", scheduledQty);
          row.put("max_capacity_qty", maxCapacityQty);
          row.put("load_rate", loadRate);
          row.put("open_shift_count", openShiftCountByDateProcess.getOrDefault(key, 0));
          rows.add(localizeRow(row));
        }
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listScheduleShiftProcessLoad(String versionNo) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);

      Map<String, MvpDomain.ProcessConfig> processByCode = new HashMap<>();
      List<String> processOrder = new ArrayList<>();
      for (MvpDomain.ProcessConfig process : state.processes) {
        String processCode = normalizeCode(process.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        processByCode.put(processCode, process);
        processOrder.add(processCode);
      }

      Map<String, Integer> workersByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.workerPools) {
        workersByDateShiftProcess.put(
          row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
          row.available
        );
      }
      Map<String, Integer> machinesByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.machinePools) {
        machinesByDateShiftProcess.put(
          row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
          row.available
        );
      }
      Map<String, Integer> occupiedWorkersByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.initialWorkerOccupancy) {
        occupiedWorkersByDateShiftProcess.put(
          row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
          Math.max(0, row.available)
        );
      }
      Map<String, Integer> occupiedMachinesByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.initialMachineOccupancy) {
        occupiedMachinesByDateShiftProcess.put(
          row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
          Math.max(0, row.available)
        );
      }

      Set<String> scheduleDateShifts = new HashSet<>();
      for (Map<String, Object> shift : schedule.shifts) {
        String date = string(shift, "date", null);
        String shiftCode = normalizeShiftCode(string(shift, "shiftCode", null));
        if (date == null || date.isBlank() || shiftCode.isBlank()) {
          continue;
        }
        scheduleDateShifts.add(date + "#" + shiftCode);
      }
      if (scheduleDateShifts.isEmpty()) {
        for (MvpDomain.ShiftRow shift : state.shiftCalendar) {
          if (!shift.open) {
            continue;
          }
          String shiftCode = normalizeShiftCode(shift.shiftCode);
          if (shiftCode.isBlank()) {
            continue;
          }
          scheduleDateShifts.add(shift.date + "#" + shiftCode);
        }
      }

      Map<String, Double> scheduledQtyByDateShiftProcess = new HashMap<>();
      for (MvpDomain.Allocation allocation : schedule.allocations) {
        if (allocation.date == null || allocation.date.isBlank()) {
          continue;
        }
        String shiftCode = normalizeShiftCode(allocation.shiftCode);
        if (shiftCode.isBlank()) {
          continue;
        }
        String dateShiftKey = allocation.date + "#" + shiftCode;
        if (!scheduleDateShifts.contains(dateShiftKey)) {
          continue;
        }
        String processCode = normalizeCode(allocation.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        scheduledQtyByDateShiftProcess.merge(dateShiftKey + "#" + processCode, allocation.scheduledQty, Double::sum);
      }

      Map<String, Double> maxCapacityByDateShiftProcess = new HashMap<>();
      Map<String, Integer> maxGroupsByDateShiftProcess = new HashMap<>();
      for (MvpDomain.ShiftRow shift : state.shiftCalendar) {
        if (!shift.open) {
          continue;
        }
        String date = shift.date.toString();
        String shiftCode = normalizeShiftCode(shift.shiftCode);
        if (shiftCode.isBlank()) {
          continue;
        }
        String dateShiftKey = date + "#" + shiftCode;
        if (!scheduleDateShifts.contains(dateShiftKey)) {
          continue;
        }
        for (String processCode : processOrder) {
          MvpDomain.ProcessConfig process = processByCode.get(processCode);
          if (process == null) {
            continue;
          }
          String usageKey = dateShiftKey + "#" + processCode;
          int workersAvailable = workersByDateShiftProcess.getOrDefault(usageKey, 0);
          int machinesAvailable = machinesByDateShiftProcess.getOrDefault(usageKey, 0);
          int occupiedWorkers = occupiedWorkersByDateShiftProcess.getOrDefault(usageKey, 0);
          int occupiedMachines = occupiedMachinesByDateShiftProcess.getOrDefault(usageKey, 0);
          int effectiveWorkers = Math.max(0, workersAvailable - occupiedWorkers);
          int effectiveMachines = Math.max(0, machinesAvailable - occupiedMachines);
          int groupsByWorkers = effectiveWorkers / Math.max(1, process.requiredWorkers);
          int groupsByMachines = effectiveMachines / Math.max(1, process.requiredMachines);
          int maxGroups = Math.max(0, Math.min(groupsByWorkers, groupsByMachines));
          maxCapacityByDateShiftProcess.put(usageKey, Math.max(0, maxGroups) * process.capacityPerShift);
          maxGroupsByDateShiftProcess.put(usageKey, maxGroups);
        }
      }

      List<String> orderedDateShifts = new ArrayList<>(scheduleDateShifts);
      orderedDateShifts.sort((left, right) -> {
        String[] leftParts = left.split("#", 2);
        String[] rightParts = right.split("#", 2);
        String leftDate = leftParts.length > 0 ? leftParts[0] : "";
        String rightDate = rightParts.length > 0 ? rightParts[0] : "";
        int byDate = leftDate.compareTo(rightDate);
        if (byDate != 0) {
          return byDate;
        }
        String leftShift = leftParts.length > 1 ? leftParts[1] : "";
        String rightShift = rightParts.length > 1 ? rightParts[1] : "";
        int byShift = Integer.compare(shiftSortIndex(leftShift), shiftSortIndex(rightShift));
        if (byShift != 0) {
          return byShift;
        }
        return leftShift.compareTo(rightShift);
      });

      List<Map<String, Object>> rows = new ArrayList<>();
      for (String dateShift : orderedDateShifts) {
        String[] parts = dateShift.split("#", 2);
        String date = parts.length > 0 ? parts[0] : "";
        String shiftCode = parts.length > 1 ? parts[1] : "";
        if (date.isBlank() || shiftCode.isBlank()) {
          continue;
        }
        for (String processCode : processOrder) {
          String key = dateShift + "#" + processCode;
          double scheduledQty = round2(scheduledQtyByDateShiftProcess.getOrDefault(key, 0d));
          double maxCapacityQty = round2(maxCapacityByDateShiftProcess.getOrDefault(key, 0d));
          if (scheduledQty <= 1e-9 && maxCapacityQty <= 1e-9) {
            continue;
          }
          double loadRate = maxCapacityQty > 1e-9
            ? round2(Math.max(0d, (scheduledQty / maxCapacityQty) * 100d))
            : (scheduledQty > 1e-9 ? 100d : 0d);
          MvpDomain.ProcessConfig process = processByCode.get(processCode);

          Map<String, Object> row = new LinkedHashMap<>();
          row.put("version_no", schedule.versionNo);
          row.put("calendar_date", date);
          row.put("shift_code", normalizeShiftCodeLabel(shiftCode));
          row.put("process_code", processCode);
          row.put("scheduled_qty", scheduledQty);
          row.put("max_capacity_qty", maxCapacityQty);
          row.put("load_rate", loadRate);
          row.put("capacity_per_shift", process == null ? 0d : round2(process.capacityPerShift));
          row.put("required_workers", process == null ? 0 : process.requiredWorkers);
          row.put("required_machines", process == null ? 0 : process.requiredMachines);
          int grossWorkers = workersByDateShiftProcess.getOrDefault(key, 0);
          int grossMachines = machinesByDateShiftProcess.getOrDefault(key, 0);
          int occupiedWorkers = occupiedWorkersByDateShiftProcess.getOrDefault(key, 0);
          int occupiedMachines = occupiedMachinesByDateShiftProcess.getOrDefault(key, 0);
          row.put("available_workers", Math.max(0, grossWorkers - occupiedWorkers));
          row.put("available_machines", Math.max(0, grossMachines - occupiedMachines));
          row.put("occupied_workers", occupiedWorkers);
          row.put("occupied_machines", occupiedMachines);
          row.put("gross_workers", grossWorkers);
          row.put("gross_machines", grossMachines);
          row.put("max_group_count", maxGroupsByDateShiftProcess.getOrDefault(key, 0));
          rows.add(localizeRow(row));
        }
      }
      return rows;
    }
  }

  public Map<String, Object> getScheduleAlgorithm(String versionNo, String requestId) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);
      Map<String, Object> observabilityMetrics = buildScheduleObservabilityMetrics(schedule, null, null);
      String strategyCode = normalizeScheduleStrategy(
        firstString(
          schedule.metadata,
          "schedule_strategy_code",
          "scheduleStrategyCode",
          "strategy_code",
          "strategyCode"
        )
      );
      String strategyNameCn = scheduleStrategyNameCn(strategyCode);

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("task_count", schedule.tasks.size());
      summary.put("allocation_count", schedule.allocations.size());
      summary.put("order_count", schedule.tasks.stream().map(task -> task.orderNo).filter(Objects::nonNull).distinct().count());
      summary.put("target_qty", round2(number(schedule.metrics, "targetQty", 0d)));
      summary.put("scheduled_qty", round2(number(schedule.metrics, "scheduledQty", 0d)));
      summary.put("schedule_completion_rate", round2(number(observabilityMetrics, "schedule_completion_rate", 0d)));
      summary.put("unscheduled_task_count", (int) number(observabilityMetrics, "unscheduled_task_count", schedule.unscheduled.size()));
      summary.put(
        "schedule_generate_duration_ms",
        (long) number(schedule.metrics, "schedule_generate_duration_ms", 0d)
      );
      summary.put(
        "schedule_generate_phase_duration_ms",
        schedule.metrics.get("schedule_generate_phase_duration_ms") instanceof Map<?, ?> phaseDuration
          ? deepCopyMap((Map<String, Object>) phaseDuration)
          : new LinkedHashMap<String, Object>()
      );
      summary.put(
        "unscheduled_reason_distribution",
        observabilityMetrics.get("unscheduled_reason_distribution") instanceof Map<?, ?> reasons
          ? deepCopyMap((Map<String, Object>) reasons)
          : new LinkedHashMap<String, Object>()
      );
      summary.put("locked_or_frozen_impact_count", (int) number(observabilityMetrics, "locked_or_frozen_impact_count", 0d));
      summary.put("publish_count", (int) number(observabilityMetrics, "publish_count", 0d));
      summary.put("rollback_count", (int) number(observabilityMetrics, "rollback_count", 0d));
      summary.put("publish_rollback_count", (int) number(observabilityMetrics, "publish_rollback_count", 0d));
      summary.put("replan_failure_rate", number(observabilityMetrics, "replan_failure_rate", 0d));
      summary.put("api_error_rate", number(observabilityMetrics, "api_error_rate", 0d));

      List<String> logic = new ArrayList<>();
      logic.add("Current strategy: " + strategyNameCn + " (" + strategyCode + ").");
      logic.add("Dispatch objective uses weighted scoring: urgent guarantee + due-date risk + WIP reduction + changeover penalty.");
      logic.add("Urgent policy reserves a minimum process share and daily output floor before regular orders are fully expanded.");
      logic.add("Locked baseline is preserved by default, but can be partially released when urgent protection must be satisfied.");
      if (schedule.metadata.containsKey("hardConstraints")) {
        logic.add("Hard constraints: " + schedule.metadata.get("hardConstraints"));
      } else {
        logic.add("Hard constraints: MAN, MACHINE, MATERIAL.");
      }
      if (schedule.metadata.containsKey("dependencyTypes")) {
        logic.add("Dependency rules: " + schedule.metadata.get("dependencyTypes"));
      } else {
        logic.add("Dependency rules: FS/SS.");
      }
      logic.add("Transfer rules include minimum transfer batch, minimum lot size, and sterilization lag surrogate.");
      logic.add("Capacity is adjusted by shift efficiency and changeover/product-mix penalties.");
      logic.add("Material is checked by cumulative arrival and cumulative consumption, not only single-shift snapshots.");
      logic.add("Component kit check is applied on first-process tasks using BOM-like inventory + inbound-time approximation.");
      logic.add("Unscheduled tasks keep structured reason codes, for example TRANSFER_CONSTRAINT/LOCK_PREEMPTED_BY_URGENT.");

      Comparator<MvpDomain.Order> priorityComparator = switch (strategyCode) {
        case STRATEGY_MAX_CAPACITY_FIRST -> Comparator
            .comparingDouble((MvpDomain.Order o) -> {
              double totalQty = o.items == null ? 0d : o.items.stream().mapToDouble(item -> Math.max(0d, item.qty - item.completedQty)).sum();
              return -totalQty;
            })
            .thenComparing((MvpDomain.Order o) -> !o.urgent)
            .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(o -> o.orderNo);
        case STRATEGY_MIN_DELAY_FIRST -> Comparator
            .comparing((MvpDomain.Order o) -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing((MvpDomain.Order o) -> !o.urgent)
            .thenComparing(o -> o.orderNo);
        default -> Comparator
            .comparing((MvpDomain.Order o) -> !o.urgent)
            .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(o -> o.orderNo);
      };
      List<Map<String, Object>> priorityPreview = state.orders.stream()
        .sorted(priorityComparator)
        .limit(10)
        .map(order -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("order_no", order.orderNo);
          row.put("urgent_flag", order.urgent ? 1 : 0);
          row.put("due_date", order.dueDate == null ? null : order.dueDate.toString());
          row.put("status", order.status);
          row.put("status_name_cn", statusNameCn(order.status));
          row.put(
            "remaining_qty",
            round2(order.items == null ? 0d : order.items.stream().mapToDouble(item -> Math.max(0d, item.qty - item.completedQty)).sum())
          );
          return row;
        })
        .toList();

      Map<String, Integer> shiftIndexByShiftId = new HashMap<>();
      for (int i = 0; i < schedule.shifts.size(); i += 1) {
        Map<String, Object> shift = schedule.shifts.get(i);
        String shiftId = string(shift, "shiftId", null);
        if (shiftId != null && !shiftId.isBlank()) {
          shiftIndexByShiftId.put(shiftId, i);
        }
      }

      Map<String, MvpDomain.ScheduleTask> taskByTaskKey = new HashMap<>();
      for (MvpDomain.ScheduleTask task : schedule.tasks) {
        taskByTaskKey.put(task.taskKey, task);
      }

      Map<String, MvpDomain.ProcessConfig> processConfigByCode = new HashMap<>();
      for (MvpDomain.ProcessConfig process : state.processes) {
        processConfigByCode.put(normalizeCode(process.processCode), process);
      }

      Map<String, Integer> workersAvailableByShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.workerPools) {
        workersAvailableByShiftProcess.put(row.date + "#" + row.shiftCode + "#" + normalizeCode(row.processCode), row.available);
      }
      Map<String, Integer> machinesAvailableByShiftProcess = new HashMap<>();
      for (MvpDomain.ResourceRow row : state.machinePools) {
        machinesAvailableByShiftProcess.put(row.date + "#" + row.shiftCode + "#" + normalizeCode(row.processCode), row.available);
      }
      Map<String, Double> materialAvailableByShiftProductProcess = new HashMap<>();
      for (MvpDomain.MaterialRow row : state.materialAvailability) {
        materialAvailableByShiftProductProcess.put(
          row.date + "#" + row.shiftCode + "#" + row.productCode + "#" + normalizeCode(row.processCode),
          row.availableQty
        );
      }

      Map<String, Double> targetQtyByProcess = new HashMap<>();
      Map<String, Set<String>> orderSetByProcess = new HashMap<>();
      for (MvpDomain.ScheduleTask task : schedule.tasks) {
        String processCode = normalizeCode(task.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        targetQtyByProcess.merge(processCode, task.targetQty, Double::sum);
        orderSetByProcess.computeIfAbsent(processCode, key -> new HashSet<>()).add(task.orderNo);
      }

      Map<String, Double> scheduledQtyByProcess = new HashMap<>();
      Map<String, MvpDomain.Allocation> maxAllocationByProcess = new HashMap<>();
      Map<String, List<MvpDomain.Allocation>> allocationsByTaskKey = new HashMap<>();
      Map<String, Double> totalScheduledByTaskKey = new HashMap<>();
      Map<String, Double> maxAllocationQtyByProcess = new HashMap<>();
      Map<String, Integer> allocationCountByProcess = new HashMap<>();
      for (MvpDomain.Allocation allocation : schedule.allocations) {
        String processCode = normalizeCode(allocation.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        scheduledQtyByProcess.merge(processCode, allocation.scheduledQty, Double::sum);
        maxAllocationQtyByProcess.merge(processCode, allocation.scheduledQty, Math::max);
        allocationCountByProcess.merge(processCode, 1, Integer::sum);
        allocationsByTaskKey.computeIfAbsent(allocation.taskKey, key -> new ArrayList<>()).add(allocation);
        totalScheduledByTaskKey.merge(allocation.taskKey, allocation.scheduledQty, Double::sum);
        MvpDomain.Allocation existing = maxAllocationByProcess.get(processCode);
        if (
          existing == null
            || allocation.scheduledQty > existing.scheduledQty + 1e-9
            || (
              Math.abs(allocation.scheduledQty - existing.scheduledQty) <= 1e-9
                && shiftIndexByShiftId.getOrDefault(allocation.shiftId, Integer.MAX_VALUE)
                  < shiftIndexByShiftId.getOrDefault(existing.shiftId, Integer.MAX_VALUE)
            )
        ) {
          maxAllocationByProcess.put(processCode, allocation);
        }
      }

      Map<String, Integer> reasonCount = new HashMap<>();
      Map<String, Double> unscheduledQtyByProcess = new HashMap<>();
      Map<String, Map<String, Integer>> reasonCountByProcess = new HashMap<>();
      List<Map<String, Object>> unscheduledSamples = new ArrayList<>();
      for (Map<String, Object> row : schedule.unscheduled) {
        String processCode = normalizeCode(string(row, "processCode", ""));
        if (!processCode.isBlank()) {
          unscheduledQtyByProcess.merge(processCode, number(row, "remainingQty", 0d), Double::sum);
        }

        String reasonCode = resolveUnscheduledReasonCode(row);
        if (reasonCode == null || reasonCode.isBlank()) {
          reasonCode = "UNKNOWN";
        }
        reasonCount.merge(reasonCode, 1, Integer::sum);
        if (!processCode.isBlank()) {
          reasonCountByProcess.computeIfAbsent(processCode, key -> new HashMap<>()).merge(reasonCode, 1, Integer::sum);
        }

        if (unscheduledSamples.size() >= 10) {
          continue;
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("task_key", string(row, "taskKey", null));
        sample.put("order_no", string(row, "orderNo", null));
        sample.put("process_code", string(row, "processCode", null));
        sample.put("remaining_qty", round2(number(row, "remainingQty", 0d)));
        sample.put("reason_code", reasonCode);
        sample.put("reason_name_cn", scheduleReasonNameCn(reasonCode));
        sample.put("reason_detail", string(row, "reason_detail", null));
        unscheduledSamples.add(localizeRow(sample));
      }

      Set<String> processCodeSet = new HashSet<>();
      processCodeSet.addAll(targetQtyByProcess.keySet());
      processCodeSet.addAll(scheduledQtyByProcess.keySet());
      processCodeSet.addAll(unscheduledQtyByProcess.keySet());
      List<String> processCodes = new ArrayList<>(processCodeSet);
      processCodes.sort((a, b) -> {
        int byTarget = Double.compare(targetQtyByProcess.getOrDefault(b, 0d), targetQtyByProcess.getOrDefault(a, 0d));
        if (byTarget != 0) {
          return byTarget;
        }
        int byScheduled = Double.compare(scheduledQtyByProcess.getOrDefault(b, 0d), scheduledQtyByProcess.getOrDefault(a, 0d));
        if (byScheduled != 0) {
          return byScheduled;
        }
        return a.compareTo(b);
      });

      List<Map<String, Object>> processSummary = new ArrayList<>();
      for (String processCode : processCodes) {
        double targetQty = round2(targetQtyByProcess.getOrDefault(processCode, 0d));
        double scheduledQty = round2(scheduledQtyByProcess.getOrDefault(processCode, 0d));
        double unscheduledQty = round2(Math.max(unscheduledQtyByProcess.getOrDefault(processCode, 0d), targetQty - scheduledQty));
        double scheduleRate = targetQty > 1e-9 ? round2(Math.min(100d, Math.max(0d, (scheduledQty / targetQty) * 100d))) : 100d;
        int orderCount = orderSetByProcess.getOrDefault(processCode, Set.of()).size();
        String topReasonCode = topReasonCode(reasonCountByProcess.get(processCode));
        String topReasonCn = topReasonCode == null ? null : scheduleReasonNameCn(topReasonCode);
        MvpDomain.Allocation maxAllocation = maxAllocationByProcess.get(processCode);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("process_code", processCode);
        row.put("target_qty", targetQty);
        row.put("max_allocation_qty", round2(maxAllocationQtyByProcess.getOrDefault(processCode, 0d)));
        row.put("scheduled_qty", scheduledQty);
        row.put("unscheduled_qty", unscheduledQty);
        row.put("schedule_rate", scheduleRate);
        row.put("allocation_count", allocationCountByProcess.getOrDefault(processCode, 0));
        row.put("order_count", orderCount);
        row.put("top_block_reason_code", topReasonCode);
        row.put("top_block_reason_cn", topReasonCn);
        if (maxAllocation != null) {
          int workersAvailable = workersAvailableByShiftProcess.getOrDefault(
            maxAllocation.date + "#" + maxAllocation.shiftCode + "#" + processCode,
            0
          );
          int machinesAvailable = machinesAvailableByShiftProcess.getOrDefault(
            maxAllocation.date + "#" + maxAllocation.shiftCode + "#" + processCode,
            0
          );
          double materialAvailable = materialAvailableByShiftProductProcess.getOrDefault(
            maxAllocation.date + "#" + maxAllocation.shiftCode + "#" + maxAllocation.productCode + "#" + processCode,
            0d
          );
          MvpDomain.ProcessConfig processConfig = processConfigByCode.get(processCode);
          double resourceCapacity = estimateResourceCapacity(processConfig, workersAvailable, machinesAvailable);
          MvpDomain.ScheduleTask task = taskByTaskKey.get(maxAllocation.taskKey);
          double taskTargetQty = task == null ? 0d : task.targetQty;
          double producedBeforeShift = calcTaskProducedBeforeShift(
            maxAllocation,
            allocationsByTaskKey.get(maxAllocation.taskKey),
            shiftIndexByShiftId
          );
          double remainingBeforeShift = Math.max(0d, taskTargetQty - producedBeforeShift);

          row.put("max_allocation_task_key", maxAllocation.taskKey);
          row.put("max_allocation_order_no", maxAllocation.orderNo);
          row.put("max_allocation_product_code", maxAllocation.productCode);
          row.put("max_allocation_date", maxAllocation.date);
          row.put("max_allocation_shift_code", maxAllocation.shiftCode);
          row.put("max_allocation_shift_name_cn", shiftNameCn(maxAllocation.shiftCode));
          row.put("max_allocation_workers_used", maxAllocation.workersUsed);
          row.put("max_allocation_machines_used", maxAllocation.machinesUsed);
          row.put("max_allocation_groups_used", maxAllocation.groupsUsed);
          row.put("max_allocation_workers_available", workersAvailable);
          row.put("max_allocation_machines_available", machinesAvailable);
          row.put("max_allocation_resource_capacity", round2(resourceCapacity));
          row.put("max_allocation_material_available", round2(materialAvailable));
          row.put("max_allocation_task_target_qty", round2(taskTargetQty));
          row.put("max_allocation_task_remaining_before_shift", round2(remainingBeforeShift));
          row.put("max_allocation_task_total_scheduled", round2(totalScheduledByTaskKey.getOrDefault(maxAllocation.taskKey, 0d)));
          row.put(
            "max_allocation_explain_cn",
            buildMaxAllocationExplainCn(
              processCode,
              maxAllocation,
              remainingBeforeShift,
              resourceCapacity,
              materialAvailable
            )
          );
        }
        row.put(
          "explain_cn",
          buildProcessAllocationExplainCn(processCode, targetQty, scheduledQty, unscheduledQty, orderCount, topReasonCode)
        );
        processSummary.add(localizeRow(row));
      }

      List<Map<String, Object>> unscheduledReasonSummary = reasonCount.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .map(entry -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("reason_code", entry.getKey());
          row.put("reason_name_cn", scheduleReasonNameCn(entry.getKey()));
          row.put("count", entry.getValue());
          return row;
        })
        .toList();

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("request_id", requestId);
      out.put("version_no", schedule.versionNo);
      out.put("status", schedule.status);
      out.put("status_name_cn", statusNameCn(schedule.status));
      out.put("rule_version_no", schedule.ruleVersionNo);
      out.put("created_at", schedule.createdAt == null ? null : schedule.createdAt.toString());
      out.put("publish_time", schedule.publishTime == null ? null : schedule.publishTime.toString());
      out.put("strategy_code", strategyCode);
      out.put("strategy_name_cn", strategyNameCn);
      out.put("summary", summary);
      out.put("logic", logic);
      out.put("priority_preview", priorityPreview);
      out.put("process_summary", processSummary);
      out.put("unscheduled_reason_summary", unscheduledReasonSummary);
      out.put("unscheduled_samples", unscheduledSamples);
      out.put("metadata", deepCopyMap(schedule.metadata));
      out.put("metrics", deepCopyMap(schedule.metrics));
      return out;
    }
  }

  public Map<String, Object> getVersionDiff(String versionNo, String compareWith, String requestId) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion base = getScheduleEntity(compareWith);
      MvpDomain.ScheduleVersion current = getScheduleEntity(versionNo);
      Map<String, Double> a = new HashMap<>();
      Map<String, Double> b = new HashMap<>();
      for (MvpDomain.Allocation row : base.allocations) {
        a.put(row.taskKey + "#" + row.shiftId, row.scheduledQty);
      }
      for (MvpDomain.Allocation row : current.allocations) {
        b.put(row.taskKey + "#" + row.shiftId, row.scheduledQty);
      }
      Set<String> allTaskKeys = new HashSet<>(a.keySet());
      allTaskKeys.addAll(b.keySet());
      int changedTaskCount = 0;
      for (String key : allTaskKeys) {
        if (Math.abs(a.getOrDefault(key, 0d) - b.getOrDefault(key, 0d)) > 1e-9) {
          changedTaskCount += 1;
        }
      }
      Set<String> baseOrders = new HashSet<>();
      Set<String> currentOrders = new HashSet<>();
      for (MvpDomain.Allocation row : base.allocations) {
        baseOrders.add(row.orderNo);
      }
      for (MvpDomain.Allocation row : current.allocations) {
        currentOrders.add(row.orderNo);
      }
      Set<String> allOrders = new HashSet<>(baseOrders);
      allOrders.addAll(currentOrders);
      int changedOrderCount = 0;
      for (String orderNo : allOrders) {
        if (baseOrders.contains(orderNo) != currentOrders.contains(orderNo)) {
          changedOrderCount += 1;
        }
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("request_id", requestId);
      out.put("base_version_no", compareWith);
      out.put("compare_version_no", versionNo);
      out.put("changed_order_count", changedOrderCount);
      out.put("changed_task_count", changedTaskCount);
      out.put("delayed_order_delta", current.unscheduled.size() - base.unscheduled.size());
      out.put("overloaded_process_delta", countCapacityBlocked(current.unscheduled) - countCapacityBlocked(base.unscheduled));
      return out;
    }
  }

  public List<Map<String, Object>> listSalesOrderLines() {
    synchronized (lock) {
      List<Map<String, Object>> erpRows = erpDataManager.getSalesOrderLines();
      if (!erpRows.isEmpty()) {
        return erpRows;
      }
      List<Map<String, Object>> rows = new ArrayList<>();
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      for (MvpDomain.Order order : state.orders) {
        MvpDomain.OrderBusinessData business = businessData(order);
        for (int i = 0; i < order.items.size(); i += 1) {
          MvpDomain.OrderItem item = order.items.get(i);
          rows.add(Map.of(
            "sales_order_no",
            (business.salesOrderNo == null || business.salesOrderNo.isBlank()) ? "SO-" + order.orderNo : business.salesOrderNo,
            "line_no", String.valueOf(i + 1),
            "product_code", item.productCode,
            "order_qty", item.qty,
            "order_date", toDateTime((business.orderDate == null ? order.dueDate.minusDays(2) : business.orderDate).toString(), "D", true),
            "expected_due_date", toDateTime(order.dueDate.toString(), "D", true),
            "requested_ship_date", toDateTime(order.dueDate.toString(), "N", true),
            "urgent_flag", order.urgent ? 1 : 0,
            "order_status", order.status,
            "last_update_time", now
          ));
        }
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listErpSalesOrderHeadersRaw() {
    synchronized (lock) {
      return erpDataManager.getSalesOrderHeadersRaw();
    }
  }

  public List<Map<String, Object>> listErpSalesOrderLinesRaw() {
    synchronized (lock) {
      return erpDataManager.getSalesOrderLinesRaw();
    }
  }

  public List<Map<String, Object>> listPlanOrders() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        MvpDomain.OrderBusinessData business = businessData(order);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("plan_order_no", "PO-" + order.orderNo);
        row.put("source_sales_order_no", business.salesOrderNo == null || business.salesOrderNo.isBlank()
          ? "SO-" + order.orderNo
          : business.salesOrderNo);
        row.put("source_line_no", "1");
        row.put("release_type", "PRODUCTION");
        row.put("release_status", "RELEASED");
        row.put("release_time", now);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listPurchaseOrders() {
    synchronized (lock) {
      return erpDataManager.getPurchaseOrders();
    }
  }

  public List<Map<String, Object>> listProductionOrders() {
    synchronized (lock) {
      List<Map<String, Object>> erpRows = erpDataManager.getProductionOrders();
      if (!erpRows.isEmpty()) {
        return erpRows;
      }
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        MvpDomain.OrderBusinessData business = businessData(order);
        double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
        LocalDate expectedStartDate = resolveExpectedStartDate(order);
        String expectedStartTime = expectedStartDate == null ? null : toDateTime(expectedStartDate.toString(), "D", true);
        String expectedFinishTime = estimateExpectedFinishTime(order);
        LocalDate expectedFinishDate = parseLocalDateFlexible(expectedFinishTime, null);
        String productCode = order.items.isEmpty() ? "UNKNOWN" : normalizeCode(order.items.get(0).productCode);
        List<Map<String, Object>> processContexts = buildProcessContextsForProduct(productCode);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("production_order_no", order.orderNo);
        row.put("source_sales_order_no", business.salesOrderNo == null || business.salesOrderNo.isBlank()
          ? "SO-" + order.orderNo
          : business.salesOrderNo);
        row.put("source_line_no", "1");
        row.put("source_plan_order_no", "PO-" + order.orderNo);
        row.put("material_list_no", "ML-" + order.orderNo);
        row.put("product_code", order.items.isEmpty() ? "UNKNOWN" : order.items.get(0).productCode);
        row.put("product_name_cn", business.productName);
        row.put("workshop_codes", joinContextValues(processContexts, "workshop_codes"));
        row.put("line_codes", joinContextValues(processContexts, "line_codes"));
        row.put("process_codes", joinContextValues(processContexts, "process_code"));
        row.put("process_route_summary", summarizeProcessContexts(processContexts));
        row.put("process_contexts", processContexts);
        row.put("plan_qty", totalQty);
        row.put("production_status", order.status);
        row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
        row.put("expected_start_time", expectedStartTime);
        row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
        row.put("expected_finish_time", expectedFinishTime);
        row.put("order_date", business.orderDate == null ? null : business.orderDate.toString());
        row.put("customer_remark", business.customerRemark);
        row.put("spec_model", business.specModel);
        row.put("production_batch_no", business.productionBatchNo);
        row.put("planned_finish_date_1", business.plannedFinishDate1);
        row.put("planned_finish_date_2", business.plannedFinishDate2);
        row.put("production_date_foreign_trade", business.productionDateForeignTrade);
        row.put("packaging_form", business.packagingForm);
        row.put("sales_order_no", business.salesOrderNo);
        row.put("purchase_due_date", business.purchaseDueDate);
        row.put("injection_due_date", business.injectionDueDate);
        row.put("market_remark_info", business.marketRemarkInfo);
        row.put("market_demand", business.marketDemand);
        row.put("semi_finished_code", business.semiFinishedCode);
        row.put("semi_finished_inventory", business.semiFinishedInventory);
        row.put("semi_finished_demand", business.semiFinishedDemand);
        row.put("semi_finished_wip", business.semiFinishedWip);
        row.put("need_order_qty", business.needOrderQty);
        row.put("pending_inbound_qty", business.pendingInboundQty);
        row.put("weekly_monthly_process_plan", business.weeklyMonthlyPlanRemark);
        row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
        row.put("note", business.note);
        row.put("workshop_completed_qty", business.workshopCompletedQty);
        row.put("workshop_completed_time", business.workshopCompletedTime);
        row.put("outer_completed_qty", business.outerCompletedQty);
        row.put("outer_completed_time", business.outerCompletedTime);
        row.put("match_status", business.matchStatus);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listErpProductionOrderHeadersRaw() {
    synchronized (lock) {
      return erpDataManager.getProductionOrderHeadersRaw();
    }
  }

  public List<Map<String, Object>> listErpProductionOrderLinesRaw() {
    synchronized (lock) {
      return erpDataManager.getProductionOrderLinesRaw();
    }
  }

  public List<Map<String, Object>> listWorkshopWeeklyPlanRows() {
    return listWorkshopWeeklyPlanRows(null);
  }

  public List<Map<String, Object>> listWorkshopWeeklyPlanRows(String versionNo) {
    synchronized (lock) {
      ReportVersionBinding binding = resolveReportVersionBinding(versionNo);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        if (binding.orderNos != null && !binding.orderNos.contains(order.orderNo)) {
          continue;
        }
        MvpDomain.OrderBusinessData business = businessData(order);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("production_order_no", order.orderNo);
        row.put("customer_remark", business.customerRemark);
        row.put("product_name", business.productName);
        row.put("spec_model", business.specModel);
        row.put("production_batch_no", business.productionBatchNo);
        row.put("order_qty", order.items.stream().mapToDouble(item -> item.qty).sum());
        row.put("packaging_form", business.packagingForm);
        row.put("sales_order_no", business.salesOrderNo);
        row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
        row.put("process_schedule_remark", business.weeklyMonthlyPlanRemark);
        row.put("schedule_version_no", binding.versionNo);
        row.put("schedule_version_status", binding.status);
        row.put("schedule_version_status_name_cn", statusNameCn(binding.status));
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listWorkshopMonthlyPlanRows() {
    return listWorkshopMonthlyPlanRows(null);
  }

  public List<Map<String, Object>> listWorkshopMonthlyPlanRows(String versionNo) {
    synchronized (lock) {
      ReportVersionBinding binding = resolveReportVersionBinding(versionNo);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        if (binding.orderNos != null && !binding.orderNos.contains(order.orderNo)) {
          continue;
        }
        MvpDomain.OrderBusinessData business = businessData(order);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_date", business.orderDate == null ? null : business.orderDate.toString());
        row.put("production_order_no", order.orderNo);
        row.put("customer_remark", business.customerRemark);
        row.put("product_name", business.productName);
        row.put("spec_model", business.specModel);
        row.put("production_batch_no", business.productionBatchNo);
        row.put("planned_finish_date_2", business.plannedFinishDate2);
        row.put("production_date_foreign_trade", business.productionDateForeignTrade);
        row.put("order_qty", order.items.stream().mapToDouble(item -> item.qty).sum());
        row.put("packaging_form", business.packagingForm);
        row.put("sales_order_no", business.salesOrderNo);
        row.put("purchase_due_date", business.purchaseDueDate);
        row.put("injection_due_date", business.injectionDueDate);
        row.put("market_remark_info", business.marketRemarkInfo);
        row.put("market_demand", business.marketDemand);
        row.put("planned_finish_date_1", business.plannedFinishDate1);
        row.put("semi_finished_code", business.semiFinishedCode);
        row.put("semi_finished_inventory", business.semiFinishedInventory);
        row.put("semi_finished_demand", business.semiFinishedDemand);
        row.put("semi_finished_wip", business.semiFinishedWip);
        row.put("need_order_qty", business.needOrderQty);
        row.put("pending_inbound_qty", business.pendingInboundQty);
        row.put("weekly_monthly_process_plan", business.weeklyMonthlyPlanRemark);
        row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
        row.put("note", business.note);
        row.put("workshop_completed_qty", business.workshopCompletedQty);
        row.put("workshop_completed_time", business.workshopCompletedTime);
        row.put("outer_completed_qty", business.outerCompletedQty);
        row.put("outer_completed_time", business.outerCompletedTime);
        row.put("match_status", business.matchStatus);
        row.put("schedule_version_no", binding.versionNo);
        row.put("schedule_version_status", binding.status);
        row.put("schedule_version_status_name_cn", statusNameCn(binding.status));
        rows.add(row);
      }
      return rows;
    }
  }

  public byte[] exportWorkshopWeeklyPlanXlsx() {
    return exportWorkshopWeeklyPlanXlsx(null);
  }

  public byte[] exportWorkshopWeeklyPlanXlsx(String versionNo) {
    synchronized (lock) {
      try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet(WEEKLY_PLAN_SHEET_NAME);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);

        Row titleRow = sheet.createRow(0);
        writeCell(titleRow, 0, WEEKLY_PLAN_TITLE_CN, titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, WEEKLY_PLAN_HEADERS_CN.length - 1));

        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < WEEKLY_PLAN_HEADERS_CN.length; i += 1) {
          writeCell(headerRow, i, WEEKLY_PLAN_HEADERS_CN[i], headerStyle);
        }

        List<Map<String, Object>> rows = listWorkshopWeeklyPlanRows(versionNo);
        for (int i = 0; i < rows.size(); i += 1) {
          Row dataRow = sheet.createRow(i + 2);
          writeRowValues(dataRow, rows.get(i), WEEKLY_PLAN_KEYS, bodyStyle);
        }

        for (int i = 0; i < WEEKLY_PLAN_HEADERS_CN.length; i += 1) {
          sheet.setColumnWidth(i, 18 * 256);
        }
        for (String extraSheetName : WEEKLY_PLAN_EXTRA_SHEETS) {
          workbook.createSheet(extraSheetName);
        }
        workbook.write(output);
        return output.toByteArray();
      } catch (IOException ex) {
        throw new MvpServiceException(500, "EXPORT_FAILED", "Failed to export workshop weekly plan.", true);
      }
    }
  }

  public byte[] exportWorkshopMonthlyPlanXls() {
    return exportWorkshopMonthlyPlanXls(null);
  }

  public byte[] exportWorkshopMonthlyPlanXls(String versionNo) {
    synchronized (lock) {
      try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet(MONTHLY_PLAN_SHEET_NAME);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);

        Row titleRow = sheet.createRow(0);
        writeCell(titleRow, 0, MONTHLY_PLAN_TITLE_CN, titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, MONTHLY_PLAN_HEADERS_CN.length - 1));

        Row groupRow = sheet.createRow(1);
        writeCell(groupRow, 0, "鐠併垹宕熼崺鐑樻拱娣団剝浼", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 13));
        writeCell(groupRow, 16, "閸楀﹥鍨氶崫浣蜂繆閹", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 16, 21));
        writeCell(groupRow, 22, "鐠併垹宕熼幒鎺嶉獓娣団剝浼", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 22, 24));
        writeCell(groupRow, 25, "鐠併垹宕熸潻娑樺", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 25, 28));

        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < MONTHLY_PLAN_HEADERS_CN.length; i += 1) {
          writeCell(headerRow, i, MONTHLY_PLAN_HEADERS_CN[i], headerStyle);
        }

        List<Map<String, Object>> rows = listWorkshopMonthlyPlanRows(versionNo);
        for (int i = 0; i < rows.size(); i += 1) {
          Row dataRow = sheet.createRow(i + 3);
          writeRowValues(dataRow, rows.get(i), MONTHLY_PLAN_KEYS, bodyStyle);
        }

        for (int i = 0; i < MONTHLY_PLAN_HEADERS_CN.length; i += 1) {
          sheet.setColumnWidth(i, 15 * 256);
        }
        workbook.write(output);
        return output.toByteArray();
      } catch (IOException ex) {
        throw new MvpServiceException(500, "EXPORT_FAILED", "Failed to export workshop monthly plan.", true);
      }
    }
  }

  public List<Map<String, Object>> listScheduleControls() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("order_type", order.orderType);
        row.put("review_passed_flag", 1);
        row.put("frozen_flag", order.frozen ? 1 : 0);
        row.put("schedulable_flag", "CLOSED".equals(order.status) ? 0 : 1);
        row.put("close_flag", "CLOSED".equals(order.status) ? 1 : 0);
        row.put("promised_due_date", toDateTime(order.dueDate.toString(), "D", true));
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listMrpLinks() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("order_type", order.orderType);
        row.put("mrp_run_id", "MRP-" + order.orderNo);
        row.put("run_time", now);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listDeliveryProgress() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("order_type", order.orderType);
        row.put("warehoused_qty", order.items.stream().mapToDouble(item -> item.completedQty).sum());
        row.put("shipped_qty", 0d);
        row.put("delivery_status", "IN_PROGRESS");
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listMaterialAvailability() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.MaterialRow rowData : state.materialAvailability) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("material_code", rowData.productCode);
        row.put("order_no", "");
        row.put("process_code", rowData.processCode);
        row.put("available_qty", rowData.availableQty);
        row.put("available_time", toDateTime(rowData.date.toString(), rowData.shiftCode, true));
        row.put("ready_flag", rowData.availableQty > 0 ? 1 : 0);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listEquipments() {
    synchronized (lock) {
      List<Map<String, Object>> rows = new ArrayList<>();
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      Map<String, Integer> maxCountByProcess = new LinkedHashMap<>();
      for (MvpDomain.ResourceRow row : state.machinePools) {
        String processCode = normalizeCode(row.processCode);
        int current = maxCountByProcess.getOrDefault(processCode, 0);
        maxCountByProcess.put(processCode, Math.max(current, Math.max(1, row.available)));
      }

      for (Map.Entry<String, Integer> entry : maxCountByProcess.entrySet()) {
        String processCode = entry.getKey();
        int count = entry.getValue();
        List<MvpDomain.LineProcessBinding> lineBindings = lineBindingsForProcess(processCode, true);
        if (lineBindings.isEmpty()) {
          lineBindings = List.of(defaultLineBindingForProcess(processCode));
        }
        for (int i = 1; i <= count; i += 1) {
          MvpDomain.LineProcessBinding binding = lineBindings.get((i - 1) % lineBindings.size());
          String lineCode = normalizeCode(binding.lineCode);
          String workshopCode = normalizeCode(binding.workshopCode);
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("equipment_code", lineCode + "-" + processCode + "-EQ-" + i);
          row.put("process_code", processCode);
          row.put("line_code", lineCode);
          row.put("line_name", binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim());
          row.put("workshop_code", workshopCode);
          row.put("status", "AVAILABLE");
          row.put("capacity_per_shift", 1);
          row.put("last_update_time", now);
          rows.add(localizeRow(row));
        }
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listProcessRoutes() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Map.Entry<String, List<MvpDomain.ProcessStep>> route : state.processRoutes.entrySet()) {
        for (int i = 0; i < route.getValue().size(); i += 1) {
          MvpDomain.ProcessStep step = route.getValue().get(i);
          MvpDomain.ProcessConfig config = state.processes.stream()
            .filter(process -> process.processCode.equals(step.processCode))
            .findFirst()
            .orElse(null);
          rows.add(localizeRow(Map.of(
            "route_no", "ROUTE-" + route.getKey(),
            "product_code", route.getKey(),
            "process_code", step.processCode,
            "sequence_no", i + 1,
            "dependency_type", step.dependencyType,
            "capacity_per_shift", config == null ? 0 : config.capacityPerShift,
            "required_manpower_per_group", config == null ? 0 : config.requiredWorkers,
            "required_equipment_count", config == null ? 0 : config.requiredMachines,
            "enabled_flag", 1,
            "last_update_time", now
          )));
        }
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listEquipmentProcessCapabilities() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.ProcessConfig process : state.processes) {
        List<MvpDomain.LineProcessBinding> lineBindings = lineBindingsForProcess(process.processCode, false);
        if (lineBindings.isEmpty()) {
          lineBindings = List.of(defaultLineBindingForProcess(process.processCode));
        }
        for (MvpDomain.LineProcessBinding binding : lineBindings) {
          String lineCode = normalizeCode(binding.lineCode);
          String workshopCode = normalizeCode(binding.workshopCode);
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("equipment_code", lineCode + "-" + process.processCode + "-EQ-1");
          row.put("process_code", process.processCode);
          row.put("line_code", lineCode);
          row.put("line_name", binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim());
          row.put("workshop_code", workshopCode);
          row.put("enabled_flag", binding.enabled ? 1 : 0);
          row.put("capacity_factor", 1);
          row.put("last_update_time", now);
          rows.add(localizeRow(row));
        }
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listEmployeeSkills() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.ProcessConfig process : state.processes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("employee_id", process.processCode + "-EMP-1");
        row.put("process_code", process.processCode);
        row.put("skill_level", "INDEPENDENT");
        row.put("efficiency_factor", 1.0);
        row.put("active_flag", 1);
        row.put("last_update_time", now);
        rows.add(localizeRow(row));
      }
      return rows;
    }
  }

  public List<Map<String, Object>> listShiftCalendar() {
    synchronized (lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.ShiftRow rowData : state.shiftCalendar) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("calendar_date", rowData.date.toString());
        row.put("shift_code", "D".equals(rowData.shiftCode) ? "DAY" : "NIGHT");
        row.put("shift_start_time", toDateTime(rowData.date.toString(), rowData.shiftCode, true));
        row.put("shift_end_time", toDateTime(rowData.date.toString(), rowData.shiftCode, false));
        row.put("open_flag", rowData.open ? 1 : 0);
        row.put("workshop_code", workshopCodesSummaryAll());
        row.put("last_update_time", now);
        rows.add(localizeRow(row));
      }
      return rows;
    }
  }

  public Map<String, Object> getMasterdataConfig(String requestId) {
    synchronized (lock) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("request_id", requestId);
      out.put("horizon_start_date", state.startDate == null ? null : state.startDate.toString());
      out.put("horizon_days", state.horizonDays);
      out.put("shifts_per_day", state.shiftsPerDay);
      out.put("shift_hours", state.shiftHours);
      out.put("skip_statutory_holidays", state.skipStatutoryHolidays);
      out.put("weekend_rest_mode", normalizeWeekendRestMode(state.weekendRestMode));
      out.put(
        "date_shift_mode_by_date",
        state.dateShiftModeByDate == null ? Map.of() : new LinkedHashMap<>(state.dateShiftModeByDate)
      );
      out.put("process_configs", listProcessConfigRowsForEdit());
      out.put("line_topology", listLineTopologyRowsForEdit());
      out.put("section_leader_bindings", listSectionLeaderBindingsRowsForEdit());
      out.put("resource_pool", listResourcePoolRowsForEdit());
      out.put("initial_carryover_occupancy", listInitialCarryoverRowsForEdit());
      out.put("material_availability", listMaterialAvailabilityRowsForEdit());
      return out;
    }
  }

  public Map<String, Object> saveMasterdataConfig(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      int updatedRowCount = 0;

      boolean hasProcessConfigs = payload.containsKey("process_configs") || payload.containsKey("processConfigs");
      List<Map<String, Object>> processConfigs = maps(payload.get("process_configs"));
      if (processConfigs.isEmpty()) {
        processConfigs = maps(payload.get("processConfigs"));
      }
      if (hasProcessConfigs) {
        applyProcessConfigPatch(processConfigs);
        updatedRowCount += processConfigs.size();
      }

      boolean hasLineTopology = payload.containsKey("line_topology") || payload.containsKey("lineTopology");
      List<Map<String, Object>> lineTopologyRows = maps(payload.get("line_topology"));
      if (lineTopologyRows.isEmpty()) {
        lineTopologyRows = maps(payload.get("lineTopology"));
      }
      if (hasLineTopology) {
        applyLineTopologyPatch(lineTopologyRows);
        updatedRowCount += lineTopologyRows.size();
      }

      boolean hasSectionLeaderBindings = payload.containsKey("section_leader_bindings")
        || payload.containsKey("sectionLeaderBindings");
      List<Map<String, Object>> sectionLeaderRows = maps(payload.get("section_leader_bindings"));
      if (sectionLeaderRows.isEmpty()) {
        sectionLeaderRows = maps(payload.get("sectionLeaderBindings"));
      }
      if (hasSectionLeaderBindings) {
        applySectionLeaderBindingsPatch(sectionLeaderRows);
        updatedRowCount += sectionLeaderRows.size();
      }

      boolean planningWindowUpdated = applyPlanningWindowPatch(payload);
      if (planningWindowUpdated) {
        updatedRowCount += 1;
      }
      boolean planningCalendarUpdated = applyPlanningCalendarPatch(payload);
      if (planningCalendarUpdated) {
        updatedRowCount += 1;
      }

      boolean hasResourcePool = payload.containsKey("resource_pool") || payload.containsKey("resourcePool");
      List<Map<String, Object>> resourcePool = maps(payload.get("resource_pool"));
      if (resourcePool.isEmpty()) {
        resourcePool = maps(payload.get("resourcePool"));
      }
      if (hasResourcePool) {
        applyResourcePoolPatch(resourcePool);
        updatedRowCount += resourcePool.size();
      }

      boolean hasInitialCarryoverRows = payload.containsKey("initial_carryover_occupancy")
        || payload.containsKey("initialCarryoverOccupancy");
      List<Map<String, Object>> initialCarryoverRows = maps(payload.get("initial_carryover_occupancy"));
      if (initialCarryoverRows.isEmpty()) {
        initialCarryoverRows = maps(payload.get("initialCarryoverOccupancy"));
      }
      if (hasInitialCarryoverRows) {
        applyInitialCarryoverPatch(initialCarryoverRows);
        updatedRowCount += initialCarryoverRows.size();
      }

      boolean hasMaterialRows = payload.containsKey("material_availability") || payload.containsKey("materialAvailability");
      List<Map<String, Object>> materialRows = maps(payload.get("material_availability"));
      if (materialRows.isEmpty()) {
        materialRows = maps(payload.get("materialAvailability"));
      }
      if (hasMaterialRows) {
        applyMaterialAvailabilityPatch(materialRows);
        updatedRowCount += materialRows.size();
      }

      appendAudit(
        "MASTERDATA",
        "CONFIG",
        "UPDATE_MASTERDATA_CONFIG",
        operator,
        requestId,
        "updated_rows="
          + updatedRowCount
          + ", planning_window_updated="
          + planningWindowUpdated
          + ", planning_calendar_updated="
          + planningCalendarUpdated
      );

      Map<String, Object> out = getMasterdataConfig(requestId);
      out.put("updated_row_count", updatedRowCount);
      out.put("message", "Masterdata config updated.");
      return out;
    }
  }

  public Map<String, Object> createMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String productCode = normalizeCode(string(payload, "product_code", string(payload, "productCode", null)));
      if (productCode.isBlank()) {
        throw badRequest("product_code is required.");
      }
      if (state.processRoutes.containsKey(productCode)) {
        throw badRequest("Route already exists for product_code: " + productCode);
      }

      List<Map<String, Object>> routeStepRows = extractRouteStepRows(payload);
      List<MvpDomain.ProcessStep> steps = parseRouteSteps(routeStepRows);
      upsertProcessRoute(productCode, steps);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + productCode,
        "CREATE_PROCESS_ROUTE",
        operator,
        requestId,
        "product_code=" + productCode + ", step_count=" + steps.size()
      );

      return buildRouteMutationResult(requestId, productCode, "Process route created.", false);
    }
  }

  public Map<String, Object> updateMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String productCode = normalizeCode(string(payload, "product_code", string(payload, "productCode", null)));
      if (productCode.isBlank()) {
        throw badRequest("product_code is required.");
      }
      if (!state.processRoutes.containsKey(productCode)) {
        throw notFound("Route not found for product_code: " + productCode);
      }

      List<Map<String, Object>> routeStepRows = extractRouteStepRows(payload);
      List<MvpDomain.ProcessStep> steps = parseRouteSteps(routeStepRows);
      upsertProcessRoute(productCode, steps);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + productCode,
        "UPDATE_PROCESS_ROUTE",
        operator,
        requestId,
        "product_code=" + productCode + ", step_count=" + steps.size()
      );

      return buildRouteMutationResult(requestId, productCode, "Process route updated.", false);
    }
  }

  public Map<String, Object> copyMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String sourceProductCode = normalizeCode(string(
        payload,
        "source_product_code",
        string(payload, "sourceProductCode", string(payload, "product_code", string(payload, "productCode", null)))
      ));
      String targetProductCode = normalizeCode(string(
        payload,
        "target_product_code",
        string(payload, "targetProductCode", string(payload, "new_product_code", string(payload, "newProductCode", null)))
      ));
      if (sourceProductCode.isBlank()) {
        throw badRequest("source_product_code is required.");
      }
      if (targetProductCode.isBlank()) {
        throw badRequest("target_product_code is required.");
      }
      if (sourceProductCode.equals(targetProductCode)) {
        throw badRequest("source_product_code and target_product_code cannot be the same.");
      }

      List<MvpDomain.ProcessStep> sourceRoute = state.processRoutes.get(sourceProductCode);
      if (sourceRoute == null || sourceRoute.isEmpty()) {
        throw notFound("Route not found for source_product_code: " + sourceProductCode);
      }

      boolean overwrite = bool(payload, "overwrite", false);
      if (state.processRoutes.containsKey(targetProductCode) && !overwrite) {
        throw badRequest("Route already exists for target_product_code: " + targetProductCode + ". Set overwrite=true to replace.");
      }

      List<Map<String, Object>> routeStepRows = extractRouteStepRows(payload);
      List<MvpDomain.ProcessStep> steps = routeStepRows.isEmpty()
        ? cloneProcessSteps(sourceRoute)
        : parseRouteSteps(routeStepRows);
      upsertProcessRoute(targetProductCode, steps);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + targetProductCode,
        "COPY_PROCESS_ROUTE",
        operator,
        requestId,
        "source_product_code=" + sourceProductCode + ", target_product_code=" + targetProductCode + ", step_count=" + steps.size()
      );

      Map<String, Object> out = buildRouteMutationResult(requestId, targetProductCode, "Process route copied.", false);
      out.put("source_product_code", sourceProductCode);
      return out;
    }
  }

  public Map<String, Object> deleteMasterdataRoute(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    synchronized (lock) {
      String productCode = normalizeCode(string(payload, "product_code", string(payload, "productCode", null)));
      if (productCode.isBlank()) {
        throw badRequest("product_code is required.");
      }
      if (!state.processRoutes.containsKey(productCode)) {
        throw notFound("Route not found for product_code: " + productCode);
      }

      Map<String, List<MvpDomain.ProcessStep>> nextRoutes = mutableProcessRoutesCopy();
      nextRoutes.remove(productCode);
      state.processRoutes = sortProcessRoutes(nextRoutes);
      rebuildMasterdataHorizonWindow();

      appendAudit(
        "MASTERDATA",
        "ROUTE-" + productCode,
        "DELETE_PROCESS_ROUTE",
        operator,
        requestId,
        "product_code=" + productCode
      );

      return buildRouteMutationResult(requestId, productCode, "Process route deleted.", true);
    }
  }

  private Map<String, Object> buildRouteMutationResult(
    String requestId,
    String productCode,
    String message,
    boolean deleted
  ) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("message", message);
    out.put("product_code", productCode);
    out.put("route_no", "ROUTE-" + productCode);
    out.put("deleted", deleted);

    List<MvpDomain.ProcessStep> steps = state.processRoutes.getOrDefault(productCode, List.of());
    List<Map<String, Object>> stepRows = new ArrayList<>();
    for (int i = 0; i < steps.size(); i += 1) {
      MvpDomain.ProcessStep step = steps.get(i);
      stepRows.add(localizeRow(Map.of(
        "route_no", "ROUTE-" + productCode,
        "product_code", productCode,
        "process_code", step.processCode,
        "sequence_no", i + 1,
        "dependency_type", step.dependencyType
      )));
    }
    out.put("steps", stepRows);
    out.put("route_count", state.processRoutes.size());
    return out;
  }

  private List<Map<String, Object>> extractRouteStepRows(Map<String, Object> payload) {
    List<Map<String, Object>> rows = maps(payload == null ? null : payload.get("steps"));
    if (rows.isEmpty()) {
      rows = maps(payload == null ? null : payload.get("process_steps"));
    }
    if (rows.isEmpty()) {
      rows = maps(payload == null ? null : payload.get("route_steps"));
    }
    return rows;
  }

  private List<MvpDomain.ProcessStep> parseRouteSteps(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      throw badRequest("steps is required and cannot be empty.");
    }

    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      validProcessCodes.add(normalizeCode(process.processCode));
    }

    List<Map<String, Object>> normalizedRows = new ArrayList<>();
    for (int i = 0; i < rows.size(); i += 1) {
      Map<String, Object> row = rows.get(i);
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw badRequest("process_code is required in steps.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw badRequest("Unknown process_code in steps: " + processCode);
      }
      String dependencyType = normalizeRouteDependencyType(
        string(row, "dependency_type", string(row, "dependencyType", "FS"))
      );
      double sequenceNo = number(row, "sequence_no", number(row, "sequenceNo", i + 1d));

      Map<String, Object> normalized = new LinkedHashMap<>();
      normalized.put("process_code", processCode);
      normalized.put("dependency_type", dependencyType);
      normalized.put("sequence_no", sequenceNo);
      normalized.put("input_index", i);
      normalizedRows.add(normalized);
    }

    normalizedRows.sort((a, b) -> {
      int bySequence = Double.compare(number(a, "sequence_no", 0d), number(b, "sequence_no", 0d));
      if (bySequence != 0) {
        return bySequence;
      }
      return Integer.compare((int) number(a, "input_index", 0d), (int) number(b, "input_index", 0d));
    });

    Set<String> uniqueProcessCodes = new HashSet<>();
    List<MvpDomain.ProcessStep> steps = new ArrayList<>();
    for (Map<String, Object> row : normalizedRows) {
      String processCode = normalizeCode(string(row, "process_code", null));
      if (!uniqueProcessCodes.add(processCode)) {
        throw badRequest("Duplicate process_code in steps: " + processCode);
      }
      steps.add(new MvpDomain.ProcessStep(
        processCode,
        normalizeRouteDependencyType(string(row, "dependency_type", "FS"))
      ));
    }
    return steps;
  }

  private String normalizeRouteDependencyType(String dependencyType) {
    String normalized = normalizeCode(dependencyType);
    if (normalized.isBlank()) {
      return "FS";
    }
    if ("FS".equals(normalized) || "SS".equals(normalized)) {
      return normalized;
    }
    throw badRequest("dependency_type must be FS or SS.");
  }

  private void upsertProcessRoute(String productCode, List<MvpDomain.ProcessStep> steps) {
    Map<String, List<MvpDomain.ProcessStep>> nextRoutes = mutableProcessRoutesCopy();
    nextRoutes.put(productCode, cloneProcessSteps(steps));
    state.processRoutes = sortProcessRoutes(nextRoutes);
  }

  private Map<String, List<MvpDomain.ProcessStep>> mutableProcessRoutesCopy() {
    Map<String, List<MvpDomain.ProcessStep>> out = new LinkedHashMap<>();
    for (Map.Entry<String, List<MvpDomain.ProcessStep>> entry : state.processRoutes.entrySet()) {
      out.put(entry.getKey(), cloneProcessSteps(entry.getValue()));
    }
    return out;
  }

  private List<MvpDomain.ProcessStep> cloneProcessSteps(List<MvpDomain.ProcessStep> source) {
    List<MvpDomain.ProcessStep> out = new ArrayList<>();
    if (source == null) {
      return out;
    }
    for (MvpDomain.ProcessStep step : source) {
      out.add(new MvpDomain.ProcessStep(step.processCode, step.dependencyType));
    }
    return out;
  }

  private Map<String, List<MvpDomain.ProcessStep>> sortProcessRoutes(Map<String, List<MvpDomain.ProcessStep>> routes) {
    List<String> products = new ArrayList<>(routes.keySet());
    products.sort(String::compareTo);
    Map<String, List<MvpDomain.ProcessStep>> ordered = new LinkedHashMap<>();
    for (String productCode : products) {
      ordered.put(productCode, cloneProcessSteps(routes.get(productCode)));
    }
    return ordered;
  }

  private List<Map<String, Object>> listProcessConfigRowsForEdit() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("process_code", process.processCode);
      row.put("capacity_per_shift", round2(process.capacityPerShift));
      row.put("required_workers", process.requiredWorkers);
      row.put("required_machines", process.requiredMachines);
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code"))));
    return rows;
  }

  private List<Map<String, Object>> listLineTopologyRowsForEdit() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String workshopCode = normalizeCode(binding.workshopCode);
      String lineCode = normalizeCode(binding.lineCode);
      String lineName = binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim();
      String processCode = normalizeCode(binding.processCode);
      if (workshopCode.isBlank() || lineCode.isBlank() || processCode.isBlank()) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("workshop_code", workshopCode);
      row.put("line_code", lineCode);
      row.put("line_name", lineName);
      row.put("process_code", processCode);
      row.put("enabled_flag", binding.enabled ? 1 : 0);
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> {
      int byWorkshop = String.valueOf(a.get("workshop_code")).compareTo(String.valueOf(b.get("workshop_code")));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      int byLine = String.valueOf(a.get("line_code")).compareTo(String.valueOf(b.get("line_code")));
      if (byLine != 0) {
        return byLine;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }

  private List<Map<String, Object>> listSectionLeaderBindingsRowsForEdit() {
    Map<String, String> lineNameByCode = new HashMap<>();
    Map<String, String> workshopByLineCode = new HashMap<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String lineCode = normalizeCode(binding.lineCode);
      if (lineCode.isBlank()) {
        continue;
      }
      String lineName = binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim();
      lineNameByCode.putIfAbsent(lineCode, lineName);
      workshopByLineCode.putIfAbsent(lineCode, normalizeCode(binding.workshopCode));
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.SectionLeaderBinding binding : state.sectionLeaderBindings) {
      String leaderId = normalizeCode(binding.leaderId);
      String lineCode = normalizeCode(binding.lineCode);
      if (leaderId.isBlank() || lineCode.isBlank()) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("leader_id", leaderId);
      row.put("leader_name", binding.leaderName == null ? "" : binding.leaderName.trim());
      row.put("line_code", lineCode);
      row.put("line_name", lineNameByCode.getOrDefault(lineCode, lineCode));
      row.put("workshop_code", workshopByLineCode.getOrDefault(lineCode, ""));
      row.put("active_flag", binding.active ? 1 : 0);
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> {
      int byLeader = String.valueOf(a.get("leader_id")).compareTo(String.valueOf(b.get("leader_id")));
      if (byLeader != 0) {
        return byLeader;
      }
      return String.valueOf(a.get("line_code")).compareTo(String.valueOf(b.get("line_code")));
    });
    return rows;
  }

  private List<Map<String, Object>> listResourcePoolRowsForEdit() {
    Map<String, Integer> workersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.workerPools) {
      workersByKey.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> machinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.machinePools) {
      machinesByKey.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> openByDateShift = new HashMap<>();
    for (MvpDomain.ShiftRow row : state.shiftCalendar) {
      openByDateShift.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode), row.open ? 1 : 0);
    }

    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(workersByKey.keySet());
    allKeys.addAll(machinesByKey.keySet());

    List<Map<String, Object>> rows = new ArrayList<>();
    for (String key : allKeys) {
      String[] parts = key.split("#", 3);
      if (parts.length < 3) {
        continue;
      }
      String date = parts[0];
      String shiftCode = parts[1];
      String processCode = parts[2];
      if (date.isBlank() || shiftCode.isBlank() || processCode.isBlank()) {
        continue;
      }
      String dateShiftKey = date + "#" + shiftCode;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("calendar_date", date);
      row.put("shift_code", shiftCode);
      row.put("process_code", processCode);
      row.put("workers_available", workersByKey.getOrDefault(key, 0));
      row.put("machines_available", machinesByKey.getOrDefault(key, 0));
      row.put("open_flag", openByDateShift.getOrDefault(dateShiftKey, 1));
      rows.add(localizeRow(row));
    }

    rows.sort((a, b) -> {
      String aDate = String.valueOf(a.get("calendar_date"));
      String bDate = String.valueOf(b.get("calendar_date"));
      int byDate = aDate.compareTo(bDate);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(
        shiftSortIndex(String.valueOf(a.get("shift_code"))),
        shiftSortIndex(String.valueOf(b.get("shift_code")))
      );
      if (byShift != 0) {
        return byShift;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }

  private List<Map<String, Object>> listInitialCarryoverRowsForEdit() {
    Map<String, Integer> occupiedWorkersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialWorkerOccupancy) {
      occupiedWorkersByKey.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> occupiedMachinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialMachineOccupancy) {
      occupiedMachinesByKey.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }

    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(occupiedWorkersByKey.keySet());
    allKeys.addAll(occupiedMachinesByKey.keySet());

    List<Map<String, Object>> rows = new ArrayList<>();
    for (String key : allKeys) {
      String[] parts = key.split("#", 3);
      if (parts.length < 3) {
        continue;
      }
      String date = parts[0];
      String shiftCode = parts[1];
      String processCode = parts[2];
      if (date.isBlank() || shiftCode.isBlank() || processCode.isBlank()) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("calendar_date", date);
      row.put("shift_code", shiftCode);
      row.put("process_code", processCode);
      row.put("occupied_workers", Math.max(0, occupiedWorkersByKey.getOrDefault(key, 0)));
      row.put("occupied_machines", Math.max(0, occupiedMachinesByKey.getOrDefault(key, 0)));
      rows.add(localizeRow(row));
    }

    rows.sort((a, b) -> {
      String aDate = String.valueOf(a.get("calendar_date"));
      String bDate = String.valueOf(b.get("calendar_date"));
      int byDate = aDate.compareTo(bDate);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(
        shiftSortIndex(String.valueOf(a.get("shift_code"))),
        shiftSortIndex(String.valueOf(b.get("shift_code")))
      );
      if (byShift != 0) {
        return byShift;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }

  private List<Map<String, Object>> listMaterialAvailabilityRowsForEdit() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.MaterialRow rowData : state.materialAvailability) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("calendar_date", rowData.date.toString());
      row.put("shift_code", normalizeShiftCodeLabel(rowData.shiftCode));
      row.put("product_code", rowData.productCode);
      row.put("process_code", rowData.processCode);
      row.put("available_qty", round2(rowData.availableQty));
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> {
      String aDate = String.valueOf(a.get("calendar_date"));
      String bDate = String.valueOf(b.get("calendar_date"));
      int byDate = aDate.compareTo(bDate);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(
        shiftSortIndex(String.valueOf(a.get("shift_code"))),
        shiftSortIndex(String.valueOf(b.get("shift_code")))
      );
      if (byShift != 0) {
        return byShift;
      }
      int byProduct = String.valueOf(a.get("product_code")).compareTo(String.valueOf(b.get("product_code")));
      if (byProduct != 0) {
        return byProduct;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }

  private boolean applyPlanningWindowPatch(Map<String, Object> payload) {
    boolean hasStartDate = payload.containsKey("horizon_start_date") || payload.containsKey("horizonStartDate");
    boolean hasHorizonDays = payload.containsKey("horizon_days") || payload.containsKey("horizonDays");
    boolean hasShiftsPerDay = payload.containsKey("shifts_per_day") || payload.containsKey("shiftsPerDay");
    if (!hasStartDate && !hasHorizonDays && !hasShiftsPerDay) {
      return false;
    }

    LocalDate nextStartDate = state.startDate;
    if (hasStartDate) {
      String text = string(payload, "horizon_start_date", string(payload, "horizonStartDate", null));
      if (text == null || text.isBlank()) {
        throw badRequest("horizon_start_date is required.");
      }
      nextStartDate = parseConfigDate(text);
    }
    int nextHorizonDays = hasHorizonDays
      ? clampInt((int) Math.round(number(payload, "horizon_days", number(payload, "horizonDays", state.horizonDays))), 1, 90)
      : state.horizonDays;
    int nextShiftsPerDay = 2;

    if (
      Objects.equals(nextStartDate, state.startDate)
        && nextHorizonDays == state.horizonDays
        && nextShiftsPerDay == state.shiftsPerDay
    ) {
      return false;
    }

    state.startDate = nextStartDate;
    state.horizonDays = nextHorizonDays;
    state.shiftsPerDay = nextShiftsPerDay;
    rebuildMasterdataHorizonWindow();
    return true;
  }

  private boolean applyPlanningCalendarPatch(Map<String, Object> payload) {
    boolean hasSkipStatutoryHolidays = payload.containsKey("skip_statutory_holidays")
      || payload.containsKey("skipStatutoryHolidays");
    boolean hasWeekendRestMode = payload.containsKey("weekend_rest_mode") || payload.containsKey("weekendRestMode");
    boolean hasDateShiftModeByDate = payload.containsKey("date_shift_mode_by_date")
      || payload.containsKey("dateShiftModeByDate");
    if (!hasSkipStatutoryHolidays && !hasWeekendRestMode && !hasDateShiftModeByDate) {
      return false;
    }

    boolean nextSkipStatutoryHolidays = hasSkipStatutoryHolidays
      ? bool(
        payload,
        "skip_statutory_holidays",
        bool(payload, "skipStatutoryHolidays", state.skipStatutoryHolidays)
      )
      : state.skipStatutoryHolidays;
    String nextWeekendRestMode = hasWeekendRestMode
      ? normalizeWeekendRestMode(string(payload, "weekend_rest_mode", string(payload, "weekendRestMode", state.weekendRestMode)))
      : normalizeWeekendRestMode(state.weekendRestMode);
    Map<String, String> nextDateShiftModeByDate = hasDateShiftModeByDate
      ? normalizeDateShiftModeByDate(
        payload.get("date_shift_mode_by_date") != null ? payload.get("date_shift_mode_by_date") : payload.get("dateShiftModeByDate")
      )
      : state.dateShiftModeByDate == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state.dateShiftModeByDate);

    if (
      nextSkipStatutoryHolidays == state.skipStatutoryHolidays
        && Objects.equals(nextWeekendRestMode, normalizeWeekendRestMode(state.weekendRestMode))
        && Objects.equals(nextDateShiftModeByDate, state.dateShiftModeByDate)
    ) {
      return false;
    }

    state.skipStatutoryHolidays = nextSkipStatutoryHolidays;
    state.weekendRestMode = nextWeekendRestMode;
    state.dateShiftModeByDate = nextDateShiftModeByDate;
    rebuildMasterdataHorizonWindow();
    return true;
  }

  private void rebuildMasterdataHorizonWindow() {
    Map<String, Integer> workersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.workerPools) {
      workersByKey.put(row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> machinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.machinePools) {
      machinesByKey.put(row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> occupiedWorkersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialWorkerOccupancy) {
      occupiedWorkersByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Integer> occupiedMachinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialMachineOccupancy) {
      occupiedMachinesByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Double> materialByKey = new HashMap<>();
    for (MvpDomain.MaterialRow row : state.materialAvailability) {
      materialByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.productCode) + "#" + normalizeCode(row.processCode),
        row.availableQty
      );
    }

    String[] shiftCodes = {"D", "N"};
    List<MvpDomain.ShiftRow> shiftRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> workerRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> machineRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> initialWorkerRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> initialMachineRows = new ArrayList<>();
    List<MvpDomain.MaterialRow> materialRows = new ArrayList<>();

    for (int i = 0; i < Math.max(1, state.horizonDays); i += 1) {
      LocalDate date = state.startDate.plusDays(i);
      for (int s = 0; s < Math.max(1, Math.min(2, state.shiftsPerDay)); s += 1) {
        String shiftCode = shiftCodes[s];
        String dateShiftKey = date + "#" + shiftCode;
        boolean open = isShiftOpenInDateMode(shiftCode, resolveDateShiftMode(date));
        shiftRows.add(new MvpDomain.ShiftRow(date, shiftCode, open));

        for (MvpDomain.ProcessConfig process : state.processes) {
          String processCode = normalizeCode(process.processCode);
          String usageKey = dateShiftKey + "#" + processCode;
          int defaultWorkers = Math.max(
            process.requiredWorkers,
            BASE_WORKERS_BY_PROCESS.getOrDefault(processCode, Math.max(2, process.requiredWorkers * 3))
          );
          int defaultMachines = Math.max(
            process.requiredMachines,
            BASE_MACHINES_BY_PROCESS.getOrDefault(processCode, Math.max(1, process.requiredMachines * 2))
          );
          int workers = Math.max(process.requiredWorkers, workersByKey.getOrDefault(usageKey, defaultWorkers));
          int machines = Math.max(process.requiredMachines, machinesByKey.getOrDefault(usageKey, defaultMachines));
          int occupiedWorkers = clampInt(occupiedWorkersByKey.getOrDefault(usageKey, 0), 0, workers);
          int occupiedMachines = clampInt(occupiedMachinesByKey.getOrDefault(usageKey, 0), 0, machines);
          workerRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, workers));
          machineRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, machines));
          initialWorkerRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedWorkers));
          initialMachineRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedMachines));
        }

        for (Map.Entry<String, List<MvpDomain.ProcessStep>> route : state.processRoutes.entrySet()) {
          String productCode = normalizeCode(route.getKey());
          for (MvpDomain.ProcessStep step : route.getValue()) {
            String processCode = normalizeCode(step.processCode);
            String materialKey = dateShiftKey + "#" + productCode + "#" + processCode;
            double availableQty = materialByKey.getOrDefault(materialKey, 5000d);
            materialRows.add(new MvpDomain.MaterialRow(date, shiftCode, productCode, processCode, round2(Math.max(0d, availableQty))));
          }
        }
      }
    }

    state.shiftCalendar = shiftRows;
    state.workerPools = workerRows;
    state.machinePools = machineRows;
    state.initialWorkerOccupancy = initialWorkerRows;
    state.initialMachineOccupancy = initialMachineRows;
    state.materialAvailability = materialRows;
  }

  private boolean isDateInCurrentHorizon(LocalDate date) {
    if (date == null || state.startDate == null) {
      return false;
    }
    LocalDate endExclusive = state.startDate.plusDays(Math.max(1, state.horizonDays));
    return !date.isBefore(state.startDate) && date.isBefore(endExclusive);
  }

  private boolean isShiftEnabledInCurrentSetting(String shiftCode) {
    String normalized = normalizeShiftCode(shiftCode);
    if ("D".equals(normalized)) {
      return true;
    }
    return "N".equals(normalized);
  }

  private void applyProcessConfigPatch(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      throw badRequest("process_configs cannot be empty.");
    }

    Map<String, MvpDomain.ProcessConfig> processByCode = new HashMap<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      processByCode.put(normalizeCode(process.processCode), process);
    }

    Map<String, MvpDomain.ProcessConfig> nextByCode = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw badRequest("process_code is required in process_configs.");
      }
      if (nextByCode.containsKey(processCode)) {
        throw badRequest("Duplicate process_code in process_configs: " + processCode);
      }
      MvpDomain.ProcessConfig process = processByCode.get(processCode);
      double defaultCapacity = process == null ? 1d : process.capacityPerShift;
      int defaultWorkers = process == null ? 1 : Math.max(1, process.requiredWorkers);
      int defaultMachines = process == null ? 1 : Math.max(1, process.requiredMachines);

      double capacityPerShift = number(
        row,
        "capacity_per_shift",
        number(row, "capacityPerShift", defaultCapacity)
      );
      int requiredWorkers = (int) Math.round(number(
        row,
        "required_workers",
        number(row, "required_manpower_per_group", number(row, "requiredWorkers", defaultWorkers))
      ));
      int requiredMachines = (int) Math.round(number(
        row,
        "required_machines",
        number(row, "required_equipment_count", number(row, "requiredMachines", defaultMachines))
      ));

      if (capacityPerShift <= 0d) {
        throw badRequest("capacity_per_shift must be > 0 for " + processCode);
      }
      if (requiredWorkers <= 0) {
        throw badRequest("required_workers must be > 0 for " + processCode);
      }
      if (requiredMachines <= 0) {
        throw badRequest("required_machines must be > 0 for " + processCode);
      }

      nextByCode.put(
        processCode,
        new MvpDomain.ProcessConfig(
          processCode,
          round2(capacityPerShift),
          requiredWorkers,
          requiredMachines
        )
      );
    }

    List<MvpDomain.ProcessConfig> nextRows = new ArrayList<>(nextByCode.values());
    nextRows.sort((a, b) -> normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode)));
    state.processes = nextRows;

    Set<String> validProcessCodes = new HashSet<>(nextByCode.keySet());
    pruneMasterdataRowsByProcessCodes(validProcessCodes);
  }

  private void pruneMasterdataRowsByProcessCodes(Set<String> validProcessCodes) {
    List<MvpDomain.LineProcessBinding> nextLineBindings = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String processCode = normalizeCode(binding.processCode);
      if (validProcessCodes.contains(processCode)) {
        nextLineBindings.add(binding);
      }
    }
    nextLineBindings.sort((a, b) -> {
      int byWorkshop = normalizeCode(a.workshopCode).compareTo(normalizeCode(b.workshopCode));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      int byLine = normalizeCode(a.lineCode).compareTo(normalizeCode(b.lineCode));
      if (byLine != 0) {
        return byLine;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    state.lineProcessBindings = nextLineBindings;
    pruneSectionLeaderBindingsByLineTopology();

    state.workerPools = filterResourceRowsByProcessCodes(state.workerPools, validProcessCodes);
    state.machinePools = filterResourceRowsByProcessCodes(state.machinePools, validProcessCodes);
    state.initialWorkerOccupancy = filterResourceRowsByProcessCodes(state.initialWorkerOccupancy, validProcessCodes);
    state.initialMachineOccupancy = filterResourceRowsByProcessCodes(state.initialMachineOccupancy, validProcessCodes);

    List<MvpDomain.MaterialRow> nextMaterialRows = new ArrayList<>();
    for (MvpDomain.MaterialRow row : state.materialAvailability) {
      String processCode = normalizeCode(row.processCode);
      if (validProcessCodes.contains(processCode)) {
        nextMaterialRows.add(new MvpDomain.MaterialRow(row.date, normalizeShiftCode(row.shiftCode), normalizeCode(row.productCode), processCode, round2(row.availableQty)));
      }
    }
    nextMaterialRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      int byProduct = normalizeCode(a.productCode).compareTo(normalizeCode(b.productCode));
      if (byProduct != 0) {
        return byProduct;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    state.materialAvailability = nextMaterialRows;
  }

  private List<MvpDomain.ResourceRow> filterResourceRowsByProcessCodes(
    List<MvpDomain.ResourceRow> rows,
    Set<String> validProcessCodes
  ) {
    List<MvpDomain.ResourceRow> nextRows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (MvpDomain.ResourceRow row : rows) {
      String processCode = normalizeCode(row.processCode);
      if (!validProcessCodes.contains(processCode)) {
        continue;
      }
      String shiftCode = normalizeShiftCode(row.shiftCode);
      String key = row.date + "#" + shiftCode + "#" + processCode;
      if (!seen.add(key)) {
        continue;
      }
      nextRows.add(new MvpDomain.ResourceRow(row.date, shiftCode, processCode, Math.max(0, row.available)));
    }
    nextRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    return nextRows;
  }

  private void applyLineTopologyPatch(List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      validProcessCodes.add(normalizeCode(process.processCode));
    }

    Map<String, MvpDomain.LineProcessBinding> byKey = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String workshopCode = normalizeCode(string(row, "workshop_code", string(row, "workshopCode", null)));
      String lineCode = normalizeCode(string(row, "line_code", string(row, "lineCode", null)));
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (workshopCode.isBlank() || lineCode.isBlank() || processCode.isBlank()) {
        throw badRequest("workshop_code, line_code and process_code are required in line_topology.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw badRequest("Unknown process_code in line_topology: " + processCode);
      }
      String lineName = string(row, "line_name", string(row, "lineName", lineCode));
      boolean enabled = bool(row, "enabled_flag", bool(row, "enabledFlag", bool(row, "enabled", true)));
      String key = workshopCode + "#" + lineCode + "#" + processCode;
      byKey.put(
        key,
        new MvpDomain.LineProcessBinding(
          workshopCode,
          lineCode,
          lineName == null || lineName.isBlank() ? lineCode : lineName.trim(),
          processCode,
          enabled
        )
      );
    }

    List<MvpDomain.LineProcessBinding> nextRows = new ArrayList<>(byKey.values());
    nextRows.sort((a, b) -> {
      int byWorkshop = normalizeCode(a.workshopCode).compareTo(normalizeCode(b.workshopCode));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      int byLine = normalizeCode(a.lineCode).compareTo(normalizeCode(b.lineCode));
      if (byLine != 0) {
        return byLine;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    state.lineProcessBindings = nextRows;
    pruneSectionLeaderBindingsByLineTopology();
  }

  private void applySectionLeaderBindingsPatch(List<Map<String, Object>> rows) {
    Set<String> validLineCodes = new HashSet<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String lineCode = normalizeCode(binding.lineCode);
      if (!lineCode.isBlank()) {
        validLineCodes.add(lineCode);
      }
    }

    Map<String, MvpDomain.SectionLeaderBinding> byKey = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String leaderId = normalizeCode(string(
        row,
        "leader_id",
        string(row, "leaderId", string(row, "section_leader_id", string(row, "sectionLeaderId", null)))
      ));
      String lineCode = normalizeCode(string(row, "line_code", string(row, "lineCode", null)));
      if (leaderId.isBlank() || lineCode.isBlank()) {
        throw badRequest("leader_id and line_code are required in section_leader_bindings.");
      }
      if (!validLineCodes.contains(lineCode)) {
        throw badRequest("Unknown line_code in section_leader_bindings: " + lineCode);
      }
      String leaderName = string(row, "leader_name", string(row, "leaderName", leaderId));
      boolean active = bool(row, "active_flag", bool(row, "activeFlag", bool(row, "active", true)));
      String key = leaderId + "#" + lineCode;
      byKey.put(
        key,
        new MvpDomain.SectionLeaderBinding(
          leaderId,
          leaderName == null || leaderName.isBlank() ? leaderId : leaderName.trim(),
          lineCode,
          active
        )
      );
    }

    List<MvpDomain.SectionLeaderBinding> nextRows = new ArrayList<>(byKey.values());
    nextRows.sort((a, b) -> {
      int byLeader = normalizeCode(a.leaderId).compareTo(normalizeCode(b.leaderId));
      if (byLeader != 0) {
        return byLeader;
      }
      return normalizeCode(a.lineCode).compareTo(normalizeCode(b.lineCode));
    });
    state.sectionLeaderBindings = nextRows;
  }

  private void pruneSectionLeaderBindingsByLineTopology() {
    Set<String> validLineCodes = new HashSet<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String lineCode = normalizeCode(binding.lineCode);
      if (!lineCode.isBlank()) {
        validLineCodes.add(lineCode);
      }
    }
    List<MvpDomain.SectionLeaderBinding> kept = new ArrayList<>();
    for (MvpDomain.SectionLeaderBinding binding : state.sectionLeaderBindings) {
      String lineCode = normalizeCode(binding.lineCode);
      if (validLineCodes.contains(lineCode)) {
        kept.add(binding);
      }
    }
    state.sectionLeaderBindings = kept;
  }

  private void applyResourcePoolPatch(List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      validProcessCodes.add(normalizeCode(process.processCode));
    }

    Map<String, Integer> workersByKey = new LinkedHashMap<>();
    Map<String, Integer> machinesByKey = new LinkedHashMap<>();
    Map<String, LocalDate> dateByKey = new HashMap<>();
    Map<String, String> shiftByKey = new HashMap<>();
    Map<String, String> processByKey = new HashMap<>();
    Map<String, Boolean> openByDateShift = new LinkedHashMap<>();

    for (Map<String, Object> row : rows) {
      String dateText = string(row, "calendar_date", string(row, "date", null));
      if (dateText == null || dateText.isBlank()) {
        throw badRequest("calendar_date is required in resource_pool.");
      }
      LocalDate date = parseConfigDate(dateText);
      String shiftCode = normalizeShiftCode(string(row, "shift_code", string(row, "shiftCode", null)));
      if (shiftCode.isBlank()) {
        throw badRequest("shift_code is required in resource_pool.");
      }
      if (!isDateInCurrentHorizon(date) || !isShiftEnabledInCurrentSetting(shiftCode)) {
        continue;
      }
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw badRequest("process_code is required in resource_pool.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw badRequest("Unknown process_code in resource_pool: " + processCode);
      }

      int workersAvailable = (int) Math.round(number(
        row,
        "workers_available",
        number(row, "available_workers", number(row, "workers", 0d))
      ));
      int machinesAvailable = (int) Math.round(number(
        row,
        "machines_available",
        number(row, "available_machines", number(row, "machines", 0d))
      ));
      if (workersAvailable < 0 || machinesAvailable < 0) {
        throw badRequest("workers_available and machines_available must be >= 0.");
      }

      String key = date + "#" + shiftCode + "#" + processCode;
      if (workersByKey.containsKey(key)) {
        throw badRequest("Duplicate date/shift/process in resource_pool: " + date + "/" + normalizeShiftCodeLabel(shiftCode) + "/" + processCode);
      }
      workersByKey.put(key, workersAvailable);
      machinesByKey.put(key, machinesAvailable);
      dateByKey.put(key, date);
      shiftByKey.put(key, shiftCode);
      processByKey.put(key, processCode);

      boolean open = bool(row, "open_flag", bool(row, "openFlag", true));
      openByDateShift.put(date + "#" + shiftCode, open);
    }

    List<MvpDomain.ResourceRow> nextWorkerPools = new ArrayList<>();
    List<MvpDomain.ResourceRow> nextMachinePools = new ArrayList<>();
    for (String key : workersByKey.keySet()) {
      LocalDate date = dateByKey.get(key);
      String shiftCode = shiftByKey.get(key);
      String processCode = processByKey.get(key);
      nextWorkerPools.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, Math.max(0, workersByKey.getOrDefault(key, 0))));
      nextMachinePools.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, Math.max(0, machinesByKey.getOrDefault(key, 0))));
    }
    nextWorkerPools.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    nextMachinePools.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });

    List<MvpDomain.ShiftRow> nextShiftCalendar = new ArrayList<>();
    String[] shiftCodes = {"D", "N"};
    for (int i = 0; i < Math.max(1, state.horizonDays); i += 1) {
      LocalDate date = state.startDate.plusDays(i);
      for (int s = 0; s < Math.max(1, Math.min(2, state.shiftsPerDay)); s += 1) {
        String shiftCode = shiftCodes[s];
        String dateShiftKey = date + "#" + shiftCode;
        boolean open = openByDateShift.containsKey(dateShiftKey)
          ? Boolean.TRUE.equals(openByDateShift.get(dateShiftKey))
          : isShiftOpenInDateMode(shiftCode, resolveDateShiftMode(date));
        nextShiftCalendar.add(new MvpDomain.ShiftRow(date, shiftCode, open));
      }
    }
    nextShiftCalendar.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      return Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
    });

    state.workerPools = nextWorkerPools;
    state.machinePools = nextMachinePools;
    state.shiftCalendar = nextShiftCalendar;
    state.initialWorkerOccupancy = clampOccupancyRows(state.initialWorkerOccupancy, workersByKey);
    state.initialMachineOccupancy = clampOccupancyRows(state.initialMachineOccupancy, machinesByKey);
  }

  private List<MvpDomain.ResourceRow> clampOccupancyRows(
    List<MvpDomain.ResourceRow> source,
    Map<String, Integer> capacityByKey
  ) {
    List<MvpDomain.ResourceRow> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (MvpDomain.ResourceRow row : source) {
      String dateShiftProcessKey = row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode);
      Integer cap = capacityByKey.get(dateShiftProcessKey);
      if (cap == null || !seen.add(dateShiftProcessKey)) {
        continue;
      }
      int value = clampInt(row.available, 0, Math.max(0, cap));
      out.add(new MvpDomain.ResourceRow(row.date, normalizeShiftCode(row.shiftCode), normalizeCode(row.processCode), value));
    }
    out.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    return out;
  }

  private void applyInitialCarryoverPatch(List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      validProcessCodes.add(normalizeCode(process.processCode));
    }

    Map<String, Integer> workerCapacityByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.workerPools) {
      workerCapacityByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Integer> machineCapacityByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.machinePools) {
      machineCapacityByKey.put(
        row.date + "#" + normalizeShiftCode(row.shiftCode) + "#" + normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }

    Map<String, Integer> occupiedWorkersByKey = new LinkedHashMap<>();
    Map<String, Integer> occupiedMachinesByKey = new LinkedHashMap<>();
    Map<String, LocalDate> dateByKey = new HashMap<>();
    Map<String, String> shiftByKey = new HashMap<>();
    Map<String, String> processByKey = new HashMap<>();

    for (Map<String, Object> row : rows) {
      String dateText = string(row, "calendar_date", string(row, "date", null));
      if (dateText == null || dateText.isBlank()) {
        throw badRequest("calendar_date is required in initial_carryover_occupancy.");
      }
      LocalDate date = parseConfigDate(dateText);
      String shiftCode = normalizeShiftCode(string(row, "shift_code", string(row, "shiftCode", null)));
      if (shiftCode.isBlank()) {
        throw badRequest("shift_code is required in initial_carryover_occupancy.");
      }
      if (!isDateInCurrentHorizon(date) || !isShiftEnabledInCurrentSetting(shiftCode)) {
        continue;
      }

      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw badRequest("process_code is required in initial_carryover_occupancy.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw badRequest("Unknown process_code in initial_carryover_occupancy: " + processCode);
      }

      int occupiedWorkers = (int) Math.round(number(
        row,
        "occupied_workers",
        number(row, "occupiedWorkers", number(row, "carryover_workers", 0d))
      ));
      int occupiedMachines = (int) Math.round(number(
        row,
        "occupied_machines",
        number(row, "occupiedMachines", number(row, "carryover_machines", 0d))
      ));
      if (occupiedWorkers < 0 || occupiedMachines < 0) {
        throw badRequest("occupied_workers and occupied_machines must be >= 0.");
      }

      String key = date + "#" + shiftCode + "#" + processCode;
      if (occupiedWorkersByKey.containsKey(key)) {
        throw badRequest(
          "Duplicate date/shift/process in initial_carryover_occupancy: "
            + date + "/" + normalizeShiftCodeLabel(shiftCode) + "/" + processCode
        );
      }

      int workerCap = workerCapacityByKey.getOrDefault(key, 0);
      int machineCap = machineCapacityByKey.getOrDefault(key, 0);
      int nextOccupiedWorkers = Math.min(Math.max(0, occupiedWorkers), workerCap);
      int nextOccupiedMachines = Math.min(Math.max(0, occupiedMachines), machineCap);
      occupiedWorkersByKey.put(key, nextOccupiedWorkers);
      occupiedMachinesByKey.put(key, nextOccupiedMachines);
      dateByKey.put(key, date);
      shiftByKey.put(key, shiftCode);
      processByKey.put(key, processCode);
    }

    List<MvpDomain.ResourceRow> nextWorkerRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> nextMachineRows = new ArrayList<>();
    for (String key : occupiedWorkersByKey.keySet()) {
      LocalDate date = dateByKey.get(key);
      String shiftCode = shiftByKey.get(key);
      String processCode = processByKey.get(key);
      nextWorkerRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedWorkersByKey.getOrDefault(key, 0)));
      nextMachineRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedMachinesByKey.getOrDefault(key, 0)));
    }
    nextWorkerRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    nextMachineRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });

    state.initialWorkerOccupancy = nextWorkerRows;
    state.initialMachineOccupancy = nextMachineRows;
  }

  private void applyMaterialAvailabilityPatch(List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      validProcessCodes.add(normalizeCode(process.processCode));
    }

    Map<String, MvpDomain.MaterialRow> nextRowsByKey = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String dateText = string(row, "calendar_date", string(row, "date", null));
      if (dateText == null || dateText.isBlank()) {
        throw badRequest("calendar_date is required in material_availability.");
      }
      LocalDate date = parseConfigDate(dateText);
      String shiftCode = normalizeShiftCode(string(row, "shift_code", string(row, "shiftCode", null)));
      if (shiftCode.isBlank()) {
        throw badRequest("shift_code is required in material_availability.");
      }
      if (!isDateInCurrentHorizon(date) || !isShiftEnabledInCurrentSetting(shiftCode)) {
        continue;
      }
      String productCode = normalizeCode(string(row, "product_code", string(row, "productCode", null)));
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (productCode.isBlank() || processCode.isBlank()) {
        throw badRequest("product_code and process_code are required in material_availability.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw badRequest("Unknown process_code in material_availability: " + processCode);
      }
      double availableQty = number(row, "available_qty", number(row, "availableQty", -1d));
      if (availableQty < 0d) {
        throw badRequest("available_qty must be >= 0 in material_availability.");
      }

      String key = date + "#" + shiftCode + "#" + productCode + "#" + processCode;
      if (nextRowsByKey.containsKey(key)) {
        throw badRequest(
          "Duplicate date/shift/product/process in material_availability: "
            + date + "/" + normalizeShiftCodeLabel(shiftCode) + "/" + productCode + "/" + processCode
        );
      }
      nextRowsByKey.put(key, new MvpDomain.MaterialRow(date, shiftCode, productCode, processCode, round2(availableQty)));
    }

    List<MvpDomain.MaterialRow> nextRows = new ArrayList<>(nextRowsByKey.values());
    nextRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      int byProduct = normalizeCode(a.productCode).compareTo(normalizeCode(b.productCode));
      if (byProduct != 0) {
        return byProduct;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    state.materialAvailability = nextRows;
  }

  private MvpDomain.ResourceRow findResourceRow(
    List<MvpDomain.ResourceRow> pool,
    LocalDate date,
    String shiftCode,
    String processCode
  ) {
    for (MvpDomain.ResourceRow row : pool) {
      if (
        Objects.equals(row.date, date)
          && normalizeShiftCode(row.shiftCode).equals(normalizeShiftCode(shiftCode))
          && normalizeCode(row.processCode).equals(normalizeCode(processCode))
      ) {
        return row;
      }
    }
    return null;
  }

  private MvpDomain.ShiftRow findShiftRow(LocalDate date, String shiftCode) {
    for (MvpDomain.ShiftRow row : state.shiftCalendar) {
      if (Objects.equals(row.date, date) && normalizeShiftCode(row.shiftCode).equals(normalizeShiftCode(shiftCode))) {
        return row;
      }
    }
    return null;
  }

  private MvpDomain.MaterialRow findMaterialRow(
    LocalDate date,
    String shiftCode,
    String productCode,
    String processCode
  ) {
    for (MvpDomain.MaterialRow row : state.materialAvailability) {
      if (
        Objects.equals(row.date, date)
          && normalizeShiftCode(row.shiftCode).equals(normalizeShiftCode(shiftCode))
          && normalizeCode(row.productCode).equals(normalizeCode(productCode))
          && normalizeCode(row.processCode).equals(normalizeCode(processCode))
      ) {
        return row;
      }
    }
    return null;
  }

  private LocalDate parseConfigDate(String dateText) {
    try {
      return LocalDate.parse(dateText.trim());
    } catch (RuntimeException ex) {
      throw badRequest("Invalid date format: " + dateText);
    }
  }

  private Map<String, Object> doCreateReplanJob(Map<String, Object> payload, String requestId, String operator) {
    String baseVersionNo = string(payload, "base_version_no", null);
    if (baseVersionNo == null) {
      throw badRequest("base_version_no is required.");
    }
    MvpDomain.ScheduleVersion baseVersion = getScheduleEntity(baseVersionNo);
    String strategyCode = normalizeScheduleStrategy(
      string(
        payload,
        "strategy_code",
        firstString(baseVersion.metadata, "schedule_strategy_code", "scheduleStrategyCode", "strategy_code", "strategyCode")
      )
    );
    String jobNo = "RPJ-%05d".formatted(replanSeq.incrementAndGet());
    Map<String, Object> job = new LinkedHashMap<>();
    job.put("request_id", requestId);
    job.put("job_no", jobNo);
    job.put("trigger_type", string(payload, "trigger_type", "PROGRESS_GAP"));
    job.put("scope_type", string(payload, "scope_type", "LOCAL"));
    job.put("base_version_no", baseVersionNo);
    job.put("result_version_no", null);
    job.put("status", "RUNNING");
    job.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    job.put("finished_at", null);
    job.put("error_msg", "");
    job.put("strategy_code", strategyCode);
    job.put("strategy_name_cn", scheduleStrategyNameCn(strategyCode));
    state.replanJobs.add(job);
    long startNanos = System.nanoTime();
    String reason = string(payload, "reason", null);
    try {
      Map<String, Object> generatePayload = new LinkedHashMap<>();
      generatePayload.put("request_id", requestId + ":generate");
          generatePayload.put("base_version_no", baseVersionNo);
          generatePayload.put("autoReplan", true);
          generatePayload.put("strategy_code", strategyCode);
          generatePayload.put("compact_response", true);
          Map<String, Object> schedule = generateSchedule(
            generatePayload,
            requestId + ":generate",
            operator
          );
      job.put("result_version_no", schedule.get("versionNo"));
      job.put("status", "DONE");
      job.put("finished_at", OffsetDateTime.now(ZoneOffset.UTC).toString());

      Map<String, Object> perfContext = new LinkedHashMap<>();
      perfContext.put("request_id", requestId);
      perfContext.put("phase", "replan_job");
      perfContext.put("duration_ms", elapsedMillis(startNanos));
      perfContext.put("result_version_no", schedule.get("versionNo"));
      perfContext.put("strategy_code", strategyCode);
      appendAudit("REPLAN_JOB", jobNo, "TRIGGER_REPLAN", operator, requestId, reason, perfContext);
      return new LinkedHashMap<>(job);
    } catch (RuntimeException ex) {
      job.put("status", "FAILED");
      job.put("finished_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
      job.put("error_msg", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());

      Map<String, Object> perfContext = new LinkedHashMap<>();
      perfContext.put("request_id", requestId);
      perfContext.put("phase", "replan_job");
      perfContext.put("duration_ms", elapsedMillis(startNanos));
      perfContext.put("error_msg", job.get("error_msg"));
      appendAudit("REPLAN_JOB", jobNo, "TRIGGER_REPLAN", operator, requestId, reason, perfContext);
      throw ex;
    }
  }

  private Map<String, Object> maybeTriggerProgressGapReplan(MvpDomain.Reporting reporting, String requestId, String operator) {
    if (state.schedules.isEmpty()) {
      return null;
    }
    MvpDomain.ScheduleVersion latest = state.schedules.get(state.schedules.size() - 1);
    double plannedQty = latest.allocations.stream()
      .filter(row -> row.orderNo.equals(reporting.orderNo)
        && row.productCode.equals(reporting.productCode)
        && row.processCode.equals(reporting.processCode))
      .mapToDouble(row -> row.scheduledQty)
      .sum();
    if (plannedQty <= 0d) {
      return null;
    }
    double reportedQty = state.reportings.stream()
      .filter(row -> row.orderNo.equals(reporting.orderNo)
        && row.productCode.equals(reporting.productCode)
        && row.processCode.equals(reporting.processCode))
      .mapToDouble(row -> row.reportQty)
      .sum();
    double deviation = Math.abs(plannedQty - reportedQty) / plannedQty * 100d;
    if (deviation <= 10d) {
      return null;
    }
    Map<String, Object> job = doCreateReplanJob(
      Map.of(
        "request_id", requestId + ":progress-gap",
        "trigger_type", "PROGRESS_GAP",
        "scope_type", "LOCAL",
        "base_version_no", latest.versionNo,
        "reason", "Auto-trigger by reporting deviation > 10%"
      ),
      requestId + ":progress-gap",
      "system"
    );
    Map<String, Object> alert = createAlert(
      "PROGRESS_GAP",
      "WARN",
      reporting.orderNo,
      reporting.processCode,
      latest.versionNo,
      deviation,
      10d,
      plannedQty,
      reportedQty
    );
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("job_no", job.get("job_no"));
    out.put("alert_id", alert.get("alert_id"));
    return out;
  }

  private Map<String, Object> createAlert(
    String alertType,
    String severity,
    String orderNo,
    String processCode,
    String versionNo,
    double triggerValue,
    double thresholdValue,
    Double targetValue,
    Double actualValue
  ) {
    String alertId = "ALT-%05d".formatted(alertSeq.incrementAndGet());
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("alert_id", alertId);
    row.put("alert_type", alertType);
    row.put("severity", severity);
    row.put("order_no", orderNo);
    row.put("order_type", "production");
    row.put("process_code", processCode);
    row.put("version_no", versionNo);
    row.put("trigger_value", round2(triggerValue));
    row.put("threshold_value", round2(thresholdValue));
    row.put("deviation_percent", round2(triggerValue));
    row.put("alert_threshold_percent", round2(thresholdValue));
    row.put("target_value", targetValue == null ? null : round2(targetValue));
    row.put("actual_value", actualValue == null ? null : round2(actualValue));
    row.put("status", "OPEN");
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("ack_by", null);
    row.put("ack_time", null);
    row.put("close_by", null);
    row.put("close_time", null);
    state.alerts.add(row);
    return row;
  }

  private Map<String, Object> enrichAlertMetrics(Map<String, Object> source) {
    Map<String, Object> row = new LinkedHashMap<>(source);
    String alertType = string(row, "alert_type", "");
    if (!"PROGRESS_GAP".equals(alertType)) {
      return row;
    }
    if (row.get("target_value") != null
      && row.get("actual_value") != null
      && row.get("deviation_percent") != null
      && row.get("alert_threshold_percent") != null) {
      return row;
    }

    String orderNo = string(row, "order_no", null);
    String processCode = string(row, "process_code", null);
    if (orderNo == null || orderNo.isBlank() || processCode == null || processCode.isBlank()) {
      return row;
    }

    String versionNo = string(row, "version_no", null);
    MvpDomain.ScheduleVersion schedule = null;
    if (versionNo != null && !versionNo.isBlank()) {
      schedule = state.schedules.stream().filter(item -> versionNo.equals(item.versionNo)).findFirst().orElse(null);
    }
    if (schedule == null && !state.schedules.isEmpty()) {
      schedule = state.schedules.get(state.schedules.size() - 1);
    }
    if (schedule == null) {
      return row;
    }

    double plannedQty = schedule.allocations.stream()
      .filter(item -> orderNo.equals(item.orderNo) && processCode.equals(item.processCode))
      .mapToDouble(item -> item.scheduledQty)
      .sum();
    if (plannedQty <= 0d) {
      return row;
    }
    double reportedQty = state.reportings.stream()
      .filter(item -> orderNo.equals(item.orderNo) && processCode.equals(item.processCode))
      .mapToDouble(item -> item.reportQty)
      .sum();
    double deviation = Math.abs(plannedQty - reportedQty) / plannedQty * 100d;
    double threshold = number(row, "threshold_value", 10d);

    row.put("target_value", round2(plannedQty));
    row.put("actual_value", round2(reportedQty));
    row.put("deviation_percent", round2(deviation));
    row.put("alert_threshold_percent", round2(threshold));
    row.putIfAbsent("trigger_value", round2(deviation));
    row.putIfAbsent("threshold_value", round2(threshold));
    return row;
  }

  private Map<String, Object> updateAlert(String alertId, Map<String, Object> payload, String requestId, String operator, String nextStatus) {
    return runIdempotent(requestId, nextStatus + "_ALERT#" + alertId, () -> {
      Map<String, Object> alert = state.alerts.stream()
        .filter(row -> Objects.equals(row.get("alert_id"), alertId))
        .findFirst()
        .orElseThrow(() -> notFound("Alert %s not found.".formatted(alertId)));
      alert.put("status", nextStatus);
      if ("ACKED".equals(nextStatus)) {
        alert.put("ack_by", string(payload, "operator", operator));
        alert.put("ack_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
      }
      if ("CLOSED".equals(nextStatus)) {
        alert.put("close_by", string(payload, "operator", operator));
        alert.put("close_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
      }
      appendAudit("ALERT", alertId, "ACKED".equals(nextStatus) ? "ACK_ALERT" : "CLOSE_ALERT", operator, requestId, string(payload, "reason", null));
      return Map.of("request_id", requestId, "success", true, "message", "Alert %s set to %s.".formatted(alertId, nextStatus));
    });
  }

  private void applyDispatchCommand(Map<String, Object> command) {
    String targetOrderNo = string(command, "target_order_no", null);
    if (targetOrderNo == null) {
      return;
    }
    MvpDomain.Order order = state.orders.stream().filter(it -> it.orderNo.equals(targetOrderNo)).findFirst().orElse(null);
    if (order == null) {
      return;
    }
    String commandType = string(command, "command_type", "");
    if ("LOCK".equals(commandType)) {
      order.lockFlag = true;
    } else if ("UNLOCK".equals(commandType)) {
      order.lockFlag = false;
    } else if ("FREEZE".equals(commandType)) {
      order.frozen = true;
    } else if ("UNFREEZE".equals(commandType)) {
      order.frozen = false;
    } else if ("PRIORITY".equals(commandType)) {
      order.urgent = true;
    } else if ("UNPRIORITY".equals(commandType)) {
      order.urgent = false;
    }
  }

  private MvpDomain.Order fromOrderPayload(Map<String, Object> payload) {
    String orderNo = string(payload, "orderNo", string(payload, "order_no", null));
    String orderType = string(payload, "orderType", string(payload, "order_type", "production"));
    LocalDate dueDate = LocalDate.parse(string(payload, "dueDate", string(payload, "due_date", state.startDate.toString())));
    boolean urgent = bool(payload, "urgent", number(payload, "urgent_flag", 0d) == 1d);
    boolean frozen = bool(payload, "frozen", number(payload, "frozen_flag", 0d) == 1d);
    boolean lockFlag = bool(payload, "lockFlag", number(payload, "lock_flag", 0d) == 1d);
    String status = string(payload, "status", string(payload, "order_status", "OPEN"));
    List<Map<String, Object>> items = maps(payload.get("items"));
    List<MvpDomain.OrderItem> orderItems = new ArrayList<>();
    for (Map<String, Object> item : items) {
      orderItems.add(new MvpDomain.OrderItem(
        string(item, "productCode", string(item, "product_code", "UNKNOWN")),
        number(item, "qty", 0d),
        number(item, "completedQty", number(item, "completed_qty", 0d))
      ));
    }
    MvpDomain.OrderBusinessData businessData = parseBusinessData(payload, orderNo, orderItems, dueDate);
    LocalDate expectedStartDate = parseLocalDateFlexible(
      string(
        payload,
        "expected_start_date",
        string(payload, "expectedStartDate", string(payload, "expected_start_time", string(payload, "expectedStartTime", null)))
      ),
      state.startDate
    );
    return new MvpDomain.Order(
      orderNo,
      orderType,
      dueDate,
      expectedStartDate,
      urgent,
      frozen,
      lockFlag,
      status,
      orderItems,
      businessData
    );
  }

  private void applyOrderPatch(MvpDomain.Order order, Map<String, Object> patch) {
    if (patch.containsKey("frozen")) {
      order.frozen = bool(patch, "frozen", false);
    }
    if (patch.containsKey("frozen_flag")) {
      order.frozen = number(patch, "frozen_flag", 0d) == 1d;
    }
    if (patch.containsKey("urgent")) {
      order.urgent = bool(patch, "urgent", false);
    }
    if (patch.containsKey("urgent_flag")) {
      order.urgent = number(patch, "urgent_flag", 0d) == 1d;
    }
    if (patch.containsKey("lockFlag")) {
      order.lockFlag = bool(patch, "lockFlag", false);
    }
    if (patch.containsKey("lock_flag")) {
      order.lockFlag = number(patch, "lock_flag", 0d) == 1d;
    }
    if (patch.containsKey("dueDate")) {
      order.dueDate = LocalDate.parse(string(patch, "dueDate", order.dueDate.toString()));
    }
    if (patch.containsKey("due_date")) {
      order.dueDate = LocalDate.parse(string(patch, "due_date", order.dueDate.toString()));
    }
    if (
      patch.containsKey("expected_start_date")
        || patch.containsKey("expectedStartDate")
        || patch.containsKey("expected_start_time")
        || patch.containsKey("expectedStartTime")
    ) {
      LocalDate fallback = order.expectedStartDate == null ? state.startDate : order.expectedStartDate;
      order.expectedStartDate = parseLocalDateFlexible(
        string(
          patch,
          "expected_start_date",
          string(patch, "expectedStartDate", string(patch, "expected_start_time", string(patch, "expectedStartTime", null)))
        ),
        fallback
      );
    }
    if (patch.containsKey("status")) {
      order.status = string(patch, "status", order.status);
    }
    if (patch.containsKey("order_status")) {
      order.status = string(patch, "order_status", order.status);
    }
    applyBusinessPatch(order, patch);
    for (Map<String, Object> itemPatch : maps(patch.get("items"))) {
      String productCode = string(itemPatch, "productCode", string(itemPatch, "product_code", null));
      MvpDomain.OrderItem item = order.items.stream().filter(it -> it.productCode.equals(productCode)).findFirst().orElse(null);
      if (item != null && itemPatch.containsKey("qty")) {
        item.qty = number(itemPatch, "qty", item.qty);
        item.completedQty = Math.min(item.completedQty, item.qty);
      }
    }
    updateOrderProgressFacts(order);
  }

  private MvpDomain.OrderBusinessData parseBusinessData(
    Map<String, Object> payload,
    String orderNo,
    List<MvpDomain.OrderItem> orderItems,
    LocalDate dueDate
  ) {
    String productCode = orderItems.isEmpty() ? "PROD_UNKNOWN" : orderItems.get(0).productCode;
    double orderQty = orderItems.stream().mapToDouble(item -> item.qty).sum();
    MvpDomain.OrderBusinessData data = new MvpDomain.OrderBusinessData();
    data.orderDate = parseLocalDateFlexible(
      string(payload, "order_date", string(payload, "orderDate", dueDate.minusDays(2).toString())),
      dueDate.minusDays(2)
    );
    data.customerRemark = string(payload, "customer_remark", string(payload, "customerRemark", ""));
    data.productName = string(
      payload,
      "product_name",
      string(payload, "product_name_cn", productNameCn(productCode))
    );
    data.specModel = string(payload, "spec_model", string(payload, "specModel", ""));
    data.productionBatchNo = string(payload, "production_batch_no", string(payload, "productionBatchNo", "BATCH-" + orderNo));
    data.packagingForm = string(payload, "packaging_form", string(payload, "packagingForm", "缁剧顢栫悮"));
    data.salesOrderNo = string(payload, "sales_order_no", string(payload, "salesOrderNo", "SO-" + orderNo));
    data.productionDateForeignTrade = string(
      payload,
      "production_date_foreign_trade",
      string(payload, "productionDateForeignTrade", "")
    );
    data.purchaseDueDate = string(payload, "purchase_due_date", string(payload, "purchaseDueDate", ""));
    data.injectionDueDate = string(payload, "injection_due_date", string(payload, "injectionDueDate", ""));
    data.marketRemarkInfo = string(payload, "market_remark_info", string(payload, "marketRemarkInfo", ""));
    data.marketDemand = number(payload, "market_demand", number(payload, "marketDemand", orderQty));
    data.plannedFinishDate1 = string(
      payload,
      "planned_finish_date_1",
      string(payload, "plannedFinishDate1", dueDate.toString())
    );
    data.plannedFinishDate2 = string(
      payload,
      "planned_finish_date_2",
      string(payload, "plannedFinishDate2", dueDate.toString())
    );
    data.semiFinishedCode = string(payload, "semi_finished_code", string(payload, "semiFinishedCode", "SF-" + productCode));
    data.semiFinishedInventory = number(payload, "semi_finished_inventory", number(payload, "semiFinishedInventory", 0d));
    data.semiFinishedDemand = number(payload, "semi_finished_demand", number(payload, "semiFinishedDemand", orderQty));
    data.semiFinishedWip = number(payload, "semi_finished_wip", number(payload, "semiFinishedWip", 0d));
    data.needOrderQty = number(
      payload,
      "need_order_qty",
      number(payload, "needOrderQty", Math.max(0d, data.semiFinishedDemand - data.semiFinishedInventory - data.semiFinishedWip))
    );
    data.pendingInboundQty = number(payload, "pending_inbound_qty", number(payload, "pendingInboundQty", 0d));
    data.weeklyMonthlyPlanRemark = string(
      payload,
      "weekly_monthly_process_plan",
      string(payload, "process_schedule_remark", string(payload, "weeklyMonthlyPlanRemark", "閸涖劏顓搁崚鎺炵礄3.16-3.22閿"))
    );
    data.workshopOuterPackagingDate = string(
      payload,
      "workshop_outer_packaging_date",
      string(payload, "workshopOuterPackagingDate", formatShortDate(dueDate))
    );
    data.note = string(payload, "note", string(payload, "remark", ""));
    data.workshopCompletedQty = number(payload, "workshop_completed_qty", number(payload, "workshopCompletedQty", 0d));
    data.workshopCompletedTime = string(payload, "workshop_completed_time", string(payload, "workshopCompletedTime", ""));
    data.outerCompletedQty = number(payload, "outer_completed_qty", number(payload, "outerCompletedQty", 0d));
    data.outerCompletedTime = string(payload, "outer_completed_time", string(payload, "outerCompletedTime", ""));
    data.matchStatus = string(payload, "match_status", string(payload, "matchStatus", "瀵板懎灏柊"));
    return data;
  }

  private void applyBusinessPatch(MvpDomain.Order order, Map<String, Object> patch) {
    MvpDomain.OrderBusinessData data = businessData(order);
    if (patch.containsKey("order_date") || patch.containsKey("orderDate")) {
      data.orderDate = parseLocalDateFlexible(
        string(patch, "order_date", string(patch, "orderDate", data.orderDate == null ? null : data.orderDate.toString())),
        data.orderDate == null ? order.dueDate.minusDays(2) : data.orderDate
      );
    }
    if (patch.containsKey("customer_remark") || patch.containsKey("customerRemark")) {
      data.customerRemark = string(patch, "customer_remark", string(patch, "customerRemark", data.customerRemark));
    }
    if (patch.containsKey("product_name") || patch.containsKey("product_name_cn")) {
      data.productName = string(patch, "product_name", string(patch, "product_name_cn", data.productName));
    }
    if (patch.containsKey("spec_model") || patch.containsKey("specModel")) {
      data.specModel = string(patch, "spec_model", string(patch, "specModel", data.specModel));
    }
    if (patch.containsKey("production_batch_no") || patch.containsKey("productionBatchNo")) {
      data.productionBatchNo = string(
        patch,
        "production_batch_no",
        string(patch, "productionBatchNo", data.productionBatchNo)
      );
    }
    if (patch.containsKey("packaging_form") || patch.containsKey("packagingForm")) {
      data.packagingForm = string(patch, "packaging_form", string(patch, "packagingForm", data.packagingForm));
    }
    if (patch.containsKey("sales_order_no") || patch.containsKey("salesOrderNo")) {
      data.salesOrderNo = string(patch, "sales_order_no", string(patch, "salesOrderNo", data.salesOrderNo));
    }
    if (patch.containsKey("production_date_foreign_trade")) {
      data.productionDateForeignTrade = string(
        patch,
        "production_date_foreign_trade",
        data.productionDateForeignTrade
      );
    }
    if (patch.containsKey("purchase_due_date")) {
      data.purchaseDueDate = string(patch, "purchase_due_date", data.purchaseDueDate);
    }
    if (patch.containsKey("injection_due_date")) {
      data.injectionDueDate = string(patch, "injection_due_date", data.injectionDueDate);
    }
    if (patch.containsKey("market_remark_info")) {
      data.marketRemarkInfo = string(patch, "market_remark_info", data.marketRemarkInfo);
    }
    if (patch.containsKey("market_demand")) {
      data.marketDemand = number(patch, "market_demand", data.marketDemand);
    }
    if (patch.containsKey("planned_finish_date_1")) {
      data.plannedFinishDate1 = string(patch, "planned_finish_date_1", data.plannedFinishDate1);
    }
    if (patch.containsKey("planned_finish_date_2")) {
      data.plannedFinishDate2 = string(patch, "planned_finish_date_2", data.plannedFinishDate2);
    }
    if (patch.containsKey("semi_finished_code")) {
      data.semiFinishedCode = string(patch, "semi_finished_code", data.semiFinishedCode);
    }
    if (patch.containsKey("semi_finished_inventory")) {
      data.semiFinishedInventory = number(patch, "semi_finished_inventory", data.semiFinishedInventory);
    }
    if (patch.containsKey("semi_finished_demand")) {
      data.semiFinishedDemand = number(patch, "semi_finished_demand", data.semiFinishedDemand);
    }
    if (patch.containsKey("semi_finished_wip")) {
      data.semiFinishedWip = number(patch, "semi_finished_wip", data.semiFinishedWip);
    }
    if (patch.containsKey("need_order_qty")) {
      data.needOrderQty = number(patch, "need_order_qty", data.needOrderQty);
    }
    if (patch.containsKey("pending_inbound_qty")) {
      data.pendingInboundQty = number(patch, "pending_inbound_qty", data.pendingInboundQty);
    }
    if (patch.containsKey("weekly_monthly_process_plan") || patch.containsKey("process_schedule_remark")) {
      data.weeklyMonthlyPlanRemark = string(
        patch,
        "weekly_monthly_process_plan",
        string(patch, "process_schedule_remark", data.weeklyMonthlyPlanRemark)
      );
    }
    if (patch.containsKey("workshop_outer_packaging_date")) {
      data.workshopOuterPackagingDate = string(patch, "workshop_outer_packaging_date", data.workshopOuterPackagingDate);
    }
    if (patch.containsKey("note") || patch.containsKey("remark")) {
      data.note = string(patch, "note", string(patch, "remark", data.note));
    }
    if (patch.containsKey("workshop_completed_qty")) {
      data.workshopCompletedQty = number(patch, "workshop_completed_qty", data.workshopCompletedQty);
    }
    if (patch.containsKey("workshop_completed_time")) {
      data.workshopCompletedTime = string(patch, "workshop_completed_time", data.workshopCompletedTime);
    }
    if (patch.containsKey("outer_completed_qty")) {
      data.outerCompletedQty = number(patch, "outer_completed_qty", data.outerCompletedQty);
    }
    if (patch.containsKey("outer_completed_time")) {
      data.outerCompletedTime = string(patch, "outer_completed_time", data.outerCompletedTime);
    }
    if (patch.containsKey("match_status")) {
      data.matchStatus = string(patch, "match_status", data.matchStatus);
    }
  }

  private void updateOrderProgressFacts(MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = businessData(order);
    double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
    double completedQty = order.items.stream().mapToDouble(item -> item.completedQty).sum();
    business.workshopCompletedQty = round2(completedQty);
    if (completedQty > 0d) {
      business.workshopCompletedTime = OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
    if (totalQty > 0d && completedQty + 1e-9 >= totalQty) {
      business.outerCompletedQty = round2(completedQty);
      business.outerCompletedTime = OffsetDateTime.now(ZoneOffset.UTC).toString();
      business.matchStatus = "瀹告彃灏柊";
    } else {
      business.outerCompletedQty = round2(Math.max(0d, completedQty - (0.1d * totalQty)));
      business.matchStatus = "瀵板懎灏柊";
    }
  }

  private void syncCompletedQtyFromFinalProcessReports() {
    if (state.reportings == null) {
      return;
    }

    int reportingSize = state.reportings.size();
    boolean fullResync = false;
    if (reportingSize < finalCompletedSyncCursor) {
      finalCompletedByOrderProductCache.clear();
      finalCompletedSyncCursor = 0;
      fullResync = true;
    }

    Set<String> touchedOrderNos = new HashSet<>();
    for (int index = finalCompletedSyncCursor; index < reportingSize; index += 1) {
      MvpDomain.Reporting reporting = state.reportings.get(index);
      if (!isFinalProcessForProduct(reporting.productCode, reporting.processCode)) {
        continue;
      }
      String key = reporting.orderNo + "#" + reporting.productCode;
      finalCompletedByOrderProductCache.merge(key, reporting.reportQty, Double::sum);
      touchedOrderNos.add(reporting.orderNo);
    }
    finalCompletedSyncCursor = reportingSize;

    if (!fullResync && touchedOrderNos.isEmpty()) {
      return;
    }

    for (MvpDomain.Order order : state.orders) {
      if (!fullResync && !touchedOrderNos.contains(order.orderNo)) {
        continue;
      }
      boolean updated = false;
      for (MvpDomain.OrderItem item : order.items) {
        String key = order.orderNo + "#" + item.productCode;
        if (!finalCompletedByOrderProductCache.containsKey(key)) {
          continue;
        }
        double corrected = Math.min(item.qty, Math.max(0d, finalCompletedByOrderProductCache.get(key)));
        if (Math.abs(item.completedQty - corrected) > 1e-9) {
          item.completedQty = corrected;
          updated = true;
        }
      }
      if (updated) {
        updateOrderProgressFacts(order);
      }
    }
  }

  private boolean isFinalProcessForProduct(String productCode, String processCode) {
    String normalizedProcessCode = normalizeCode(processCode);
    if (normalizedProcessCode.isBlank()) {
      return false;
    }
    List<MvpDomain.ProcessStep> route = state.processRoutes.get(productCode);
    if (route == null || route.isEmpty()) {
      return isFallbackFinalProcess(normalizedProcessCode);
    }
    MvpDomain.ProcessStep lastStep = route.get(route.size() - 1);
    String normalizedLastStepCode = normalizeCode(lastStep.processCode);
    if (normalizedLastStepCode.isBlank()) {
      return isFallbackFinalProcess(normalizedProcessCode);
    }
    return normalizedProcessCode.equals(normalizedLastStepCode);
  }

  private static boolean isFallbackFinalProcess(String normalizedProcessCode) {
    return "PROC_STERILE".equals(normalizedProcessCode)
      || normalizedProcessCode.endsWith("_STERILE")
      || normalizedProcessCode.contains("FINAL");
  }

  private MvpDomain.OrderBusinessData businessData(MvpDomain.Order order) {
    if (order.businessData == null) {
      order.businessData = new MvpDomain.OrderBusinessData();
    }
    return order.businessData;
  }

  private MvpDomain.OrderBusinessData buildSimulationBusinessData(
    LocalDate businessDate,
    LocalDate dueDate,
    String salesOrderNo,
    String productCode,
    double qty
  ) {
    MvpDomain.OrderBusinessData data = new MvpDomain.OrderBusinessData();
    data.orderDate = businessDate;
    data.customerRemark = "濡剝瀚欑拋銏犲礋";
    data.productName = productNameCn(productCode);
    data.specModel = "SIM-" + productCode + "-" + (int) qty;
    data.productionBatchNo = "SIM-" + businessDate.toString().replace("-", "");
    data.packagingForm = "缁剧顢栫悮";
    data.salesOrderNo = salesOrderNo;
    data.productionDateForeignTrade = "";
    data.purchaseDueDate = "";
    data.injectionDueDate = "";
    data.marketRemarkInfo = "娴犺法婀￠懛顏勫З閻㈢喐鍨";
    data.marketDemand = qty;
    data.plannedFinishDate1 = dueDate.toString();
    data.plannedFinishDate2 = dueDate.toString();
    data.semiFinishedCode = "SF-" + productCode;
    data.semiFinishedInventory = round2(qty * 0.1d);
    data.semiFinishedDemand = qty;
    data.semiFinishedWip = round2(qty * 0.2d);
    data.needOrderQty = round2(Math.max(0d, qty - data.semiFinishedInventory - data.semiFinishedWip));
    data.pendingInboundQty = round2(data.needOrderQty * 0.5d);
    data.weeklyMonthlyPlanRemark = "娴犺法婀＄拋鈥冲灊";
    data.workshopOuterPackagingDate = formatShortDate(dueDate);
    data.note = "SIM";
    data.workshopCompletedQty = 0d;
    data.workshopCompletedTime = "";
    data.outerCompletedQty = 0d;
    data.outerCompletedTime = "";
    data.matchStatus = "瀵板懎灏柊";
    return data;
  }

  private static String formatShortDate(LocalDate date) {
    return date.getMonthValue() + "." + date.getDayOfMonth();
  }

  private static OffsetDateTime parseOffsetDateTimeFilter(Map<String, String> filters, String... keys) {
    if (filters == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String raw = filters.get(key);
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        return OffsetDateTime.parse(raw);
      } catch (Exception ignore) {
        try {
          return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (Exception ignoreAgain) {
          // ignore invalid filter value
        }
      }
    }
    return null;
  }

  private static LocalDate parseLocalDateFlexible(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String normalized = value.trim();
    if (normalized.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}")) {
      String[] parts = normalized.replace('/', '-').split("-");
      try {
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        return LocalDate.of(year, month, day);
      } catch (Exception ignore) {
        // continue
      }
    }
    if (normalized.length() >= 10) {
      String dateOnly = normalized.substring(0, 10).replace("/", "-");
      try {
        return LocalDate.parse(dateOnly);
      } catch (Exception ignore) {
        // continue
      }
    }
    if (normalized.matches("\\d{8}")) {
      String dateOnly = normalized.substring(0, 4) + "-" + normalized.substring(4, 6) + "-" + normalized.substring(6, 8);
      try {
        return LocalDate.parse(dateOnly);
      } catch (Exception ignore) {
        return fallback;
      }
    }
    return fallback;
  }

  private MvpDomain.Order findOrder(String orderNo) {
    return state.orders.stream().filter(order -> order.orderNo.equals(orderNo)).findFirst()
      .orElseThrow(() -> notFound("Order %s not found.".formatted(orderNo)));
  }

  private MvpDomain.ScheduleVersion getScheduleEntity(String versionNo) {
    if (versionNo == null || versionNo.isBlank()) {
      if (state.schedules.isEmpty()) {
        throw badRequest("No schedule generated.");
      }
      return state.schedules.get(state.schedules.size() - 1);
    }
    return state.schedules.stream()
      .filter(item -> item.versionNo.equals(versionNo))
      .findFirst()
      .orElseThrow(() -> notFound("Schedule %s not found.".formatted(versionNo)));
  }

  private ReportVersionBinding resolveReportVersionBinding(String versionNo) {
    String requestedVersionNo = versionNo == null ? "" : versionNo.trim();
    MvpDomain.ScheduleVersion schedule = null;

    if (!requestedVersionNo.isBlank()) {
      schedule = getScheduleEntity(requestedVersionNo);
    } else if (state.publishedVersionNo != null && !state.publishedVersionNo.isBlank()) {
      schedule = getScheduleEntity(state.publishedVersionNo);
    } else if (!state.schedules.isEmpty()) {
      schedule = state.schedules.get(state.schedules.size() - 1);
    }

    if (schedule == null) {
      return new ReportVersionBinding(LIVE_REPORT_VERSION_NO, LIVE_REPORT_STATUS, null);
    }

    Set<String> orderNos = new HashSet<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      if (task.orderNo != null && !task.orderNo.isBlank()) {
        orderNos.add(task.orderNo);
      }
    }
    return new ReportVersionBinding(schedule.versionNo, schedule.status, orderNos);
  }

  private LocalDate resolveExpectedStartDate(MvpDomain.Order order) {
    if (order == null) {
      return state.startDate;
    }
    if (order.expectedStartDate != null) {
      return order.expectedStartDate;
    }
    MvpDomain.OrderBusinessData business = businessData(order);
    if (business.orderDate != null) {
      if (state.startDate == null) {
        return business.orderDate;
      }
      return business.orderDate.isBefore(state.startDate) ? state.startDate : business.orderDate;
    }
    if (state.startDate != null) {
      return state.startDate;
    }
    return order.dueDate;
  }

  private String estimateExpectedFinishTime(MvpDomain.Order order) {
    LocalDate startDate = resolveExpectedStartDate(order);
    if (startDate == null) {
      return null;
    }
    int requiredShifts = estimateRequiredShifts(order);
    int shiftsPerDay = Math.max(1, Math.min(2, state.shiftsPerDay));
    int finishShiftIndex = Math.max(0, requiredShifts <= 0 ? 0 : requiredShifts - 1);
    LocalDate finishDate = startDate.plusDays(finishShiftIndex / shiftsPerDay);
    String finishShiftCode = shiftsPerDay == 1 ? "D" : (finishShiftIndex % shiftsPerDay == 0 ? "D" : "N");
    return toDateTime(finishDate.toString(), finishShiftCode, false);
  }

  private int estimateRequiredShifts(MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return 0;
    }
    int totalShifts = 0;
    for (MvpDomain.OrderItem item : order.items) {
      double remainingQty = Math.max(0d, item.qty - item.completedQty);
      if (remainingQty <= 1e-9d) {
        continue;
      }
      List<MvpDomain.ProcessStep> route = state.processRoutes.getOrDefault(item.productCode, List.of());
      if (route.isEmpty()) {
        totalShifts += Math.max(1, (int) Math.ceil(remainingQty / 1000d));
        continue;
      }
      for (MvpDomain.ProcessStep step : route) {
        double shiftCapacity = estimateProcessCapacityPerShift(step.processCode);
        double normalizedCapacity = shiftCapacity <= 1e-9d ? 1d : shiftCapacity;
        totalShifts += Math.max(1, (int) Math.ceil(remainingQty / normalizedCapacity));
      }
    }
    return totalShifts;
  }

  private double estimateProcessCapacityPerShift(String processCode) {
    String normalizedProcessCode = normalizeCode(processCode);
    if (normalizedProcessCode.isBlank()) {
      return 0d;
    }
    MvpDomain.ProcessConfig config = state.processes.stream()
      .filter(row -> normalizedProcessCode.equals(normalizeCode(row.processCode)))
      .findFirst()
      .orElse(null);
    if (config == null) {
      return 0d;
    }

    int requiredWorkers = Math.max(1, config.requiredWorkers);
    int requiredMachines = Math.max(1, config.requiredMachines);
    int maxWorkers = maxResourceForProcess(state.workerPools, normalizedProcessCode);
    int maxMachines = maxResourceForProcess(state.machinePools, normalizedProcessCode);
    if (maxWorkers <= 0) {
      maxWorkers = requiredWorkers;
    }
    if (maxMachines <= 0) {
      maxMachines = requiredMachines;
    }
    int groupsByWorkers = Math.max(1, maxWorkers / requiredWorkers);
    int groupsByMachines = Math.max(1, maxMachines / requiredMachines);
    int groups = Math.max(1, Math.min(groupsByWorkers, groupsByMachines));
    return round2(config.capacityPerShift * groups);
  }

  private int maxResourceForProcess(List<MvpDomain.ResourceRow> rows, String processCode) {
    int max = 0;
    for (MvpDomain.ResourceRow row : rows) {
      if (normalizeCode(row.processCode).equals(processCode)) {
        max = Math.max(max, Math.max(0, row.available));
      }
    }
    return max;
  }

  private String orderPrimaryProductCode(MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return "UNKNOWN";
    }
    return normalizeCode(order.items.get(0).productCode);
  }

  private List<Map<String, Object>> buildProcessContextsForProduct(String productCode) {
    String normalizedProductCode = normalizeCode(productCode);
    List<MvpDomain.ProcessStep> route = state.processRoutes.getOrDefault(normalizedProductCode, List.of());
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < route.size(); i += 1) {
      MvpDomain.ProcessStep step = route.get(i);
      String processCode = normalizeCode(step.processCode);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("sequence_no", i + 1);
      row.put("product_code", normalizedProductCode);
      row.put("product_name_cn", productNameCn(normalizedProductCode));
      row.put("process_code", processCode);
      row.put("process_name_cn", processNameCn(processCode));
      row.put("dependency_type", step.dependencyType == null ? "FS" : step.dependencyType);
      row.put("workshop_code", workshopCodeForProcess(processCode));
      row.put("line_code", lineCodeForProcess(processCode));
      row.put("workshop_codes", workshopCodesForProcessSummary(processCode));
      row.put("line_codes", lineCodesForProcessSummary(processCode));
      rows.add(row);
    }
    return rows;
  }

  private String summarizeProcessContexts(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String processCode = firstString(row, "process_code");
      String processName = firstString(row, "process_name_cn");
      String workshopCode = firstString(row, "workshop_codes", "workshop_code");
      String lineCode = firstString(row, "line_codes", "line_code");
      String label = (processName == null || processName.isBlank()) ? (processCode == null ? "-" : processCode) : processName;
      parts.add(label + " (" + (workshopCode == null ? "-" : workshopCode) + "/" + (lineCode == null ? "-" : lineCode) + ")");
    }
    return String.join(" -> ", parts);
  }

  private String joinContextValues(List<Map<String, Object>> rows, String key) {
    if (rows == null || rows.isEmpty()) {
      return "";
    }
    List<String> values = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String value = firstString(row, key);
      if (value == null || value.isBlank()) {
        continue;
      }
      String[] parts = value.split(",");
      for (String part : parts) {
        String normalized = part == null ? "" : part.trim();
        if (normalized.isBlank() || values.contains(normalized)) {
          continue;
        }
        values.add(normalized);
      }
    }
    return String.join(",", values);
  }

  private Map<String, Object> toOrderMap(MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = businessData(order);
    String productCode = orderPrimaryProductCode(order);
    List<Map<String, Object>> processContexts = buildProcessContextsForProduct(productCode);
    LocalDate expectedStartDate = resolveExpectedStartDate(order);
    String expectedStartTime = expectedStartDate == null ? null : toDateTime(expectedStartDate.toString(), "D", true);
    String expectedFinishTime = estimateExpectedFinishTime(order);
    LocalDate expectedFinishDate = parseLocalDateFlexible(expectedFinishTime, null);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("orderNo", order.orderNo);
    row.put("order_no", order.orderNo);
    row.put("orderType", order.orderType);
    row.put("order_type", order.orderType);
    row.put("dueDate", order.dueDate.toString());
    row.put("due_date", order.dueDate.toString());
    row.put("urgent", order.urgent);
    row.put("urgent_flag", order.urgent ? 1 : 0);
    row.put("frozen", order.frozen);
    row.put("frozen_flag", order.frozen ? 1 : 0);
    row.put("lockFlag", order.lockFlag);
    row.put("lock_flag", order.lockFlag ? 1 : 0);
    row.put("status", order.status);
    row.put("order_status", order.status);
    row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
    row.put("expected_start_time", expectedStartTime);
    row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
    row.put("expected_finish_time", expectedFinishTime);
    row.put("workshop_codes", joinContextValues(processContexts, "workshop_codes"));
    row.put("line_codes", joinContextValues(processContexts, "line_codes"));
    row.put("process_codes", joinContextValues(processContexts, "process_code"));
    row.put("process_route_summary", summarizeProcessContexts(processContexts));
    row.put("process_contexts", processContexts);
    row.put("order_date", business.orderDate == null ? null : business.orderDate.toString());
    row.put("customer_remark", business.customerRemark);
    row.put("product_name", business.productName);
    row.put("spec_model", business.specModel);
    row.put("production_batch_no", business.productionBatchNo);
    row.put("packaging_form", business.packagingForm);
    row.put("sales_order_no", business.salesOrderNo);
    row.put("production_date_foreign_trade", business.productionDateForeignTrade);
    row.put("purchase_due_date", business.purchaseDueDate);
    row.put("injection_due_date", business.injectionDueDate);
    row.put("market_remark_info", business.marketRemarkInfo);
    row.put("market_demand", business.marketDemand);
    row.put("planned_finish_date_1", business.plannedFinishDate1);
    row.put("planned_finish_date_2", business.plannedFinishDate2);
    row.put("semi_finished_code", business.semiFinishedCode);
    row.put("semi_finished_inventory", business.semiFinishedInventory);
    row.put("semi_finished_demand", business.semiFinishedDemand);
    row.put("semi_finished_wip", business.semiFinishedWip);
    row.put("need_order_qty", business.needOrderQty);
    row.put("pending_inbound_qty", business.pendingInboundQty);
    row.put("weekly_monthly_process_plan", business.weeklyMonthlyPlanRemark);
    row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
    row.put("note", business.note);
    row.put("workshop_completed_qty", business.workshopCompletedQty);
    row.put("workshop_completed_time", business.workshopCompletedTime);
    row.put("outer_completed_qty", business.outerCompletedQty);
    row.put("outer_completed_time", business.outerCompletedTime);
    row.put("match_status", business.matchStatus);
    List<Map<String, Object>> items = new ArrayList<>();
    for (MvpDomain.OrderItem item : order.items) {
      Map<String, Object> itemRow = new LinkedHashMap<>();
      itemRow.put("productCode", item.productCode);
      itemRow.put("product_code", item.productCode);
      itemRow.put("product_name_cn", productNameCn(item.productCode));
      itemRow.put("qty", item.qty);
      itemRow.put("completedQty", item.completedQty);
      itemRow.put("completed_qty", item.completedQty);
      items.add(itemRow);
    }
    row.put("items", items);
    return localizeRow(row);
  }

  private boolean filterOrderPoolRow(Map<String, Object> row, Map<String, String> filters) {
    if (filters == null) {
      return true;
    }
    if (filters.containsKey("status") && !Objects.equals(filters.get("status"), row.get("status"))) {
      return false;
    }
    if (filters.containsKey("frozen_flag")
      && Integer.parseInt(filters.get("frozen_flag")) != (int) number(row, "frozen_flag", 0d)) {
      return false;
    }
    if (filters.containsKey("urgent_flag")
      && Integer.parseInt(filters.get("urgent_flag")) != (int) number(row, "urgent_flag", 0d)) {
      return false;
    }
    return true;
  }

  private Map<String, Object> toOrderPoolItemFromErp(Map<String, Object> erpRow) {
    String orderNo = string(erpRow, "production_order_no", null);
    String productCode = normalizeCode(string(erpRow, "product_code", "UNKNOWN"));
    List<Map<String, Object>> processContexts = buildProcessContextsForProduct(productCode);
    LocalDate expectedStartDate = parseLocalDateFlexible(
      string(
        erpRow,
        "expected_start_date",
        string(erpRow, "planned_finish_date_1", string(erpRow, "order_date", null))
      ),
      null
    );
    LocalDate expectedFinishDate = parseLocalDateFlexible(
      string(
        erpRow,
        "expected_finish_date",
        string(erpRow, "planned_finish_date_2", string(erpRow, "planned_finish_date_1", null))
      ),
      null
    );
    String expectedStartTime = expectedStartDate == null ? null : toDateTime(expectedStartDate.toString(), "D", true);
    String expectedFinishTime = expectedFinishDate == null ? null : toDateTime(expectedFinishDate.toString(), "D", true);
    double totalQty = number(erpRow, "plan_qty", number(erpRow, "order_qty", 0d));
    double completedQty = number(erpRow, "workshop_completed_qty", number(erpRow, "completed_qty", 0d));
    double progressRate = totalQty > 0d ? Math.min(100d, Math.max(0d, (completedQty / totalQty) * 100d)) : 0d;
    String status = string(
      erpRow,
      "status",
      string(erpRow, "production_status", string(erpRow, "order_status", "OPEN"))
    );

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("order_no", orderNo);
    row.put("order_type", "production");
    row.put("line_no", string(erpRow, "source_line_no", "1"));
    row.put("product_code", productCode);
    row.put("product_name_cn", string(erpRow, "product_name_cn", productNameCn(productCode)));
    row.put("order_qty", totalQty);
    row.put("completed_qty", round2(completedQty));
    row.put("remaining_qty", round2(Math.max(0d, totalQty - completedQty)));
    row.put("progress_rate", round2(progressRate));
    row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
    row.put("expected_start_time", expectedStartTime);
    row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
    row.put("expected_finish_time", expectedFinishTime);
    row.put("workshop_codes", joinContextValues(processContexts, "workshop_codes"));
    row.put("line_codes", joinContextValues(processContexts, "line_codes"));
    row.put("process_codes", joinContextValues(processContexts, "process_code"));
    row.put("process_route_summary", summarizeProcessContexts(processContexts));
    row.put("process_contexts", processContexts);
    row.put(
      "expected_due_date",
      expectedFinishDate == null ? null : toDateTime(expectedFinishDate.toString(), "D", true)
    );
    row.put(
      "promised_due_date",
      expectedFinishDate == null ? null : toDateTime(expectedFinishDate.toString(), "D", true)
    );
    row.put("urgent_flag", 0);
    row.put("lock_flag", 0);
    row.put("frozen_flag", 0);
    row.put("status", status == null || status.isBlank() ? "OPEN" : status);
    row.put("customer_remark", string(erpRow, "customer_remark", ""));
    row.put("spec_model", string(erpRow, "spec_model", ""));
    row.put("production_batch_no", string(erpRow, "production_batch_no", ""));
    row.put("packaging_form", string(erpRow, "packaging_form", ""));
    row.put("sales_order_no", string(erpRow, "sales_order_no", string(erpRow, "source_sales_order_no", "")));
    row.put("workshop_outer_packaging_date", string(erpRow, "workshop_outer_packaging_date", ""));
    row.put("material_list_no", string(erpRow, "material_list_no", ""));
    row.put("erp_order_flag", 1);
    return localizeRow(row);
  }

  private Map<String, Object> toOrderPoolItem(MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = businessData(order);
    String productCode = orderPrimaryProductCode(order);
    List<Map<String, Object>> processContexts = buildProcessContextsForProduct(productCode);
    double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
    double completedQty = order.items.stream().mapToDouble(item -> item.completedQty).sum();
    double progressRate = totalQty > 0d ? Math.min(100d, Math.max(0d, (completedQty / totalQty) * 100d)) : 0d;
    LocalDate expectedStartDate = resolveExpectedStartDate(order);
    String expectedStartTime = expectedStartDate == null ? null : toDateTime(expectedStartDate.toString(), "D", true);
    String expectedFinishTime = estimateExpectedFinishTime(order);
    LocalDate expectedFinishDate = parseLocalDateFlexible(expectedFinishTime, null);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("order_no", order.orderNo);
    row.put("order_type", order.orderType);
    row.put("line_no", "1");
    row.put("product_code", order.items.isEmpty() ? "UNKNOWN" : order.items.get(0).productCode);
    row.put("product_name_cn", productNameCn(order.items.isEmpty() ? "UNKNOWN" : order.items.get(0).productCode));
    row.put("order_qty", totalQty);
    row.put("completed_qty", round2(completedQty));
    row.put("remaining_qty", round2(Math.max(0d, totalQty - completedQty)));
    row.put("progress_rate", round2(progressRate));
    row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
    row.put("expected_start_time", expectedStartTime);
    row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
    row.put("expected_finish_time", expectedFinishTime);
    row.put("workshop_codes", joinContextValues(processContexts, "workshop_codes"));
    row.put("line_codes", joinContextValues(processContexts, "line_codes"));
    row.put("process_codes", joinContextValues(processContexts, "process_code"));
    row.put("process_route_summary", summarizeProcessContexts(processContexts));
    row.put("process_contexts", processContexts);
    row.put("expected_due_date", toDateTime(order.dueDate.toString(), "D", true));
    row.put("promised_due_date", toDateTime(order.dueDate.toString(), "D", true));
    row.put("urgent_flag", order.urgent ? 1 : 0);
    row.put("lock_flag", order.lockFlag ? 1 : 0);
    row.put("frozen_flag", order.frozen ? 1 : 0);
    row.put("status", order.status);
    row.put("customer_remark", business.customerRemark);
    row.put("spec_model", business.specModel);
    row.put("production_batch_no", business.productionBatchNo);
    row.put("packaging_form", business.packagingForm);
    row.put("sales_order_no", business.salesOrderNo);
    row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
    return localizeRow(row);
  }

  private Map<String, Object> toScheduleMap(MvpDomain.ScheduleVersion schedule) {
    Map<String, Object> row = new LinkedHashMap<>();
    boolean showDetailedReasons = bool(schedule.metadata, "showDetailedReasons", true);
    row.put("requestId", schedule.requestId);
    row.put("request_id", schedule.requestId);
    row.put("versionNo", schedule.versionNo);
    row.put("version_no", schedule.versionNo);
    row.put("generatedAt", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
    row.put("generated_at", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
    row.put("shiftHours", schedule.shiftHours);
    row.put("shiftsPerDay", schedule.shiftsPerDay);
    row.put("shifts", deepCopyList(schedule.shifts));

    List<Map<String, Object>> tasks = new ArrayList<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      Map<String, Object> taskRow = new LinkedHashMap<>();
      taskRow.put("taskKey", task.taskKey);
      taskRow.put("orderNo", task.orderNo);
      taskRow.put("itemIndex", task.itemIndex);
      taskRow.put("stepIndex", task.stepIndex);
      taskRow.put("productCode", task.productCode);
      taskRow.put("processCode", task.processCode);
      taskRow.put("dependencyType", task.dependencyType);
      taskRow.put("predecessorTaskKey", task.predecessorTaskKey);
      taskRow.put("targetQty", task.targetQty);
      taskRow.put("producedQty", task.producedQty);
      taskRow.put("dependencyStatus", task.dependencyStatus);
      taskRow.put("dependency_status", task.dependencyStatus);
      taskRow.put("taskStatus", task.taskStatus);
      taskRow.put("task_status", task.taskStatus);
      taskRow.put("lastBlockReason", normalizeReasonCode(task.lastBlockReason));
      taskRow.put("last_block_reason", normalizeReasonCode(task.lastBlockReason));
      taskRow.put("lastBlockingDimension", showDetailedReasons ? task.lastBlockingDimension : null);
      taskRow.put("last_blocking_dimension", showDetailedReasons ? task.lastBlockingDimension : null);
      taskRow.put("lastBlockReasonDetail", showDetailedReasons ? task.lastBlockReasonDetail : null);
      taskRow.put("last_block_reason_detail", showDetailedReasons ? task.lastBlockReasonDetail : null);
      taskRow.put(
        "lastBlockEvidence",
        showDetailedReasons ? deepCopyMap(task.lastBlockEvidence == null ? Map.of() : task.lastBlockEvidence) : Map.of()
      );
      taskRow.put(
        "last_block_evidence",
        showDetailedReasons ? deepCopyMap(task.lastBlockEvidence == null ? Map.of() : task.lastBlockEvidence) : Map.of()
      );
      tasks.add(taskRow);
    }
    row.put("tasks", tasks);

    List<Map<String, Object>> allocations = new ArrayList<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      Map<String, Object> allocationRow = new LinkedHashMap<>();
      allocationRow.put("taskKey", allocation.taskKey);
      allocationRow.put("orderNo", allocation.orderNo);
      allocationRow.put("productCode", allocation.productCode);
      allocationRow.put("processCode", allocation.processCode);
      allocationRow.put("dependencyType", allocation.dependencyType);
      allocationRow.put("shiftId", allocation.shiftId);
      allocationRow.put("date", allocation.date);
      allocationRow.put("shiftCode", allocation.shiftCode);
      allocationRow.put("scheduledQty", allocation.scheduledQty);
      allocationRow.put("workersUsed", allocation.workersUsed);
      allocationRow.put("machinesUsed", allocation.machinesUsed);
      allocationRow.put("groupsUsed", allocation.groupsUsed);
      allocations.add(allocationRow);
    }
    row.put("allocations", allocations);

    Map<String, MvpDomain.ScheduleTask> taskByTaskKey = new HashMap<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      taskByTaskKey.put(task.taskKey, task);
    }
    List<Map<String, Object>> normalizedUnscheduled = new ArrayList<>();
    for (Map<String, Object> unscheduledRow : schedule.unscheduled) {
      String taskKey = firstString(unscheduledRow, "taskKey", "task_key");
      normalizedUnscheduled.add(normalizeUnscheduledRow(unscheduledRow, taskByTaskKey.get(taskKey), showDetailedReasons));
    }
    row.put("unscheduled", normalizedUnscheduled);
    row.put("metrics", deepCopyMap(schedule.metrics));
    row.put("metadata", deepCopyMap(schedule.metadata));
    row.put("status", schedule.status);
    row.put("basedOnVersion", schedule.basedOnVersion);
    row.put("based_on_version", schedule.basedOnVersion);
    row.put("ruleVersionNo", schedule.ruleVersionNo);
    row.put("rule_version_no", schedule.ruleVersionNo);
    row.put("publishTime", schedule.publishTime == null ? null : schedule.publishTime.toString());
    row.put("publish_time", schedule.publishTime == null ? null : schedule.publishTime.toString());
    row.put("createdBy", schedule.createdBy);
    row.put("created_by", schedule.createdBy);
    row.put("createdAt", schedule.createdAt == null ? null : schedule.createdAt.toString());
    row.put("created_at", schedule.createdAt == null ? null : schedule.createdAt.toString());
    row.put("rollbackFrom", schedule.rollbackFrom);
    row.put("rollback_from", schedule.rollbackFrom);
    return row;
  }

  private Map<String, Object> toReportingMap(MvpDomain.Reporting reporting) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("reportingId", reporting.reportingId);
    row.put("reporting_id", reporting.reportingId);
    row.put("request_id", reporting.requestId);
    row.put("orderNo", reporting.orderNo);
    row.put("order_no", reporting.orderNo);
    row.put("productCode", reporting.productCode);
    row.put("product_code", reporting.productCode);
    row.put("product_name_cn", productNameCn(reporting.productCode));
    row.put("processCode", reporting.processCode);
    row.put("process_code", reporting.processCode);
    row.put("process_name_cn", processNameCn(reporting.processCode));
    row.put("reportQty", reporting.reportQty);
    row.put("report_qty", reporting.reportQty);
    row.put("reportTime", reporting.reportTime.toString());
    row.put("report_time", reporting.reportTime.toString());
    row.put("triggered_replan_job_no", reporting.triggeredReplanJobNo);
    row.put("triggered_alert_id", reporting.triggeredAlertId);
    return localizeRow(row);
  }

  private void appendAudit(
    String entityType,
    String entityId,
    String action,
    String operator,
    String requestId,
    String reason
  ) {
    appendAudit(entityType, entityId, action, operator, requestId, reason, null);
  }

  private void appendAudit(
    String entityType,
    String entityId,
    String action,
    String operator,
    String requestId,
    String reason,
    Map<String, Object> perfContext
  ) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("entity_type", entityType);
    row.put("entity_id", entityId);
    row.put("action", action);
    row.put("action_name_cn", actionNameCn(action));
    row.put("operator", operator == null ? "system" : operator);
    row.put("request_id", requestId);
    row.put("operate_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("reason", reason);
    if (perfContext != null && !perfContext.isEmpty()) {
      row.put("perf_context", deepCopyMap(perfContext));
    }
    state.auditLogs.add(row);
  }

  private void appendInbox(String topic, String entityId, String requestId, String status, String errorMsg) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("message_id", "IN-" + entityId + "-" + (state.integrationInbox.size() + 1));
    row.put("source", "MES");
    row.put("target", "SCHEDULER");
    row.put("topic", topic);
    row.put("topic_name_cn", topicNameCn(topic));
    row.put("status", status);
    row.put("status_name_cn", statusNameCn(status));
    row.put("retry_count", 0);
    row.put("error_msg", errorMsg == null ? "" : errorMsg);
    row.put("request_id", requestId);
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    state.integrationInbox.add(row);
  }

  private void appendOutbox(String topic, String entityId, String requestId, String status, String errorMsg) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("message_id", "OUT-" + entityId + "-" + (state.integrationOutbox.size() + 1));
    row.put("source", "SCHEDULER");
    row.put("target", "ERP");
    row.put("topic", topic);
    row.put("topic_name_cn", topicNameCn(topic));
    row.put("status", status);
    row.put("status_name_cn", statusNameCn(status));
    row.put("retry_count", 0);
    row.put("error_msg", errorMsg == null ? "" : errorMsg);
    row.put("request_id", requestId);
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    state.integrationOutbox.add(row);
  }

  private boolean matchIntegrationFilters(Map<String, Object> row, Map<String, String> filters) {
    if (filters == null) {
      return true;
    }
    if (filters.containsKey("status") && !Objects.equals(filters.get("status"), row.get("status"))) {
      return false;
    }
    if (filters.containsKey("topic") && !Objects.equals(filters.get("topic"), row.get("topic"))) {
      return false;
    }
    return true;
  }

  private static CellStyle createTitleStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 14);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    setCellBorder(style);
    return style;
  }

  private static CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    setCellBorder(style);
    return style;
  }

  private static CellStyle createBodyStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    setCellBorder(style);
    return style;
  }

  private static void setCellBorder(CellStyle style) {
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
  }

  private static void writeRowValues(Row row, Map<String, Object> source, String[] keys, CellStyle style) {
    for (int i = 0; i < keys.length; i += 1) {
      writeCell(row, i, source.get(keys[i]), style);
    }
  }

  private static void writeCell(Row row, int columnIndex, Object value, CellStyle style) {
    Cell cell = row.createCell(columnIndex);
    if (style != null) {
      cell.setCellStyle(style);
    }
    if (value == null) {
      cell.setCellValue("");
      return;
    }
    if (value instanceof Number number) {
      cell.setCellValue(number.doubleValue());
      return;
    }
    if (value instanceof Boolean boolValue) {
      cell.setCellValue(boolValue);
      return;
    }
    cell.setCellValue(String.valueOf(value));
  }

  private Map<String, Object> localizeRow(Map<String, Object> source) {
    Map<String, Object> row = new LinkedHashMap<>(source);

    String productCode = firstString(row, "product_code", "productCode");
    if (productCode != null && !productCode.isBlank()) {
      row.putIfAbsent("product_name_cn", productNameCn(productCode));
    }

    String processCode = firstString(row, "process_code", "processCode");
    if (processCode != null && !processCode.isBlank()) {
      row.putIfAbsent("process_name_cn", processNameCn(processCode));
    }

    String status = firstString(row, "status", "order_status");
    if (status != null && !status.isBlank()) {
      row.putIfAbsent("status_name_cn", statusNameCn(status));
    }

    String alertType = firstString(row, "alert_type");
    if (alertType != null && !alertType.isBlank()) {
      row.put("alert_type_name_cn", alertTypeNameCn(alertType));
    }

    String severity = firstString(row, "severity");
    if (severity != null && !severity.isBlank()) {
      row.put("severity_name_cn", severityNameCn(severity));
    }

    String commandType = firstString(row, "command_type");
    if (commandType != null && !commandType.isBlank()) {
      row.putIfAbsent("command_type_name_cn", actionNameCn(commandType));
    }

    String action = firstString(row, "action");
    if (action != null && !action.isBlank()) {
      row.putIfAbsent("action_name_cn", actionNameCn(action));
    }

    String eventType = firstString(row, "event_type");
    if (eventType != null && !eventType.isBlank()) {
      row.put("event_type_name_cn", eventTypeNameCn(eventType));
    }

    String routeNo = firstString(row, "route_no");
    if (routeNo != null && !routeNo.isBlank()) {
      row.putIfAbsent("route_name_cn", routeNameCn(routeNo));
    }

    String topic = firstString(row, "topic");
    if (topic != null && !topic.isBlank()) {
      row.putIfAbsent("topic_name_cn", topicNameCn(topic));
    }

    String sourceSystem = firstString(row, "source");
    String targetSystem = firstString(row, "target");
    if (sourceSystem != null && !sourceSystem.isBlank()) {
      row.put("source_name_cn", systemNameCn(sourceSystem));
    }
    if (targetSystem != null && !targetSystem.isBlank()) {
      row.put("target_name_cn", systemNameCn(targetSystem));
    }
    if (sourceSystem != null && !sourceSystem.isBlank() && targetSystem != null && !targetSystem.isBlank()) {
      row.put("sync_flow_cn", syncFlowCn(sourceSystem, targetSystem));
    }

    String scenario = firstString(row, "scenario");
    if (scenario != null && !scenario.isBlank()) {
      row.putIfAbsent("scenario_name_cn", scenarioNameCn(scenario));
    }

    String dependencyType = firstString(row, "dependency_type");
    if (dependencyType != null && !dependencyType.isBlank()) {
      row.putIfAbsent("dependency_type_name_cn", dependencyNameCn(dependencyType));
    }

    String shiftCode = firstString(row, "shift_code", "shiftCode");
    if (shiftCode != null && !shiftCode.isBlank()) {
      row.putIfAbsent("shift_name_cn", shiftNameCn(shiftCode));
    }

    return row;
  }

  private static String firstString(Map<String, Object> row, String... keys) {
    if (row == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      Object value = row.get(key);
      if (value != null) {
        String text = String.valueOf(value);
        if (!text.isBlank()) {
          return text;
        }
      }
    }
    return null;
  }

  private static String productNameCn(String productCode) {
    return PRODUCT_NAME_CN.getOrDefault(productCode, productCode == null ? "" : productCode);
  }

  private static String processNameCn(String processCode) {
    return PROCESS_NAME_CN.getOrDefault(processCode, processCode == null ? "" : processCode);
  }

  private static String statusNameCn(String status) {
    return STATUS_NAME_CN.getOrDefault(status, status == null ? "" : status);
  }

  private static String actionNameCn(String action) {
    return ACTION_NAME_CN.getOrDefault(action, action == null ? "" : action);
  }

  private static String eventTypeNameCn(String eventType) {
    return EVENT_TYPE_NAME_CN.getOrDefault(eventType, eventType == null ? "" : eventType);
  }

  private static String topicNameCn(String topic) {
    return TOPIC_NAME_CN.getOrDefault(topic, topic == null ? "" : topic);
  }

  private static String systemNameCn(String system) {
    return SYSTEM_NAME_CN.getOrDefault(system, system == null ? "" : system);
  }

  private static String scenarioNameCn(String scenario) {
    return SCENARIO_NAME_CN.getOrDefault(scenario, scenario == null ? "" : scenario);
  }

  private static String shiftNameCn(String shiftCode) {
    return SHIFT_NAME_CN.getOrDefault(shiftCode, shiftCode == null ? "" : shiftCode);
  }

  private static String normalizeShiftCode(String shiftCode) {
    if (shiftCode == null) {
      return "";
    }
    String normalized = shiftCode.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "DAY" -> "D";
      case "NIGHT" -> "N";
      default -> normalized;
    };
  }

  private static String normalizeShiftCodeLabel(String shiftCode) {
    return switch (normalizeShiftCode(shiftCode)) {
      case "D" -> "DAY";
      case "N" -> "NIGHT";
      default -> shiftCode == null ? "" : shiftCode;
    };
  }

  private static int shiftSortIndex(String shiftCode) {
    return switch (normalizeShiftCode(shiftCode)) {
      case "D" -> 0;
      case "N" -> 1;
      default -> 9;
    };
  }

  private static String normalizeWeekendRestMode(String modeText) {
    if (modeText == null) {
      return WEEKEND_REST_MODE_DOUBLE;
    }
    String normalized = modeText.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case WEEKEND_REST_MODE_NONE -> WEEKEND_REST_MODE_NONE;
      case WEEKEND_REST_MODE_SINGLE -> WEEKEND_REST_MODE_SINGLE;
      default -> WEEKEND_REST_MODE_DOUBLE;
    };
  }

  private static String normalizeDateShiftMode(String modeText) {
    if (modeText == null) {
      return "";
    }
    String normalized = modeText.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case DATE_SHIFT_MODE_REST -> DATE_SHIFT_MODE_REST;
      case DATE_SHIFT_MODE_DAY -> DATE_SHIFT_MODE_DAY;
      case DATE_SHIFT_MODE_NIGHT -> DATE_SHIFT_MODE_NIGHT;
      case DATE_SHIFT_MODE_BOTH -> DATE_SHIFT_MODE_BOTH;
      default -> "";
    };
  }

  private static Map<String, String> normalizeDateShiftModeByDate(Object input) {
    Map<String, String> out = new LinkedHashMap<>();
    if (!(input instanceof Map<?, ?> rawMap)) {
      return out;
    }
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      String dateText = String.valueOf(entry.getKey() == null ? "" : entry.getKey()).trim();
      if (dateText.isBlank()) {
        continue;
      }
      try {
        LocalDate.parse(dateText);
      } catch (RuntimeException ignored) {
        continue;
      }
      String mode = normalizeDateShiftMode(String.valueOf(entry.getValue() == null ? "" : entry.getValue()));
      if (mode.isBlank()) {
        continue;
      }
      out.put(dateText, mode);
    }
    return out;
  }

  private static boolean isCnStatutoryHoliday(LocalDate date) {
    if (date == null) {
      return false;
    }
    return CN_STATUTORY_HOLIDAY_DATE_SET.contains(date.toString());
  }

  private String resolveDateShiftMode(LocalDate date) {
    if (date == null) {
      return DATE_SHIFT_MODE_DAY;
    }
    String manualMode = state.dateShiftModeByDate == null ? "" : normalizeDateShiftMode(state.dateShiftModeByDate.get(date.toString()));
    if (!manualMode.isBlank()) {
      return manualMode;
    }
    if (!state.skipStatutoryHolidays) {
      return DATE_SHIFT_MODE_DAY;
    }
    if (isCnStatutoryHoliday(date)) {
      return DATE_SHIFT_MODE_REST;
    }

    String weekendRestMode = normalizeWeekendRestMode(state.weekendRestMode);
    int weekday = date.getDayOfWeek().getValue();
    if (WEEKEND_REST_MODE_NONE.equals(weekendRestMode)) {
      return DATE_SHIFT_MODE_DAY;
    }
    if (WEEKEND_REST_MODE_SINGLE.equals(weekendRestMode)) {
      return weekday == 7 ? DATE_SHIFT_MODE_REST : DATE_SHIFT_MODE_DAY;
    }
    return (weekday == 6 || weekday == 7) ? DATE_SHIFT_MODE_REST : DATE_SHIFT_MODE_DAY;
  }

  private static boolean isShiftOpenInDateMode(String shiftCode, String modeText) {
    String normalizedShiftCode = normalizeShiftCode(shiftCode);
    String mode = normalizeDateShiftMode(modeText);
    if (DATE_SHIFT_MODE_REST.equals(mode)) {
      return false;
    }
    if (DATE_SHIFT_MODE_NIGHT.equals(mode)) {
      return "N".equals(normalizedShiftCode);
    }
    if (DATE_SHIFT_MODE_BOTH.equals(mode)) {
      return "D".equals(normalizedShiftCode) || "N".equals(normalizedShiftCode);
    }
    return "D".equals(normalizedShiftCode);
  }

  private static String dependencyNameCn(String dependencyType) {
    return DEPENDENCY_NAME_CN.getOrDefault(dependencyType, dependencyType == null ? "" : dependencyType);
  }

  private static String alertTypeNameCn(String alertType) {
    return ALERT_TYPE_NAME_CN.getOrDefault(alertType, alertType == null ? "" : alertType);
  }

  private static String severityNameCn(String severity) {
    return SEVERITY_NAME_CN.getOrDefault(severity, severity == null ? "" : severity);
  }

  private String workshopCodeForProcess(String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(processCode, true);
    if (!bindings.isEmpty()) {
      return normalizeCode(bindings.get(0).workshopCode);
    }
    return normalizeCode(defaultLineBindingForProcess(processCode).workshopCode);
  }

  private String lineCodeForProcess(String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(processCode, true);
    if (!bindings.isEmpty()) {
      return normalizeCode(bindings.get(0).lineCode);
    }
    return normalizeCode(defaultLineBindingForProcess(processCode).lineCode);
  }

  private String lineNameForCode(String lineCode) {
    String normalizedLineCode = normalizeCode(lineCode);
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      if (!normalizeCode(binding.lineCode).equals(normalizedLineCode)) {
        continue;
      }
      if (binding.lineName != null && !binding.lineName.isBlank()) {
        return binding.lineName.trim();
      }
    }
    return normalizedLineCode;
  }

  private String lineCodesForProcessSummary(String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(processCode, true);
    if (bindings.isEmpty()) {
      return normalizeCode(defaultLineBindingForProcess(processCode).lineCode);
    }
    List<String> lineCodes = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : bindings) {
      String lineCode = normalizeCode(binding.lineCode);
      if (!lineCode.isBlank() && !lineCodes.contains(lineCode)) {
        lineCodes.add(lineCode);
      }
    }
    return String.join(",", lineCodes);
  }

  private String workshopCodesForProcessSummary(String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(processCode, true);
    if (bindings.isEmpty()) {
      return normalizeCode(defaultLineBindingForProcess(processCode).workshopCode);
    }
    List<String> workshopCodes = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : bindings) {
      String workshopCode = normalizeCode(binding.workshopCode);
      if (!workshopCode.isBlank() && !workshopCodes.contains(workshopCode)) {
        workshopCodes.add(workshopCode);
      }
    }
    return String.join(",", workshopCodes);
  }

  private String workshopCodesSummaryAll() {
    List<String> workshops = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String workshopCode = normalizeCode(binding.workshopCode);
      if (!workshopCode.isBlank() && !workshops.contains(workshopCode)) {
        workshops.add(workshopCode);
      }
    }
    if (workshops.isEmpty()) {
      workshops.add("WS-PRODUCTION");
    }
    return String.join(",", workshops);
  }

  private List<MvpDomain.LineProcessBinding> lineBindingsForProcess(String processCode, boolean enabledOnly) {
    String normalizedProcessCode = normalizeCode(processCode);
    if (normalizedProcessCode.isBlank()) {
      return List.of();
    }
    List<MvpDomain.LineProcessBinding> out = new ArrayList<>();
    Set<String> dedupe = new HashSet<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String bindingProcessCode = normalizeCode(binding.processCode);
      if (!bindingProcessCode.equals(normalizedProcessCode)) {
        continue;
      }
      if (enabledOnly && !binding.enabled) {
        continue;
      }
      String workshopCode = normalizeCode(binding.workshopCode);
      String lineCode = normalizeCode(binding.lineCode);
      if (workshopCode.isBlank() || lineCode.isBlank()) {
        continue;
      }
      String key = workshopCode + "#" + lineCode + "#" + bindingProcessCode;
      if (!dedupe.add(key)) {
        continue;
      }
      out.add(
        new MvpDomain.LineProcessBinding(
          workshopCode,
          lineCode,
          binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim(),
          bindingProcessCode,
          binding.enabled
        )
      );
    }
    out.sort((a, b) -> {
      int byWorkshop = normalizeCode(a.workshopCode).compareTo(normalizeCode(b.workshopCode));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      return normalizeCode(a.lineCode).compareTo(normalizeCode(b.lineCode));
    });
    return out;
  }

  private MvpDomain.LineProcessBinding defaultLineBindingForProcess(String processCode) {
    String normalized = normalizeCode(processCode);
    String workshopCode = normalized.contains("STERILE") ? "WS-STERILE" : "WS-PRODUCTION";
    String lineCode = switch (normalized) {
      case "PROC_TUBE" -> "LINE-TUBE";
      case "PROC_ASSEMBLY" -> "LINE-ASSEMBLY";
      case "PROC_BALLOON" -> "LINE-BALLOON";
      case "PROC_STENT" -> "LINE-STENT";
      case "PROC_STERILE" -> "LINE-STERILE";
      default -> "LINE-MIXED";
    };
    return new MvpDomain.LineProcessBinding(workshopCode, lineCode, lineCode, normalized, true);
  }

  private static String routeNameCn(String routeNo) {
    if (routeNo != null && routeNo.startsWith("ROUTE-")) {
      String productCode = routeNo.substring("ROUTE-".length());
      return productNameCn(productCode) + "\u5de5\u827a\u8def\u7ebf";
    }
    return routeNo == null ? "" : routeNo;
  }

  private static String syncFlowCn(String source, String target) {
    return systemNameCn(source) + "\u5199\u5165\uff0c" + systemNameCn(target) + "\u8bfb\u53d6";
  }

  private Map<String, Object> runIdempotent(String requestId, String action, Supplier<Map<String, Object>> executor) {
    if (requestId == null || requestId.isBlank()) {
      return executor.get();
    }
    String key = action + "#" + requestId;
    if (state.idempotencyLedger.containsKey(key)) {
      return deepCopyMap(state.idempotencyLedger.get(key));
    }
    Map<String, Object> result = executor.get();
    state.idempotencyLedger.put(key, deepCopyMap(result));
    return deepCopyMap(result);
  }

  private static String toDateTime(String date, String shiftCode, boolean start) {
    LocalDate parsed = LocalDate.parse(date);
    int startHour = "N".equals(shiftCode) || "NIGHT".equals(shiftCode) ? 20 : 8;
    OffsetDateTime time = parsed.atTime(startHour, 0).atOffset(ZoneOffset.UTC);
    if (!start) {
      time = time.plusHours(12);
    }
    return time.toString();
  }

  private static int countCapacityBlocked(List<Map<String, Object>> unscheduled) {
    int count = 0;
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = resolveUnscheduledReasonCode(row);
      if (reasonCode == null) {
        continue;
      }
      if (
        reasonCode.startsWith("CAPACITY_")
          || "MATERIAL_SHORTAGE".equals(reasonCode)
          || "CAPACITY_LIMIT".equals(reasonCode)
      ) {
        count += 1;
      }
    }
    return count;
  }

  private static int countByReasonCodes(List<Map<String, Object>> unscheduled, Set<String> reasonCodes) {
    if (unscheduled == null || unscheduled.isEmpty() || reasonCodes == null || reasonCodes.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = resolveUnscheduledReasonCode(row);
      if (reasonCode != null && reasonCodes.contains(normalizeReasonCode(reasonCode))) {
        count += 1;
      }
    }
    return count;
  }

  private static Map<String, Integer> buildUnscheduledReasonDistribution(List<Map<String, Object>> unscheduled) {
    Map<String, Integer> distribution = new LinkedHashMap<>();
    if (unscheduled == null) {
      return distribution;
    }
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = resolveUnscheduledReasonCode(row);
      String normalized = normalizeReasonCode(reasonCode == null ? "UNKNOWN" : reasonCode);
      if (normalized.isBlank()) {
        normalized = "UNKNOWN";
      }
      distribution.merge(normalized, 1, Integer::sum);
    }
    return distribution;
  }

  private Map<String, Object> buildScheduleObservabilityMetrics(
    MvpDomain.ScheduleVersion schedule,
    Long candidateOrderCount,
    Long candidateTaskCount
  ) {
    List<Map<String, Object>> unscheduled = schedule.unscheduled == null ? List.of() : schedule.unscheduled;
    Map<String, Object> metrics = new LinkedHashMap<>();
    Map<String, Integer> unscheduledReasonDistribution = buildUnscheduledReasonDistribution(unscheduled);
    int lockedOrFrozenImpactCount = countByReasonCodes(
      unscheduled,
      Set.of("FROZEN_BY_POLICY", "LOCKED_PRESERVED")
    );
    double completionRate = round2(number(
      schedule.metrics,
      "schedule_completion_rate",
      number(schedule.metrics, "scheduleCompletionRate", 0d)
    ));
    int publishCount = countPublishActions();
    int rollbackCount = countRollbackActions();

    metrics.put("unscheduled_reason_distribution", new LinkedHashMap<>(unscheduledReasonDistribution));
    metrics.put("unscheduledReasonDistribution", new LinkedHashMap<>(unscheduledReasonDistribution));
    metrics.put("unscheduled_task_count", unscheduled.size());
    metrics.put("unscheduledTaskCount", unscheduled.size());
    metrics.put("locked_or_frozen_impact_count", lockedOrFrozenImpactCount);
    metrics.put("schedule_completion_rate", completionRate);
    metrics.put("scheduleCompletionRate", completionRate);
    if (candidateOrderCount != null) {
      metrics.put("candidate_order_count", candidateOrderCount);
    }
    if (candidateTaskCount != null) {
      metrics.put("candidate_task_count", candidateTaskCount);
    }
    metrics.put("publish_count", publishCount);
    metrics.put("rollback_count", rollbackCount);
    metrics.put("publish_rollback_count", publishCount + rollbackCount);
    metrics.put("replan_failure_rate", round2(calcReplanFailureRate()));
    metrics.put("api_error_rate", round2(calcApiErrorRate()));
    return metrics;
  }

  private int countPublishActions() {
    return countVersionActions(Set.of("PUBLISH_VERSION"));
  }

  private int countRollbackActions() {
    return countVersionActions(Set.of("ROLLBACK_VERSION"));
  }

  private int countVersionActions(Set<String> actions) {
    if (actions == null || actions.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (Map<String, Object> row : state.auditLogs) {
      String action = normalizeCode(firstString(row, "action"));
      if (actions.contains(action)) {
        count += 1;
      }
    }
    return count;
  }

  private double calcReplanFailureRate() {
    int total = state.replanJobs.size();
    if (total <= 0) {
      return 0d;
    }
    int failed = 0;
    for (Map<String, Object> row : state.replanJobs) {
      if ("FAILED".equals(normalizeCode(firstString(row, "status")))) {
        failed += 1;
      }
    }
    return (failed * 100d) / total;
  }

  private double calcApiErrorRate() {
    int total = state.integrationInbox.size() + state.integrationOutbox.size() + state.replanJobs.size();
    if (total <= 0) {
      return 0d;
    }
    int failed = 0;
    for (Map<String, Object> row : state.integrationInbox) {
      if ("FAILED".equals(normalizeCode(firstString(row, "status")))) {
        failed += 1;
      }
    }
    for (Map<String, Object> row : state.integrationOutbox) {
      if ("FAILED".equals(normalizeCode(firstString(row, "status")))) {
        failed += 1;
      }
    }
    for (Map<String, Object> row : state.replanJobs) {
      if ("FAILED".equals(normalizeCode(firstString(row, "status")))) {
        failed += 1;
      }
    }
    return (failed * 100d) / total;
  }

  private static String scheduleReasonNameCn(String reasonCode) {
    String normalized = normalizeCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_MANPOWER" -> "瑜版挸澧犻悵顓燁偧娴滃搫濮忔稉宥堝喕閿涘本妫ゅ▔鏇犳埛缂侇厽甯撴禍";
      case "CAPACITY_MACHINE" -> "瑜版挸澧犻悵顓燁偧鐠佹儳顦稉宥堝喕閿涘本妫ゅ▔鏇犳埛缂侇厽甯撴禍";
      case "MATERIAL_SHORTAGE" -> "瑜版挸澧犻悵顓燁偧閻椻晜鏋℃稉宥堝喕閿涘本妫ゅ▔鏇犳埛缂侇厽甯撴禍";
      case "COMPONENT_SHORTAGE" -> "BOM缂佸嫪娆㈤張顏堢秷婵傛鍨ㄩ張顏勫煂閺傛瑱绱濊ぐ鎾冲閻濐厽顐奸弮鐘崇《閹烘帊楠";
      case "TRANSFER_CONSTRAINT" -> "閸欐娓剁亸蹇氭祮鏉╂劖澹掗柌蹇嬧偓浣瑰闂傚鐡戝鍛灗閺堚偓鐏忓繑澹掑▎锟犳閸掕绱濊ぐ鎾冲閻濐厽顐奸弳鍌欑瑝閸欘垱甯";
      case "BEFORE_EXPECTED_START" -> "閺堫亜鍩岀拋銏犲礋妫板嫯顓稿鈧慨瀣闂傝揪绱濊ぐ鎾冲閻濐厽顐奸弳鍌欑瑝閹烘帊楠";
      case "DEPENDENCY_BLOCKED", "DEPENDENCY_LIMIT" -> "閸欐澧犳惔蹇撲紣鎼村繒瀹抽弶鐕傜礉閸氬骸绨弳鍌涙娑撳秴褰茬紒褏鐢婚幒鎺嶉獓";
      case "FROZEN_BY_POLICY" -> "鐠併垹宕熸径鍕艾閸愯崵绮ㄧ粵鏍殣娑擃叏绱濇稉宥呭棘娑撳孩婀版潪顔藉笓娴";
      case "LOCKED_PRESERVED" -> "闁夸礁宕熼幐澶婄唨缁惧じ绻氶悾娆欑礉閺堫剝鐤嗘稉宥夊櫢閹烘帟顕氶柈銊ュ瀻娴犺濮";
      case "URGENT_GUARANTEE" -> "閸旂姵鈧儴顓归崡鏇⌒曢崣鎴滅箽鎼存洝绁┃鎰灗閺堚偓娴ｅ孩妫╂禍褍鍤穱婵囧Б";
      case "LOCK_PREEMPTED_BY_URGENT" -> "娑撹桨绻氶梾婊冨閹儴顓归崡鏇礉闁夸礁宕熺挧鍕爱鐞氼偊鍎撮崚鍡氼唨娴";
      case "CAPACITY_LIMIT", "CAPACITY_UNKNOWN" -> "瑜版挸澧犻悵顓燁偧閸欘垳鏁ゆ禍褑鍏樻稉宥堝喕閿涘牅姹夐崝?鐠佹儳顦?閻椻晜鏋￠崣妤呮閿";
      case "UNKNOWN", "" -> "閺堫亝鐖ｇ拋鏉垮斧閸";
      default -> normalized;
    };
  }

  private static String resolveUnscheduledReasonCode(Map<String, Object> unscheduledRow) {
    if (unscheduledRow == null) {
      return null;
    }
    String reasonCode = normalizeCode(firstString(unscheduledRow, "reason_code", "reasonCode", "last_block_reason", "lastBlockReason"));
    if (!reasonCode.isBlank()) {
      return normalizeReasonCode(reasonCode);
    }
    Object reasonsObj = unscheduledRow.get("reasons");
    if (reasonsObj instanceof List<?> reasons) {
      for (Object reason : reasons) {
        String code = normalizeCode(String.valueOf(reason));
        if (!code.isBlank()) {
          return normalizeReasonCode(code);
        }
      }
    }
    return null;
  }

  private static String normalizeReasonCode(String reasonCode) {
    String normalized = normalizeCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_LIMIT" -> "CAPACITY_UNKNOWN";
      case "DEPENDENCY_LIMIT" -> "DEPENDENCY_BLOCKED";
      default -> normalized;
    };
  }

  private static String toLegacyReasonCode(String reasonCode) {
    String normalized = normalizeReasonCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_MANPOWER", "CAPACITY_MACHINE", "MATERIAL_SHORTAGE", "COMPONENT_SHORTAGE", "CAPACITY_UNKNOWN" -> "CAPACITY_LIMIT";
      case "DEPENDENCY_BLOCKED", "TRANSFER_CONSTRAINT", "BEFORE_EXPECTED_START" -> "DEPENDENCY_LIMIT";
      default -> normalized;
    };
  }

  private Map<String, Object> normalizeUnscheduledRow(
    Map<String, Object> unscheduledRow,
    MvpDomain.ScheduleTask task,
    boolean showDetailedReasons
  ) {
    Map<String, Object> normalized = deepCopyMap(unscheduledRow);
    String reasonCode = resolveUnscheduledReasonCode(normalized);
    String dependencyStatus = resolveTaskDependencyStatus(task, normalized);
    String taskStatus = resolveTaskStatus(task, normalized, false);
    String lastBlockReason = resolveTaskLastBlockReason(task, normalized, reasonCode);
    String reasonDetail = firstString(normalized, "reason_detail", "reasonDetail");
    if ((reasonDetail == null || reasonDetail.isBlank()) && task != null) {
      reasonDetail = task.lastBlockReasonDetail;
    }
    String blockingDimension = firstString(normalized, "blocking_dimension", "blockingDimension");
    if ((blockingDimension == null || blockingDimension.isBlank()) && task != null) {
      blockingDimension = task.lastBlockingDimension;
    }

    Object evidenceRaw = normalized.get("evidence");
    Map<String, Object> evidence;
    if (evidenceRaw instanceof Map<?, ?>) {
      evidence = objectMapper.convertValue(evidenceRaw, new TypeReference<LinkedHashMap<String, Object>>() {});
    } else if (task != null && task.lastBlockEvidence != null) {
      evidence = deepCopyMap(task.lastBlockEvidence);
    } else {
      evidence = new LinkedHashMap<>();
    }

    List<String> reasons = new ArrayList<>();
    Object reasonsObj = normalized.get("reasons");
    if (reasonsObj instanceof List<?> reasonList) {
      for (Object reason : reasonList) {
        String code = normalizeReasonCode(String.valueOf(reason));
        if (!code.isBlank() && !reasons.contains(code)) {
          reasons.add(code);
        }
      }
    }
    if (reasonCode != null && !reasonCode.isBlank() && !reasons.contains(reasonCode)) {
      reasons.add(0, reasonCode);
    }
    if (!reasons.isEmpty()) {
      String legacyReason = toLegacyReasonCode(reasons.get(0));
      if (!legacyReason.isBlank() && !reasons.contains(legacyReason)) {
        reasons.add(legacyReason);
      }
    }

    normalized.put("reason_code", reasonCode);
    normalized.put("reasonCode", reasonCode);
    normalized.put("dependency_status", dependencyStatus);
    normalized.put("dependencyStatus", dependencyStatus);
    normalized.put("task_status", taskStatus);
    normalized.put("taskStatus", taskStatus);
    normalized.put("last_block_reason", lastBlockReason);
    normalized.put("lastBlockReason", lastBlockReason);
    normalized.put("reasons", reasons);

    if (showDetailedReasons) {
      normalized.put("reason_detail", reasonDetail);
      normalized.put("reasonDetail", reasonDetail);
      normalized.put("blocking_dimension", blockingDimension);
      normalized.put("blockingDimension", blockingDimension);
      normalized.put("evidence", evidence);
    } else {
      normalized.remove("reason_detail");
      normalized.remove("reasonDetail");
      normalized.remove("blocking_dimension");
      normalized.remove("blockingDimension");
      normalized.put("evidence", new LinkedHashMap<>());
    }
    return normalized;
  }

  private static String resolveTaskDependencyStatus(MvpDomain.ScheduleTask task, Map<String, Object> unscheduledRow) {
    String dependencyStatus = task == null ? null : normalizeCode(task.dependencyStatus);
    if (dependencyStatus == null || dependencyStatus.isBlank()) {
      dependencyStatus = normalizeCode(firstString(unscheduledRow, "dependency_status", "dependencyStatus"));
    }
    if (dependencyStatus == null || dependencyStatus.isBlank()) {
      return "READY";
    }
    return dependencyStatus;
  }

  private static String resolveTaskLastBlockReason(
    MvpDomain.ScheduleTask task,
    Map<String, Object> unscheduledRow,
    String fallbackReasonCode
  ) {
    String reasonCode = normalizeReasonCode(firstString(unscheduledRow, "last_block_reason", "lastBlockReason"));
    if ((reasonCode == null || reasonCode.isBlank()) && task != null) {
      reasonCode = normalizeReasonCode(task.lastBlockReason);
    }
    if ((reasonCode == null || reasonCode.isBlank()) && fallbackReasonCode != null) {
      reasonCode = normalizeReasonCode(fallbackReasonCode);
    }
    if (reasonCode == null || reasonCode.isBlank()) {
      return null;
    }
    return reasonCode;
  }

  private static String resolveTaskStatus(
    MvpDomain.ScheduleTask task,
    Map<String, Object> unscheduledRow,
    boolean hasAllocation
  ) {
    String taskStatus = task == null ? null : normalizeCode(task.taskStatus);
    if (taskStatus == null || taskStatus.isBlank()) {
      taskStatus = normalizeCode(firstString(unscheduledRow, "task_status", "taskStatus"));
    }
    if (taskStatus == null || taskStatus.isBlank()) {
      if (!hasAllocation) {
        return "UNSCHEDULED";
      }
      return unscheduledRow == null ? "READY" : "PARTIALLY_ALLOCATED";
    }
    return taskStatus;
  }

  private static String topReasonCode(Map<String, Integer> reasonCountByCode) {
    if (reasonCountByCode == null || reasonCountByCode.isEmpty()) {
      return null;
    }
    return reasonCountByCode.entrySet().stream()
      .sorted((a, b) -> {
        int byCount = Integer.compare(b.getValue(), a.getValue());
        if (byCount != 0) {
          return byCount;
        }
        return a.getKey().compareTo(b.getKey());
      })
      .map(Map.Entry::getKey)
      .findFirst()
      .orElse(null);
  }

  private static String buildProcessAllocationExplainCn(
    String processCode,
    double targetQty,
    double scheduledQty,
    double unscheduledQty,
    int orderCount,
    String topReasonCode
  ) {
    String processName = processNameCn(processCode);
    if (targetQty <= 1e-9) {
      return processName + "閸︺劍婀板▎鈩冨笓娴溠傝厬濞屸剝婀佸鍛瀻闁板秶娲伴弽鍥櫤閵";
    }

    double scheduleRate = Math.max(0d, Math.min(100d, (scheduledQty / targetQty) * 100d));
    if (unscheduledQty <= 1e-9) {
      return processName +
      "閻╊喗鐖ｉ柌" + formatQtyText(targetQty) +
      "閿涘苯鍑￠崚鍡涘帳" + formatQtyText(scheduledQty) +
      "閿" + formatPercentText(scheduleRate) +
      "閿涘绱濆☉澶婂挤" + orderCount + "娑擃亣顓归崡鏇礉瑜版挸澧犵痪锔芥将娑撳鍑￠崗銊╁劥鐟曞棛娲婇妴";
    }

    return processName +
    "閻╊喗鐖ｉ柌" + formatQtyText(targetQty) +
    "閿涘苯鍑￠崚鍡涘帳" + formatQtyText(scheduledQty) +
    "閿" + formatPercentText(scheduleRate) +
    "閿涘绱濇禒宥嗘箒" + formatQtyText(unscheduledQty) +
    "閺堫亝甯撻敍灞煎瘜鐟曚礁褰堥垾" + scheduleReasonNameCn(topReasonCode) + "閳ユ繂濂栭崫宥冣偓";
  }

  private static String formatQtyText(double value) {
    double rounded = round2(value);
    long intValue = Math.round(rounded);
    if (Math.abs(rounded - intValue) < 1e-9) {
      return String.valueOf(intValue);
    }
    return String.format(Locale.ROOT, "%.2f", rounded);
  }

  private static String formatPercentText(double value) {
    double rounded = round2(value);
    long intValue = Math.round(rounded);
    if (Math.abs(rounded - intValue) < 1e-9) {
      return intValue + "%";
    }
    return String.format(Locale.ROOT, "%.2f%%", rounded);
  }

  private static double estimateResourceCapacity(
    MvpDomain.ProcessConfig processConfig,
    int workersAvailable,
    int machinesAvailable
  ) {
    if (processConfig == null) {
      return 0d;
    }
    int groupsByWorkers = workersAvailable / Math.max(1, processConfig.requiredWorkers);
    int groupsByMachines = machinesAvailable / Math.max(1, processConfig.requiredMachines);
    int maxGroups = Math.max(0, Math.min(groupsByWorkers, groupsByMachines));
    return maxGroups * processConfig.capacityPerShift;
  }

  private static double calcTaskProducedBeforeShift(
    MvpDomain.Allocation current,
    List<MvpDomain.Allocation> taskAllocations,
    Map<String, Integer> shiftIndexByShiftId
  ) {
    if (current == null || taskAllocations == null || taskAllocations.isEmpty()) {
      return 0d;
    }
    int currentShiftIndex = shiftIndexByShiftId.getOrDefault(current.shiftId, Integer.MAX_VALUE);
    double produced = 0d;
    for (MvpDomain.Allocation row : taskAllocations) {
      int rowShiftIndex = shiftIndexByShiftId.getOrDefault(row.shiftId, Integer.MAX_VALUE);
      if (rowShiftIndex < currentShiftIndex) {
        produced += row.scheduledQty;
      }
    }
    return produced;
  }

  private static String buildMaxAllocationExplainCn(
    String processCode,
    MvpDomain.Allocation maxAllocation,
    double taskRemainingBeforeShift,
    double resourceCapacity,
    double materialAvailable
  ) {
    if (maxAllocation == null) {
      return processNameCn(processCode) + "閺嗗倹妫ら崣顖澬掗柌濠勬畱瀹勬澘鈧厧鍨庨柊宥堫唶瑜版洏鈧";
    }

    double peakQty = round2(maxAllocation.scheduledQty);
    double remaining = round2(Math.max(0d, taskRemainingBeforeShift));
    double resourceCap = round2(Math.max(0d, resourceCapacity));
    double materialCap = round2(Math.max(0d, materialAvailable));

    double minCap = Double.POSITIVE_INFINITY;
    String dominant = "";
    if (remaining > 1e-9 && remaining < minCap) {
      minCap = remaining;
      dominant = "娴犺濮熼崷銊嚉閻濐厽顐奸崜宥囨畱閸撯晙缍戦崣顖涘笓闁";
    }
    if (resourceCap > 1e-9 && resourceCap < minCap) {
      minCap = resourceCap;
      dominant = "鐠囥儳褰▎锛勬畱娴滃搫濮?鐠佹儳顦挧鍕爱閼宠棄濮忔稉濠囨";
    }
    if (materialCap > 1e-9 && materialCap < minCap) {
      minCap = materialCap;
      dominant = "鐠囥儳褰▎鈥冲讲閻劎澧块弬娆庣瑐闂";
    }
    if (dominant.isBlank()) {
      dominant = "娴溿倖婀℃导妯哄帥缁狙佲偓浣稿鎼村繑鏂佺悰宀勫櫤娑撳氦绁┃鎰閺夌喓娈戠紒鐓庢値楠炲疇銆€";
    }

    return processNameCn(processCode) +
    "閸" + maxAllocation.date + shiftNameCn(maxAllocation.shiftCode) +
    "鐎电顓归崡" + maxAllocation.orderNo +
    "娴溠呮晸閸楁洘顐煎畡鏉库偓鐓庡瀻闁" + formatQtyText(peakQty) +
    "閵嗗倸缍嬮悵顓濇崲閸斺€冲⒖娴ｆ瑥褰查幒鎺楀櫤缁" + formatQtyText(remaining) +
    "閿涘矁绁┃鎰讲閺€顖涙嫼娑撳﹪妾虹痪" + formatQtyText(resourceCap) +
    "閿涘瞼澧块弬娆忓讲閻劑鍣虹痪" + formatQtyText(materialCap) +
    "閿涙稓鐣诲▔鏇熷瘻閸欘垵顢戞稉濠囨閸欐牗娓剁亸蹇撯偓鑹扮箻鐞涘苯鍨庨柊宥忕礉閸ョ姵顒濋張顒侇偧瀹勬澘鈧厧褰堥垾" + dominant + "閳ユ繀瀵岀€电鈧";
  }

  private int generateDailySalesAndProductionOrders(LocalDate businessDate, int dailySales, Random random, String requestId) {
    int created = 0;
    for (int i = 0; i < dailySales; i += 1) {
      String productCode = pickProductCode(random);
      boolean urgent = random.nextDouble() < 0.2d;
      double qty = (2 + random.nextInt(10)) * 100d;
      LocalDate dueDate = businessDate.plusDays(urgent ? 1 + random.nextInt(2) : 2 + random.nextInt(5));

      String salesOrderNo = "SO-SIM-%05d".formatted(salesSeq.incrementAndGet());
      String productionOrderNo = "MO-SIM-%05d".formatted(productionSeq.incrementAndGet());

      Map<String, Object> salesOrder = new LinkedHashMap<>();
      salesOrder.put("sales_order_no", salesOrderNo);
      salesOrder.put("line_no", "1");
      salesOrder.put("product_code", productCode);
      salesOrder.put("product_name_cn", productNameCn(productCode));
      salesOrder.put("order_qty", qty);
      salesOrder.put("order_date", toDateTime(businessDate.toString(), "D", true));
      salesOrder.put("expected_due_date", toDateTime(dueDate.toString(), "D", true));
      salesOrder.put("requested_ship_date", toDateTime(dueDate.toString(), "N", true));
      salesOrder.put("urgent_flag", urgent ? 1 : 0);
      salesOrder.put("order_status", "OPEN");
      salesOrder.put("source", "SIMULATION");
      simulationState.salesOrders.add(salesOrder);

      MvpDomain.Order order = new MvpDomain.Order(
        productionOrderNo,
        "production",
        dueDate,
        businessDate,
        urgent,
        false,
        false,
        "OPEN",
        List.of(new MvpDomain.OrderItem(productCode, qty, 0d)),
        buildSimulationBusinessData(businessDate, dueDate, salesOrderNo, productCode, qty)
      );
      state.orders.add(order);

      appendSimulationEvent(
        businessDate,
        "SALES_RECEIVED",
        "\u63a5\u6536\u5230\u968f\u673a\u9500\u552e\u8ba2\u5355\u3002",
        requestId,
        Map.of("sales_order_no", salesOrderNo, "product_code", productCode, "order_qty", qty)
      );
      appendSimulationEvent(
        businessDate,
        "ORDER_CONVERTED",
        "\u9500\u552e\u8ba2\u5355\u5df2\u8f6c\u6362\u4e3a\u751f\u4ea7\u8ba2\u5355\u3002",
        requestId,
        Map.of("sales_order_no", salesOrderNo, "production_order_no", productionOrderNo)
      );
      created += 1;
    }
    return created;
  }

  private Map<String, Object> rebuildPlanningHorizon(
    LocalDate startDate,
    String scenario,
    Random random,
    String requestId
  ) {
    state.startDate = startDate;
    int horizonDays = Math.max(1, state.horizonDays);
    int shiftsPerDay = Math.max(1, Math.min(2, state.shiftsPerDay));
    String[] shiftCodes = {"D", "N"};
    double capacityFactor = scenarioCapacityFactor(scenario, random);

    String breakdownProcess = null;
    if ("BREAKDOWN".equals(scenario) && !state.processes.isEmpty()) {
      breakdownProcess = state.processes.get(random.nextInt(state.processes.size())).processCode;
    }

    state.shiftCalendar = new ArrayList<>();
    state.workerPools = new ArrayList<>();
    state.machinePools = new ArrayList<>();
    state.initialWorkerOccupancy = new ArrayList<>();
    state.initialMachineOccupancy = new ArrayList<>();
    state.materialAvailability = new ArrayList<>();

    for (int i = 0; i < horizonDays; i += 1) {
      LocalDate date = startDate.plusDays(i);
      for (int j = 0; j < shiftsPerDay; j += 1) {
        String shiftCode = shiftCodes[j];
        state.shiftCalendar.add(new MvpDomain.ShiftRow(date, shiftCode, true));

        for (MvpDomain.ProcessConfig process : state.processes) {
          int baseWorkers = BASE_WORKERS_BY_PROCESS.getOrDefault(process.processCode, Math.max(2, process.requiredWorkers * 3));
          int baseMachines = BASE_MACHINES_BY_PROCESS.getOrDefault(process.processCode, Math.max(1, process.requiredMachines * 2));
          int workers = Math.max(process.requiredWorkers, (int) Math.round(baseWorkers * capacityFactor));
          int machines = Math.max(process.requiredMachines, (int) Math.round(baseMachines * capacityFactor));

          if (breakdownProcess != null
            && breakdownProcess.equals(process.processCode)
            && i == 0
            && "D".equals(shiftCode)) {
            workers = Math.max(1, workers / 3);
            machines = 0;
          }

          state.workerPools.add(new MvpDomain.ResourceRow(date, shiftCode, process.processCode, workers));
          state.machinePools.add(new MvpDomain.ResourceRow(date, shiftCode, process.processCode, machines));
        }

        double materialQty = switch (scenario) {
          case "TIGHT" -> 4200d;
          case "BREAKDOWN" -> 4600d;
          default -> 5000d;
        };
        for (Map.Entry<String, List<MvpDomain.ProcessStep>> route : state.processRoutes.entrySet()) {
          for (MvpDomain.ProcessStep step : route.getValue()) {
            state.materialAvailability.add(
              new MvpDomain.MaterialRow(date, shiftCode, route.getKey(), step.processCode, materialQty)
            );
          }
        }
      }
    }

    appendSimulationEvent(
      startDate,
      "CAPACITY_CHANGED",
      "\u5df2\u5e94\u7528\u4ea7\u80fd\u573a\u666f\u3002",
      requestId,
      Map.of(
        "scenario", scenario,
        "capacity_factor", round2(capacityFactor),
        "breakdown_process", breakdownProcess == null ? "" : breakdownProcess
      )
    );

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("scenario", scenario);
    out.put("capacity_factor", round2(capacityFactor));
    out.put("breakdown_process", breakdownProcess);
    return out;
  }

  private int simulateDailyReporting(
    LocalDate businessDate,
    String versionNo,
    String scenario,
    Random random,
    String requestIdPrefix
  ) {
    MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);
    Map<String, Map<String, Double>> plannedByOrderProcess = new LinkedHashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      String orderNo = allocation.orderNo;
      String processCode = normalizeCode(allocation.processCode);
      if (orderNo == null || orderNo.isBlank() || processCode.isBlank()) {
        continue;
      }
      plannedByOrderProcess
        .computeIfAbsent(orderNo, key -> new LinkedHashMap<>())
        .merge(processCode, allocation.scheduledQty, Double::sum);
    }

    Map<String, Double> cumulativeReportedByOrderProcess = new HashMap<>();
    for (MvpDomain.Reporting reporting : state.reportings) {
      String key = orderProcessKey(reporting.orderNo, reporting.processCode);
      cumulativeReportedByOrderProcess.merge(key, reporting.reportQty, Double::sum);
    }

    int reportingCount = 0;
    for (Map.Entry<String, Map<String, Double>> orderEntry : plannedByOrderProcess.entrySet()) {
      String orderNo = orderEntry.getKey();
      Map<String, Double> processPlan = orderEntry.getValue();
      MvpDomain.Order order = state.orders.stream().filter(it -> it.orderNo.equals(orderNo)).findFirst().orElse(null);
      if (order == null || order.items.isEmpty()) {
        continue;
      }
      double orderQty = order.items.stream().mapToDouble(item -> item.qty).sum();
      if (orderQty <= 0d) {
        continue;
      }
      String productCode = order.items.get(0).productCode;

      List<MvpDomain.ProcessStep> route = state.processRoutes.getOrDefault(productCode, List.of());
      Set<String> handled = new HashSet<>();
      String predecessorProcessCode = null;

      for (MvpDomain.ProcessStep step : route) {
        String processCode = normalizeCode(step.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        if (processPlan.containsKey(processCode)) {
          String requestId = requestIdPrefix + ":" + reportingCount;
          if (
            createSimulatedReporting(
              orderNo,
              productCode,
              processCode,
              processPlan.getOrDefault(processCode, 0d),
              orderQty,
              predecessorProcessCode,
              scenario,
              random,
              requestId,
              cumulativeReportedByOrderProcess
            )
          ) {
            reportingCount += 1;
          }
          handled.add(processCode);
        }
        predecessorProcessCode = processCode;
      }

      for (Map.Entry<String, Double> processEntry : processPlan.entrySet()) {
        String processCode = processEntry.getKey();
        if (handled.contains(processCode)) {
          continue;
        }
        String requestId = requestIdPrefix + ":" + reportingCount;
        if (
          createSimulatedReporting(
            orderNo,
            productCode,
            processCode,
            processEntry.getValue(),
            orderQty,
            null,
            scenario,
            random,
            requestId,
            cumulativeReportedByOrderProcess
          )
        ) {
          reportingCount += 1;
        }
      }
    }

    appendSimulationEvent(
      businessDate,
      "EXECUTION_PROGRESS",
      "\u5df2\u6839\u636e\u4eff\u771f\u6267\u884c\u7ed3\u679c\u81ea\u52a8\u751f\u6210\u62a5\u5de5\u3002",
      requestIdPrefix,
      Map.of("reporting_count", reportingCount, "version_no", versionNo)
    );
    return reportingCount;
  }

  private boolean createSimulatedReporting(
    String orderNo,
    String productCode,
    String processCode,
    double plannedQty,
    double orderQty,
    String predecessorProcessCode,
    String scenario,
    Random random,
    String requestId,
    Map<String, Double> cumulativeReportedByOrderProcess
  ) {
    String normalizedProcessCode = normalizeCode(processCode);
    if (normalizedProcessCode.isBlank() || plannedQty <= 0d || orderQty <= 0d) {
      return false;
    }

    double executionRate = scenarioExecutionRate(scenario, random);
    double simulatedQty = Math.round(plannedQty * executionRate);
    if (simulatedQty <= 0d) {
      return false;
    }

    String processKey = orderProcessKey(orderNo, normalizedProcessCode);
    double alreadyReported = cumulativeReportedByOrderProcess.getOrDefault(processKey, 0d);
    double remainingByOrder = Math.max(0d, orderQty - alreadyReported);
    if (remainingByOrder <= 0d) {
      return false;
    }

    double cappedQty = Math.min(simulatedQty, remainingByOrder);
    if (predecessorProcessCode != null && !predecessorProcessCode.isBlank()) {
      String predecessorKey = orderProcessKey(orderNo, predecessorProcessCode);
      double predecessorReported = cumulativeReportedByOrderProcess.getOrDefault(predecessorKey, 0d);
      double availableWip = Math.max(0d, predecessorReported - alreadyReported);
      cappedQty = Math.min(cappedQty, availableWip);
    }

    long reportQty = (long) Math.floor(cappedQty);
    if (reportQty <= 0L) {
      return false;
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("order_no", orderNo);
    payload.put("product_code", productCode);
    payload.put("process_code", normalizedProcessCode);
    payload.put("report_qty", reportQty);
    recordReporting(payload, requestId, "simulator");
    cumulativeReportedByOrderProcess.put(processKey, alreadyReported + reportQty);
    return true;
  }

  private void refreshOrderStatuses(LocalDate businessDate) {
    for (MvpDomain.Order order : state.orders) {
      if (isOrderDone(order)) {
        order.status = "DONE";
      } else if (order.dueDate.isBefore(businessDate)) {
        order.status = "DELAY";
      } else if (order.items.stream().anyMatch(item -> item.completedQty > 0d)) {
        order.status = "IN_PROGRESS";
      } else if ("DELAY".equals(order.status)) {
        order.status = "OPEN";
      }
    }
  }

  private int countDelayedOrders(LocalDate businessDate) {
    int count = 0;
    for (MvpDomain.Order order : state.orders) {
      if (order.dueDate.isBefore(businessDate) && !isOrderDone(order)) {
        count += 1;
      }
    }
    return count;
  }

  private static boolean hasRemainingQty(MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return false;
    }
    for (MvpDomain.OrderItem item : order.items) {
      if (item == null) {
        continue;
      }
      if (item.qty - item.completedQty > 1e-9) {
        return true;
      }
    }
    return false;
  }

  private static boolean isOrderDone(MvpDomain.Order order) {
    for (MvpDomain.OrderItem item : order.items) {
      if (item.completedQty + 1e-9 < item.qty) {
        return false;
      }
    }
    return true;
  }

  private void ensureManualSimulationSnapshot() {
    if (manualSimulationSnapshot != null) {
      return;
    }
    ManualSimulationSnapshot snapshot = new ManualSimulationSnapshot();
    snapshot.state = deepCopyState(state);
    snapshot.simulationState = deepCopySimulationState(simulationState);
    snapshot.reportingSeq = reportingSeq.get();
    snapshot.replanSeq = replanSeq.get();
    snapshot.alertSeq = alertSeq.get();
    snapshot.dispatchSeq = dispatchSeq.get();
    snapshot.dispatchApprovalSeq = dispatchApprovalSeq.get();
    snapshot.salesSeq = salesSeq.get();
    snapshot.productionSeq = productionSeq.get();
    snapshot.simulationEventSeq = simulationEventSeq.get();
    snapshot.snapshotAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
    manualSimulationSnapshot = snapshot;
  }

  private void restoreManualSimulationSnapshot() {
    if (manualSimulationSnapshot == null) {
      return;
    }
    state = deepCopyState(manualSimulationSnapshot.state);
    restoreSimulationState(manualSimulationSnapshot.simulationState);
    reportingSeq.set(manualSimulationSnapshot.reportingSeq);
    replanSeq.set(manualSimulationSnapshot.replanSeq);
    alertSeq.set(manualSimulationSnapshot.alertSeq);
    dispatchSeq.set(manualSimulationSnapshot.dispatchSeq);
    dispatchApprovalSeq.set(manualSimulationSnapshot.dispatchApprovalSeq);
    salesSeq.set(manualSimulationSnapshot.salesSeq);
    productionSeq.set(manualSimulationSnapshot.productionSeq);
    simulationEventSeq.set(manualSimulationSnapshot.simulationEventSeq);
    finalCompletedByOrderProductCache.clear();
    finalCompletedSyncCursor = 0;
    manualSimulationSnapshot = null;
  }

  private MvpDomain.State deepCopyState(MvpDomain.State source) {
    MvpDomain.State target = new MvpDomain.State();
    target.startDate = source.startDate;
    target.horizonDays = source.horizonDays;
    target.shiftsPerDay = source.shiftsPerDay;
    target.shiftHours = source.shiftHours;
    target.skipStatutoryHolidays = source.skipStatutoryHolidays;
    target.weekendRestMode = normalizeWeekendRestMode(source.weekendRestMode);
    target.dateShiftModeByDate = source.dateShiftModeByDate == null
      ? new LinkedHashMap<>()
      : new LinkedHashMap<>(source.dateShiftModeByDate);
    target.strictRoute = source.strictRoute;

    target.processes = new ArrayList<>(source.processes.stream()
      .map(row -> new MvpDomain.ProcessConfig(row.processCode, row.capacityPerShift, row.requiredWorkers, row.requiredMachines))
      .toList());

    Map<String, List<MvpDomain.ProcessStep>> processRoutes = new LinkedHashMap<>();
    for (Map.Entry<String, List<MvpDomain.ProcessStep>> entry : source.processRoutes.entrySet()) {
      List<MvpDomain.ProcessStep> steps = new ArrayList<>(entry.getValue().stream()
        .map(step -> new MvpDomain.ProcessStep(step.processCode, step.dependencyType))
        .toList());
      processRoutes.put(entry.getKey(), steps);
    }
    target.processRoutes = processRoutes;

    target.shiftCalendar = new ArrayList<>(source.shiftCalendar.stream()
      .map(row -> new MvpDomain.ShiftRow(row.date, row.shiftCode, row.open))
      .toList());
    target.workerPools = new ArrayList<>(source.workerPools.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.machinePools = new ArrayList<>(source.machinePools.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.initialWorkerOccupancy = new ArrayList<>(source.initialWorkerOccupancy.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.initialMachineOccupancy = new ArrayList<>(source.initialMachineOccupancy.stream()
      .map(row -> new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available))
      .toList());
    target.materialAvailability = new ArrayList<>(source.materialAvailability.stream()
      .map(row -> new MvpDomain.MaterialRow(row.date, row.shiftCode, row.productCode, row.processCode, row.availableQty))
      .toList());
    target.lineProcessBindings = new ArrayList<>(source.lineProcessBindings.stream()
      .map(row -> new MvpDomain.LineProcessBinding(row.workshopCode, row.lineCode, row.lineName, row.processCode, row.enabled))
      .toList());
    target.sectionLeaderBindings = new ArrayList<>(source.sectionLeaderBindings.stream()
      .map(row -> new MvpDomain.SectionLeaderBinding(row.leaderId, row.leaderName, row.lineCode, row.active))
      .toList());

    target.orders = new ArrayList<>(source.orders.stream().map(this::deepCopyOrder).toList());
    target.schedules = new ArrayList<>(source.schedules.stream().map(this::deepCopyScheduleVersion).toList());
    target.publishedVersionNo = source.publishedVersionNo;
    target.reportings = new ArrayList<>(source.reportings.stream().map(this::deepCopyReporting).toList());
    target.scheduleResultWrites = new ArrayList<>(deepCopyList(source.scheduleResultWrites));
    target.scheduleStatusWrites = new ArrayList<>(deepCopyList(source.scheduleStatusWrites));
    target.wipLots = new ArrayList<>(deepCopyList(source.wipLots));
    target.wipLotEvents = new ArrayList<>(deepCopyList(source.wipLotEvents));
    target.replanJobs = new ArrayList<>(deepCopyList(source.replanJobs));
    target.alerts = new ArrayList<>(deepCopyList(source.alerts));
    target.auditLogs = new ArrayList<>(deepCopyList(source.auditLogs));
    target.dispatchCommands = new ArrayList<>(deepCopyList(source.dispatchCommands));
    target.dispatchApprovals = new ArrayList<>(deepCopyList(source.dispatchApprovals));
    target.integrationInbox = new ArrayList<>(deepCopyList(source.integrationInbox));
    target.integrationOutbox = new ArrayList<>(deepCopyList(source.integrationOutbox));

    target.idempotencyLedger = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Object>> entry : source.idempotencyLedger.entrySet()) {
      target.idempotencyLedger.put(entry.getKey(), deepCopyMap(entry.getValue()));
    }
    return target;
  }

  private MvpDomain.Order deepCopyOrder(MvpDomain.Order source) {
    List<MvpDomain.OrderItem> items = source.items.stream()
      .map(item -> new MvpDomain.OrderItem(item.productCode, item.qty, item.completedQty))
      .toList();
    return new MvpDomain.Order(
      source.orderNo,
      source.orderType,
      source.dueDate,
      source.expectedStartDate,
      source.urgent,
      source.frozen,
      source.lockFlag,
      source.status,
      items,
      source.businessData == null ? new MvpDomain.OrderBusinessData() : new MvpDomain.OrderBusinessData(source.businessData)
    );
  }

  private MvpDomain.ScheduleVersion deepCopyScheduleVersion(MvpDomain.ScheduleVersion source) {
    MvpDomain.ScheduleVersion target = new MvpDomain.ScheduleVersion();
    target.requestId = source.requestId;
    target.versionNo = source.versionNo;
    target.generatedAt = source.generatedAt;
    target.shiftHours = source.shiftHours;
    target.shiftsPerDay = source.shiftsPerDay;
    target.shifts = deepCopyList(source.shifts);

    target.tasks = source.tasks.stream().map(task -> {
      MvpDomain.ScheduleTask row = new MvpDomain.ScheduleTask();
      row.taskKey = task.taskKey;
      row.orderNo = task.orderNo;
      row.itemIndex = task.itemIndex;
      row.stepIndex = task.stepIndex;
      row.productCode = task.productCode;
      row.processCode = task.processCode;
      row.dependencyType = task.dependencyType;
      row.predecessorTaskKey = task.predecessorTaskKey;
      row.targetQty = task.targetQty;
      row.producedQty = task.producedQty;
      return row;
    }).toList();

    target.allocations = source.allocations.stream().map(allocation -> {
      MvpDomain.Allocation row = new MvpDomain.Allocation();
      row.taskKey = allocation.taskKey;
      row.orderNo = allocation.orderNo;
      row.productCode = allocation.productCode;
      row.processCode = allocation.processCode;
      row.dependencyType = allocation.dependencyType;
      row.shiftId = allocation.shiftId;
      row.date = allocation.date;
      row.shiftCode = allocation.shiftCode;
      row.scheduledQty = allocation.scheduledQty;
      row.workersUsed = allocation.workersUsed;
      row.machinesUsed = allocation.machinesUsed;
      row.groupsUsed = allocation.groupsUsed;
      return row;
    }).toList();

    target.unscheduled = deepCopyList(source.unscheduled);
    target.metrics = deepCopyMap(source.metrics);
    target.metadata = deepCopyMap(source.metadata);
    target.status = source.status;
    target.basedOnVersion = source.basedOnVersion;
    target.ruleVersionNo = source.ruleVersionNo;
    target.publishTime = source.publishTime;
    target.createdBy = source.createdBy;
    target.createdAt = source.createdAt;
    target.rollbackFrom = source.rollbackFrom;
    return target;
  }

  private MvpDomain.Reporting deepCopyReporting(MvpDomain.Reporting source) {
    MvpDomain.Reporting target = new MvpDomain.Reporting();
    target.reportingId = source.reportingId;
    target.requestId = source.requestId;
    target.orderNo = source.orderNo;
    target.productCode = source.productCode;
    target.processCode = source.processCode;
    target.reportQty = source.reportQty;
    target.reportTime = source.reportTime;
    target.triggeredReplanJobNo = source.triggeredReplanJobNo;
    target.triggeredAlertId = source.triggeredAlertId;
    return target;
  }

  private SimulationState deepCopySimulationState(SimulationState source) {
    SimulationState target = new SimulationState();
    target.currentDate = source.currentDate;
    target.seed = source.seed;
    target.scenario = source.scenario;
    target.dailySalesOrderCount = source.dailySalesOrderCount;
    target.salesOrders = new ArrayList<>(deepCopyList(source.salesOrders));
    target.events = new ArrayList<>(deepCopyList(source.events));
    target.lastRunSummary = deepCopyMap(source.lastRunSummary);
    return target;
  }

  private void restoreSimulationState(SimulationState source) {
    simulationState.currentDate = source.currentDate;
    simulationState.seed = source.seed;
    simulationState.scenario = source.scenario;
    simulationState.dailySalesOrderCount = source.dailySalesOrderCount;
    simulationState.salesOrders = new ArrayList<>(deepCopyList(source.salesOrders));
    simulationState.events = new ArrayList<>(deepCopyList(source.events));
    simulationState.lastRunSummary = deepCopyMap(source.lastRunSummary);
  }

  private void resetSimulationState(long seed, String scenario, int dailySales) {
    simulationState.currentDate = state.startDate;
    simulationState.seed = seed;
    simulationState.scenario = normalizeScenario(scenario);
    simulationState.dailySalesOrderCount = dailySales;
    simulationState.salesOrders.clear();
    simulationState.events.clear();
    simulationState.lastRunSummary = new LinkedHashMap<>();
  }

  private Map<String, Object> buildSimulationStateResponse(String requestId) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("current_sim_date", simulationState.currentDate.toString());
    out.put("seed", simulationState.seed);
    out.put("scenario", simulationState.scenario);
    out.put("scenario_name_cn", scenarioNameCn(simulationState.scenario));
    out.put("daily_sales_order_count", simulationState.dailySalesOrderCount);
    out.put("sales_order_total", simulationState.salesOrders.size());
    out.put("production_order_total", state.orders.size());
    out.put("order_pool_total", state.orders.stream().filter(order -> !"DONE".equals(order.status)).count());
    out.put("event_total", simulationState.events.size());
    out.put("open_alert_total", state.alerts.stream().filter(row -> Objects.equals("OPEN", row.get("status"))).count());
    out.put("latest_version_no", state.schedules.isEmpty() ? null : state.schedules.get(state.schedules.size() - 1).versionNo);
    out.put("published_version_no", state.publishedVersionNo);
    out.put("manual_session_active", manualSimulationSnapshot != null);
    out.put("manual_snapshot_at", manualSimulationSnapshot == null ? null : manualSimulationSnapshot.snapshotAt);
    out.put("last_run_summary", deepCopyMap(simulationState.lastRunSummary));
    return out;
  }

  private void appendSimulationEvent(
    LocalDate date,
    String eventType,
    String message,
    String requestId,
    Map<String, Object> details
  ) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("event_id", "SIM-EVT-%06d".formatted(simulationEventSeq.incrementAndGet()));
    row.put("event_date", date.toString());
    row.put("event_type", eventType);
    row.put("event_type_name_cn", eventTypeNameCn(eventType));
    row.put("message", message);
    row.put("message_cn", message);
    row.put("request_id", requestId);
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    if (details != null) {
      row.putAll(details);
    }
    simulationState.events.add(localizeRow(row));
  }

  private String pickProductCode(Random random) {
    List<String> products = new ArrayList<>(state.processRoutes.keySet());
    products.sort(String::compareTo);
    if (products.isEmpty()) {
      return "PROD_UNKNOWN";
    }
    return products.get(random.nextInt(products.size()));
  }

  private static String normalizeScenario(String scenario) {
    String normalized = scenario == null ? "" : scenario.trim().toUpperCase();
    return switch (normalized) {
      case "STABLE", "TIGHT", "BREAKDOWN" -> normalized;
      default -> DEFAULT_SIM_SCENARIO;
    };
  }

  private static String normalizeScheduleStrategy(String strategy) {
    String normalized = strategy == null ? "" : strategy.trim().toUpperCase();
    return switch (normalized) {
      case "MAX_CAPACITY_FIRST", "最大产能优先" -> STRATEGY_MAX_CAPACITY_FIRST;
      case "MIN_DELAY_FIRST", "MIN_TARDINESS_FIRST", "交期最小延期优先" -> STRATEGY_MIN_DELAY_FIRST;
      case "KEY_ORDER_FIRST", "CRITICAL_ORDER_FIRST", "关键订单优先" -> STRATEGY_KEY_ORDER_FIRST;
      default -> STRATEGY_KEY_ORDER_FIRST;
    };
  }

  private static String scheduleStrategyNameCn(String strategyCode) {
    return SCHEDULE_STRATEGY_NAME_CN.getOrDefault(
      normalizeScheduleStrategy(strategyCode),
      SCHEDULE_STRATEGY_NAME_CN.get(STRATEGY_KEY_ORDER_FIRST)
    );
  }

  private static String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static String normalizeMaterialCode(String value) {
    return normalizeCode(value);
  }

  private static List<Map<String, Object>> freezeMaterialRows(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> frozen = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      frozen.add(new LinkedHashMap<>(row));
    }
    return frozen;
  }

  private static List<Map<String, Object>> copyMaterialRows(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> copied = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      copied.add(new LinkedHashMap<>(row));
    }
    return copied;
  }

  private static String materialListBaseCode(String materialListNo) {
    String normalized = normalizeMaterialCode(materialListNo);
    if (normalized.isBlank()) {
      return "";
    }
    int splitIndex = normalized.indexOf('_');
    if (splitIndex <= 0) {
      splitIndex = normalized.indexOf("-V");
    }
    if (splitIndex <= 0) {
      return normalized;
    }
    return normalized.substring(0, splitIndex);
  }

  private static boolean isSameMaterialCode(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return normalizeMaterialCode(left).equals(normalizeMaterialCode(right));
  }

  private static String orderProcessKey(String orderNo, String processCode) {
    return (orderNo == null ? "" : orderNo.trim()) + "#" + normalizeCode(processCode);
  }

  private static double scenarioCapacityFactor(String scenario, Random random) {
    return switch (scenario) {
      case "TIGHT" -> 0.75d + random.nextDouble() * 0.15d;
      case "BREAKDOWN" -> 0.70d + random.nextDouble() * 0.15d;
      default -> 0.95d + random.nextDouble() * 0.10d;
    };
  }

  private static double scenarioExecutionRate(String scenario, Random random) {
    return switch (scenario) {
      case "TIGHT" -> 0.55d + random.nextDouble() * 0.25d;
      case "BREAKDOWN" -> 0.30d + random.nextDouble() * 0.35d;
      default -> 0.78d + random.nextDouble() * 0.17d;
    };
  }

  private static int clampInt(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }

  private static long elapsedMillis(long startNanos) {
    return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
  }

  private static final class CachedOrderPoolMaterials {
    List<Map<String, Object>> rows;
    String refreshedAt;

    CachedOrderPoolMaterials(List<Map<String, Object>> rows, String refreshedAt) {
      this.rows = rows == null ? List.of() : rows;
      this.refreshedAt = refreshedAt;
    }
  }

  private static final class ReportVersionBinding {
    String versionNo;
    String status;
    Set<String> orderNos;

    ReportVersionBinding(String versionNo, String status, Set<String> orderNos) {
      this.versionNo = versionNo;
      this.status = status;
      this.orderNos = orderNos;
    }
  }

  private static final class SimulationState {
    LocalDate currentDate;
    long seed;
    String scenario;
    int dailySalesOrderCount;
    List<Map<String, Object>> salesOrders = new ArrayList<>();
    List<Map<String, Object>> events = new ArrayList<>();
    Map<String, Object> lastRunSummary = new LinkedHashMap<>();
  }

  private static final class ManualSimulationSnapshot {
    MvpDomain.State state;
    SimulationState simulationState;
    int reportingSeq;
    int replanSeq;
    int alertSeq;
    int dispatchSeq;
    int dispatchApprovalSeq;
    int salesSeq;
    int productionSeq;
    int simulationEventSeq;
    String snapshotAt;
  }

  private MvpServiceException badRequest(String message) {
    return new MvpServiceException(400, "BAD_REQUEST", message, false);
  }

  private MvpServiceException notFound(String message) {
    return new MvpServiceException(404, "NOT_FOUND", message, false);
  }

  private static String string(Map<String, Object> payload, String key, String fallback) {
    Object value = payload == null ? null : payload.get(key);
    if (value == null) {
      return fallback;
    }
    String parsed = String.valueOf(value);
    return parsed.isBlank() ? fallback : parsed;
  }

  private static double number(Map<String, Object> payload, String key, double fallback) {
    Object value = payload == null ? null : payload.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException ignore) {
      return fallback;
    }
  }

  private static boolean bool(Map<String, Object> payload, String key, boolean fallback) {
    Object value = payload == null ? null : payload.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.intValue() != 0;
    }
    String normalized = String.valueOf(value).trim().toLowerCase();
    if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
      return true;
    }
    if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
      return false;
    }
    return fallback;
  }

  private List<Map<String, Object>> maps(Object raw) {
    if (raw == null) {
      return List.of();
    }
    return objectMapper.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
  }

  private Map<String, Object> deepCopyMap(Map<String, Object> raw) {
    if (raw == null) {
      return new LinkedHashMap<>();
    }
    return objectMapper.convertValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private List<Map<String, Object>> deepCopyList(List<Map<String, Object>> raw) {
    if (raw == null) {
      return List.of();
    }
    return objectMapper.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
  }
}




