package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningMutations {
  private SchedulerPlanningMutations() {}

  static void decrementRemainingQtyByOrder(
    Map<String, Double> remainingQtyByOrder,
    String orderNo,
    double producedQty,
    boolean affectsOrderRemaining
  ) {
    if (remainingQtyByOrder == null || orderNo == null || orderNo.isBlank() || producedQty <= EPS) {
      return;
    }
    if (!affectsOrderRemaining) {
      return;
    }
    remainingQtyByOrder.put(orderNo, round3(Math.max(0d, remainingQtyByOrder.getOrDefault(orderNo, 0d) - producedQty)));
  }

  static void addProducedByTaskShift(
    Map<String, Map<Integer, Double>> producedByTaskShift,
    String taskKey,
    int shiftIndex,
    double producedQty
  ) {
    if (producedByTaskShift == null || taskKey == null || taskKey.isBlank() || shiftIndex < 0 || producedQty <= EPS) {
      return;
    }
    producedByTaskShift
      .computeIfAbsent(taskKey, ignored -> new HashMap<>())
      .merge(shiftIndex, round3(producedQty), Double::sum);
  }

  static void registerProcessProductUsage(
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess,
    String processUsageKey,
    String productCode
  ) {
    if (processUsageKey == null || processUsageKey.isBlank() || productCode == null || productCode.isBlank()) {
      return;
    }
    lastProductByShiftProcess.put(processUsageKey, productCode);
    productMixByShiftProcess.computeIfAbsent(processUsageKey, ignored -> new java.util.HashSet<>()).add(productCode);
  }
}

