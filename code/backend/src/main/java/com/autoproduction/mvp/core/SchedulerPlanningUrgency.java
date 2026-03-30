package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.URGENT_MIN_SHARE_PER_PROCESS;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.lineUsageKey;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.processCapacityFactor;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.shiftCapacityFactor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningUrgency {
  private SchedulerPlanningUrgency() {}

  static Map<String, Double> buildUrgentDemandByProcess(
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder,
    Map<String, MvpDomain.Order> orderByNo,
    Set<String> lockedOrderSet
  ) {
    Map<String, Double> out = new HashMap<>();
    for (Map.Entry<String, List<MvpDomain.ScheduleTask>> entry : tasksByOrder.entrySet()) {
      MvpDomain.Order order = orderByNo.get(entry.getKey());
      if (order == null || order.frozen || !order.urgent || lockedOrderSet.contains(order.orderNo)) {
        continue;
      }
      for (MvpDomain.ScheduleTask task : entry.getValue()) {
        double remaining = round3(task.targetQty - task.producedQty);
        if (remaining <= EPS) {
          continue;
        }
        String processCode = normalizeProcessCode(task.processCode);
        if (processCode == null) {
          continue;
        }
        out.merge(processCode, remaining, Double::sum);
      }
    }
    return out;
  }

  static Map<String, Double> buildUrgentQuotaForShift(
    String shiftId,
    String shiftCode,
    Map<String, MvpDomain.Order> orderByNo,
    List<MvpDomain.Order> schedulableOrders,
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder,
    Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> lineShiftScheduledQty
  ) {
    Map<String, Double> urgentDemandByProcess = new HashMap<>();
    for (MvpDomain.Order order : schedulableOrders) {
      if (order == null || !order.urgent || order.frozen) {
        continue;
      }
      for (MvpDomain.ScheduleTask task : tasksByOrder.getOrDefault(order.orderNo, List.of())) {
        double remaining = round3(task.targetQty - task.producedQty);
        if (remaining <= EPS) {
          continue;
        }
        String processCode = normalizeProcessCode(task.processCode);
        if (processCode == null) {
          continue;
        }
        urgentDemandByProcess.merge(processCode, remaining, Double::sum);
      }
    }

    Map<String, Double> out = new HashMap<>();
    for (Map.Entry<String, Double> entry : urgentDemandByProcess.entrySet()) {
      String processCode = normalizeProcessCode(entry.getKey());
      double demand = entry.getValue();
      if (processCode == null || demand <= EPS) {
        continue;
      }
      List<MvpDomain.LineProcessBinding> bindings = lineBindingsByProcess.getOrDefault(processCode, List.of());
      if (bindings.isEmpty()) {
        continue;
      }
      String resourceUsageKey = shiftId + "#" + processCode;
      int workersRemaining = Math.max(0, workerByShiftProcess.getOrDefault(resourceUsageKey, 0) - shiftWorkersUsed.getOrDefault(resourceUsageKey, 0));
      int machinesRemaining = Math.max(0, machineByShiftProcess.getOrDefault(resourceUsageKey, 0) - shiftMachinesUsed.getOrDefault(resourceUsageKey, 0));
      double baseCapacity = 0d;
      for (MvpDomain.LineProcessBinding binding : bindings) {
        String lineUsageKey = lineUsageKey(shiftId, binding, processCode);
        double alreadyScheduled = lineShiftScheduledQty.getOrDefault(lineUsageKey, 0d);
        boolean lineReserved = alreadyScheduled > EPS;
        int workersNeeded = lineReserved ? 0 : Math.max(1, binding.requiredWorkers);
        int machinesNeeded = lineReserved ? 0 : Math.max(1, binding.requiredMachines);
        if (workersRemaining < workersNeeded || machinesRemaining < machinesNeeded) {
          continue;
        }
        double lineCapacity = Math.max(
          EPS,
          binding.capacityPerShift * shiftCapacityFactor(shiftCode) * processCapacityFactor(processCode)
        );
        double remainingCapacity = Math.max(0d, lineCapacity - alreadyScheduled);
        if (remainingCapacity <= EPS) {
          continue;
        }
        baseCapacity += remainingCapacity;
        workersRemaining -= workersNeeded;
        machinesRemaining -= machinesNeeded;
      }
      if (baseCapacity <= EPS) {
        continue;
      }
      double quota = Math.min(demand, baseCapacity * URGENT_MIN_SHARE_PER_PROCESS);
      if (quota > EPS) {
        out.put(processCode, round3(quota));
      }
    }
    return out;
  }
}

