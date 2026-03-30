package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentKeyForTask;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.cumulativeComponentIndex;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.effectiveResourceIndex;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.shiftIndexById;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningValidation {
  private SchedulerPlanningValidation() {}

  static Map<String, Object> validate(MvpDomain.State state, MvpDomain.ScheduleVersion schedule) {
    List<Map<String, Object>> violations = new ArrayList<>();
    SchedulerValidationSupport.validateDependencies(schedule, violations, EPS);
    SchedulerValidationSupport.validateFrozenOrders(state.orders, schedule, violations);
    validateResourceAndMaterialUsage(state, schedule, violations);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("passed", violations.isEmpty());
    out.put("violationCount", violations.size());
    out.put("violations", violations);
    return out;
  }

  private static void validateResourceAndMaterialUsage(
    MvpDomain.State state,
    MvpDomain.ScheduleVersion schedule,
    List<Map<String, Object>> violations
  ) {
    Map<String, Integer> workerCapacity = effectiveResourceIndex(state.workerPools, state.initialWorkerOccupancy);
    Map<String, Integer> machineCapacity = effectiveResourceIndex(state.machinePools, state.initialMachineOccupancy);
    Map<String, Integer> workerUsed = new HashMap<>();
    Map<String, Integer> machineUsed = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      String key = allocation.shiftId + "#" + allocation.processCode;
      workerUsed.merge(key, Math.max(0, allocation.workersUsed), Integer::sum);
      machineUsed.merge(key, Math.max(0, allocation.machinesUsed), Integer::sum);
    }
    for (Map.Entry<String, Integer> entry : workerUsed.entrySet()) {
      int capacity = workerCapacity.getOrDefault(entry.getKey(), 0);
      if (entry.getValue() <= capacity) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", "RESOURCE_OVERBOOKED");
      row.put("resourceType", "WORKER");
      row.put("shiftProcessKey", entry.getKey());
      row.put("used", entry.getValue());
      row.put("capacity", capacity);
      violations.add(row);
    }
    for (Map.Entry<String, Integer> entry : machineUsed.entrySet()) {
      int capacity = machineCapacity.getOrDefault(entry.getKey(), 0);
      if (entry.getValue() <= capacity) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", "RESOURCE_OVERBOOKED");
      row.put("resourceType", "MACHINE");
      row.put("shiftProcessKey", entry.getKey());
      row.put("used", entry.getValue());
      row.put("capacity", capacity);
      violations.add(row);
    }

    Map<String, Integer> shiftIndexById = shiftIndexById(schedule.shifts);
    Map<Integer, String> shiftIdByIndex = new HashMap<>();
    for (Map.Entry<String, Integer> entry : shiftIndexById.entrySet()) {
      shiftIdByIndex.put(entry.getValue(), entry.getKey());
    }

    Map<String, MvpDomain.Order> orderByNo = new HashMap<>();
    for (MvpDomain.Order order : state.orders) {
      orderByNo.put(order.orderNo, order);
    }
    Map<String, MvpDomain.ScheduleTask> taskByKey = new HashMap<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      taskByKey.put(task.taskKey, task);
    }
    Map<String, Double> cumulativeComponent = cumulativeComponentIndex(state, schedule.shifts);
    Map<String, Map<Integer, Double>> componentUsageByShift = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      Integer index = shiftIndexById.get(allocation.shiftId);
      if (index == null) {
        continue;
      }
      MvpDomain.ScheduleTask task = taskByKey.get(allocation.taskKey);
      if (task == null || task.stepIndex != 0) {
        continue;
      }
      MvpDomain.Order order = orderByNo.get(allocation.orderNo);
      String componentKey = componentKeyForTask(task, order, true);
      if (componentKey == null || componentKey.isBlank()) {
        continue;
      }
      componentUsageByShift
        .computeIfAbsent(componentKey, ignored -> new HashMap<>())
        .merge(index, Math.max(0d, allocation.scheduledQty), Double::sum);
    }
    for (Map.Entry<String, Map<Integer, Double>> entry : componentUsageByShift.entrySet()) {
      double cumulativeUsed = 0d;
      for (int index = 0; index < schedule.shifts.size(); index += 1) {
        cumulativeUsed = round3(cumulativeUsed + entry.getValue().getOrDefault(index, 0d));
        String shiftId = shiftIdByIndex.get(index);
        if (shiftId == null) {
          continue;
        }
        double cumulativeAvailable = cumulativeComponent.getOrDefault(shiftId + "#" + entry.getKey(), 0d);
        if (cumulativeUsed <= cumulativeAvailable + EPS) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "COMPONENT_OVERBOOKED");
        row.put("componentKey", entry.getKey());
        row.put("shiftId", shiftId);
        row.put("usedCumulative", round3(cumulativeUsed));
        row.put("availableCumulative", round3(cumulativeAvailable));
        violations.add(row);
        break;
      }
    }
  }

  @SuppressWarnings("unused")
  private static Set<String> normalizeOrderNoSet(Set<String> orderNos) {
    Set<String> out = new HashSet<>();
    if (orderNos == null) {
      return out;
    }
    for (String orderNo : orderNos) {
      if (orderNo == null) {
        continue;
      }
      String normalized = orderNo.trim();
      if (!normalized.isBlank()) {
        out.add(normalized);
      }
    }
    return out;
  }
}

