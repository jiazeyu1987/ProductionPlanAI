package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MvpStoreReportReportingSupport {
  private MvpStoreReportReportingSupport() {}

  static Map<String, Object> recordReporting(
    MvpStoreReportDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    String orderNo = domain.string(payload, "orderNo", domain.string(payload, "order_no", null));
    MvpDomain.Order order = domain.findOrder(orderNo);
    String productCode = domain.string(payload, "productCode", domain.string(payload, "product_code", null));
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
      throw domain.badRequest("Product %s not found in order %s.".formatted(productCode, orderNo));
    }
    double reportQtyRaw = domain.number(payload, "reportQty", domain.number(payload, "report_qty", -1d));
    if (reportQtyRaw <= 0d) {
      throw domain.badRequest("reportQty must be > 0.");
    }
    double reportQty = Math.round(reportQtyRaw);
    if (reportQty <= 0d) {
      throw domain.badRequest("reportQty must be >= 1.");
    }

    MvpDomain.Reporting reporting = new MvpDomain.Reporting();
    reporting.reportingId = "RPT-%05d".formatted(domain.reportingSeq.incrementAndGet());
    reporting.requestId = requestId;
    reporting.orderNo = orderNo;
    reporting.productCode = productCode;
    reporting.processCode = domain.string(payload, "processCode", domain.string(payload, "process_code", "UNKNOWN"));
    reporting.reportQty = reportQty;
    reporting.reportTime = OffsetDateTime.now(ZoneOffset.UTC);

    if (domain.isFinalProcessForProduct(item.productCode, reporting.processCode)) {
      item.completedQty = Math.min(item.qty, item.completedQty + reportQty);
    }
    domain.updateOrderProgressFacts(order);

    domain.state.reportings.add(reporting);
    domain.appendAudit("REPORTING", reporting.reportingId, "CREATE_REPORTING", operator, requestId, null);
    domain.appendInbox("MES_REPORTING", reporting.reportingId, requestId, "SUCCESS", null);
    domain.erpDataManager.refreshTriggered("MES_REPORTING", requestId, "reporting created");

    Map<String, Object> triggered = domain.maybeTriggerProgressGapReplan(reporting, requestId, operator);
    if (triggered != null) {
      reporting.triggeredReplanJobNo = domain.string(triggered, "job_no", null);
      reporting.triggeredAlertId = domain.string(triggered, "alert_id", null);
    }

    return domain.toReportingMap(reporting);
  }

  static Map<String, Object> deleteReporting(
    MvpStoreReportDomain domain,
    String reportingId,
    String requestId,
    String operator
  ) {
    String normalizedReportingId = domain.normalizeCode(reportingId);
    if (normalizedReportingId.isBlank()) {
      throw domain.badRequest("reportingId is required.");
    }

    int targetIndex = -1;
    for (int i = 0; i < domain.state.reportings.size(); i += 1) {
      MvpDomain.Reporting current = domain.state.reportings.get(i);
      if (domain.normalizeCode(current.reportingId).equals(normalizedReportingId)) {
        targetIndex = i;
        break;
      }
    }
    if (targetIndex < 0) {
      throw domain.notFound("Reporting %s not found.".formatted(reportingId));
    }

    MvpDomain.Reporting removed = domain.state.reportings.remove(targetIndex);
    if (domain.isFinalProcessForProduct(removed.productCode, removed.processCode)) {
      try {
        MvpDomain.Order order = domain.findOrder(removed.orderNo);
        for (MvpDomain.OrderItem item : order.items) {
          if (!Objects.equals(item.productCode, removed.productCode)) {
            continue;
          }
          item.completedQty = Math.max(0d, item.completedQty - removed.reportQty);
        }
        domain.updateOrderProgressFacts(order);
      } catch (MvpServiceException ignore) {
        // Order may have been removed by other flows; keep deletion successful.
      }
    }

    if (domain.state.reportings.size() < domain.finalCompletedSyncCursor) {
      domain.finalCompletedByOrderProductCache.clear();
      domain.finalCompletedSyncCursor = 0;
    }

    domain.appendAudit("REPORTING", removed.reportingId, "DELETE_REPORTING", operator, requestId, null);
    domain.erpDataManager.refreshTriggered("MES_REPORTING", requestId, "reporting deleted");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("request_id", requestId);
    result.put("report_id", removed.reportingId);
    result.put("order_no", removed.orderNo);
    result.put("product_code", removed.productCode);
    result.put("process_code", removed.processCode);
    result.put("deleted", true);
    return result;
  }

  static List<Map<String, Object>> listReportings(MvpStoreReportDomain domain) {
    return domain.state.reportings.stream().map(domain::toReportingMap).toList();
  }

  static List<Map<String, Object>> listReportingsForMes(MvpStoreReportDomain domain, Map<String, String> filters) {
    OffsetDateTime startTime = domain.parseOffsetDateTimeFilter(filters, "start_time", "report_time_start");
    OffsetDateTime endTime = domain.parseOffsetDateTimeFilter(filters, "end_time", "report_time_end");
    return domain.state.reportings.stream()
      .filter(reporting -> startTime == null || !reporting.reportTime.isBefore(startTime))
      .filter(reporting -> endTime == null || !reporting.reportTime.isAfter(endTime))
      .map(reporting -> {
        String shiftCode = reporting.reportTime.getHour() >= 20 || reporting.reportTime.getHour() < 8 ? "NIGHT" : "DAY";
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("report_id", reporting.reportingId);
        row.put("order_no", reporting.orderNo);
        row.put("order_type", "production");
        row.put("product_code", reporting.productCode);
        row.put("product_name_cn", domain.productNameCn(reporting.productCode));
        row.put("process_code", reporting.processCode);
        row.put("process_name_cn", domain.processNameCn(reporting.processCode));
        row.put("report_qty", reporting.reportQty);
        row.put("report_time", reporting.reportTime.toString());
        row.put("shift_code", shiftCode);
        row.put("shift_name_cn", domain.shiftNameCn(shiftCode));
        row.put("team_code", "TEAM-A");
        row.put("operator_code", "OP-A");
        row.put("last_update_time", reporting.reportTime.toString());
        return row;
      })
      .toList();
  }
}

