package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MvpStoreEntityMappingRowSupport {
  private MvpStoreEntityMappingRowSupport() {}

  static Map<String, Object> toOrderMap(MvpStoreEntityMappingSupport store, MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = store.businessData(order);
    String productCode = store.orderPrimaryProductCode(order);
    List<Map<String, Object>> processContexts = store.buildProcessContextsForProduct(productCode);
    LocalDate expectedStartDate = store.resolveExpectedStartDate(order);
    String expectedStartTime = expectedStartDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedStartDate.toString(), "D", true);
    String expectedFinishTime = store.estimateExpectedFinishTime(order);
    LocalDate expectedFinishDate = MvpStoreEntityMappingSupport.parseLocalDateFlexible(expectedFinishTime, null);
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
    row.put("company_codes", store.joinContextValues(processContexts, "company_codes"));
    row.put("workshop_codes", store.joinContextValues(processContexts, "workshop_codes"));
    row.put("line_codes", store.joinContextValues(processContexts, "line_codes"));
    row.put("process_codes", store.joinContextValues(processContexts, "process_code"));
    row.put("process_route_summary", store.summarizeProcessContexts(processContexts));
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
      itemRow.put("product_name_cn", MvpStoreLocalizationAndExportSupport.productNameCn(item.productCode));
      itemRow.put("qty", item.qty);
      itemRow.put("completedQty", item.completedQty);
      itemRow.put("completed_qty", item.completedQty);
      items.add(itemRow);
    }
    row.put("items", items);
    return store.localizeRow(row);
  }

  static boolean filterOrderPoolRow(MvpStoreEntityMappingSupport store, Map<String, Object> row, Map<String, String> filters) {
    if (filters == null) {
      return true;
    }
    if (filters.containsKey("status") && !Objects.equals(filters.get("status"), row.get("status"))) {
      return false;
    }
    if (filters.containsKey("frozen_flag")
      && Integer.parseInt(filters.get("frozen_flag")) != (int) store.number(row, "frozen_flag", 0d)) {
      return false;
    }
    if (filters.containsKey("urgent_flag")
      && Integer.parseInt(filters.get("urgent_flag")) != (int) store.number(row, "urgent_flag", 0d)) {
      return false;
    }
    return true;
  }

  static Map<String, Object> toOrderPoolItemFromErp(MvpStoreEntityMappingSupport store, Map<String, Object> erpRow) {
    String orderNo = store.string(erpRow, "production_order_no", null);
    String productCode = MvpStoreCoreNormalizationSupport.normalizeCode(store.string(erpRow, "product_code", "UNKNOWN"));
    List<Map<String, Object>> processContexts = store.buildProcessContextsForProduct(productCode);
    LocalDate expectedStartDate = MvpStoreEntityMappingSupport.parseLocalDateFlexible(
      store.string(
        erpRow,
        "expected_start_date",
        store.string(erpRow, "planned_finish_date_1", store.string(erpRow, "order_date", null))
      ),
      null
    );
    LocalDate expectedFinishDate = MvpStoreEntityMappingSupport.parseLocalDateFlexible(
      store.string(
        erpRow,
        "expected_finish_date",
        store.string(erpRow, "planned_finish_date_2", store.string(erpRow, "planned_finish_date_1", null))
      ),
      null
    );
    String expectedStartTime = expectedStartDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedStartDate.toString(), "D", true);
    String expectedFinishTime = expectedFinishDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedFinishDate.toString(), "D", true);
    double totalQty = store.number(erpRow, "order_qty", store.number(erpRow, "production_qty", 0d));
    double completedQty = store.number(erpRow, "completed_qty", store.number(erpRow, "warehoused_qty", 0d));
    double progressRate = totalQty > 0d ? Math.min(100d, Math.max(0d, (completedQty / totalQty) * 100d)) : 0d;
    String status = store.string(erpRow, "order_status", store.string(erpRow, "status", null));
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("order_no", orderNo == null ? "" : orderNo.trim());
    row.put("order_type", store.string(erpRow, "order_type", store.string(erpRow, "production_order_type", "")));
    row.put("line_no", "1");
    row.put("product_code", productCode);
    row.put("product_name_cn", store.string(erpRow, "product_name_cn", MvpStoreLocalizationAndExportSupport.productNameCn(productCode)));
    row.put("order_qty", totalQty);
    row.put("completed_qty", MvpStoreCoreNormalizationSupport.round2(completedQty));
    row.put("remaining_qty", MvpStoreCoreNormalizationSupport.round2(Math.max(0d, totalQty - completedQty)));
    row.put("progress_rate", MvpStoreCoreNormalizationSupport.round2(progressRate));
    row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
    row.put("expected_start_time", expectedStartTime);
    row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
    row.put("expected_finish_time", expectedFinishTime);
    row.put("company_codes", store.joinContextValues(processContexts, "company_codes"));
    row.put("workshop_codes", store.joinContextValues(processContexts, "workshop_codes"));
    row.put("line_codes", store.joinContextValues(processContexts, "line_codes"));
    row.put("process_codes", store.joinContextValues(processContexts, "process_code"));
    row.put("process_route_summary", store.summarizeProcessContexts(processContexts));
    row.put("process_contexts", processContexts);
    row.put(
      "expected_due_date",
      expectedFinishDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedFinishDate.toString(), "D", true)
    );
    row.put(
      "promised_due_date",
      expectedFinishDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedFinishDate.toString(), "D", true)
    );
    row.put("urgent_flag", 0);
    row.put("lock_flag", 0);
    row.put("frozen_flag", 0);
    row.put("status", status == null || status.isBlank() ? "OPEN" : status);
    row.put("customer_remark", store.string(erpRow, "customer_remark", ""));
    row.put("spec_model", store.string(erpRow, "spec_model", ""));
    row.put("production_batch_no", store.string(erpRow, "production_batch_no", ""));
    row.put("packaging_form", store.string(erpRow, "packaging_form", ""));
    row.put("sales_order_no", store.string(erpRow, "sales_order_no", store.string(erpRow, "source_sales_order_no", "")));
    row.put("workshop_outer_packaging_date", store.string(erpRow, "workshop_outer_packaging_date", ""));
    row.put("material_list_no", store.string(erpRow, "material_list_no", ""));
    row.put("erp_order_flag", 1);
    return store.localizeRow(row);
  }

  static Map<String, Object> toOrderPoolItem(MvpStoreEntityMappingSupport store, MvpDomain.Order order) {
    MvpDomain.OrderBusinessData business = store.businessData(order);
    String productCode = store.orderPrimaryProductCode(order);
    List<Map<String, Object>> processContexts = store.buildProcessContextsForProduct(productCode);
    double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
    double completedQty = order.items.stream().mapToDouble(item -> item.completedQty).sum();
    double progressRate = totalQty > 0d ? Math.min(100d, Math.max(0d, (completedQty / totalQty) * 100d)) : 0d;
    LocalDate expectedStartDate = store.resolveExpectedStartDate(order);
    String expectedStartTime = expectedStartDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedStartDate.toString(), "D", true);
    String expectedFinishTime = store.estimateExpectedFinishTime(order);
    LocalDate expectedFinishDate = MvpStoreEntityMappingSupport.parseLocalDateFlexible(expectedFinishTime, null);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("order_no", order.orderNo);
    row.put("order_type", order.orderType);
    row.put("line_no", "1");
    row.put("product_code", order.items.isEmpty() ? "UNKNOWN" : order.items.get(0).productCode);
    row.put(
      "product_name_cn",
      business.productName == null || business.productName.isBlank()
        ? MvpStoreLocalizationAndExportSupport.productNameCn(order.items.isEmpty() ? "UNKNOWN" : order.items.get(0).productCode)
        : business.productName
    );
    row.put("product_name", business.productName);
    row.put("order_qty", totalQty);
    row.put("completed_qty", MvpStoreCoreNormalizationSupport.round2(completedQty));
    row.put("remaining_qty", MvpStoreCoreNormalizationSupport.round2(Math.max(0d, totalQty - completedQty)));
    row.put("progress_rate", MvpStoreCoreNormalizationSupport.round2(progressRate));
    row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
    row.put("expected_start_time", expectedStartTime);
    row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
    row.put("expected_finish_time", expectedFinishTime);
    row.put("company_codes", store.joinContextValues(processContexts, "company_codes"));
    row.put("workshop_codes", store.joinContextValues(processContexts, "workshop_codes"));
    row.put("line_codes", store.joinContextValues(processContexts, "line_codes"));
    row.put("process_codes", store.joinContextValues(processContexts, "process_code"));
    row.put("process_route_summary", store.summarizeProcessContexts(processContexts));
    row.put("process_contexts", processContexts);
    row.put("expected_due_date", MvpStoreRuntimeBase.toDateTime(order.dueDate.toString(), "D", true));
    row.put("promised_due_date", MvpStoreRuntimeBase.toDateTime(order.dueDate.toString(), "D", true));
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
    return store.localizeRow(row);
  }

  static Map<String, Object> toScheduleMap(MvpStoreEntityMappingSupport store, MvpDomain.ScheduleVersion schedule) {
    Map<String, Object> row = new LinkedHashMap<>();
    boolean showDetailedReasons = store.bool(schedule.metadata, "showDetailedReasons", true);
    row.put("requestId", schedule.requestId);
    row.put("request_id", schedule.requestId);
    row.put("versionNo", schedule.versionNo);
    row.put("version_no", schedule.versionNo);
    row.put("generatedAt", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
    row.put("generated_at", schedule.generatedAt == null ? null : schedule.generatedAt.toString());
    row.put("shiftHours", schedule.shiftHours);
    row.put("shiftsPerDay", schedule.shiftsPerDay);
    row.put("shifts", store.deepCopyList(schedule.shifts));

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
      taskRow.put("lastBlockReason", MvpStoreScheduleExplainMetricsSupport.normalizeReasonCode(task.lastBlockReason));
      taskRow.put("last_block_reason", MvpStoreScheduleExplainMetricsSupport.normalizeReasonCode(task.lastBlockReason));
      taskRow.put("lastBlockingDimension", showDetailedReasons ? task.lastBlockingDimension : null);
      taskRow.put("last_blocking_dimension", showDetailedReasons ? task.lastBlockingDimension : null);
      taskRow.put("lastBlockReasonDetail", showDetailedReasons ? task.lastBlockReasonDetail : null);
      taskRow.put("last_block_reason_detail", showDetailedReasons ? task.lastBlockReasonDetail : null);
      taskRow.put(
        "lastBlockEvidence",
        showDetailedReasons ? store.deepCopyMap(task.lastBlockEvidence == null ? Map.of() : task.lastBlockEvidence) : Map.of()
      );
      taskRow.put(
        "last_block_evidence",
        showDetailedReasons ? store.deepCopyMap(task.lastBlockEvidence == null ? Map.of() : task.lastBlockEvidence) : Map.of()
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
      allocationRow.put("companyCode", allocation.companyCode);
      allocationRow.put("company_code", allocation.companyCode);
      allocationRow.put("workshopCode", allocation.workshopCode);
      allocationRow.put("workshop_code", allocation.workshopCode);
      allocationRow.put("lineCode", allocation.lineCode);
      allocationRow.put("line_code", allocation.lineCode);
      allocationRow.put("lineName", allocation.lineName);
      allocationRow.put("line_name", allocation.lineName);
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
      String taskKey = MvpStoreRuntimeBase.firstString(unscheduledRow, "taskKey", "task_key");
      normalizedUnscheduled.add(store.normalizeUnscheduledRow(unscheduledRow, taskByTaskKey.get(taskKey), showDetailedReasons));
    }
    row.put("unscheduled", normalizedUnscheduled);
    row.put("metrics", store.deepCopyMap(schedule.metrics));
    row.put("metadata", store.deepCopyMap(schedule.metadata));
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

  static Map<String, Object> toReportingMap(MvpStoreEntityMappingSupport store, MvpDomain.Reporting reporting) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("reportingId", reporting.reportingId);
    row.put("reporting_id", reporting.reportingId);
    row.put("request_id", reporting.requestId);
    row.put("orderNo", reporting.orderNo);
    row.put("order_no", reporting.orderNo);
    row.put("productCode", reporting.productCode);
    row.put("product_code", reporting.productCode);
    row.put("product_name_cn", MvpStoreLocalizationAndExportSupport.productNameCn(reporting.productCode));
    row.put("processCode", reporting.processCode);
    row.put("process_code", reporting.processCode);
    row.put("process_name_cn", MvpStoreLocalizationAndExportSupport.processNameCn(reporting.processCode));
    row.put("reportQty", reporting.reportQty);
    row.put("report_qty", reporting.reportQty);
    row.put("reportTime", reporting.reportTime.toString());
    row.put("report_time", reporting.reportTime.toString());
    row.put("triggered_replan_job_no", reporting.triggeredReplanJobNo);
    row.put("triggered_alert_id", reporting.triggeredAlertId);
    return store.localizeRow(row);
  }
}
