package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

final class MvpStoreReportScheduleWritebackSupport {
  private MvpStoreReportScheduleWritebackSupport() {}

  static Map<String, Object> writeScheduleResults(
    MvpStoreReportDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    String scheduleVersion = domain.string(payload, "schedule_version", null);
    List<Map<String, Object>> items = domain.maps(payload.get("items"));
    if (scheduleVersion == null || items.isEmpty()) {
      throw domain.badRequest("schedule_version and items are required.");
    }
    int successCount = 0;
    int failedCount = 0;
    for (Map<String, Object> row : items) {
      String orderNo = domain.string(row, "order_no", domain.string(row, "orderNo", null));
      MvpDomain.Order order = domain.state.orders.stream().filter(o -> o.orderNo.equals(orderNo)).findFirst().orElse(null);
      if (order == null) {
        failedCount += 1;
        continue;
      }
      if (row.containsKey("lock_flag")) {
        order.lockFlag = domain.number(row, "lock_flag", 0d) == 1d;
      }
      successCount += 1;
    }
    domain.state.scheduleResultWrites.add(Map.of(
      "request_id",
      requestId,
      "schedule_version",
      scheduleVersion,
      "item_count",
      items.size(),
      "created_at",
      OffsetDateTime.now(ZoneOffset.UTC).toString()
    ));
    domain.appendAudit("ERP_WRITEBACK", scheduleVersion, "WRITE_SCHEDULE_RESULTS", operator, requestId, null);
    domain.appendOutbox(
      "ERP_SCHEDULE_RESULTS",
      scheduleVersion,
      requestId,
      failedCount == 0 ? "SUCCESS" : "PARTIAL",
      null
    );
    domain.erpDataManager.refreshTriggered("ERP_SCHEDULE_RESULTS", requestId, "schedule result writeback");
    return Map.of("request_id", requestId, "success_count", successCount, "failed_count", failedCount);
  }

  static Map<String, Object> writeScheduleStatus(
    MvpStoreReportDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    String scheduleVersion = domain.string(payload, "schedule_version", null);
    List<Map<String, Object>> items = domain.maps(payload.get("items"));
    if (scheduleVersion == null || items.isEmpty()) {
      throw domain.badRequest("schedule_version and items are required.");
    }
    int successCount = 0;
    int failedCount = 0;
    for (Map<String, Object> row : items) {
      String orderNo = domain.string(row, "order_no", domain.string(row, "orderNo", null));
      String status = domain.string(row, "status", null);
      MvpDomain.Order order = domain.state.orders.stream().filter(o -> o.orderNo.equals(orderNo)).findFirst().orElse(null);
      if (order == null || status == null) {
        failedCount += 1;
        continue;
      }
      order.status = status;
      successCount += 1;
    }
    domain.state.scheduleStatusWrites.add(Map.of(
      "request_id",
      requestId,
      "schedule_version",
      scheduleVersion,
      "item_count",
      items.size(),
      "created_at",
      OffsetDateTime.now(ZoneOffset.UTC).toString()
    ));
    domain.appendAudit("ERP_WRITEBACK", scheduleVersion, "WRITE_SCHEDULE_STATUS", operator, requestId, null);
    domain.appendOutbox(
      "ERP_SCHEDULE_STATUS",
      scheduleVersion,
      requestId,
      failedCount == 0 ? "SUCCESS" : "PARTIAL",
      null
    );
    domain.erpDataManager.refreshTriggered("ERP_SCHEDULE_STATUS", requestId, "schedule status writeback");
    return Map.of("request_id", requestId, "success_count", successCount, "failed_count", failedCount);
  }
}

