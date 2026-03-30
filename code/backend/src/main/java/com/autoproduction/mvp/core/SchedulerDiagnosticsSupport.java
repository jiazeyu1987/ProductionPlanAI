package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.Map;

final class SchedulerDiagnosticsSupport {
  private static final String REASON_CAPACITY_MANPOWER = "CAPACITY_MANPOWER";
  private static final String REASON_CAPACITY_MACHINE = "CAPACITY_MACHINE";
  private static final String REASON_CAPACITY_UNKNOWN = "CAPACITY_UNKNOWN";
  private static final String REASON_MATERIAL_SHORTAGE = "MATERIAL_SHORTAGE";
  private static final String REASON_COMPONENT_SHORTAGE = "COMPONENT_SHORTAGE";
  private static final String REASON_DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
  private static final String REASON_TRANSFER_CONSTRAINT = "TRANSFER_CONSTRAINT";
  private static final String REASON_FROZEN_BY_POLICY = "FROZEN_BY_POLICY";
  private static final String REASON_LOCKED_PRESERVED = "LOCKED_PRESERVED";
  private static final String REASON_LOCK_PREEMPTED = "LOCK_PREEMPTED_BY_URGENT";
  private static final String DEPENDENCY_READY = "READY";

  private SchedulerDiagnosticsSupport() {}

