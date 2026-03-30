package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MvpAuditLogSupport {

  private MvpAuditLogSupport() {}

  static Map<String, Object> createAuditRow(
    String entityType,
    String entityId,
    String action,
    String actionNameCn,
    String operator,
    String requestId,
    String reason,
    Map<String, Object> perfContext
  ) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("entity_type", entityType);
    row.put("entity_id", entityId);
    row.put("action", action);
    row.put("action_name_cn", actionNameCn);
    row.put("operator", operator == null ? "system" : operator);
    row.put("request_id", requestId);
    row.put("operate_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("reason", reason);
    if (perfContext != null && !perfContext.isEmpty()) {
      row.put("perf_context", perfContext);
    }
    return row;
  }

  static boolean matchesFilters(Map<String, Object> row, Map<String, String> filters) {
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
  }
}
