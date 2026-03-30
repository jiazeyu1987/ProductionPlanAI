package com.autoproduction.mvp.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchedulerMetricsSupport {

  private SchedulerMetricsSupport() {}

  static Map<String, Object> buildMetrics(
    Collection<MvpDomain.ScheduleTask> tasks,
    List<MvpDomain.Allocation> allocations,
    List<Map<String, Object>> unscheduled,
    double eps
  ) {
    double targetQty = 0d;
    double producedQty = 0d;
    if (tasks != null) {
      for (MvpDomain.ScheduleTask task : tasks) {
        if (task == null) {
          continue;
        }
        targetQty += Math.max(0d, task.targetQty);
        producedQty += Math.max(0d, task.producedQty);
      }
    }

    double scheduledQty = 0d;
    if (allocations != null) {
      for (MvpDomain.Allocation allocation : allocations) {
        if (allocation == null) {
          continue;
        }
        scheduledQty += Math.max(0d, allocation.scheduledQty);
      }
    }

    double unscheduledQty = 0d;
    if (unscheduled != null) {
      for (Map<String, Object> row : unscheduled) {
        unscheduledQty += Math.max(0d, asDouble(row == null ? null : row.get("remainingQty")));
      }
    }
    if (unscheduledQty <= eps) {
      unscheduledQty = Math.max(0d, targetQty - scheduledQty);
    }

    double completionRate = targetQty > eps
      ? round2(Math.max(0d, Math.min(100d, (scheduledQty / targetQty) * 100d)))
      : 100d;

    Map<String, Integer> reasonDistribution = SchedulerExplainSupport.buildReasonDistribution(
      unscheduled == null ? List.of() : unscheduled
    );

    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("targetQty", round3(targetQty));
    metrics.put("producedQty", round3(producedQty));
    metrics.put("scheduledQty", round3(scheduledQty));
    metrics.put("unscheduledQty", round3(unscheduledQty));
    metrics.put("unscheduledTaskCount", unscheduled == null ? 0 : unscheduled.size());
    metrics.put("unscheduledReasonDistribution", new LinkedHashMap<>(reasonDistribution));
    metrics.put("schedule_completion_rate", completionRate);
    metrics.put("scheduleCompletionRate", completionRate);
    return metrics;
  }

  private static double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return 0d;
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return 0d;
    }
  }

  private static double round2(double value) {
    return Math.round(value * 100d) / 100d;
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}
