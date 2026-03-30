package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreScheduleObservabilityMetricsSupport {
  private MvpStoreScheduleObservabilityMetricsSupport() {}

  static Map<String, Object> buildScheduleObservabilityMetrics(
    MvpStoreScheduleExplainMetricsSupport store,
    MvpDomain.ScheduleVersion schedule,
    Long candidateOrderCount,
    Long candidateTaskCount
  ) {
    List<Map<String, Object>> unscheduled = schedule.unscheduled == null ? List.of() : schedule.unscheduled;
    Map<String, Object> metrics = new LinkedHashMap<>();
    Map<String, Integer> unscheduledReasonDistribution =
      MvpStoreScheduleUnscheduledReasonSupport.buildUnscheduledReasonDistribution(unscheduled);
    int lockedOrFrozenImpactCount = MvpStoreScheduleUnscheduledReasonSupport.countByReasonCodes(
      unscheduled,
      Set.of("FROZEN_BY_POLICY", "LOCKED_PRESERVED")
    );
    double completionRate = MvpStoreCoreNormalizationSupport.round2(MvpStoreCoreExceptionSupport.number(
      schedule.metrics,
      "schedule_completion_rate",
      MvpStoreCoreExceptionSupport.number(schedule.metrics, "scheduleCompletionRate", 0d)
    ));
    int publishCount = countPublishActions(store);
    int rollbackCount = countRollbackActions(store);

    metrics.put("unscheduled_reason_distribution", new LinkedHashMap<>(unscheduledReasonDistribution));
    metrics.put("unscheduledReasonDistribution", new LinkedHashMap<>(unscheduledReasonDistribution));
    metrics.put("unscheduled_task_count", unscheduled.size());
    metrics.put("unscheduledTaskCount", unscheduled.size());
    metrics.put("locked_or_frozen_impact_count", lockedOrFrozenImpactCount);
    metrics.put("schedule_completion_rate", completionRate);
    metrics.put("scheduleCompletionRate", completionRate);
    if (candidateOrderCount != null) {
      metrics.put("candidate_order_count", candidateOrderCount);
    }
    if (candidateTaskCount != null) {
      metrics.put("candidate_task_count", candidateTaskCount);
    }
    metrics.put("publish_count", publishCount);
    metrics.put("rollback_count", rollbackCount);
    metrics.put("publish_rollback_count", publishCount + rollbackCount);
    metrics.put("replan_failure_rate", MvpStoreCoreNormalizationSupport.round2(calcReplanFailureRate(store)));
    metrics.put("api_error_rate", MvpStoreCoreNormalizationSupport.round2(calcApiErrorRate(store)));
    return metrics;
  }

  static int countPublishActions(MvpStoreScheduleExplainMetricsSupport store) {
    return countVersionActions(store, Set.of("PUBLISH_VERSION"));
  }

  static int countRollbackActions(MvpStoreScheduleExplainMetricsSupport store) {
    return countVersionActions(store, Set.of("ROLLBACK_VERSION"));
  }

  static int countVersionActions(MvpStoreScheduleExplainMetricsSupport store, Set<String> actions) {
    if (actions == null || actions.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (Map<String, Object> row : store.state.auditLogs) {
      String action = MvpStoreCoreNormalizationSupport.normalizeCode(MvpStoreRuntimeBase.firstString(row, "action"));
      if (actions.contains(action)) {
        count += 1;
      }
    }
    return count;
  }

  static double calcReplanFailureRate(MvpStoreScheduleExplainMetricsSupport store) {
    int total = store.state.replanJobs.size();
    if (total <= 0) {
      return 0d;
    }
    int failed = 0;
    for (Map<String, Object> row : store.state.replanJobs) {
      if ("FAILED".equals(MvpStoreCoreNormalizationSupport.normalizeCode(MvpStoreRuntimeBase.firstString(row, "status")))) {
        failed += 1;
      }
    }
    return (failed * 100d) / total;
  }

  static double calcApiErrorRate(MvpStoreScheduleExplainMetricsSupport store) {
    int total = store.state.integrationInbox.size() + store.state.integrationOutbox.size() + store.state.replanJobs.size();
    if (total <= 0) {
      return 0d;
    }
    int failed = 0;
    for (Map<String, Object> row : store.state.integrationInbox) {
      if ("FAILED".equals(MvpStoreCoreNormalizationSupport.normalizeCode(MvpStoreRuntimeBase.firstString(row, "status")))) {
        failed += 1;
      }
    }
    for (Map<String, Object> row : store.state.integrationOutbox) {
      if ("FAILED".equals(MvpStoreCoreNormalizationSupport.normalizeCode(MvpStoreRuntimeBase.firstString(row, "status")))) {
        failed += 1;
      }
    }
    for (Map<String, Object> row : store.state.replanJobs) {
      if ("FAILED".equals(MvpStoreCoreNormalizationSupport.normalizeCode(MvpStoreRuntimeBase.firstString(row, "status")))) {
        failed += 1;
      }
    }
    return (failed * 100d) / total;
  }
}

