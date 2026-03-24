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
  private static final String REASON_CAPACITY_MANPOWER = "CAPACITY_MANPOWER";
  private static final String REASON_CAPACITY_MACHINE = "CAPACITY_MACHINE";
  private static final String REASON_CAPACITY_UNKNOWN = "CAPACITY_UNKNOWN";
  private static final String REASON_MATERIAL_SHORTAGE = "MATERIAL_SHORTAGE";
  private static final String REASON_DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
  private static final String REASON_FROZEN_BY_POLICY = "FROZEN_BY_POLICY";
  private static final String REASON_LOCKED_PRESERVED = "LOCKED_PRESERVED";
  private static final String DEPENDENCY_READY = "READY";
  private static final String DEPENDENCY_WAIT_PREDECESSOR = "WAIT_PREDECESSOR";
  private static final String DEPENDENCY_BLOCKED_PREDECESSOR = "BLOCKED_BY_PREDECESSOR";
  private static final String TASK_STATUS_READY = "READY";
  private static final String TASK_STATUS_PARTIALLY_ALLOCATED = "PARTIALLY_ALLOCATED";
  private static final String TASK_STATUS_UNSCHEDULED = "UNSCHEDULED";
  private static final String TASK_STATUS_PRESERVED_LOCKED = "PRESERVED_LOCKED";
  private static final String TASK_STATUS_SKIPPED_FROZEN = "SKIPPED_FROZEN";

  private SchedulerEngine() {}

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo
  ) {
    return generate(state, orders, requestId, versionNo, null, Set.of());
  }

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo,
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrders
  ) {
    validateInput(state);
    Set<String> lockedOrderSet = lockedOrders == null ? Set.of() : new HashSet<>(lockedOrders);

    List<Map<String, Object>> shifts = buildShifts(state);
    Map<String, Integer> workerByShiftProcess = effectiveResourceIndex(state.workerPools, state.initialWorkerOccupancy);
    Map<String, Integer> machineByShiftProcess = effectiveResourceIndex(state.machinePools, state.initialMachineOccupancy);
    Map<String, Double> materialByShiftProductProcess = materialIndex(state.materialAvailability);
    Map<String, Integer> maxWorkersByProcess = maxResourceByProcess(workerByShiftProcess);
    Map<String, Integer> maxMachinesByProcess = maxResourceByProcess(machineByShiftProcess);
    Map<String, Double> totalMaterialByProductProcess = totalMaterialByProductProcess(state.materialAvailability);
    Map<String, MvpDomain.ProcessConfig> processConfigMap = new HashMap<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      processConfigMap.put(process.processCode, process);
    }

    List<MvpDomain.Order> sortedOrders = new ArrayList<>(orders);
    sortedOrders.sort(Comparator
      .comparing((MvpDomain.Order o) -> !o.urgent)
      .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(o -> o.orderNo));

    Map<String, MvpDomain.Order> orderByNo = new HashMap<>();
    Map<String, MvpDomain.ScheduleTask> tasks = new LinkedHashMap<>();
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder = new HashMap<>();
    List<Map<String, Object>> unscheduled = new ArrayList<>();
    Map<String, Integer> shiftWorkersUsed = new HashMap<>();
    Map<String, Integer> shiftMachinesUsed = new HashMap<>();
    Map<String, Double> shiftMaterialUsed = new HashMap<>();
    Map<String, ReasonInfo> lastBlockedByTask = new HashMap<>();
    List<MvpDomain.Allocation> allocations = new ArrayList<>();

    for (MvpDomain.Order order : sortedOrders) {
      orderByNo.put(order.orderNo, order);
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
          tasksByOrder.computeIfAbsent(order.orderNo, ignored -> new ArrayList<>()).add(task);
        }
      }
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

    Set<String> openShiftIds = new HashSet<>();
    for (Map<String, Object> shift : shifts) {
      openShiftIds.add(String.valueOf(shift.get("shiftId")));
    }
    Set<String> preservedLockedTaskKeys = new HashSet<>();
    int preservedLockedAllocationCount = applyBaselineLockedAllocations(
      baseVersion,
      lockedOrderSet,
      tasks,
      orderByNo,
      openShiftIds,
      processConfigMap,
      shiftWorkersUsed,
      shiftMachinesUsed,
      shiftMaterialUsed,
      allocations,
      preservedLockedTaskKeys
    );

    for (Map<String, Object> shift : shifts) {
      String shiftId = String.valueOf(shift.get("shiftId"));
      String shiftDate = String.valueOf(shift.get("date"));
      String shiftCode = String.valueOf(shift.get("shiftCode"));
      Map<String, Double> producedBeforeShift = snapshotProduced(tasks);

      for (MvpDomain.Order order : schedulableOrders) {
        List<MvpDomain.ScheduleTask> orderTasks = tasksByOrder.getOrDefault(order.orderNo, List.of());
        for (MvpDomain.ScheduleTask task : orderTasks) {
          double remainingQty = round3(task.targetQty - task.producedQty);
          if (remainingQty <= EPS) {
            lastBlockedByTask.remove(task.taskKey);
            continue;
          }
          MvpDomain.ProcessConfig processConfig = processConfigMap.get(task.processCode);
          if (processConfig == null) {
            lastBlockedByTask.put(
              task.taskKey,
              ReasonInfo.capacity(
                REASON_CAPACITY_UNKNOWN,
                "Process config is missing, capacity cannot be resolved.",
                "UNKNOWN",
                task.processCode,
                0,
                0,
                0d
              )
            );
            continue;
          }

          double allowance = calcAllowance(task, tasks, producedBeforeShift);
          if (allowance <= EPS) {
            lastBlockedByTask.put(task.taskKey, dependencyBlockedReason(task, tasks, producedBeforeShift));
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
            String reasonCode = capacityReasonCode(groupsByWorkers, groupsByMachines);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("workers_available", workersAvailable);
            evidence.put("workers_used", workersUsed);
            evidence.put("workers_remaining", workersRemaining);
            evidence.put("workers_required_per_group", processConfig.requiredWorkers);
            evidence.put("machines_available", machinesAvailable);
            evidence.put("machines_used", machinesUsed);
            evidence.put("machines_remaining", machinesRemaining);
            evidence.put("machines_required_per_group", processConfig.requiredMachines);
            evidence.put("remaining_qty", remainingQty);
            evidence.put("allowance_qty", allowance);
            evidence.put("process_code", task.processCode);
            lastBlockedByTask.put(
              task.taskKey,
              new ReasonInfo(
                reasonCode,
                reasonCode.equals(REASON_CAPACITY_MANPOWER)
                  ? "Available manpower cannot satisfy required worker groups."
                  : reasonCode.equals(REASON_CAPACITY_MACHINE)
                    ? "Available machines cannot satisfy required machine groups."
                    : "Resource capacity is insufficient in this shift.",
                "ENGINE",
                "CAPACITY",
                dependencyStatusAtShift(task, tasks, producedBeforeShift),
                evidence
              )
            );
            continue;
          }
          double capacityByResources = maxGroups * processConfig.capacityPerShift;

          String materialKey = shiftId + "#" + task.productCode + "#" + task.processCode;
          double materialAvailable = materialByShiftProductProcess.getOrDefault(materialKey, 0d);
          double materialUsed = shiftMaterialUsed.getOrDefault(materialKey, 0d);
          double materialRemaining = Math.max(0d, materialAvailable - materialUsed);
          if (materialRemaining <= EPS) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("process_code", task.processCode);
            evidence.put("product_code", task.productCode);
            evidence.put("material_available", round3(materialAvailable));
            evidence.put("material_used", round3(materialUsed));
            evidence.put("material_remaining", round3(materialRemaining));
            evidence.put("remaining_qty", remainingQty);
            lastBlockedByTask.put(
              task.taskKey,
              new ReasonInfo(
                REASON_MATERIAL_SHORTAGE,
                "Material is exhausted in this shift for product/process.",
                "ENGINE",
                "MATERIAL",
                dependencyStatusAtShift(task, tasks, producedBeforeShift),
                evidence
              )
            );
            continue;
          }

          double schedulable = Math.min(Math.min(remainingQty, allowance), Math.min(capacityByResources, materialRemaining));
          if (schedulable <= EPS) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("remaining_qty", remainingQty);
            evidence.put("allowance_qty", allowance);
            evidence.put("capacity_qty", round3(capacityByResources));
            evidence.put("material_remaining", round3(materialRemaining));
            lastBlockedByTask.put(
              task.taskKey,
              new ReasonInfo(
                REASON_CAPACITY_UNKNOWN,
                "No allocatable quantity after constraint evaluation.",
                "ENGINE",
                "UNKNOWN",
                dependencyStatusAtShift(task, tasks, producedBeforeShift),
                evidence
              )
            );
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

          double remainingAfter = round3(task.targetQty - task.producedQty);
          if (remainingAfter <= EPS) {
            lastBlockedByTask.remove(task.taskKey);
          } else if (allowance <= capacityByResources + EPS && allowance <= materialRemaining + EPS) {
            lastBlockedByTask.put(task.taskKey, dependencyBlockedReason(task, tasks, producedBeforeShift));
          } else if (materialRemaining <= capacityByResources + EPS) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("process_code", task.processCode);
            evidence.put("product_code", task.productCode);
            evidence.put("material_remaining", round3(materialRemaining));
            evidence.put("remaining_after_shift", remainingAfter);
            lastBlockedByTask.put(
              task.taskKey,
              new ReasonInfo(
                REASON_MATERIAL_SHORTAGE,
                "Material remaining quantity is the binding constraint in current shift.",
                "ENGINE",
                "MATERIAL",
                dependencyStatusAtShift(task, tasks, producedBeforeShift),
                evidence
              )
            );
          } else {
            String reasonCode = capacityReasonCode(groupsByWorkers, groupsByMachines);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("process_code", task.processCode);
            evidence.put("capacity_by_resources", round3(capacityByResources));
            evidence.put("remaining_after_shift", remainingAfter);
            evidence.put("workers_remaining", workersRemaining);
            evidence.put("machines_remaining", machinesRemaining);
            evidence.put("workers_required_per_group", processConfig.requiredWorkers);
            evidence.put("machines_required_per_group", processConfig.requiredMachines);
            lastBlockedByTask.put(
              task.taskKey,
              new ReasonInfo(
                reasonCode,
                reasonCode.equals(REASON_CAPACITY_MANPOWER)
                  ? "Worker capacity is the binding constraint in current shift."
                  : reasonCode.equals(REASON_CAPACITY_MACHINE)
                    ? "Machine capacity is the binding constraint in current shift."
                    : "Resource capacity is the binding constraint in current shift.",
                "ENGINE",
                "CAPACITY",
                dependencyStatusAtShift(task, tasks, producedBeforeShift),
                evidence
              )
            );
          }
        }
      }
    }

    for (MvpDomain.ScheduleTask task : tasks.values()) {
      double remaining = round3(task.targetQty - task.producedQty);
      MvpDomain.Order order = orderByNo.get(task.orderNo);
      task.dependencyStatus = dependencyStatusAtVersionEnd(task, tasks);
      task.taskStatus = resolveTaskStatus(task, order, remaining);
      if (remaining <= EPS) {
        task.lastBlockReason = null;
        task.lastBlockReasonDetail = null;
        task.lastBlockingDimension = null;
        task.lastBlockEvidence = new LinkedHashMap<>();
        continue;
      }
      ReasonInfo reasonInfo;
      if (order != null && order.frozen) {
        reasonInfo = frozenReason(task);
      } else if (order != null && lockedOrderSet.contains(order.orderNo)) {
        reasonInfo = lockedPreservedReason(
          task,
          preservedLockedTaskKeys.contains(task.taskKey),
          baseVersion == null ? null : baseVersion.versionNo
        );
      } else {
        reasonInfo = lastBlockedByTask.get(task.taskKey);
      }
      if (reasonInfo == null) {
        reasonInfo = diagnoseUnscheduledReason(
          task,
          order,
          tasks,
          processConfigMap,
          maxWorkersByProcess,
          maxMachinesByProcess,
          totalMaterialByProductProcess
        );
      }
      task.lastBlockReason = reasonInfo.reasonCode;
      task.lastBlockReasonDetail = reasonInfo.reasonDetail;
      task.lastBlockingDimension = reasonInfo.blockingDimension;
      task.lastBlockEvidence = new LinkedHashMap<>(reasonInfo.evidence);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("taskKey", task.taskKey);
      row.put("orderNo", task.orderNo);
      row.put("productCode", task.productCode);
      row.put("processCode", task.processCode);
      row.put("remainingQty", remaining);
      row.put("reason_code", reasonInfo.reasonCode);
      row.put("reason_detail", reasonInfo.reasonDetail);
      row.put("reason_source", reasonInfo.reasonSource);
      row.put("blocking_dimension", reasonInfo.blockingDimension);
      row.put("dependency_status", reasonInfo.dependencyStatus);
      row.put("task_status", task.taskStatus);
      row.put("last_block_reason", reasonInfo.reasonCode);
      row.put("evidence", new LinkedHashMap<>(reasonInfo.evidence));
      row.put("reasons", reasonCodesForCompatibility(reasonInfo.reasonCode));
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
    result.metadata.put("reasonCodes", List.of(
      REASON_CAPACITY_MANPOWER,
      REASON_CAPACITY_MACHINE,
      REASON_CAPACITY_UNKNOWN,
      REASON_MATERIAL_SHORTAGE,
      REASON_DEPENDENCY_BLOCKED,
      REASON_FROZEN_BY_POLICY,
      REASON_LOCKED_PRESERVED
    ));
    result.metadata.put("dependencyStatuses", List.of(
      DEPENDENCY_READY,
      DEPENDENCY_WAIT_PREDECESSOR,
      DEPENDENCY_BLOCKED_PREDECESSOR
    ));
    result.metadata.put("showDetailedReasons", true);
    result.metadata.put("filteredFrozenOrders", sortedOrders.stream().filter(o -> o.frozen).map(o -> o.orderNo).toList());
    List<String> lockedOrderList = new ArrayList<>(lockedOrderSet);
    lockedOrderList.sort(String::compareTo);
    result.metadata.put("lockedOrders", lockedOrderList);
    result.metadata.put("baselineVersionNo", baseVersion == null ? null : baseVersion.versionNo);
    result.metadata.put("preservedLockedTaskCount", preservedLockedTaskKeys.size());
    result.metadata.put("preservedLockedAllocationCount", preservedLockedAllocationCount);
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

  private static int applyBaselineLockedAllocations(
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrderSet,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, MvpDomain.Order> orderByNo,
    Set<String> openShiftIds,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> shiftMaterialUsed,
    List<MvpDomain.Allocation> allocations,
    Set<String> preservedLockedTaskKeys
  ) {
    if (baseVersion == null || baseVersion.allocations == null || baseVersion.allocations.isEmpty() || lockedOrderSet.isEmpty()) {
      return 0;
    }

    int preservedCount = 0;
    for (MvpDomain.Allocation source : baseVersion.allocations) {
      if (source == null || source.orderNo == null || !lockedOrderSet.contains(source.orderNo)) {
        continue;
      }
      MvpDomain.Order order = orderByNo.get(source.orderNo);
      if (order == null || order.frozen) {
        continue;
      }
      if (source.shiftId == null || !openShiftIds.contains(source.shiftId)) {
        continue;
      }
      MvpDomain.ScheduleTask task = tasks.get(source.taskKey);
      if (task == null || !Objects.equals(task.orderNo, source.orderNo)) {
        continue;
      }

      double remainingQty = round3(task.targetQty - task.producedQty);
      if (remainingQty <= EPS) {
        continue;
      }
      double baselineQty = round3(source.scheduledQty);
      if (baselineQty <= EPS) {
        continue;
      }
      double preservedQty = round3(Math.min(remainingQty, baselineQty));
      if (preservedQty <= EPS) {
        continue;
      }

      MvpDomain.ProcessConfig processConfig = processConfigMap.get(task.processCode);
      int groupsUsed = resolveGroupsUsed(source, processConfig, preservedQty);
      int workersUsed = resolveResourceUsed(
        source.workersUsed,
        source.scheduledQty,
        preservedQty,
        groupsUsed,
        processConfig == null ? 0 : processConfig.requiredWorkers
      );
      int machinesUsed = resolveResourceUsed(
        source.machinesUsed,
        source.scheduledQty,
        preservedQty,
        groupsUsed,
        processConfig == null ? 0 : processConfig.requiredMachines
      );

      String processUsageKey = source.shiftId + "#" + task.processCode;
      shiftWorkersUsed.put(processUsageKey, shiftWorkersUsed.getOrDefault(processUsageKey, 0) + workersUsed);
      shiftMachinesUsed.put(processUsageKey, shiftMachinesUsed.getOrDefault(processUsageKey, 0) + machinesUsed);
      String materialKey = source.shiftId + "#" + task.productCode + "#" + task.processCode;
      shiftMaterialUsed.put(materialKey, round3(shiftMaterialUsed.getOrDefault(materialKey, 0d) + preservedQty));

      task.producedQty = round3(task.producedQty + preservedQty);

      MvpDomain.Allocation preserved = new MvpDomain.Allocation();
      preserved.taskKey = task.taskKey;
      preserved.orderNo = task.orderNo;
      preserved.productCode = task.productCode;
      preserved.processCode = task.processCode;
      preserved.dependencyType = task.dependencyType;
      preserved.shiftId = source.shiftId;
      preserved.date = source.date;
      preserved.shiftCode = source.shiftCode;
      preserved.scheduledQty = preservedQty;
      preserved.workersUsed = workersUsed;
      preserved.machinesUsed = machinesUsed;
      preserved.groupsUsed = groupsUsed;
      allocations.add(preserved);

      preservedLockedTaskKeys.add(task.taskKey);
      preservedCount += 1;
    }
    return preservedCount;
  }

  private static int resolveGroupsUsed(
    MvpDomain.Allocation source,
    MvpDomain.ProcessConfig processConfig,
    double preservedQty
  ) {
    int groupsUsed = 0;
    if (source.groupsUsed > 0) {
      if (source.scheduledQty > EPS) {
        groupsUsed = (int) Math.ceil(source.groupsUsed * preservedQty / source.scheduledQty);
      } else {
        groupsUsed = source.groupsUsed;
      }
    }
    if (groupsUsed <= 0 && processConfig != null) {
      groupsUsed = (int) Math.ceil(preservedQty / Math.max(1d, processConfig.capacityPerShift));
    }
    return Math.max(1, groupsUsed);
  }

  private static int resolveResourceUsed(
    int baselineUsed,
    double baselineQty,
    double preservedQty,
    int groupsUsed,
    int requiredPerGroup
  ) {
    if (baselineUsed > 0 && baselineQty > EPS) {
      return Math.max(1, (int) Math.ceil((baselineUsed * preservedQty) / baselineQty));
    }
    if (baselineUsed > 0) {
      return baselineUsed;
    }
    if (requiredPerGroup <= 0) {
      return 0;
    }
    return groupsUsed * requiredPerGroup;
  }

  private static ReasonInfo lockedPreservedReason(
    MvpDomain.ScheduleTask task,
    boolean preservedFromBaseline,
    String baselineVersionNo
  ) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("order_no", task.orderNo);
    evidence.put("task_key", task.taskKey);
    evidence.put("process_code", task.processCode);
    evidence.put("baseline_version_no", baselineVersionNo == null ? "" : baselineVersionNo);
    evidence.put("baseline_resolved", baselineVersionNo != null && !baselineVersionNo.isBlank());
    evidence.put("task_preserved_from_baseline", preservedFromBaseline);
    String detail;
    if (baselineVersionNo == null || baselineVersionNo.isBlank()) {
      detail = "Locked order skipped because no baseline version was provided.";
    } else if (!preservedFromBaseline) {
      detail = "Locked order has no baseline allocation for this task; task remains unscheduled.";
    } else {
      detail = "Locked order preserved from baseline; remaining quantity is not replanned.";
    }
    return new ReasonInfo(
      REASON_LOCKED_PRESERVED,
      detail,
      "POLICY",
      "POLICY",
      "SKIPPED_LOCKED",
      evidence
    );
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
      out.put(row.date + "#" + row.shiftCode + "#" + row.processCode, Math.max(0, row.available));
    }
    return out;
  }

  private static Map<String, Integer> effectiveResourceIndex(
    List<MvpDomain.ResourceRow> capacityRows,
    List<MvpDomain.ResourceRow> occupiedRows
  ) {
    Map<String, Integer> capacity = resourceIndex(capacityRows);
    Map<String, Integer> occupied = resourceIndex(occupiedRows);
    Map<String, Integer> out = new HashMap<>();
    for (Map.Entry<String, Integer> entry : capacity.entrySet()) {
      int gross = Math.max(0, entry.getValue());
      int used = Math.max(0, occupied.getOrDefault(entry.getKey(), 0));
      out.put(entry.getKey(), Math.max(0, gross - used));
    }
    return out;
  }

  private static Map<String, Integer> maxResourceByProcess(Map<String, Integer> byShiftProcess) {
    Map<String, Integer> out = new HashMap<>();
    for (Map.Entry<String, Integer> entry : byShiftProcess.entrySet()) {
      String key = entry.getKey();
      if (key == null) {
        continue;
      }
      int idx = key.lastIndexOf('#');
      if (idx < 0 || idx + 1 >= key.length()) {
        continue;
      }
      String processCode = key.substring(idx + 1);
      out.merge(processCode, Math.max(0, entry.getValue()), Math::max);
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

  private static Map<String, Double> totalMaterialByProductProcess(List<MvpDomain.MaterialRow> rows) {
    Map<String, Double> out = new HashMap<>();
    for (MvpDomain.MaterialRow row : rows) {
      out.merge(row.productCode + "#" + row.processCode, row.availableQty, Double::sum);
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

  private static ReasonInfo dependencyBlockedReason(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift
  ) {
    MvpDomain.ScheduleTask predecessor = task.predecessorTaskKey == null ? null : tasks.get(task.predecessorTaskKey);
    double predecessorBeforeShift = task.predecessorTaskKey == null
      ? 0d
      : producedBeforeShift.getOrDefault(task.predecessorTaskKey, 0d);
    double predecessorProduced = predecessor == null ? 0d : predecessor.producedQty;
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("predecessor_task_key", task.predecessorTaskKey);
    evidence.put("dependency_type", task.dependencyType);
    evidence.put("predecessor_produced_before_shift", round3(predecessorBeforeShift));
    evidence.put("predecessor_produced_qty", round3(predecessorProduced));
    evidence.put("current_produced_qty", round3(task.producedQty));
    return new ReasonInfo(
      REASON_DEPENDENCY_BLOCKED,
      "Task is waiting for predecessor output release.",
      "ENGINE",
      "DEPENDENCY",
      dependencyStatusAtShift(task, tasks, producedBeforeShift),
      evidence
    );
  }

  private static ReasonInfo frozenReason(MvpDomain.ScheduleTask task) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("order_no", task.orderNo);
    evidence.put("task_key", task.taskKey);
    evidence.put("process_code", task.processCode);
    return new ReasonInfo(
      REASON_FROZEN_BY_POLICY,
      "Task skipped because the order is frozen by policy.",
      "POLICY",
      "POLICY",
      TASK_STATUS_SKIPPED_FROZEN,
      evidence
    );
  }

  private static ReasonInfo diagnoseUnscheduledReason(
    MvpDomain.ScheduleTask task,
    MvpDomain.Order order,
    Map<String, MvpDomain.ScheduleTask> allTasks,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, Integer> maxWorkersByProcess,
    Map<String, Integer> maxMachinesByProcess,
    Map<String, Double> totalMaterialByProductProcess
  ) {
    if (order != null && order.frozen) {
      return frozenReason(task);
    }

    String dependencyStatus = dependencyStatusAtVersionEnd(task, allTasks);
    if (!DEPENDENCY_READY.equals(dependencyStatus)) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("predecessor_task_key", task.predecessorTaskKey);
      MvpDomain.ScheduleTask predecessor = task.predecessorTaskKey == null ? null : allTasks.get(task.predecessorTaskKey);
      evidence.put("predecessor_produced_qty", round3(predecessor == null ? 0d : predecessor.producedQty));
      evidence.put("current_produced_qty", round3(task.producedQty));
      return new ReasonInfo(
        REASON_DEPENDENCY_BLOCKED,
        "Task is blocked by predecessor completion/release state.",
        "ENGINE",
        "DEPENDENCY",
        dependencyStatus,
        evidence
      );
    }

    MvpDomain.ProcessConfig processConfig = processConfigMap.get(task.processCode);
    if (processConfig == null) {
      return ReasonInfo.capacity(
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
      return ReasonInfo.capacity(
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
      return ReasonInfo.capacity(
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
    if (materialTotal <= EPS) {
      return ReasonInfo.capacity(
        REASON_MATERIAL_SHORTAGE,
        "No material is available in planning horizon.",
        "MATERIAL",
        task.processCode,
        0,
        0,
        materialTotal
      );
    }

    return ReasonInfo.capacity(
      REASON_CAPACITY_UNKNOWN,
      "Task is unscheduled after applying all constraints in horizon.",
      "UNKNOWN",
      task.processCode,
      workersCapacity,
      machinesCapacity,
      materialTotal
    );
  }

  private static String capacityReasonCode(int groupsByWorkers, int groupsByMachines) {
    if (groupsByWorkers < groupsByMachines) {
      return REASON_CAPACITY_MANPOWER;
    }
    if (groupsByMachines < groupsByWorkers) {
      return REASON_CAPACITY_MACHINE;
    }
    return REASON_CAPACITY_UNKNOWN;
  }

  private static List<String> reasonCodesForCompatibility(String reasonCode) {
    List<String> out = new ArrayList<>();
    out.add(reasonCode);
    String legacyReason = toLegacyReasonCode(reasonCode);
    if (!legacyReason.equals(reasonCode)) {
      out.add(legacyReason);
    }
    return out;
  }

  private static String toLegacyReasonCode(String reasonCode) {
    return switch (reasonCode) {
      case REASON_CAPACITY_MANPOWER, REASON_CAPACITY_MACHINE, REASON_MATERIAL_SHORTAGE, REASON_CAPACITY_UNKNOWN -> "CAPACITY_LIMIT";
      case REASON_DEPENDENCY_BLOCKED -> "DEPENDENCY_LIMIT";
      default -> reasonCode;
    };
  }

  private static String dependencyStatusAtVersionEnd(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks
  ) {
    if (task.predecessorTaskKey == null || task.predecessorTaskKey.isBlank()) {
      return DEPENDENCY_READY;
    }
    MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
    if (predecessor == null) {
      return DEPENDENCY_BLOCKED_PREDECESSOR;
    }
    double released = predecessor.producedQty - task.producedQty;
    if (released > EPS) {
      return DEPENDENCY_READY;
    }
    if (predecessor.producedQty + EPS < predecessor.targetQty || predecessor.producedQty <= EPS) {
      return DEPENDENCY_WAIT_PREDECESSOR;
    }
    return DEPENDENCY_BLOCKED_PREDECESSOR;
  }

  private static String dependencyStatusAtShift(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift
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
    if (released > EPS) {
      return DEPENDENCY_READY;
    }
    if (predecessorReferenceQty + EPS < predecessor.targetQty || predecessorReferenceQty <= EPS) {
      return DEPENDENCY_WAIT_PREDECESSOR;
    }
    return DEPENDENCY_BLOCKED_PREDECESSOR;
  }

  private static String resolveTaskStatus(
    MvpDomain.ScheduleTask task,
    MvpDomain.Order order,
    double remainingQty
  ) {
    if (order != null && order.frozen) {
      return TASK_STATUS_SKIPPED_FROZEN;
    }
    if (order != null && order.lockFlag && remainingQty > EPS) {
      return TASK_STATUS_PRESERVED_LOCKED;
    }
    if (remainingQty <= EPS) {
      return TASK_STATUS_READY;
    }
    if (task.producedQty > EPS) {
      return TASK_STATUS_PARTIALLY_ALLOCATED;
    }
    return TASK_STATUS_UNSCHEDULED;
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
    double scheduleCompletionRate = targetQty > EPS ? round3((producedQty / targetQty) * 100d) : 0d;
    Map<String, Integer> reasonDistribution = buildReasonDistribution(unscheduled);
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("taskCount", taskCount);
    metrics.put("task_count", taskCount);
    metrics.put("allocationCount", allocations.size());
    metrics.put("allocation_count", allocations.size());
    metrics.put("targetQty", round3(targetQty));
    metrics.put("target_qty", round3(targetQty));
    metrics.put("scheduledQty", round3(producedQty));
    metrics.put("scheduled_qty", round3(producedQty));
    metrics.put("scheduleCompletionRate", scheduleCompletionRate);
    metrics.put("schedule_completion_rate", scheduleCompletionRate);
    metrics.put("unscheduledTaskCount", unscheduled.size());
    metrics.put("unscheduled_task_count", unscheduled.size());
    metrics.put("unscheduledReasonDistribution", new LinkedHashMap<>(reasonDistribution));
    metrics.put("unscheduled_reason_distribution", new LinkedHashMap<>(reasonDistribution));
    return metrics;
  }

  private static Map<String, Integer> buildReasonDistribution(List<Map<String, Object>> unscheduled) {
    Map<String, Integer> reasonDistribution = new LinkedHashMap<>();
    for (Map<String, Object> row : unscheduled) {
      String reasonCode = String.valueOf(row.getOrDefault("reason_code", ""));
      if (reasonCode == null || reasonCode.isBlank()) {
        continue;
      }
      reasonDistribution.merge(reasonCode, 1, Integer::sum);
    }
    return reasonDistribution;
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

  private static final class ReasonInfo {
    final String reasonCode;
    final String reasonDetail;
    final String reasonSource;
    final String blockingDimension;
    final String dependencyStatus;
    final Map<String, Object> evidence;

    ReasonInfo(
      String reasonCode,
      String reasonDetail,
      String reasonSource,
      String blockingDimension,
      String dependencyStatus,
      Map<String, Object> evidence
    ) {
      this.reasonCode = reasonCode;
      this.reasonDetail = reasonDetail;
      this.reasonSource = reasonSource;
      this.blockingDimension = blockingDimension;
      this.dependencyStatus = dependencyStatus;
      this.evidence = evidence;
    }

    static ReasonInfo capacity(
      String reasonCode,
      String reasonDetail,
      String blockingDimension,
      String processCode,
      int availableValue,
      int requiredValue,
      double materialValue
    ) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("process_code", processCode);
      evidence.put("available", availableValue);
      evidence.put("required", requiredValue);
      evidence.put("material_available", round3(materialValue));
      return new ReasonInfo(reasonCode, reasonDetail, "ENGINE", blockingDimension, DEPENDENCY_READY, evidence);
    }
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}

