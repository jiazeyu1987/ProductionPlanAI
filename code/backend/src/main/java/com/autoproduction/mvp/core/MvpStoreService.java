package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
  private static final String LIVE_REPORT_VERSION_NO = "LIVE_STATE";
  private static final String LIVE_REPORT_STATUS = "LIVE";
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
    Map.entry("PROD_UNKNOWN", "未知产品")
  );
  private static final Map<String, String> PROCESS_NAME_CN = Map.ofEntries(
    Map.entry("PROC_TUBE", "制管"),
    Map.entry("PROC_ASSEMBLY", "装配"),
    Map.entry("PROC_BALLOON", "球囊成型"),
    Map.entry("PROC_STENT", "支架成型"),
    Map.entry("PROC_STERILE", "灭菌")
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
    Map.entry("PROGRESS_GAP", "进度偏差"),
    Map.entry("EQUIPMENT_DOWN", "设备故障")
  );
  private static final Map<String, String> SEVERITY_NAME_CN = Map.ofEntries(
    Map.entry("CRITICAL", "严重"),
    Map.entry("WARN", "警告"),
    Map.entry("INFO", "提示")
  );

  private final Object lock = new Object();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ErpSqliteOrderLoader erpSqliteOrderLoader;
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
  private ManualSimulationSnapshot manualSimulationSnapshot;

  public MvpStoreService(ErpSqliteOrderLoader erpSqliteOrderLoader) {
    this.erpSqliteOrderLoader = erpSqliteOrderLoader;
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
      return state.orders.stream()
        .map(this::toOrderPoolItem)
        .filter(row -> {
          if (filters == null) {
            return true;
          }
          if (filters.containsKey("status") && !Objects.equals(filters.get("status"), row.get("status"))) {
            return false;
          }
          if (filters.containsKey("frozen_flag")
            && Integer.parseInt(filters.get("frozen_flag")) != (int) row.get("frozen_flag")) {
            return false;
          }
          if (filters.containsKey("urgent_flag")
            && Integer.parseInt(filters.get("urgent_flag")) != (int) row.get("urgent_flag")) {
            return false;
          }
          return true;
        })
        .toList();
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
      syncCompletedQtyFromFinalProcessReports();
      boolean autoReplan = bool(options, "autoReplan", false);
      List<String> excludedLockedOrders = new ArrayList<>();
      List<MvpDomain.Order> orders = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        if (autoReplan && order.lockFlag) {
          excludedLockedOrders.add(order.orderNo);
          continue;
        }
        orders.add(order);
      }
      String versionNo = "V%03d".formatted(state.schedules.size() + 1);
      MvpDomain.ScheduleVersion schedule = SchedulerEngine.generate(state, orders, requestId, versionNo);
      schedule.status = "DRAFT";
      schedule.basedOnVersion = string(options, "base_version_no", null);
      schedule.ruleVersionNo = "RULE-P0-BASE";
      schedule.publishTime = null;
      schedule.createdBy = operator;
      schedule.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
      schedule.metadata.put("autoReplan", autoReplan);
      schedule.metadata.put("excludedLockedOrders", excludedLockedOrders);
      state.schedules.add(schedule);
      appendAudit(
        "SCHEDULE_VERSION",
        schedule.versionNo,
        autoReplan ? "AUTO_REPLAN_SCHEDULE" : "GENERATE_SCHEDULE",
        operator,
        requestId,
        string(options, "reason", null)
      );
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

      Map<String, Object> triggered = maybeTriggerProgressGapReplan(reporting, requestId, operator);
      if (triggered != null) {
        reporting.triggeredReplanJobNo = string(triggered, "job_no", null);
        reporting.triggeredAlertId = string(triggered, "alert_id", null);
      }

      return toReportingMap(reporting);
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
      return buildSimulationStateResponse(requestId);
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
          "仿真状态已重置。",
          requestId,
          Map.of()
        );
        appendAudit("SIMULATION", "SIMULATION_STATE", "RESET_SIMULATION", operator, requestId, null);
        Map<String, Object> out = buildSimulationStateResponse(requestId);
        out.put("message", "仿真状态已重置。");
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
          Map<String, Object> schedule = generateSchedule(generatePayload, requestId + ":generate:" + i, "simulator");
          String versionNo = string(schedule, "version_no", string(schedule, "versionNo", null));
          totalVersions += 1;
          appendSimulationEvent(
            businessDate,
            "SCHEDULE_GENERATED",
            "已生成当日仿真排产版本。",
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
            "已发布当日仿真排产版本。",
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

        appendAudit("SIMULATION", "SIM-RUN", "RUN_SIMULATION", operator, requestId, "仿真运行完成。");
        appendSimulationEvent(
          simulationState.currentDate.minusDays(1),
          "SIM_RUN_DONE",
          "仿真运行完成。",
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
        data.customerRemark = "手动模拟订单";
        data.weeklyMonthlyPlanRemark = "手动模拟";
        data.note = "MANUAL_SIM";

        MvpDomain.Order order = new MvpDomain.Order(
          productionOrderNo,
          "production",
          dueDate,
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
          "手动模拟已新增生产订单。",
          requestId,
          Map.of(
            "production_order_no", productionOrderNo,
            "sales_order_no", salesOrderNo,
            "product_code", productCode,
            "product_name_cn", productNameCn(productCode),
            "order_qty", qty
          )
        );
        appendAudit("SIMULATION", productionOrderNo, "CREATE_ORDER", operator, requestId, "手动模拟新增生产订单");

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

        LocalDate businessDate = simulationState.currentDate;
        Random dayRandom = new Random(seed + businessDate.toEpochDay() * 997L + 17L);
        Map<String, Object> capacity = rebuildPlanningHorizon(businessDate, scenario, dayRandom, requestId + ":manual:capacity");
        double capacityFactor = number(capacity, "capacity_factor", 1d);

        String baseVersionNo = state.schedules.isEmpty() ? null : state.schedules.get(state.schedules.size() - 1).versionNo;
        Map<String, Object> generatePayload = new LinkedHashMap<>();
        generatePayload.put("base_version_no", baseVersionNo);
        generatePayload.put("autoReplan", false);
        generatePayload.put("reason", "MANUAL_SIM_ADVANCE");
        generatePayload.put("request_id", requestId + ":manual:generate");
        Map<String, Object> schedule = generateSchedule(generatePayload, requestId + ":manual:generate", "simulator");
        String versionNo = string(schedule, "version_no", string(schedule, "versionNo", null));

        Map<String, Object> publishPayload = new LinkedHashMap<>();
        publishPayload.put("request_id", requestId + ":manual:publish");
        publishPayload.put("operator", "simulator");
        publishPayload.put("reason", "MANUAL_SIM_ADVANCE");
        publishSchedule(versionNo, publishPayload, requestId + ":manual:publish", "simulator");

        int reportingCount = simulateDailyReporting(
          businessDate,
          versionNo,
          scenario,
          dayRandom,
          requestId + ":manual:report"
        );
        refreshOrderStatuses(businessDate.plusDays(1));
        int delayedOrders = countDelayedOrders(businessDate.plusDays(1));
        simulationState.currentDate = businessDate.plusDays(1);

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
        simulationState.lastRunSummary = deepCopyMap(summary);

        appendSimulationEvent(
          businessDate,
          "SIM_RUN_DONE",
          "手动模拟推进一天完成。",
          requestId,
          Map.of("version_no", versionNo, "days", 1, "reporting_count", reportingCount)
        );
        appendAudit("SIMULATION", "SIM-MANUAL-ADVANCE", "RUN_SIMULATION", operator, requestId, "手动模拟推进一天");

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
          out.put("message", "当前没有可重置的手动模拟会话。");
          return out;
        }
        restoreManualSimulationSnapshot();
        Map<String, Object> out = buildSimulationStateResponse(requestId);
        out.put("message", "已恢复到手动模拟前的数据状态。");
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
      int id = 1;
      for (MvpDomain.Allocation allocation : schedule.allocations) {
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
        tasks.add(localizeRow(row));
      }
      return tasks;
    }
  }

  public Map<String, Object> getScheduleAlgorithm(String versionNo, String requestId) {
    synchronized (lock) {
      MvpDomain.ScheduleVersion schedule = getScheduleEntity(versionNo);

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("task_count", schedule.tasks.size());
      summary.put("allocation_count", schedule.allocations.size());
      summary.put("order_count", schedule.tasks.stream().map(task -> task.orderNo).filter(Objects::nonNull).distinct().count());
      summary.put("target_qty", round2(number(schedule.metrics, "targetQty", 0d)));
      summary.put("scheduled_qty", round2(number(schedule.metrics, "scheduledQty", 0d)));
      summary.put("schedule_completion_rate", round2(number(schedule.metrics, "scheduleCompletionRate", 0d)));
      summary.put("unscheduled_task_count", schedule.unscheduled.size());

      List<String> logic = new ArrayList<>();
      logic.add("Order priority: urgent first, then due date, then order number.");
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
      logic.add("Allocation is created only when dependency, resource, and material are all feasible in current shift.");
      logic.add("Unscheduled tasks keep reason codes, for example CAPACITY_LIMIT.");

      List<Map<String, Object>> priorityPreview = state.orders.stream()
        .sorted(Comparator
          .comparing((MvpDomain.Order o) -> !o.urgent)
          .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(o -> o.orderNo))
        .limit(10)
        .map(order -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("order_no", order.orderNo);
          row.put("urgent_flag", order.urgent ? 1 : 0);
          row.put("due_date", order.dueDate == null ? null : order.dueDate.toString());
          row.put("status", order.status);
          row.put("status_name_cn", statusNameCn(order.status));
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

        List<?> reasons = row.get("reasons") instanceof List<?> items ? items : List.of();
        if (reasons.isEmpty()) {
          reasonCount.merge("UNKNOWN", 1, Integer::sum);
          if (!processCode.isBlank()) {
            reasonCountByProcess.computeIfAbsent(processCode, key -> new HashMap<>()).merge("UNKNOWN", 1, Integer::sum);
          }
        } else {
          for (Object reason : reasons) {
            String code = normalizeCode(String.valueOf(reason));
            if (code.isBlank()) {
              code = "UNKNOWN";
            }
            reasonCount.merge(code, 1, Integer::sum);
            if (!processCode.isBlank()) {
              reasonCountByProcess.computeIfAbsent(processCode, key -> new HashMap<>()).merge(code, 1, Integer::sum);
            }
          }
        }

        if (unscheduledSamples.size() >= 10) {
          continue;
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("task_key", string(row, "taskKey", null));
        sample.put("order_no", string(row, "orderNo", null));
        sample.put("process_code", string(row, "processCode", null));
        sample.put("remaining_qty", round2(number(row, "remainingQty", 0d)));
        sample.put("reasons", reasons);
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
      List<Map<String, Object>> erpRows = erpSqliteOrderLoader.loadSalesOrderLines();
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
      return erpSqliteOrderLoader.loadSalesOrderHeadersRaw();
    }
  }

  public List<Map<String, Object>> listErpSalesOrderLinesRaw() {
    synchronized (lock) {
      return erpSqliteOrderLoader.loadSalesOrderLinesRaw();
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

  public List<Map<String, Object>> listProductionOrders() {
    synchronized (lock) {
      List<Map<String, Object>> erpRows = erpSqliteOrderLoader.loadProductionOrders();
      if (!erpRows.isEmpty()) {
        return erpRows;
      }
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : state.orders) {
        MvpDomain.OrderBusinessData business = businessData(order);
        double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
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
        row.put("plan_qty", totalQty);
        row.put("production_status", order.status);
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
      return erpSqliteOrderLoader.loadProductionOrderHeadersRaw();
    }
  }

  public List<Map<String, Object>> listErpProductionOrderLinesRaw() {
    synchronized (lock) {
      return erpSqliteOrderLoader.loadProductionOrderLinesRaw();
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
        writeCell(groupRow, 0, "订单基本信息", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 13));
        writeCell(groupRow, 16, "半成品信息", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 16, 21));
        writeCell(groupRow, 22, "订单排产信息", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 22, 24));
        writeCell(groupRow, 25, "订单进度", headerStyle);
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
      for (MvpDomain.ResourceRow row : state.machinePools) {
        int count = Math.max(1, row.available);
        for (int i = 1; i <= count; i += 1) {
          rows.add(localizeRow(Map.of(
            "equipment_code", row.processCode + "-EQ-" + i,
            "process_code", row.processCode,
            "line_code", "LINE-A",
            "workshop_code", "WS-A",
            "status", "AVAILABLE",
            "capacity_per_shift", 1,
            "last_update_time", now
          )));
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
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("equipment_code", process.processCode + "-EQ-1");
        row.put("process_code", process.processCode);
        row.put("enabled_flag", 1);
        row.put("capacity_factor", 1);
        row.put("last_update_time", now);
        rows.add(localizeRow(row));
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
        row.put("workshop_code", "WS-A");
        row.put("last_update_time", now);
        rows.add(localizeRow(row));
      }
      return rows;
    }
  }

  private Map<String, Object> doCreateReplanJob(Map<String, Object> payload, String requestId, String operator) {
    String baseVersionNo = string(payload, "base_version_no", null);
    if (baseVersionNo == null) {
      throw badRequest("base_version_no is required.");
    }
    getScheduleEntity(baseVersionNo);
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
    state.replanJobs.add(job);

    Map<String, Object> schedule = generateSchedule(
      Map.of("request_id", requestId + ":generate", "base_version_no", baseVersionNo, "autoReplan", true),
      requestId + ":generate",
      operator
    );
    job.put("result_version_no", schedule.get("versionNo"));
    job.put("status", "DONE");
    job.put("finished_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    appendAudit("REPLAN_JOB", jobNo, "TRIGGER_REPLAN", operator, requestId, string(payload, "reason", null));
    return new LinkedHashMap<>(job);
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
    return new MvpDomain.Order(orderNo, orderType, dueDate, urgent, frozen, lockFlag, status, orderItems, businessData);
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
    data.packagingForm = string(payload, "packaging_form", string(payload, "packagingForm", "纸塑袋"));
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
      string(payload, "process_schedule_remark", string(payload, "weeklyMonthlyPlanRemark", "周计划（3.16-3.22）"))
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
    data.matchStatus = string(payload, "match_status", string(payload, "matchStatus", "待匹配"));
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
      business.matchStatus = "已匹配";
    } else {
      business.outerCompletedQty = round2(Math.max(0d, completedQty - (0.1d * totalQty)));
      business.matchStatus = "待匹配";
    }
  }

  private void syncCompletedQtyFromFinalProcessReports() {
    Map<String, Double> finalCompletedByOrderProduct = new HashMap<>();
    for (MvpDomain.Reporting reporting : state.reportings) {
      if (!isFinalProcessForProduct(reporting.productCode, reporting.processCode)) {
        continue;
      }
      String key = reporting.orderNo + "#" + reporting.productCode;
      finalCompletedByOrderProduct.merge(key, reporting.reportQty, Double::sum);
    }

    for (MvpDomain.Order order : state.orders) {
      boolean updated = false;
      for (MvpDomain.OrderItem item : order.items) {
        String key = order.orderNo + "#" + item.productCode;
        if (!finalCompletedByOrderProduct.containsKey(key)) {
          continue;
        }
        double corrected = Math.min(item.qty, Math.max(0d, finalCompletedByOrderProduct.get(key)));
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
    data.customerRemark = "模拟订单";
    data.productName = productNameCn(productCode);
    data.specModel = "SIM-" + productCode + "-" + (int) qty;
    data.productionBatchNo = "SIM-" + businessDate.toString().replace("-", "");
    data.packagingForm = "纸塑袋";
    data.salesOrderNo = salesOrderNo;
    data.productionDateForeignTrade = "";
    data.purchaseDueDate = "";
    data.injectionDueDate = "";
    data.marketRemarkInfo = "仿真自动生成";
    data.marketDemand = qty;
    data.plannedFinishDate1 = dueDate.toString();
    data.plannedFinishDate2 = dueDate.toString();
    data.semiFinishedCode = "SF-" + productCode;
    data.semiFinishedInventory = round2(qty * 0.1d);
    data.semiFinishedDemand = qty;
    data.semiFinishedWip = round2(qty * 0.2d);
    data.needOrderQty = round2(Math.max(0d, qty - data.semiFinishedInventory - data.semiFinishedWip));
    data.pendingInboundQty = round2(data.needOrderQty * 0.5d);
    data.weeklyMonthlyPlanRemark = "仿真计划";
    data.workshopOuterPackagingDate = formatShortDate(dueDate);
    data.note = "SIM";
    data.workshopCompletedQty = 0d;
    data.workshopCompletedTime = "";
    data.outerCompletedQty = 0d;
    data.outerCompletedTime = "";
    data.matchStatus = "待匹配";
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

  private Map<String, Object> toOrderMap(MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = businessData(order);
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

  private Map<String, Object> toOrderPoolItem(MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = businessData(order);
    double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
    double completedQty = order.items.stream().mapToDouble(item -> item.completedQty).sum();
    double progressRate = totalQty > 0d ? Math.min(100d, Math.max(0d, (completedQty / totalQty) * 100d)) : 0d;
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
    row.put("expected_due_date", toDateTime(order.dueDate.toString(), "D", true));
    row.put("promised_due_date", toDateTime(order.dueDate.toString(), "D", true));
    row.put("urgent_flag", order.urgent ? 1 : 0);
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

    row.put("unscheduled", deepCopyList(schedule.unscheduled));
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
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("entity_type", entityType);
    row.put("entity_id", entityId);
    row.put("action", action);
    row.put("action_name_cn", actionNameCn(action));
    row.put("operator", operator == null ? "system" : operator);
    row.put("request_id", requestId);
    row.put("operate_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("reason", reason);
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
      row.putIfAbsent("alert_type_name_cn", alertTypeNameCn(alertType));
    }

    String severity = firstString(row, "severity");
    if (severity != null && !severity.isBlank()) {
      row.putIfAbsent("severity_name_cn", severityNameCn(severity));
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
      row.putIfAbsent("event_type_name_cn", eventTypeNameCn(eventType));
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
      row.putIfAbsent("source_name_cn", systemNameCn(sourceSystem));
    }
    if (targetSystem != null && !targetSystem.isBlank()) {
      row.putIfAbsent("target_name_cn", systemNameCn(targetSystem));
    }
    if (sourceSystem != null && !sourceSystem.isBlank() && targetSystem != null && !targetSystem.isBlank()) {
      row.putIfAbsent("sync_flow_cn", syncFlowCn(sourceSystem, targetSystem));
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

  private static String dependencyNameCn(String dependencyType) {
    return DEPENDENCY_NAME_CN.getOrDefault(dependencyType, dependencyType == null ? "" : dependencyType);
  }

  private static String alertTypeNameCn(String alertType) {
    return ALERT_TYPE_NAME_CN.getOrDefault(alertType, alertType == null ? "" : alertType);
  }

  private static String severityNameCn(String severity) {
    return SEVERITY_NAME_CN.getOrDefault(severity, severity == null ? "" : severity);
  }

  private static String routeNameCn(String routeNo) {
    if (routeNo != null && routeNo.startsWith("ROUTE-")) {
      String productCode = routeNo.substring("ROUTE-".length());
      return productNameCn(productCode) + "工艺路线";
    }
    return routeNo == null ? "" : routeNo;
  }

  private static String syncFlowCn(String source, String target) {
    return systemNameCn(source) + "写入，" + systemNameCn(target) + "读取";
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
      Object reasonsObj = row.get("reasons");
      if (!(reasonsObj instanceof List<?> reasons)) {
        continue;
      }
      if (reasons.contains("CAPACITY_LIMIT")) {
        count += 1;
      }
    }
    return count;
  }

  private static String scheduleReasonNameCn(String reasonCode) {
    String normalized = normalizeCode(reasonCode);
    return switch (normalized) {
      case "CAPACITY_LIMIT" -> "当前班次可用产能不足（人力/设备/物料受限）";
      case "DEPENDENCY_LIMIT" -> "受前序工序约束，后序暂时不可继续排产";
      case "UNKNOWN", "" -> "未标记原因";
      default -> normalized;
    };
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
      return processName + "在本次排产中没有待分配目标量。";
    }

    double scheduleRate = Math.max(0d, Math.min(100d, (scheduledQty / targetQty) * 100d));
    if (unscheduledQty <= 1e-9) {
      return processName +
      "目标量" + formatQtyText(targetQty) +
      "，已分配" + formatQtyText(scheduledQty) +
      "（" + formatPercentText(scheduleRate) +
      "），涉及" + orderCount + "个订单，当前约束下已全部覆盖。";
    }

    return processName +
    "目标量" + formatQtyText(targetQty) +
    "，已分配" + formatQtyText(scheduledQty) +
    "（" + formatPercentText(scheduleRate) +
    "），仍有" + formatQtyText(unscheduledQty) +
    "未排，主要受“" + scheduleReasonNameCn(topReasonCode) + "”影响。";
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
      return processNameCn(processCode) + "暂无可解释的峰值分配记录。";
    }

    double peakQty = round2(maxAllocation.scheduledQty);
    double remaining = round2(Math.max(0d, taskRemainingBeforeShift));
    double resourceCap = round2(Math.max(0d, resourceCapacity));
    double materialCap = round2(Math.max(0d, materialAvailable));

    double minCap = Double.POSITIVE_INFINITY;
    String dominant = "";
    if (remaining > 1e-9 && remaining < minCap) {
      minCap = remaining;
      dominant = "任务在该班次前的剩余可排量";
    }
    if (resourceCap > 1e-9 && resourceCap < minCap) {
      minCap = resourceCap;
      dominant = "该班次的人力/设备资源能力上限";
    }
    if (materialCap > 1e-9 && materialCap < minCap) {
      minCap = materialCap;
      dominant = "该班次可用物料上限";
    }
    if (dominant.isBlank()) {
      dominant = "交期优先级、前序放行量与资源约束的综合平衡";
    }

    return processNameCn(processCode) +
    "在" + maxAllocation.date + shiftNameCn(maxAllocation.shiftCode) +
    "对订单" + maxAllocation.orderNo +
    "产生单次峰值分配" + formatQtyText(peakQty) +
    "。当班任务剩余可排量约" + formatQtyText(remaining) +
    "，资源可支撑上限约" + formatQtyText(resourceCap) +
    "，物料可用量约" + formatQtyText(materialCap) +
    "；算法按可行上限取最小值进行分配，因此本次峰值受“" + dominant + "”主导。";
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
        "接收到随机销售订单。",
        requestId,
        Map.of("sales_order_no", salesOrderNo, "product_code", productCode, "order_qty", qty)
      );
      appendSimulationEvent(
        businessDate,
        "ORDER_CONVERTED",
        "销售订单已转换为生产订单。",
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
      "已应用产能场景。",
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
      "已根据仿真执行结果自动生成报工。",
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
    manualSimulationSnapshot = null;
  }

  private MvpDomain.State deepCopyState(MvpDomain.State source) {
    MvpDomain.State target = new MvpDomain.State();
    target.startDate = source.startDate;
    target.horizonDays = source.horizonDays;
    target.shiftsPerDay = source.shiftsPerDay;
    target.shiftHours = source.shiftHours;
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
    target.materialAvailability = new ArrayList<>(source.materialAvailability.stream()
      .map(row -> new MvpDomain.MaterialRow(row.date, row.shiftCode, row.productCode, row.processCode, row.availableQty))
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

  private static String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase();
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
