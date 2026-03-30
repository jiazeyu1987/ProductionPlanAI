package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreScheduleUnscheduledRowSupport {
  private MvpStoreScheduleUnscheduledRowSupport() {}

  static Map<String, Object> normalizeUnscheduledRow(
    MvpStoreScheduleExplainMetricsSupport store,
    Map<String, Object> unscheduledRow,
    MvpDomain.ScheduleTask task,
    boolean showDetailedReasons
  ) {
    Map<String, Object> normalized = store.deepCopyMap(unscheduledRow);
    String reasonCode = MvpStoreScheduleUnscheduledReasonSupport.resolveUnscheduledReasonCode(normalized);
    String dependencyStatus = resolveTaskDependencyStatus(task, normalized);
    String taskStatus = resolveTaskStatus(task, normalized, false);
    String lastBlockReason = resolveTaskLastBlockReason(task, normalized, reasonCode);
    String reasonDetail = MvpStoreRuntimeBase.firstString(normalized, "reason_detail", "reasonDetail");
    if ((reasonDetail == null || reasonDetail.isBlank()) && task != null) {
      reasonDetail = task.lastBlockReasonDetail;
    }
    String blockingDimension = MvpStoreRuntimeBase.firstString(normalized, "blocking_dimension", "blockingDimension");
    if ((blockingDimension == null || blockingDimension.isBlank()) && task != null) {
      blockingDimension = task.lastBlockingDimension;
    }

    Object evidenceRaw = normalized.get("evidence");
    Map<String, Object> evidence;
    if (evidenceRaw instanceof Map<?, ?>) {
      evidence = store.objectMapper.convertValue(evidenceRaw, new TypeReference<LinkedHashMap<String, Object>>() {});
    } else if (task != null && task.lastBlockEvidence != null) {
      evidence = store.deepCopyMap(task.lastBlockEvidence);
    } else {
      evidence = new LinkedHashMap<>();
    }

    List<String> reasons = new ArrayList<>();
    Object reasonsObj = normalized.get("reasons");
    if (reasonsObj instanceof List<?> reasonList) {
      for (Object reason : reasonList) {
        String code = MvpStoreScheduleUnscheduledReasonSupport.normalizeReasonCode(String.valueOf(reason));
        if (!code.isBlank() && !reasons.contains(code)) {
          reasons.add(code);
        }
      }
    }
    if (reasonCode != null && !reasonCode.isBlank() && !reasons.contains(reasonCode)) {
      reasons.add(0, reasonCode);
    }
    if (!reasons.isEmpty()) {
      String legacyReason = MvpStoreScheduleUnscheduledReasonSupport.toLegacyReasonCode(reasons.get(0));
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

  static String resolveTaskDependencyStatus(MvpDomain.ScheduleTask task, Map<String, Object> unscheduledRow) {
    String dependencyStatus = task == null ? null : MvpStoreCoreNormalizationSupport.normalizeCode(task.dependencyStatus);
    if (dependencyStatus == null || dependencyStatus.isBlank()) {
      dependencyStatus = MvpStoreCoreNormalizationSupport.normalizeCode(
        MvpStoreRuntimeBase.firstString(unscheduledRow, "dependency_status", "dependencyStatus")
      );
    }
    if (dependencyStatus == null || dependencyStatus.isBlank()) {
      return "READY";
    }
    return dependencyStatus;
  }

  static String resolveTaskLastBlockReason(
    MvpDomain.ScheduleTask task,
    Map<String, Object> unscheduledRow,
    String fallbackReasonCode
  ) {
    String reasonCode = MvpStoreScheduleUnscheduledReasonSupport.normalizeReasonCode(
      MvpStoreRuntimeBase.firstString(unscheduledRow, "last_block_reason", "lastBlockReason")
    );
    if ((reasonCode == null || reasonCode.isBlank()) && task != null) {
      reasonCode = MvpStoreScheduleUnscheduledReasonSupport.normalizeReasonCode(task.lastBlockReason);
    }
    if ((reasonCode == null || reasonCode.isBlank()) && fallbackReasonCode != null) {
      reasonCode = MvpStoreScheduleUnscheduledReasonSupport.normalizeReasonCode(fallbackReasonCode);
    }
    if (reasonCode == null || reasonCode.isBlank()) {
      return null;
    }
    return reasonCode;
  }

  static String resolveTaskStatus(
    MvpDomain.ScheduleTask task,
    Map<String, Object> unscheduledRow,
    boolean hasAllocation
  ) {
    String taskStatus = task == null ? null : MvpStoreCoreNormalizationSupport.normalizeCode(task.taskStatus);
    if (taskStatus == null || taskStatus.isBlank()) {
      taskStatus = MvpStoreCoreNormalizationSupport.normalizeCode(
        MvpStoreRuntimeBase.firstString(unscheduledRow, "task_status", "taskStatus")
      );
    }
    if (taskStatus == null || taskStatus.isBlank()) {
      if (!hasAllocation) {
        return "UNSCHEDULED";
      }
      return unscheduledRow == null ? "READY" : "PARTIALLY_ALLOCATED";
    }
    return taskStatus;
  }
}

