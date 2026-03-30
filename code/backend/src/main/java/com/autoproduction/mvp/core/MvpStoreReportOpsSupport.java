package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MvpStoreReportOpsSupport {
  private MvpStoreReportOpsSupport() {}

  static Map<String, Object> triggerReplanJob(
    MvpStoreReportDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    return domain.doCreateReplanJob(payload, requestId, operator);
  }

  static Map<String, Object> getReplanJob(MvpStoreReportDomain domain, String jobNo, String requestId) {
    Map<String, Object> found = domain.state.replanJobs.stream()
      .filter(row -> Objects.equals(row.get("job_no"), jobNo))
      .findFirst()
      .orElseThrow(() -> domain.notFound("Replan job %s not found.".formatted(jobNo)));
    Map<String, Object> out = new LinkedHashMap<>(found);
    out.put("request_id", requestId);
    return out;
  }

  static List<Map<String, Object>> listAlerts(MvpStoreReportDomain domain, Map<String, String> filters) {
    return domain.state.alerts.stream()
      .filter(row -> {
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
      })
      .map(domain::enrichAlertMetrics)
      .map(domain::localizeRow)
      .toList();
  }

  static Map<String, Object> ackAlert(
    MvpStoreReportDomain domain,
    String alertId,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    return domain.updateAlert(alertId, payload, requestId, operator, "ACKED");
  }

  static Map<String, Object> closeAlert(
    MvpStoreReportDomain domain,
    String alertId,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    return domain.updateAlert(alertId, payload, requestId, operator, "CLOSED");
  }

  static List<Map<String, Object>> listAuditLogs(MvpStoreReportDomain domain, Map<String, String> filters) {
    return domain.state.auditLogs.stream()
      .filter(row -> {
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
      })
      .map(domain::localizeRow)
      .toList();
  }

  static List<Map<String, Object>> listIntegrationInbox(MvpStoreReportDomain domain, Map<String, String> filters) {
    return domain.state.integrationInbox.stream()
      .filter(row -> domain.matchIntegrationFilters(row, filters))
      .map(domain::localizeRow)
      .toList();
  }

  static List<Map<String, Object>> listIntegrationOutbox(MvpStoreReportDomain domain, Map<String, String> filters) {
    return domain.state.integrationOutbox.stream()
      .filter(row -> domain.matchIntegrationFilters(row, filters))
      .map(domain::localizeRow)
      .toList();
  }
}

