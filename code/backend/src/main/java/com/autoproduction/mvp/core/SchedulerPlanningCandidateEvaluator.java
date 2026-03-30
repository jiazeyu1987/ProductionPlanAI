package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentKeyForTask;
import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentRemainingForShift;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.CHANGEOVER_CAPACITY_FACTOR;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.PRODUCT_MIX_CAPACITY_FACTOR;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_BEFORE_EXPECTED_START;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_MACHINE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_MANPOWER;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_UNKNOWN;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_COMPONENT_SHORTAGE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_TRANSFER_CONSTRAINT;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.defaultLineBindingForProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.lineUsageKey;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.expectedStartDateForOrder;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeLineToken;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.processCapacityFactor;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.shiftCapacityFactor;

import com.autoproduction.mvp.core.SchedulerPlanningSupport.ReasonInfo;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningCandidateEvaluator {
  private SchedulerPlanningCandidateEvaluator() {}

  static CandidateEvaluation evaluateCandidateForShift(
    MvpDomain.Order order,
    MvpDomain.ScheduleTask task,
    String shiftId,
    String shiftCode,
    int shiftIndex,
    LocalDate shiftDateValue,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> lineShiftScheduledQty,
    Map<String, Double> shiftMaterialArrivalByProductProcess,
    Map<String, Double> cumulativeMaterialByShiftProductProcess,
    Map<String, Double> materialConsumedByProductProcess,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Double> componentConsumed,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    Set<String> finalTaskKeys,
    Map<String, Double> producedBeforeShift,
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess
  ) {
    CandidateEvaluation base = new CandidateEvaluation();
    base.order = order;
    base.task = task;
    base.finalStep = finalTaskKeys.contains(task.taskKey);
    base.remainingQty = round3(task.targetQty - task.producedQty);
    if (base.remainingQty <= EPS) {
      return base;
    }

    LocalDate expectedStartDate = expectedStartDateForOrder(order);
    if (shiftDateValue != null && expectedStartDate != null && shiftDateValue.isBefore(expectedStartDate)) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("order_no", order == null ? "" : order.orderNo);
      evidence.put("task_key", task.taskKey);
      evidence.put("process_code", task.processCode);
      evidence.put("shift_date", shiftDateValue.toString());
      evidence.put("expected_start_date", expectedStartDate.toString());
      base.blockedReason = new ReasonInfo(
        REASON_BEFORE_EXPECTED_START,
        "Order release date has not reached the expected start date.",
        "ENGINE",
        "POLICY",
        SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, EPS),
        evidence
      );
      return base;
    }

    String normalizedProcessCode = normalizeProcessCode(task.processCode);
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsByProcess.getOrDefault(
      normalizedProcessCode == null ? task.processCode : normalizedProcessCode,
      List.of()
    );
    MvpDomain.ProcessConfig processConfig = processConfigMap.get(normalizedProcessCode);
    if (processConfig == null && !bindings.isEmpty()) {
      MvpDomain.LineProcessBinding first = bindings.get(0);
      processConfig = new MvpDomain.ProcessConfig(
        task.processCode,
        Math.max(EPS, first.capacityPerShift),
        Math.max(1, first.requiredWorkers),
        Math.max(1, first.requiredMachines)
      );
    }
    if (processConfig == null) {
      base.blockedReason = ReasonInfo.capacity(
        REASON_CAPACITY_UNKNOWN,
        "Process config is missing, capacity cannot be resolved.",
        "UNKNOWN",
        task.processCode,
        0,
        0,
        0d
      );
      return base;
    }

    base.allowanceQty = SchedulerDependencySupport.calcAllowance(
      task,
      tasks,
      producedBeforeShift,
      producedByTaskShift,
      shiftIndex,
      EPS
    );
    if (base.allowanceQty <= EPS) {
      base.blockedReason = SchedulerDiagnosticsSupport.dependencyBlockedReason(
        task,
        tasks,
        producedBeforeShift,
        producedByTaskShift,
        shiftIndex,
        EPS
      );
      return base;
    }

    if (bindings.isEmpty()) {
      bindings = List.of(defaultLineBindingForProcess(task.processCode, processConfig));
    }

    CandidateEvaluation best = null;
    ReasonInfo blockedReason = null;
    for (MvpDomain.LineProcessBinding binding : bindings) {
      CandidateEvaluation out = new CandidateEvaluation();
      out.order = order;
      out.task = task;
      out.finalStep = base.finalStep;
      out.remainingQty = base.remainingQty;
      out.allowanceQty = base.allowanceQty;
      out.lineBinding = binding;
      out.processConfig = new MvpDomain.ProcessConfig(
        task.processCode,
        Math.max(EPS, binding.capacityPerShift),
        Math.max(1, binding.requiredWorkers),
        Math.max(1, binding.requiredMachines)
      );

      out.resourceUsageKey = shiftId + "#" + task.processCode;
      out.processUsageKey = lineUsageKey(shiftId, binding, task.processCode);
      out.workersAvailable = workerByShiftProcess.getOrDefault(out.resourceUsageKey, 0);
      out.machinesAvailable = machineByShiftProcess.getOrDefault(out.resourceUsageKey, 0);
      out.workersUsed = shiftWorkersUsed.getOrDefault(out.resourceUsageKey, 0);
      out.machinesUsed = shiftMachinesUsed.getOrDefault(out.resourceUsageKey, 0);
      out.workersRemaining = Math.max(0, out.workersAvailable - out.workersUsed);
      out.machinesRemaining = Math.max(0, out.machinesAvailable - out.machinesUsed);

      out.lineScheduledQty = lineShiftScheduledQty.getOrDefault(out.processUsageKey, 0d);
      boolean lineReserved = out.lineScheduledQty > EPS;
      out.workersNeededDelta = lineReserved ? 0 : out.processConfig.requiredWorkers;
      out.machinesNeededDelta = lineReserved ? 0 : out.processConfig.requiredMachines;
      out.groupsByWorkers = out.workersRemaining >= out.workersNeededDelta ? 1 : 0;
      out.groupsByMachines = out.machinesRemaining >= out.machinesNeededDelta ? 1 : 0;
      out.maxGroups = Math.min(out.groupsByWorkers, out.groupsByMachines);
      if (out.maxGroups <= 0) {
        String reasonCode =
            SchedulerExplainSupport.capacityReasonCode(
                out.groupsByWorkers, out.groupsByMachines);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("workers_available", out.workersAvailable);
        evidence.put("workers_used", out.workersUsed);
        evidence.put("workers_remaining", out.workersRemaining);
        evidence.put("workers_required_per_shift", out.processConfig.requiredWorkers);
        evidence.put("machines_available", out.machinesAvailable);
        evidence.put("machines_used", out.machinesUsed);
        evidence.put("machines_remaining", out.machinesRemaining);
        evidence.put("machines_required_per_shift", out.processConfig.requiredMachines);
        evidence.put("line_code", binding.lineCode);
        evidence.put("remaining_qty", out.remainingQty);
        evidence.put("allowance_qty", out.allowanceQty);
        evidence.put("process_code", task.processCode);
        out.blockedReason = new ReasonInfo(
          reasonCode,
          reasonCode.equals(REASON_CAPACITY_MANPOWER)
            ? "Available manpower cannot satisfy required workers for this line."
            : reasonCode.equals(REASON_CAPACITY_MACHINE)
              ? "Available machines cannot satisfy required machines for this line."
              : "Resource capacity is insufficient in this shift.",
          "ENGINE",
          "CAPACITY",
          SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, EPS),
          evidence
        );
        if (blockedReason == null) {
          blockedReason = out.blockedReason;
        }
        continue;
      }

      double capacityFactor = shiftCapacityFactor(shiftCode) * processCapacityFactor(task.processCode);
      String latestProduct = lastProductByShiftProcess.get(out.processUsageKey);
      if (latestProduct != null && !latestProduct.equals(task.productCode)) {
        capacityFactor *= CHANGEOVER_CAPACITY_FACTOR;
        out.changeoverPenaltyApplied = true;
      }
      Set<String> productMix = productMixByShiftProcess.getOrDefault(out.processUsageKey, Set.of());
      if (!productMix.isEmpty() && !productMix.contains(task.productCode)) {
        capacityFactor *= PRODUCT_MIX_CAPACITY_FACTOR;
        out.changeoverPenaltyApplied = true;
      }
      out.groupCapacity = Math.max(EPS, out.processConfig.capacityPerShift * capacityFactor);
      out.capacityQty = Math.max(0d, out.groupCapacity - out.lineScheduledQty);
      if (out.capacityQty <= EPS) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("process_code", task.processCode);
        evidence.put("line_code", binding.lineCode);
        evidence.put("line_capacity_per_shift", round3(out.groupCapacity));
        evidence.put("line_scheduled_qty", round3(out.lineScheduledQty));
        out.blockedReason = new ReasonInfo(
          REASON_CAPACITY_UNKNOWN,
          "Line capacity is exhausted in current shift.",
          "ENGINE",
          "CAPACITY",
          SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, EPS),
          evidence
        );
        if (blockedReason == null) {
          blockedReason = out.blockedReason;
        }
        continue;
      }

      out.materialShiftKey = shiftId + "#" + task.productCode + "#" + task.processCode;
      out.materialConsumedKey = task.productCode + "#" + task.processCode;
      out.materialRemainingQty = Double.POSITIVE_INFINITY;

      out.componentKey = componentKeyForTask(task, order, false);
      out.componentRemainingQty = componentRemainingForShift(shiftId, out.componentKey, cumulativeComponentByShift, componentConsumed);
      if (out.componentRemainingQty <= EPS) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("process_code", task.processCode);
        evidence.put("product_code", task.productCode);
        evidence.put("component_key", out.componentKey);
        evidence.put("component_remaining", round3(out.componentRemainingQty));
        out.blockedReason = new ReasonInfo(
          REASON_COMPONENT_SHORTAGE,
          "BOM component is not kitted/arrived for current shift.",
          "ENGINE",
          "MATERIAL",
          SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, EPS),
          evidence
        );
        if (blockedReason == null) {
          blockedReason = out.blockedReason;
        }
        continue;
      }

      out.feasibleQty = round3(
        Math.min(
          Math.min(out.remainingQty, out.allowanceQty),
          Math.min(out.capacityQty, Math.min(out.materialRemainingQty, out.componentRemainingQty))
        )
      );
      if (out.feasibleQty <= EPS) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("remaining_qty", out.remainingQty);
        evidence.put("allowance_qty", out.allowanceQty);
        evidence.put("capacity_qty", round3(out.capacityQty));
        evidence.put("material_remaining", round3(out.materialRemainingQty));
        out.blockedReason = new ReasonInfo(
          REASON_CAPACITY_UNKNOWN,
          "No allocatable quantity after multi-constraint evaluation.",
          "ENGINE",
          "UNKNOWN",
          SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, EPS),
          evidence
        );
        if (blockedReason == null) {
          blockedReason = out.blockedReason;
        }
        continue;
      }

      double minLot = SchedulerDependencySupport.minLotSize(task.processCode);
      if (out.feasibleQty + EPS < minLot && out.remainingQty > minLot) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("process_code", task.processCode);
        evidence.put("feasible_qty", round3(out.feasibleQty));
        evidence.put("min_lot_qty", round3(minLot));
        evidence.put("remaining_qty", round3(out.remainingQty));
        out.blockedReason = new ReasonInfo(
          REASON_TRANSFER_CONSTRAINT,
          "Feasible quantity does not reach minimum lot size for this process in current shift.",
          "ENGINE",
          "DEPENDENCY",
          SchedulerDependencySupport.dependencyStatusAtShift(task, tasks, producedBeforeShift, EPS),
          evidence
        );
        out.feasibleQty = 0d;
        if (blockedReason == null) {
          blockedReason = out.blockedReason;
        }
        continue;
      }

      if (best == null || compareLineCandidate(out, best) > 0) {
        best = out;
      }
    }

    if (best != null) {
      return best;
    }
    base.blockedReason = blockedReason;
    return base;
  }

  private static int compareLineCandidate(CandidateEvaluation left, CandidateEvaluation right) {
    if (left == null && right == null) {
      return 0;
    }
    if (left == null) {
      return -1;
    }
    if (right == null) {
      return 1;
    }
    double leftLoadRatio = left.groupCapacity <= EPS ? 1d : left.lineScheduledQty / left.groupCapacity;
    double rightLoadRatio = right.groupCapacity <= EPS ? 1d : right.lineScheduledQty / right.groupCapacity;
    int byLoadRatio = Double.compare(rightLoadRatio, leftLoadRatio);
    if (byLoadRatio != 0) {
      return byLoadRatio;
    }
    int byFeasibleQty = Double.compare(left.feasibleQty, right.feasibleQty);
    if (byFeasibleQty != 0) {
      return byFeasibleQty;
    }
    int byCapacity = Double.compare(left.capacityQty, right.capacityQty);
    if (byCapacity != 0) {
      return byCapacity;
    }
    String leftLineCode = left.lineBinding == null ? "" : normalizeLineToken(left.lineBinding.lineCode, "");
    String rightLineCode = right.lineBinding == null ? "" : normalizeLineToken(right.lineBinding.lineCode, "");
    return rightLineCode.compareTo(leftLineCode);
  }
}

