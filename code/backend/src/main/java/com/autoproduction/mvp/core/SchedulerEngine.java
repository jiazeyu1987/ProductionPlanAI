package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SchedulerEngine {
  private static final double EPS = 1e-9d;

  private SchedulerEngine() {}

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo
  ) {
    validateInput(state);

    List<Map<String, Object>> shifts = buildShifts(state);
    Map<String, Integer> workerByShiftProcess = resourceIndex(state.workerPools);
    Map<String, Integer> machineByShiftProcess = resourceIndex(state.machinePools);
    Map<String, Double> materialByShiftProductProcess = materialIndex(state.materialAvailability);
    Map<String, MvpDomain.ProcessConfig> processConfigMap = new HashMap<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      processConfigMap.put(process.processCode, process);
    }

    List<MvpDomain.Order> sortedOrders = new ArrayList<>(orders);
    sortedOrders.sort(Comparator
      .comparing((MvpDomain.Order o) -> !o.urgent)
      .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(o -> o.orderNo));

    Map<String, MvpDomain.ScheduleTask> tasks = new LinkedHashMap<>();
    List<Map<String, Object>> unscheduled = new ArrayList<>();
    Map<String, Integer> shiftWorkersUsed = new HashMap<>();
    Map<String, Integer> shiftMachinesUsed = new HashMap<>();
    Map<String, Double> shiftMaterialUsed = new HashMap<>();
    List<MvpDomain.Allocation> allocations = new ArrayList<>();

    for (MvpDomain.Order order : sortedOrders) {
      List<MvpDomain.OrderItem> items = order.items == null ? List.of() : order.items;
      for (int itemIndex = 0; itemIndex < items.size(); itemIndex += 1) {
        MvpDomain.OrderItem item = items.get(itemIndex);
        List<MvpDomain.ProcessStep> route = state.processRoutes.getOrDefault(item.productCode, List.of());
        for (int stepIndex = 0; stepIndex < route.size(); stepIndex += 1) {
          MvpDomain.ProcessStep step = route.get(stepIndex);
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
          task.producedQty = 0d;
          tasks.put(task.taskKey, task);
        }
      }
    }

    for (Map<String, Object> shift : shifts) {
      String shiftId = String.valueOf(shift.get("shiftId"));
      String shiftDate = String.valueOf(shift.get("date"));
      String shiftCode = String.valueOf(shift.get("shiftCode"));
      Map<String, Double> producedBeforeShift = snapshotProduced(tasks);

      for (MvpDomain.Order order : sortedOrders) {
        if (order.frozen) {
          continue;
        }
        for (MvpDomain.ScheduleTask task : tasks.values()) {
          if (!Objects.equals(task.orderNo, order.orderNo)) {
            continue;
          }
          double remainingQty = round3(task.targetQty - task.producedQty);
          if (remainingQty <= EPS) {
            continue;
          }
          MvpDomain.ProcessConfig processConfig = processConfigMap.get(task.processCode);
          if (processConfig == null) {
            continue;
          }

          double allowance = calcAllowance(task, tasks, producedBeforeShift);
          if (allowance <= EPS) {
            continue;
          }

          String processUsageKey = shiftId + "#" + task.processCode;
          int workersAvailable = workerByShiftProcess.getOrDefault(processUsageKey, 0);
          int machinesAvailable = machineByShiftProcess.getOrDefault(processUsageKey, 0);
          int workersUsed = shiftWorkersUsed.getOrDefault(processUsageKey, 0);
          int machinesUsed = shiftMachinesUsed.getOrDefault(processUsageKey, 0);

          int workersRemaining = Math.max(0, workersAvailable - workersUsed);
          int machinesRemaining = Math.max(0, machinesAvailable - machinesUsed);
          int groupsByWorkers = workersRemaining / Math.max(1, processConfig.requiredWorkers);
          int groupsByMachines = machinesRemaining / Math.max(1, processConfig.requiredMachines);
          int maxGroups = Math.min(groupsByWorkers, groupsByMachines);
          if (maxGroups <= 0) {
            continue;
          }
          double capacityByResources = maxGroups * processConfig.capacityPerShift;

          String materialKey = shiftId + "#" + task.productCode + "#" + task.processCode;
          double materialAvailable = materialByShiftProductProcess.getOrDefault(materialKey, 0d);
          double materialUsed = shiftMaterialUsed.getOrDefault(materialKey, 0d);
          double materialRemaining = Math.max(0d, materialAvailable - materialUsed);
          if (materialRemaining <= EPS) {
            continue;
          }

          double schedulable = Math.min(Math.min(remainingQty, allowance), Math.min(capacityByResources, materialRemaining));
          if (schedulable <= EPS) {
            continue;
          }

          double scheduledQty = round3(schedulable);
          int groupsUsed = (int) Math.ceil(scheduledQty / Math.max(1d, processConfig.capacityPerShift));
          shiftWorkersUsed.put(processUsageKey, workersUsed + groupsUsed * processConfig.requiredWorkers);
          shiftMachinesUsed.put(processUsageKey, machinesUsed + groupsUsed * processConfig.requiredMachines);
          shiftMaterialUsed.put(materialKey, materialUsed + scheduledQty);
          task.producedQty = round3(task.producedQty + scheduledQty);

          MvpDomain.Allocation allocation = new MvpDomain.Allocation();
          allocation.taskKey = task.taskKey;
          allocation.orderNo = task.orderNo;
          allocation.productCode = task.productCode;
          allocation.processCode = task.processCode;
          allocation.dependencyType = task.dependencyType;
          allocation.shiftId = shiftId;
          allocation.date = shiftDate;
          allocation.shiftCode = shiftCode;
          allocation.scheduledQty = scheduledQty;
          allocation.workersUsed = groupsUsed * processConfig.requiredWorkers;
          allocation.machinesUsed = groupsUsed * processConfig.requiredMachines;
          allocation.groupsUsed = groupsUsed;
          allocations.add(allocation);
        }
      }
    }

    for (MvpDomain.ScheduleTask task : tasks.values()) {
      double remaining = round3(task.targetQty - task.producedQty);
      if (remaining <= EPS) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("taskKey", task.taskKey);
      row.put("orderNo", task.orderNo);
      row.put("productCode", task.productCode);
      row.put("processCode", task.processCode);
      row.put("remainingQty", remaining);
      row.put("reasons", List.of("CAPACITY_LIMIT"));
      unscheduled.add(row);
    }

    MvpDomain.ScheduleVersion result = new MvpDomain.ScheduleVersion();
    result.requestId = requestId;
    result.versionNo = versionNo;
    result.generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    result.shiftHours = state.shiftHours;
    result.shiftsPerDay = state.shiftsPerDay;
    result.shifts = shifts;
    result.tasks = new ArrayList<>(tasks.values());
    result.allocations = allocations;
    result.unscheduled = unscheduled;
    result.metrics = buildMetrics(tasks.values(), allocations, unscheduled);
    result.metadata = new HashMap<>();
    result.metadata.put("hardConstraints", List.of("MAN", "MACHINE", "MATERIAL"));
    result.metadata.put("dependencyTypes", List.of("FS", "SS"));
    result.metadata.put("filteredFrozenOrders", sortedOrders.stream().filter(o -> o.frozen).map(o -> o.orderNo).toList());
    return result;
  }

  static Map<String, Object> validate(MvpDomain.State state, MvpDomain.ScheduleVersion schedule) {
    List<Map<String, Object>> violations = new ArrayList<>();
    validateDependencies(schedule, violations);
    validateFrozenOrders(state.orders, schedule, violations);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("passed", violations.isEmpty());
    out.put("violationCount", violations.size());
    out.put("violations", violations);
    return out;
  }

  private static void validateInput(MvpDomain.State state) {
    if (state.startDate == null) {
      throw new IllegalArgumentException("startDate is required.");
    }
    if (state.shiftHours != 12) {
      throw new IllegalArgumentException("P0 requires shiftHours = 12.");
    }
    if (state.shiftsPerDay < 1 || state.shiftsPerDay > 2) {
      throw new IllegalArgumentException("P0 requires shiftsPerDay in range [1,2].");
    }
    if (state.processes == null || state.processes.isEmpty()) {
      throw new IllegalArgumentException("At least one process config is required.");
    }
  }

  private static List<Map<String, Object>> buildShifts(MvpDomain.State state) {
    List<Map<String, Object>> shifts = new ArrayList<>();
    Map<String, Boolean> openByKey = new HashMap<>();
    for (MvpDomain.ShiftRow row : state.shiftCalendar) {
      openByKey.put(row.date + "#" + row.shiftCode, row.open);
    }
    String[] shiftCodes = {"D", "N"};
    for (int i = 0; i < state.horizonDays; i += 1) {
      LocalDate date = state.startDate.plusDays(i);
      for (int s = 0; s < state.shiftsPerDay; s += 1) {
        String shiftCode = shiftCodes[s];
        String shiftId = date + "#" + shiftCode;
        if (!openByKey.getOrDefault(shiftId, true)) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", shifts.size());
        row.put("date", date.toString());
        row.put("shiftCode", shiftCode);
        row.put("shiftId", shiftId);
        shifts.add(row);
      }
    }
    if (shifts.isEmpty()) {
      throw new IllegalArgumentException("No open shifts found in planning horizon.");
    }
    return shifts;
  }

  private static Map<String, Integer> resourceIndex(List<MvpDomain.ResourceRow> rows) {
    Map<String, Integer> out = new HashMap<>();
    for (MvpDomain.ResourceRow row : rows) {
      out.put(row.date + "#" + row.shiftCode + "#" + row.processCode, row.available);
    }
    return out;
  }

  private static Map<String, Double> materialIndex(List<MvpDomain.MaterialRow> rows) {
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.MaterialRow row : rows) {
      out.put(row.date + "#" + row.shiftCode + "#" + row.productCode + "#" + row.processCode, row.availableQty);
    }
    return out;
  }

  private static Map<String, Double> snapshotProduced(Map<String, MvpDomain.ScheduleTask> tasks) {
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.ScheduleTask task : tasks.values()) {
      out.put(task.taskKey, task.producedQty);
    }
    return out;
  }

  private static double calcAllowance(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift
  ) {
    double remaining = round3(task.targetQty - task.producedQty);
    if (remaining <= EPS) {
      return 0d;
    }
    if (task.predecessorTaskKey == null) {
      return remaining;
    }
    MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
    if (predecessor == null) {
      return 0d;
    }
    if ("FS".equals(task.dependencyType)) {
      return Math.max(0d, round3(producedBeforeShift.getOrDefault(task.predecessorTaskKey, 0d) - task.producedQty));
    }
    return Math.max(0d, round3(predecessor.producedQty - task.producedQty));
  }

  private static Map<String, Object> buildMetrics(
    Iterable<MvpDomain.ScheduleTask> tasks,
    List<MvpDomain.Allocation> allocations,
    List<Map<String, Object>> unscheduled
  ) {
    double targetQty = 0d;
    double producedQty = 0d;
    int taskCount = 0;
    for (MvpDomain.ScheduleTask task : tasks) {
      taskCount += 1;
      targetQty += task.targetQty;
      producedQty += task.producedQty;
    }
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("taskCount", taskCount);
    metrics.put("allocationCount", allocations.size());
    metrics.put("targetQty", round3(targetQty));
    metrics.put("scheduledQty", round3(producedQty));
    metrics.put("scheduleCompletionRate", targetQty > EPS ? round3((producedQty / targetQty) * 100d) : 0d);
    metrics.put("unscheduledTaskCount", unscheduled.size());
    return metrics;
  }

  private static void validateDependencies(MvpDomain.ScheduleVersion schedule, List<Map<String, Object>> violations) {
    Map<String, List<MvpDomain.Allocation>> byTask = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      byTask.computeIfAbsent(allocation.taskKey, key -> new ArrayList<>()).add(allocation);
    }
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      if (task.predecessorTaskKey == null) {
        continue;
      }
      double taskQty = byTask.getOrDefault(task.taskKey, List.of()).stream().mapToDouble(a -> a.scheduledQty).sum();
      double predecessorQty = byTask.getOrDefault(task.predecessorTaskKey, List.of()).stream().mapToDouble(a -> a.scheduledQty).sum();
      if (taskQty - predecessorQty > EPS) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "DEPENDENCY_VIOLATION");
        row.put("taskKey", task.taskKey);
        row.put("predecessorTaskKey", task.predecessorTaskKey);
        row.put("dependencyType", task.dependencyType);
        violations.add(row);
      }
    }
  }

  private static void validateFrozenOrders(List<MvpDomain.Order> orders, MvpDomain.ScheduleVersion schedule, List<Map<String, Object>> violations) {
    Set<String> frozen = new HashSet<>();
    for (MvpDomain.Order order : orders) {
      if (order.frozen) {
        frozen.add(order.orderNo);
      }
    }
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      if (!frozen.contains(allocation.orderNo)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("code", "FROZEN_ORDER_SCHEDULED");
      row.put("orderNo", allocation.orderNo);
      row.put("taskKey", allocation.taskKey);
      violations.add(row);
      return;
    }
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}

