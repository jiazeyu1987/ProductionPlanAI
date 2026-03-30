package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.reportingKey;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningEngineTaskBuildSupport {
  private SchedulerPlanningEngineTaskBuildSupport() {}

  record TaskBuild(
    List<MvpDomain.Order> sortedOrders,
    List<MvpDomain.Order> schedulableOrders,
    Map<String, MvpDomain.Order> orderByNo,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder,
    Set<String> finalTaskKeys,
    Map<String, Double> remainingQtyByOrder
  ) {}

  static TaskBuild build(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    Map<String, Double> cumulativeReportedByOrderProductProcess,
    Set<String> lockedOrderSet
  ) {
    List<MvpDomain.Order> sortedOrders = new ArrayList<>(orders);
    sortedOrders.sort(Comparator
      .comparing((MvpDomain.Order o) -> !o.urgent)
      .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(o -> o.orderNo));

    Map<String, MvpDomain.Order> orderByNo = new HashMap<>();
    Map<String, MvpDomain.ScheduleTask> tasks = new LinkedHashMap<>();
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder = new HashMap<>();
    Set<String> finalTaskKeys = new HashSet<>();
    Map<String, Double> remainingQtyByOrder = new HashMap<>();

    for (MvpDomain.Order order : sortedOrders) {
      orderByNo.put(order.orderNo, order);
      List<MvpDomain.OrderItem> items = order.items == null ? List.of() : order.items;
      double orderTarget = 0d;
      for (int itemIndex = 0; itemIndex < items.size(); itemIndex += 1) {
        MvpDomain.OrderItem item = items.get(itemIndex);
        List<MvpDomain.ProcessStep> route = state.processRoutes.getOrDefault(item.productCode, List.of());
        for (int stepIndex = 0; stepIndex < route.size(); stepIndex += 1) {
          MvpDomain.ProcessStep step = route.get(stepIndex);
          String normalizedStepProcessCode = normalizeProcessCode(step.processCode);
          String reportKey = reportingKey(order.orderNo, item.productCode, normalizedStepProcessCode);
          double reportedQty = Math.max(0d, cumulativeReportedByOrderProductProcess.getOrDefault(reportKey, 0d));
          MvpDomain.ScheduleTask task = new MvpDomain.ScheduleTask();
          task.taskKey = order.orderNo + "#" + itemIndex + "#" + stepIndex;
          task.orderNo = order.orderNo;
          task.itemIndex = itemIndex;
          task.stepIndex = stepIndex;
          task.productCode = item.productCode;
          task.processCode = step.processCode;
          task.dependencyType = step.dependencyType == null ? "FS" : step.dependencyType.toUpperCase();
          task.predecessorTaskKey = stepIndex == 0 ? null : order.orderNo + "#" + itemIndex + "#" + (stepIndex - 1);
          task.targetQty = Math.max(0d, item.qty - item.completedQty);
          // Non-final processes keep historical reported progress as route WIP baseline.
          task.producedQty = stepIndex == route.size() - 1 ? 0d : round3(Math.min(item.qty, reportedQty));
          if (stepIndex == route.size() - 1) {
            finalTaskKeys.add(task.taskKey);
            orderTarget += task.targetQty;
          }
          tasks.put(task.taskKey, task);
          tasksByOrder.computeIfAbsent(order.orderNo, ignored -> new ArrayList<>()).add(task);
        }
      }
      remainingQtyByOrder.put(order.orderNo, round3(orderTarget));
    }

    List<MvpDomain.Order> schedulableOrders = new ArrayList<>();
    for (MvpDomain.Order order : sortedOrders) {
      if (order.frozen || lockedOrderSet.contains(order.orderNo)) {
        continue;
      }
      if (!tasksByOrder.containsKey(order.orderNo)) {
        continue;
      }
      schedulableOrders.add(order);
    }

    return new TaskBuild(
      sortedOrders,
      schedulableOrders,
      orderByNo,
      tasks,
      tasksByOrder,
      finalTaskKeys,
      remainingQtyByOrder
    );
  }
}

