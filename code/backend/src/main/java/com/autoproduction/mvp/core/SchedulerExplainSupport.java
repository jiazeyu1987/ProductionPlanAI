package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchedulerExplainSupport {

  private static final String REASON_CAPACITY_MANPOWER = "CAPACITY_MANPOWER";
  private static final String REASON_CAPACITY_MACHINE = "CAPACITY_MACHINE";
  private static final String REASON_CAPACITY_UNKNOWN = "CAPACITY_UNKNOWN";
  private static final String REASON_MATERIAL_SHORTAGE = "MATERIAL_SHORTAGE";
  private static final String REASON_COMPONENT_SHORTAGE = "COMPONENT_SHORTAGE";
  private static final String REASON_DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
  private static final String REASON_TRANSFER_CONSTRAINT = "TRANSFER_CONSTRAINT";

  private static final String TASK_STATUS_READY = "READY";
  private static final String TASK_STATUS_PARTIALLY_ALLOCATED = "PARTIALLY_ALLOCATED";
  private static final String TASK_STATUS_UNSCHEDULED = "UNSCHEDULED";
  private static final String TASK_STATUS_PRESERVED_LOCKED = "PRESERVED_LOCKED";
  private static final String TASK_STATUS_SKIPPED_FROZEN = "SKIPPED_FROZEN";

  private SchedulerExplainSupport() {}

  static String capacityReasonCode(int groupsByWorkers, int groupsByMachines) {
    if (groupsByWorkers < groupsByMachines) {
      return REASON_CAPACITY_MANPOWER;
    }
    if (groupsByMachines < groupsByWorkers) {
      return REASON_CAPACITY_MACHINE;
    }
    return REASON_CAPACITY_UNKNOWN;
  }

  static List<String> reasonCodesForCompatibility(String reasonCode) {
    List<String> out = new ArrayList<>();
    out.add(reasonCode);
    String legacyReason = toLegacyReasonCode(reasonCode);
    if (!legacyReason.equals(reasonCode)) {
      out.add(legacyReason);
    }
    return out;
  }

  static String resolveTaskStatus(
      boolean frozen, boolean lockFlag, double producedQty, double remainingQty, double eps) {
    if (frozen) {
      return TASK_STATUS_SKIPPED_FROZEN;
    }
    if (lockFlag && remainingQty > eps) {
      return TASK_STATUS_PRESERVED_LOCKED;
    }
    if (remainingQty <= eps) {
      return TASK_STATUS_READY;
    }
    if (producedQty > eps) {
      return TASK_STATUS_PARTIALLY_ALLOCATED;
    }
    return TASK_STATUS_UNSCHEDULED;
  }

  static Map<String, Integer> buildReasonDistribution(List<Map<String, Object>> unscheduled) {
    Map<String, Integer> reasonDistribution = new LinkedHashMap<>();
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = String.valueOf(row.getOrDefault("reason_code", ""));
      if (reasonCode == null || reasonCode.isBlank()) {
        continue;
      }
      reasonDistribution.merge(reasonCode, 1, Integer::sum);
    }
    return reasonDistribution;
  }

  private static String toLegacyReasonCode(String reasonCode) {
    return switch (reasonCode) {
      case REASON_CAPACITY_MANPOWER,
              REASON_CAPACITY_MACHINE,
              REASON_MATERIAL_SHORTAGE,
              REASON_COMPONENT_SHORTAGE,
              REASON_CAPACITY_UNKNOWN -> "CAPACITY_LIMIT";
      case REASON_DEPENDENCY_BLOCKED, REASON_TRANSFER_CONSTRAINT -> "DEPENDENCY_LIMIT";
      default -> reasonCode;
    };
  }
}
