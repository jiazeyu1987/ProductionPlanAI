package com.autoproduction.mvp.core;

import java.util.Map;

final class SchedulerDependencySupport {
  private static final int DEFAULT_TRANSFER_BATCH = 60;
  private static final int STERILE_TRANSFER_BATCH = 120;
  private static final int DEFAULT_MIN_LOT = 30;
  private static final int STERILE_MIN_LOT = 80;
  private static final int STERILE_RELEASE_LAG_SHIFTS = 1;
  private static final String DEPENDENCY_READY = "READY";
  private static final String DEPENDENCY_WAIT_PREDECESSOR = "WAIT_PREDECESSOR";
  private static final String DEPENDENCY_BLOCKED_PREDECESSOR = "BLOCKED_BY_PREDECESSOR";

  private SchedulerDependencySupport() {}

  static double calcAllowance(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    int shiftIndex,
    double eps
  ) {
    double remaining = round3(task.targetQty - task.producedQty);
    if (remaining <= eps) {
      return 0d;
    }
    if (task.predecessorTaskKey == null) {
      return remaining;
    }
    MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
    if (predecessor == null) {
      return 0d;
    }
    double predecessorReferenceQty = dependencyReferenceQty(
      task,
      tasks,
      producedBeforeShift,
      producedByTaskShift,
      shiftIndex
    );
    double released = round3(predecessorReferenceQty - task.producedQty);
    if (released <= eps) {
      return 0d;
    }
    int minBatch = transferBatchSize(task.processCode);
    if (released + eps < minBatch && remaining > minBatch) {
      return 0d;
    }
    return Math.max(0d, round3(Math.min(remaining, released)));
  }

  static int minLotSize(String processCode) {
    if (processCode == null) {
      return DEFAULT_MIN_LOT;
    }
    return processCode.toUpperCase().contains("STERILE") ? STERILE_MIN_LOT : DEFAULT_MIN_LOT;
  }

  static int transferBatchSize(String processCode) {
    if (processCode == null) {
      return DEFAULT_TRANSFER_BATCH;
    }
    return processCode.toUpperCase().contains("STERILE") ? STERILE_TRANSFER_BATCH : DEFAULT_TRANSFER_BATCH;
  }

  static int dependencyLagShifts(MvpDomain.ScheduleTask task, Map<String, MvpDomain.ScheduleTask> tasks) {
    if (task == null || task.predecessorTaskKey == null) {
      return 0;
    }
    MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
    if (predecessor == null) {
      return 0;
    }
    if (task.processCode != null && task.processCode.toUpperCase().contains("STERILE")) {
      return STERILE_RELEASE_LAG_SHIFTS;
    }
    return 0;
  }

  static double producedUntilShift(
    Map<String, Map<Integer, Double>> producedByTaskShift,
    String taskKey,
    int shiftIndexInclusive
  ) {
    if (taskKey == null || shiftIndexInclusive < 0) {
      return 0d;
    }
    Map<Integer, Double> byShift = producedByTaskShift.get(taskKey);
    if (byShift == null || byShift.isEmpty()) {
      return 0d;
    }
    double out = 0d;
    for (Map.Entry<Integer, Double> entry : byShift.entrySet()) {
      if (entry.getKey() <= shiftIndexInclusive) {
        out += entry.getValue();
      }
    }
    return round3(out);
  }

  static double dependencyReferenceQty(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    int shiftIndex
  ) {
    if (task.predecessorTaskKey == null) {
      return 0d;
    }
    double predecessorProducedBeforeShift = round3(producedBeforeShift.getOrDefault(task.predecessorTaskKey, 0d));
    double predecessorProducedInPlanUntilPrevShift = producedUntilShift(producedByTaskShift, task.predecessorTaskKey, shiftIndex - 1);
    double predecessorHistoricalProduced = round3(
      Math.max(0d, predecessorProducedBeforeShift - predecessorProducedInPlanUntilPrevShift)
    );
    int lag = dependencyLagShifts(task, tasks);
    if ("FS".equals(task.dependencyType)) {
      if (lag <= 0) {
        return predecessorProducedBeforeShift;
      }
      return round3(
        predecessorHistoricalProduced
          + producedUntilShift(producedByTaskShift, task.predecessorTaskKey, shiftIndex - 1 - lag)
      );
    }
    if (lag <= 0) {
      MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
      return round3(predecessor == null ? 0d : predecessor.producedQty);
    }
    return round3(
      predecessorHistoricalProduced + producedUntilShift(producedByTaskShift, task.predecessorTaskKey, shiftIndex - lag)
    );
  }

  static String dependencyStatusAtVersionEnd(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    double eps
  ) {
    if (task.predecessorTaskKey == null || task.predecessorTaskKey.isBlank()) {
      return DEPENDENCY_READY;
    }
    MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
    if (predecessor == null) {
      return DEPENDENCY_BLOCKED_PREDECESSOR;
    }
    double released = predecessor.producedQty - task.producedQty;
    if (released > eps) {
      return DEPENDENCY_READY;
    }
    if (predecessor.producedQty + eps < predecessor.targetQty || predecessor.producedQty <= eps) {
      return DEPENDENCY_WAIT_PREDECESSOR;
    }
    return DEPENDENCY_BLOCKED_PREDECESSOR;
  }

  static String dependencyStatusAtShift(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    double eps
  ) {
    if (task.predecessorTaskKey == null || task.predecessorTaskKey.isBlank()) {
      return DEPENDENCY_READY;
    }
    MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
    if (predecessor == null) {
      return DEPENDENCY_BLOCKED_PREDECESSOR;
    }
    double predecessorReferenceQty = "FS".equals(task.dependencyType)
      ? producedBeforeShift.getOrDefault(task.predecessorTaskKey, 0d)
      : predecessor.producedQty;
    double released = predecessorReferenceQty - task.producedQty;
    if (released > eps) {
      return DEPENDENCY_READY;
    }
    if (predecessorReferenceQty + eps < predecessor.targetQty || predecessorReferenceQty <= eps) {
      return DEPENDENCY_WAIT_PREDECESSOR;
    }
    return DEPENDENCY_BLOCKED_PREDECESSOR;
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}
