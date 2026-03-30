package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MvpStoreEntityMappingAuditSupport {
  private MvpStoreEntityMappingAuditSupport() {}

  static void appendAudit(
    MvpStoreEntityMappingSupport store,
    String entityType,
    String entityId,
    String action,
    String operator,
    String requestId,
    String reason
  ) {
    appendAudit(store, entityType, entityId, action, operator, requestId, reason, null);
  }

  static void appendAudit(
    MvpStoreEntityMappingSupport store,
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
    row.put("action_name_cn", MvpStoreLocalizationAndExportSupport.actionNameCn(action));
    row.put("operator", operator == null ? "system" : operator);
    row.put("request_id", requestId);
    row.put("operate_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("reason", reason);
    if (perfContext != null && !perfContext.isEmpty()) {
      row.put("perf_context", store.deepCopyMap(perfContext));
    }
    store.state.auditLogs.add(row);
  }

  static void appendInbox(MvpStoreEntityMappingSupport store, String topic, String entityId, String requestId, String status, String errorMsg) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("message_id", "IN-" + entityId + "-" + (store.state.integrationInbox.size() + 1));
    row.put("source", "MES");
    row.put("target", "SCHEDULER");
    row.put("topic", topic);
    row.put("topic_name_cn", MvpStoreLocalizationAndExportSupport.topicNameCn(topic));
    row.put("status", status);
    row.put("status_name_cn", MvpStoreLocalizationAndExportSupport.statusNameCn(status));
    row.put("retry_count", 0);
    row.put("error_msg", errorMsg == null ? "" : errorMsg);
    row.put("request_id", requestId);
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    store.state.integrationInbox.add(row);
  }

  static void appendOutbox(MvpStoreEntityMappingSupport store, String topic, String entityId, String requestId, String status, String errorMsg) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("message_id", "OUT-" + entityId + "-" + (store.state.integrationOutbox.size() + 1));
    row.put("source", "SCHEDULER");
    row.put("target", "ERP");
    row.put("topic", topic);
    row.put("topic_name_cn", MvpStoreLocalizationAndExportSupport.topicNameCn(topic));
    row.put("status", status);
    row.put("status_name_cn", MvpStoreLocalizationAndExportSupport.statusNameCn(status));
    row.put("retry_count", 0);
    row.put("error_msg", errorMsg == null ? "" : errorMsg);
    row.put("request_id", requestId);
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    row.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    store.state.integrationOutbox.add(row);
  }

  static boolean matchIntegrationFilters(Map<String, Object> row, Map<String, String> filters) {
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
}
