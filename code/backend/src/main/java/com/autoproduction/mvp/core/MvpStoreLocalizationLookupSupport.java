package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class MvpStoreLocalizationLookupSupport {
  private MvpStoreLocalizationLookupSupport() {}

  static Map<String, Object> localizeRow(Map<String, Object> source) {
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

  static String firstString(Map<String, Object> row, String... keys) {
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

  static String firstNonBlank(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  static String productNameCn(String productCode) {
    return MvpStoreRuntimeBase.PRODUCT_NAME_CN.getOrDefault(productCode, productCode == null ? "" : productCode);
  }

  static String processNameCn(String processCode) {
    return MvpStoreRuntimeBase.PROCESS_NAME_CN.getOrDefault(processCode, processCode == null ? "" : processCode);
  }

  static String statusNameCn(String status) {
    return MvpStoreRuntimeBase.STATUS_NAME_CN.getOrDefault(status, status == null ? "" : status);
  }

  static String actionNameCn(String action) {
    return MvpStoreRuntimeBase.ACTION_NAME_CN.getOrDefault(action, action == null ? "" : action);
  }

  static String eventTypeNameCn(String eventType) {
    return MvpStoreRuntimeBase.EVENT_TYPE_NAME_CN.getOrDefault(eventType, eventType == null ? "" : eventType);
  }

  static String topicNameCn(String topic) {
    return MvpStoreRuntimeBase.TOPIC_NAME_CN.getOrDefault(topic, topic == null ? "" : topic);
  }

  static String systemNameCn(String system) {
    return MvpStoreRuntimeBase.SYSTEM_NAME_CN.getOrDefault(system, system == null ? "" : system);
  }

  static String scenarioNameCn(String scenario) {
    return MvpStoreRuntimeBase.SCENARIO_NAME_CN.getOrDefault(scenario, scenario == null ? "" : scenario);
  }

  static String shiftNameCn(String shiftCode) {
    return MvpStoreRuntimeBase.SHIFT_NAME_CN.getOrDefault(shiftCode, shiftCode == null ? "" : shiftCode);
  }

  static String dependencyNameCn(String dependencyType) {
    return MvpStoreRuntimeBase.DEPENDENCY_NAME_CN.getOrDefault(dependencyType, dependencyType == null ? "" : dependencyType);
  }

  static String alertTypeNameCn(String alertType) {
    return MvpStoreRuntimeBase.ALERT_TYPE_NAME_CN.getOrDefault(alertType, alertType == null ? "" : alertType);
  }

  static String severityNameCn(String severity) {
    return MvpStoreRuntimeBase.SEVERITY_NAME_CN.getOrDefault(severity, severity == null ? "" : severity);
  }

  static String routeNameCn(String routeNo) {
    if (routeNo != null && routeNo.startsWith("ROUTE-")) {
      String productCode = routeNo.substring("ROUTE-".length());
      return productNameCn(productCode) + "工艺路线";
    }
    return routeNo == null ? "" : routeNo;
  }

  static String syncFlowCn(String source, String target) {
    return systemNameCn(source) + "写入，" + systemNameCn(target) + "读取";
  }

  static String normalizeShiftCode(String shiftCode) {
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
}