  static SchedulerPlanningSupport.ReasonInfo lockedPreservedReason(
    MvpDomain.ScheduleTask task,
    boolean preservedFromBaseline,
    String baselineVersionNo,
    String dependencyStatus,
    boolean lockPreemptedByUrgent
  ) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("order_no", task.orderNo);
    evidence.put("task_key", task.taskKey);
    evidence.put("process_code", task.processCode);
    evidence.put("baseline_version_no", baselineVersionNo == null ? "" : baselineVersionNo);
    evidence.put("baseline_resolved", baselineVersionNo != null && !baselineVersionNo.isBlank());
    evidence.put("task_preserved_from_baseline", preservedFromBaseline);
    evidence.put("lock_preempted_by_urgent", lockPreemptedByUrgent);
    String detail;
    if (baselineVersionNo == null || baselineVersionNo.isBlank()) {
      detail = "Locked order skipped because no baseline version was provided.";
    } else if (!preservedFromBaseline) {
      detail = lockPreemptedByUrgent
        ? "Locked order is partially released to protect urgent order guarantee in this round."
        : "Locked order has no baseline allocation for this task; task remains unscheduled.";
    } else {
      detail = lockPreemptedByUrgent
        ? "Locked order baseline is partially preserved; remainder is released to urgent order guarantee."
        : "Locked order preserved from baseline; remaining quantity is not replanned.";
    }
    String reasonCode = lockPreemptedByUrgent ? REASON_LOCK_PREEMPTED : REASON_LOCKED_PRESERVED;
    return new SchedulerPlanningSupport.ReasonInfo(
      reasonCode,
      detail,
      "POLICY",
      "POLICY",
      dependencyStatus,
      evidence
    );
  }

  static SchedulerPlanningSupport.ReasonInfo dependencyBlockedReason(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    int shiftIndex,
    double eps
  ) {
    MvpDomain.ScheduleTask predecessor = task.predecessorTaskKey == null ? null : tasks.get(task.predecessorTaskKey);
    double predecessorBeforeShift = task.predecessorTaskKey == null
      ? 0d
      : SchedulerDependencySupport.dependencyReferenceQty(task, tasks, producedBeforeShift, producedByTaskShift, shiftIndex);
    double predecessorProduced = predecessor == null ? 0d : predecessor.producedQty;
    int lagShifts = SchedulerDependencySupport.dependencyLagShifts(task, tasks);
    int minBatch = SchedulerDependencySupport.transferBatchSize(task.processCode);
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("predecessor_task_key", task.predecessorTaskKey);
    evidence.put("dependency_type", task.dependencyType);
    evidence.put("required_release_lag_shifts", lagShifts);
    evidence.put("min_transfer_batch", minBatch);
    evidence.put("predecessor_produced_before_shift", round3(predecessorBeforeShift));
    evidence.put("predecessor_produced_qty", round3(predecessorProduced));
    evidence.put("current_produced_qty", round3(task.producedQty));
    double remaining = round3(task.targetQty - task.producedQty);
    double releasedQty = round3(predecessorBeforeShift - task.producedQty);
    if (releasedQty + eps < minBatch && remaining > minBatch) {
      return new SchedulerPlanningSupport.ReasonInfo(
        REASON_TRANSFER_CONSTRAINT,
        "Task is waiting for minimum transfer batch release from predecessor.",
        "ENGINE",
        "DEPENDENCY",
        SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, eps),
        evidence
      );
    }
    if (lagShifts > 0 && predecessorBeforeShift <= task.producedQty + eps) {
      return new SchedulerPlanningSupport.ReasonInfo(
        REASON_TRANSFER_CONSTRAINT,
        "Task is waiting for mandatory transfer/inspection lag before release.",
        "ENGINE",
        "DEPENDENCY",
        SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, eps),
        evidence
      );
    }
    return new SchedulerPlanningSupport.ReasonInfo(
      REASON_DEPENDENCY_BLOCKED,
      "Task is waiting for predecessor output release.",
      "ENGINE",
      "DEPENDENCY",
      SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, eps),
      evidence
    );
  }

  static SchedulerPlanningSupport.ReasonInfo frozenReason(MvpDomain.ScheduleTask task, String dependencyStatus) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("order_no", task.orderNo);
    evidence.put("task_key", task.taskKey);
    evidence.put("process_code", task.processCode);
    return new SchedulerPlanningSupport.ReasonInfo(
      REASON_FROZEN_BY_POLICY,
      "Task skipped because the order is frozen by policy.",
      "POLICY",
      "POLICY",
      dependencyStatus,
      evidence
    );
  }

  static SchedulerPlanningSupport.ReasonInfo diagnoseUnscheduledReason(
    MvpDomain.ScheduleTask task,
    MvpDomain.Order order,
    Map<String, MvpDomain.ScheduleTask> allTasks,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, Integer> maxWorkersByProcess,
    Map<String, Integer> maxMachinesByProcess,
    Map<String, Double> totalMaterialByProductProcess,
    double componentTotalForOrder,
    double eps
  ) {
    if (order != null && order.frozen) {
      return frozenReason(task, SchedulerDependencySupport.dependencyStatusAtVersionEnd(task, allTasks, eps));
    }

    String dependencyStatus = SchedulerDependencySupport.dependencyStatusAtVersionEnd(task, allTasks, eps);
    if (!DEPENDENCY_READY.equals(dependencyStatus)) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("predecessor_task_key", task.predecessorTaskKey);
      MvpDomain.ScheduleTask predecessor = task.predecessorTaskKey == null ? null : allTasks.get(task.predecessorTaskKey);
      evidence.put("predecessor_produced_qty", round3(predecessor == null ? 0d : predecessor.producedQty));
      evidence.put("current_produced_qty", round3(task.producedQty));
      return new SchedulerPlanningSupport.ReasonInfo(
        REASON_DEPENDENCY_BLOCKED,
        "Task is blocked by predecessor completion/release state.",
        "ENGINE",
        "DEPENDENCY",
        dependencyStatus,
        evidence
      );
    }

    MvpDomain.ProcessConfig processConfig = processConfigMap.get(normalizeProcessCode(task.processCode));
    if (processConfig == null) {
      return SchedulerPlanningSupport.ReasonInfo.capacity(
        REASON_CAPACITY_UNKNOWN,
        "Process config is missing, capacity cannot be resolved.",
        "UNKNOWN",
        task.processCode,
        0,
        0,
        0d
      );
    }

    int workersCapacity = maxWorkersByProcess.getOrDefault(task.processCode, 0);
    if (workersCapacity < Math.max(1, processConfig.requiredWorkers)) {
      return SchedulerPlanningSupport.ReasonInfo.capacity(
        REASON_CAPACITY_MANPOWER,
        "Available manpower does not meet minimum required workers.",
        "MANPOWER",
        task.processCode,
        workersCapacity,
        processConfig.requiredWorkers,
        0d
      );
    }

    int machinesCapacity = maxMachinesByProcess.getOrDefault(task.processCode, 0);
    if (machinesCapacity < Math.max(1, processConfig.requiredMachines)) {
      return SchedulerPlanningSupport.ReasonInfo.capacity(
        REASON_CAPACITY_MACHINE,
        "Available machines do not meet minimum required machines.",
        "MACHINE",
        task.processCode,
        machinesCapacity,
        processConfig.requiredMachines,
        0d
      );
    }

    double materialTotal = totalMaterialByProductProcess.getOrDefault(task.productCode + "#" + task.processCode, 0d);
    if (materialTotal <= eps) {
      return SchedulerPlanningSupport.ReasonInfo.capacity(
        REASON_MATERIAL_SHORTAGE,
        "No material is available in planning horizon.",
        "MATERIAL",
        task.processCode,
        0,
        0,
        materialTotal
      );
    }
    if (task.stepIndex == 0 && componentTotalForOrder <= eps) {
      return SchedulerPlanningSupport.ReasonInfo.capacity(
        REASON_COMPONENT_SHORTAGE,
        "No BOM component inventory/inbound is available in horizon.",
        "MATERIAL",
        task.processCode,
        0,
        0,
        componentTotalForOrder
      );
    }

    return SchedulerPlanningSupport.ReasonInfo.capacity(
      REASON_CAPACITY_UNKNOWN,
      "Task is unscheduled after applying all constraints in horizon.",
      "UNKNOWN",
      task.processCode,
      workersCapacity,
      machinesCapacity,
      materialTotal
    );
  }

  private static String normalizeProcessCode(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed.toUpperCase();
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}
