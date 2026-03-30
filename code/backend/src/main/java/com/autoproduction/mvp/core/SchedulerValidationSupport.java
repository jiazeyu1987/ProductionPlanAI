package com.autoproduction.mvp.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchedulerValidationSupport {

  private SchedulerValidationSupport() {}

  static void validateDependencies(
    MvpDomain.ScheduleVersion schedule,
    List<Map<String, Object>> violations,
    double eps
  ) {
    Map<String, MvpDomain.ScheduleTask> taskByKey = new HashMap<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      if (task == null || task.taskKey == null) {
        continue;
      }
      taskByKey.put(task.taskKey, task);
      if (task.producedQty > task.targetQty + eps) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "TASK_OVER_PRODUCED");
        row.put("taskKey", task.taskKey);
        row.put("producedQty", round3(task.producedQty));
        row.put("targetQty", round3(task.targetQty));
        violations.add(row);
      }
    }

    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      if (task == null || task.predecessorTaskKey == null || task.predecessorTaskKey.isBlank()) {
        continue;
      }
      MvpDomain.ScheduleTask predecessor = taskByKey.get(task.predecessorTaskKey);
      if (predecessor == null) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "DEPENDENCY_PREDECESSOR_MISSING");
        row.put("taskKey", task.taskKey);
        row.put("predecessorTaskKey", task.predecessorTaskKey);
        violations.add(row);
        continue;
      }
      if (task.producedQty > predecessor.producedQty + eps) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "DEPENDENCY_RELEASE_OVERFLOW");
        row.put("taskKey", task.taskKey);
        row.put("predecessorTaskKey", task.predecessorTaskKey);
        row.put("taskProducedQty", round3(task.producedQty));
        row.put("predecessorProducedQty", round3(predecessor.producedQty));
        violations.add(row);
      }
    }
  }

  static void validateFrozenOrders(
    List<MvpDomain.Order> orders,
    MvpDomain.ScheduleVersion schedule,
    List<Map<String, Object>> violations
  ) {
    Map<String, Boolean> frozenByOrder = new HashMap<>();
    if (orders != null) {
      for (MvpDomain.Order order : orders) {
        if (order == null || order.orderNo == null) {
          continue;
        }
        frozenByOrder.put(order.orderNo, order.frozen);
      }
    }

    Map<String, Boolean> reportedOrder = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      if (allocation == null || allocation.orderNo == null) {
        continue;
      }
      if (!Boolean.TRUE.equals(frozenByOrder.get(allocation.orderNo))) {
        continue;
      }
      if (Boolean.TRUE.equals(reportedOrder.putIfAbsent(allocation.orderNo, true))) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", "FROZEN_ORDER_ALLOCATED");
      row.put("orderNo", allocation.orderNo);
      row.put("taskKey", allocation.taskKey);
      row.put("shiftId", allocation.shiftId);
      row.put("scheduledQty", round3(allocation.scheduledQty));
      violations.add(row);
    }
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}
