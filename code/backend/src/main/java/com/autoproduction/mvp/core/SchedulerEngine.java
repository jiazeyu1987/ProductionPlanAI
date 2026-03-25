package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
  private static final String REASON_COMPONENT_SHORTAGE = "COMPONENT_SHORTAGE";
  private static final String REASON_DEPENDENCY_BLOCKED = "DEPENDENCY_BLOCKED";
  private static final String REASON_TRANSFER_CONSTRAINT = "TRANSFER_CONSTRAINT";
  private static final String REASON_FROZEN_BY_POLICY = "FROZEN_BY_POLICY";
  private static final String REASON_LOCKED_PRESERVED = "LOCKED_PRESERVED";
  private static final String REASON_URGENT_GUARANTEE = "URGENT_GUARANTEE";
  private static final String REASON_LOCK_PREEMPTED = "LOCK_PREEMPTED_BY_URGENT";
  private static final String REASON_BEFORE_EXPECTED_START = "BEFORE_EXPECTED_START";
  private static final String DEPENDENCY_READY = "READY";
  private static final String DEPENDENCY_WAIT_PREDECESSOR = "WAIT_PREDECESSOR";
  private static final String DEPENDENCY_BLOCKED_PREDECESSOR = "BLOCKED_BY_PREDECESSOR";
  private static final String TASK_STATUS_READY = "READY";
  private static final String TASK_STATUS_PARTIALLY_ALLOCATED = "PARTIALLY_ALLOCATED";
  private static final String TASK_STATUS_UNSCHEDULED = "UNSCHEDULED";
  private static final String TASK_STATUS_PRESERVED_LOCKED = "PRESERVED_LOCKED";
  private static final String TASK_STATUS_SKIPPED_FROZEN = "SKIPPED_FROZEN";
  private static final double URGENT_MIN_SHARE_PER_PROCESS = 0.35d;
  private static final double LOCKED_MAX_SHARE_WHEN_URGENT = 0.65d;
  private static final double URGENT_MIN_DAILY_OUTPUT_RATIO = 0.12d;
  private static final double URGENT_MIN_DAILY_OUTPUT_ABS = 60d;
  private static final int MAX_ROUNDS_PER_SHIFT = 3;
  private static final double ALLOCATION_CHUNK_RATIO = 0.45d;
  private static final double CHANGEOVER_CAPACITY_FACTOR = 0.85d;
  private static final double PRODUCT_MIX_CAPACITY_FACTOR = 0.92d;
  private static final double NIGHT_SHIFT_CAPACITY_FACTOR = 0.90d;
  private static final int DEFAULT_TRANSFER_BATCH = 60;
  private static final int STERILE_TRANSFER_BATCH = 120;
  private static final int DEFAULT_MIN_LOT = 30;
  private static final int STERILE_MIN_LOT = 80;
  private static final int STERILE_RELEASE_LAG_SHIFTS = 1;
  private static final double SCORE_URGENT = 800d;
  private static final double SCORE_OVERDUE_PER_DAY = 120d;
  private static final double SCORE_DUE_ATTENUATION = 200d;
  private static final double SCORE_PROGRESS = 200d;
  private static final double SCORE_WIP = -100d;
  private static final double SCORE_CHANGEOVER = -80d;
  private static final double SCORE_URGENT_GAP = 260d;
  private static final String COMPONENT_RAW_PREFIX = "RAW_";
  private static final String STRATEGY_KEY_ORDER_FIRST = "KEY_ORDER_FIRST";
  private static final String STRATEGY_MAX_CAPACITY_FIRST = "MAX_CAPACITY_FIRST";
  private static final String STRATEGY_MIN_DELAY_FIRST = "MIN_DELAY_FIRST";

  private SchedulerEngine() {}

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo
  ) {
    return generate(state, orders, requestId, versionNo, null, Set.of(), STRATEGY_KEY_ORDER_FIRST);
  }

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo,
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrders
  ) {
    return generate(state, orders, requestId, versionNo, baseVersion, lockedOrders, STRATEGY_KEY_ORDER_FIRST);
  }

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo,
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrders,
    String strategyCode
  ) {
    validateInput(state);
    Set<String> lockedOrderSet = lockedOrders == null ? Set.of() : new HashSet<>(lockedOrders);
    String normalizedStrategyCode = normalizeStrategyCode(strategyCode);
    StrategyProfile strategyProfile = resolveStrategyProfile(normalizedStrategyCode);

    List<Map<String, Object>> shifts = buildShifts(state);
    Map<String, Integer> shiftIndexByShiftId = shiftIndexById(shifts);
    Map<String, Integer> workerByShiftProcess = effectiveResourceIndex(state.workerPools, state.initialWorkerOccupancy);
    Map<String, Integer> machineByShiftProcess = effectiveResourceIndex(state.machinePools, state.initialMachineOccupancy);
    Map<String, Double> shiftMaterialArrivalByProductProcess = materialIndex(state.materialAvailability);
    Map<String, Double> cumulativeMaterialByShiftProductProcess = cumulativeMaterialIndex(state.materialAvailability, shifts);
    Map<String, Double> cumulativeComponentByShift = cumulativeComponentIndex(state, shifts);
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
    Map<String, Double> materialConsumedByProductProcess = new HashMap<>();
    Map<String, Double> componentConsumed = new HashMap<>();
    Map<String, Map<Integer, Double>> producedByTaskShift = new HashMap<>();
    Map<String, String> lastProductByShiftProcess = new HashMap<>();
    Map<String, Set<String>> productMixByShiftProcess = new HashMap<>();
    Set<String> finalTaskKeys = new HashSet<>();
    Map<String, Double> remainingQtyByOrder = new HashMap<>();
    Map<String, ReasonInfo> lastBlockedByTask = new HashMap<>();
    List<MvpDomain.Allocation> allocations = new ArrayList<>();
    Set<String> lockPreemptedTaskKeys = new HashSet<>();

    for (MvpDomain.Order order : sortedOrders) {
      orderByNo.put(order.orderNo, order);
      List<MvpDomain.OrderItem> items = order.items == null ? List.of() : order.items;
      double orderTarget = 0d;
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
    Map<String, Double> urgentDemandByProcess = buildUrgentDemandByProcess(tasksByOrder, orderByNo, lockedOrderSet);

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
      workerByShiftProcess,
      machineByShiftProcess,
      cumulativeMaterialByShiftProductProcess,
      cumulativeComponentByShift,
      shiftIndexByShiftId,
      urgentDemandByProcess,
      shiftWorkersUsed,
      shiftMachinesUsed,
      shiftMaterialUsed,
      materialConsumedByProductProcess,
      componentConsumed,
      allocations,
      preservedLockedTaskKeys,
      lockPreemptedTaskKeys,
      producedByTaskShift,
      finalTaskKeys,
      remainingQtyByOrder,
      lastProductByShiftProcess,
      productMixByShiftProcess
    );

    Map<String, Double> urgentDailyProducedByOrderDate = new HashMap<>();
    for (Map<String, Object> shift : shifts) {
      String shiftId = String.valueOf(shift.get("shiftId"));
      String shiftDate = String.valueOf(shift.get("date"));
      String shiftCode = String.valueOf(shift.get("shiftCode"));
      int shiftIndex = shiftIndexByShiftId.getOrDefault(shiftId, 0);
      LocalDate shiftDateValue = parseDate(shiftDate);
      Map<String, Double> producedBeforeShift = snapshotProduced(tasks);

      Map<String, Double> urgentQuotaByProcess = buildUrgentQuotaForShift(
        shiftId,
        shiftCode,
        orderByNo,
        schedulableOrders,
        tasksByOrder,
        processConfigMap,
        workerByShiftProcess,
        machineByShiftProcess,
        shiftWorkersUsed,
        shiftMachinesUsed
      );
      scheduleShiftRounds(
        true,
        shiftId,
        shiftDate,
        shiftCode,
        shiftIndex,
        shiftDateValue,
        schedulableOrders,
        tasksByOrder,
        tasks,
        processConfigMap,
        workerByShiftProcess,
        machineByShiftProcess,
        shiftWorkersUsed,
        shiftMachinesUsed,
        shiftMaterialArrivalByProductProcess,
        cumulativeMaterialByShiftProductProcess,
        shiftMaterialUsed,
        materialConsumedByProductProcess,
        cumulativeComponentByShift,
        componentConsumed,
        producedByTaskShift,
        finalTaskKeys,
        producedBeforeShift,
        remainingQtyByOrder,
        urgentDailyProducedByOrderDate,
        urgentQuotaByProcess,
        normalizedStrategyCode,
        strategyProfile,
        lastProductByShiftProcess,
        productMixByShiftProcess,
        lastBlockedByTask,
        allocations
      );
      scheduleShiftRounds(
        false,
        shiftId,
        shiftDate,
        shiftCode,
        shiftIndex,
        shiftDateValue,
        schedulableOrders,
        tasksByOrder,
        tasks,
        processConfigMap,
        workerByShiftProcess,
        machineByShiftProcess,
        shiftWorkersUsed,
        shiftMachinesUsed,
        shiftMaterialArrivalByProductProcess,
        cumulativeMaterialByShiftProductProcess,
        shiftMaterialUsed,
        materialConsumedByProductProcess,
        cumulativeComponentByShift,
        componentConsumed,
        producedByTaskShift,
        finalTaskKeys,
        producedBeforeShift,
        remainingQtyByOrder,
        urgentDailyProducedByOrderDate,
        Collections.emptyMap(),
        normalizedStrategyCode,
        strategyProfile,
        lastProductByShiftProcess,
        productMixByShiftProcess,
        lastBlockedByTask,
        allocations
      );
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
        reasonInfo = frozenReason(task, task.dependencyStatus);
      } else if (order != null && lockedOrderSet.contains(order.orderNo)) {
        reasonInfo = lockedPreservedReason(
          task,
          preservedLockedTaskKeys.contains(task.taskKey),
          baseVersion == null ? null : baseVersion.versionNo,
          task.dependencyStatus,
          lockPreemptedTaskKeys.contains(task.taskKey)
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
    result.metadata.put("hardConstraints", List.of("MAN", "MACHINE", "MATERIAL", "BOM_COMPONENT"));
    result.metadata.put("dependencyTypes", List.of("FS", "SS"));
    result.metadata.put("reasonCodes", List.of(
      REASON_CAPACITY_MANPOWER,
      REASON_CAPACITY_MACHINE,
      REASON_CAPACITY_UNKNOWN,
      REASON_MATERIAL_SHORTAGE,
      REASON_COMPONENT_SHORTAGE,
      REASON_DEPENDENCY_BLOCKED,
      REASON_TRANSFER_CONSTRAINT,
      REASON_FROZEN_BY_POLICY,
      REASON_LOCKED_PRESERVED,
      REASON_URGENT_GUARANTEE,
      REASON_LOCK_PREEMPTED,
      REASON_BEFORE_EXPECTED_START
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
    result.metadata.put("lockPreemptedTaskCount", lockPreemptedTaskKeys.size());
    result.metadata.put("objectiveWeights", Map.of(
      "urgent", SCORE_URGENT,
      "overduePerDay", SCORE_OVERDUE_PER_DAY,
      "dueAttenuation", SCORE_DUE_ATTENUATION,
      "progress", SCORE_PROGRESS,
      "wip", SCORE_WIP,
      "changeover", SCORE_CHANGEOVER,
      "urgentGap", SCORE_URGENT_GAP
    ));
    result.metadata.put("scheduleStrategyCode", normalizedStrategyCode);
    result.metadata.put("scheduleStrategyNameCn", strategyNameCn(normalizedStrategyCode));
    result.metadata.put("strategyScoreFactors", Map.of(
      "urgentWeight", strategyProfile.urgentWeight,
      "dueWeight", strategyProfile.dueWeight,
      "progressWeight", strategyProfile.progressWeight,
      "wipWeight", strategyProfile.wipWeight,
      "changeoverWeight", strategyProfile.changeoverWeight,
      "urgentGapWeight", strategyProfile.urgentGapWeight,
      "quotaWeight", strategyProfile.quotaWeight,
      "feasibleWeight", strategyProfile.feasibleWeight,
      "capacityWeight", strategyProfile.capacityWeight
    ));
    result.metadata.put("urgentPolicy", Map.of(
      "minSharePerProcess", URGENT_MIN_SHARE_PER_PROCESS,
      "minDailyOutputRatio", URGENT_MIN_DAILY_OUTPUT_RATIO,
      "minDailyOutputAbs", URGENT_MIN_DAILY_OUTPUT_ABS
    ));
    result.metadata.put("lockPolicy", Map.of(
      "lockedMaxShareWhenUrgent", LOCKED_MAX_SHARE_WHEN_URGENT,
      "overrideEnabled", true
    ));
    result.metadata.put("transferPolicy", Map.of(
      "defaultTransferBatch", DEFAULT_TRANSFER_BATCH,
      "sterileTransferBatch", STERILE_TRANSFER_BATCH,
      "defaultMinLot", DEFAULT_MIN_LOT,
      "sterileMinLot", STERILE_MIN_LOT,
      "sterileReleaseLagShifts", STERILE_RELEASE_LAG_SHIFTS
    ));
    return result;
  }

  static Map<String, Object> validate(MvpDomain.State state, MvpDomain.ScheduleVersion schedule) {
    List<Map<String, Object>> violations = new ArrayList<>();
    validateDependencies(schedule, violations);
    validateFrozenOrders(state.orders, schedule, violations);
    validateResourceAndMaterialUsage(state, schedule, violations);
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
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Double> cumulativeMaterialByShiftProductProcess,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Integer> shiftIndexByShiftId,
    Map<String, Double> urgentDemandByProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> shiftMaterialUsed,
    Map<String, Double> materialConsumedByProductProcess,
    Map<String, Double> componentConsumed,
    List<MvpDomain.Allocation> allocations,
    Set<String> preservedLockedTaskKeys,
    Set<String> lockPreemptedTaskKeys,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    Set<String> finalTaskKeys,
    Map<String, Double> remainingQtyByOrder,
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess
  ) {
    if (baseVersion == null || baseVersion.allocations == null || baseVersion.allocations.isEmpty() || lockedOrderSet.isEmpty()) {
      return 0;
    }

    Map<String, Double> lockedQtyByShiftProcess = new HashMap<>();
    List<MvpDomain.Allocation> sortedBaseline = new ArrayList<>();
    for (MvpDomain.Allocation source : baseVersion.allocations) {
      if (source == null) {
        continue;
      }
      sortedBaseline.add(source);
    }
    sortedBaseline.sort(Comparator
      .comparingInt((MvpDomain.Allocation allocation) -> shiftIndexByShiftId.getOrDefault(allocation.shiftId, Integer.MAX_VALUE))
      .thenComparingInt(allocation -> {
        MvpDomain.ScheduleTask task = tasks.get(allocation.taskKey);
        return task == null ? Integer.MAX_VALUE : task.stepIndex;
      })
      .thenComparing(allocation -> allocation.taskKey == null ? "" : allocation.taskKey));

    int preservedCount = 0;
    for (MvpDomain.Allocation source : sortedBaseline) {
      if (source.orderNo == null || !lockedOrderSet.contains(source.orderNo)) {
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
      int shiftIndex = shiftIndexByShiftId.getOrDefault(source.shiftId, -1);
      if (shiftIndex < 0) {
        lockPreemptedTaskKeys.add(task.taskKey);
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
      Map<String, Double> producedBeforeShift = snapshotProduced(tasks);
      double allowance = calcAllowance(task, tasks, producedBeforeShift, producedByTaskShift, shiftIndex);
      if (allowance <= EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }
      double preservedQty = round3(Math.min(Math.min(remainingQty, baselineQty), allowance));
      if (preservedQty <= EPS) {
        continue;
      }

      MvpDomain.ProcessConfig processConfig = processConfigMap.get(task.processCode);
      if (processConfig == null) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }
      String shiftCode = source.shiftCode;
      if (shiftCode == null || shiftCode.isBlank()) {
        int split = source.shiftId == null ? -1 : source.shiftId.indexOf('#');
        shiftCode = split >= 0 && split + 1 < source.shiftId.length() ? source.shiftId.substring(split + 1) : "D";
      }

      String processUsageKey = source.shiftId + "#" + task.processCode;
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
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }
      double groupCapacity = Math.max(
        EPS,
        processConfig.capacityPerShift * shiftCapacityFactor(shiftCode) * processCapacityFactor(task.processCode)
      );
      double capacityByResources = maxGroups * groupCapacity;
      double urgentLockedCap = capacityByResources;
      if (urgentDemandByProcess.getOrDefault(task.processCode, 0d) > EPS) {
        double maxLockedShare = capacityByResources * LOCKED_MAX_SHARE_WHEN_URGENT;
        double usedLockedShare = lockedQtyByShiftProcess.getOrDefault(processUsageKey, 0d);
        urgentLockedCap = Math.max(0d, maxLockedShare - usedLockedShare);
      }

      String materialKey = source.shiftId + "#" + task.productCode + "#" + task.processCode;
      String materialConsumedKey = task.productCode + "#" + task.processCode;
      double cumulativeMaterialAvailable = cumulativeMaterialByShiftProductProcess.getOrDefault(materialKey, 0d);
      double materialConsumed = materialConsumedByProductProcess.getOrDefault(materialConsumedKey, 0d);
      double materialRemaining = Math.max(0d, cumulativeMaterialAvailable - materialConsumed);
      String componentKey = componentKeyForTask(task, order, true);
      double componentRemaining = componentRemainingForShift(source.shiftId, componentKey, cumulativeComponentByShift, componentConsumed);

      double feasibleQty = Math.min(
        preservedQty,
        Math.min(capacityByResources, Math.min(materialRemaining, Math.min(componentRemaining, urgentLockedCap)))
      );
      if (feasibleQty <= EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }

      int groupsUsed = (int) Math.ceil(feasibleQty / groupCapacity);
      groupsUsed = Math.max(1, Math.min(maxGroups, groupsUsed));
      int workersToUse = groupsUsed * processConfig.requiredWorkers;
      int machinesToUse = groupsUsed * processConfig.requiredMachines;
      feasibleQty = Math.min(feasibleQty, groupsUsed * groupCapacity);
      feasibleQty = round3(feasibleQty);
      if (feasibleQty <= EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }

      shiftWorkersUsed.put(processUsageKey, workersUsed + workersToUse);
      shiftMachinesUsed.put(processUsageKey, machinesUsed + machinesToUse);
      shiftMaterialUsed.put(materialKey, round3(shiftMaterialUsed.getOrDefault(materialKey, 0d) + feasibleQty));
      materialConsumedByProductProcess.put(
        materialConsumedKey,
        round3(materialConsumedByProductProcess.getOrDefault(materialConsumedKey, 0d) + feasibleQty)
      );
      consumeComponent(componentConsumed, componentKey, feasibleQty);
      lockedQtyByShiftProcess.put(processUsageKey, round3(lockedQtyByShiftProcess.getOrDefault(processUsageKey, 0d) + feasibleQty));

      task.producedQty = round3(task.producedQty + feasibleQty);
      decrementRemainingQtyByOrder(remainingQtyByOrder, task.orderNo, feasibleQty, finalTaskKeys.contains(task.taskKey));
      if (shiftIndex >= 0) {
        addProducedByTaskShift(producedByTaskShift, task.taskKey, shiftIndex, feasibleQty);
      }
      registerProcessProductUsage(lastProductByShiftProcess, productMixByShiftProcess, processUsageKey, task.productCode);

      MvpDomain.Allocation preserved = new MvpDomain.Allocation();
      preserved.taskKey = task.taskKey;
      preserved.orderNo = task.orderNo;
      preserved.productCode = task.productCode;
      preserved.processCode = task.processCode;
      preserved.dependencyType = task.dependencyType;
      preserved.shiftId = source.shiftId;
      preserved.date = source.date;
      preserved.shiftCode = source.shiftCode;
      preserved.scheduledQty = feasibleQty;
      preserved.workersUsed = workersToUse;
      preserved.machinesUsed = machinesToUse;
      preserved.groupsUsed = groupsUsed;
      allocations.add(preserved);

      preservedLockedTaskKeys.add(task.taskKey);
      if (source.scheduledQty - feasibleQty > EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
      }
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
    return new ReasonInfo(
      reasonCode,
      detail,
      "POLICY",
      "POLICY",
      dependencyStatus,
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

  private static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return LocalDate.of(1970, 1, 1);
    }
    try {
      return LocalDate.parse(value);
    } catch (Exception ignored) {
      return LocalDate.of(1970, 1, 1);
    }
  }

  private static Map<String, Integer> shiftIndexById(List<Map<String, Object>> shifts) {
    Map<String, Integer> out = new HashMap<>();
    for (int i = 0; i < shifts.size(); i += 1) {
      String shiftId = String.valueOf(shifts.get(i).get("shiftId"));
      if (shiftId != null && !shiftId.isBlank()) {
        out.put(shiftId, i);
      }
    }
    return out;
  }

  private static Map<String, Double> cumulativeMaterialIndex(
    List<MvpDomain.MaterialRow> rows,
    List<Map<String, Object>> shifts
  ) {
    Map<String, List<MvpDomain.MaterialRow>> rowsByShift = new HashMap<>();
    for (MvpDomain.MaterialRow row : rows) {
      String shiftId = row.date + "#" + row.shiftCode;
      rowsByShift.computeIfAbsent(shiftId, ignored -> new ArrayList<>()).add(row);
    }
    Map<String, Double> cumulativeByProductProcess = new HashMap<>();
    Map<String, Double> out = new HashMap<>();
    for (Map<String, Object> shift : shifts) {
      String shiftId = String.valueOf(shift.get("shiftId"));
      List<MvpDomain.MaterialRow> rowsInShift = rowsByShift.getOrDefault(shiftId, List.of());
      for (MvpDomain.MaterialRow row : rowsInShift) {
        String materialKey = row.productCode + "#" + row.processCode;
        cumulativeByProductProcess.merge(materialKey, row.availableQty, Double::sum);
      }
      for (Map.Entry<String, Double> entry : cumulativeByProductProcess.entrySet()) {
        out.put(shiftId + "#" + entry.getKey(), round3(entry.getValue()));
      }
    }
    return out;
  }

  private static Map<String, Double> cumulativeComponentIndex(
    MvpDomain.State state,
    List<Map<String, Object>> shifts
  ) {
    Map<String, Double> initialByComponent = new HashMap<>();
    Map<String, Map<LocalDate, Double>> arrivalsByComponentDate = new HashMap<>();
    for (MvpDomain.Order order : state.orders) {
      if (order == null) {
        continue;
      }
      String componentKey = componentKeyFromOrder(order);
      if (componentKey == null) {
        continue;
      }
      MvpDomain.OrderBusinessData businessData = order.businessData;
      double initial = 0d;
      double inbound = 0d;
      if (businessData != null) {
        initial = Math.max(0d, businessData.semiFinishedInventory + businessData.semiFinishedWip);
        inbound = Math.max(0d, businessData.pendingInboundQty);
      }
      initialByComponent.merge(componentKey, initial, Math::max);
      if (inbound <= EPS) {
        continue;
      }
      LocalDate arrivalDate = componentArrivalDate(order, state.startDate);
      if (arrivalDate == null) {
        initialByComponent.merge(componentKey, initial + inbound, Math::max);
        continue;
      }
      arrivalsByComponentDate
        .computeIfAbsent(componentKey, ignored -> new HashMap<>())
        .merge(arrivalDate, inbound, Math::max);
    }

    Set<String> components = new HashSet<>();
    components.addAll(initialByComponent.keySet());
    components.addAll(arrivalsByComponentDate.keySet());
    Map<String, Double> out = new HashMap<>();
    for (String component : components) {
      Map<LocalDate, Double> arrivalsByDate = arrivalsByComponentDate.getOrDefault(component, Map.of());
      double initial = initialByComponent.getOrDefault(component, 0d);
      for (Map<String, Object> shift : shifts) {
        String shiftId = String.valueOf(shift.get("shiftId"));
        LocalDate shiftDate = parseDate(String.valueOf(shift.get("date")));
        double arrived = 0d;
        for (Map.Entry<LocalDate, Double> arrival : arrivalsByDate.entrySet()) {
          if (!arrival.getKey().isAfter(shiftDate)) {
            arrived += arrival.getValue();
          }
        }
        double cumulative = round3(initial + arrived);
        out.put(shiftId + "#" + component, round3(cumulative));
      }
    }
    return out;
  }

  private static String componentKeyFromOrder(MvpDomain.Order order) {
    if (order == null) {
      return null;
    }
    String baseCode = null;
    if (order.businessData != null && order.businessData.semiFinishedCode != null && !order.businessData.semiFinishedCode.isBlank()) {
      baseCode = order.businessData.semiFinishedCode;
    } else if (order.items != null && !order.items.isEmpty() && order.items.get(0).productCode != null) {
      baseCode = order.items.get(0).productCode;
    }
    if (baseCode == null || baseCode.isBlank()) {
      return null;
    }
    return COMPONENT_RAW_PREFIX + baseCode.trim().toUpperCase();
  }

  private static LocalDate componentArrivalDate(MvpDomain.Order order, LocalDate defaultDate) {
    if (order == null || order.businessData == null) {
      return defaultDate;
    }
    MvpDomain.OrderBusinessData businessData = order.businessData;
    LocalDate parsed = parseFlexibleDate(businessData.purchaseDueDate, defaultDate);
    if (parsed != null) {
      return parsed;
    }
    parsed = parseFlexibleDate(businessData.injectionDueDate, defaultDate);
    if (parsed != null) {
      return parsed;
    }
    parsed = parseFlexibleDate(businessData.plannedFinishDate1, defaultDate);
    if (parsed != null) {
      return parsed;
    }
    parsed = parseFlexibleDate(businessData.plannedFinishDate2, defaultDate);
    if (parsed != null) {
      return parsed;
    }
    return order.dueDate == null ? defaultDate : order.dueDate;
  }

  private static LocalDate expectedStartDateForOrder(MvpDomain.Order order) {
    if (order == null) {
      return null;
    }
    if (order.expectedStartDate != null) {
      return order.expectedStartDate;
    }
    if (order.businessData != null && order.businessData.orderDate != null) {
      return order.businessData.orderDate;
    }
    return null;
  }

  private static LocalDate parseFlexibleDate(String raw, LocalDate referenceDate) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String text = raw.trim();
    try {
      return LocalDate.parse(text);
    } catch (Exception ignore) {
      // fallback below
    }
    String normalized = text.replace('/', '.').replace('-', '.');
    String[] parts = normalized.split("\\.");
    if (parts.length == 2) {
      try {
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        int year = referenceDate == null ? LocalDate.now().getYear() : referenceDate.getYear();
        return LocalDate.of(year, month, day);
      } catch (Exception ignore) {
        return null;
      }
    }
    return null;
  }

  private static String componentKeyForTask(
    MvpDomain.ScheduleTask task,
    MvpDomain.Order order,
    boolean strictFirstStepOnly
  ) {
    if (task == null) {
      return null;
    }
    if (strictFirstStepOnly && task.stepIndex != 0) {
      return null;
    }
    if (!strictFirstStepOnly && task.stepIndex != 0) {
      return null;
    }
    String baseCode = null;
    if (order != null && order.businessData != null && order.businessData.semiFinishedCode != null && !order.businessData.semiFinishedCode.isBlank()) {
      baseCode = order.businessData.semiFinishedCode;
    } else if (task.productCode != null && !task.productCode.isBlank()) {
      baseCode = task.productCode;
    }
    if (baseCode == null || baseCode.isBlank()) {
      return null;
    }
    return COMPONENT_RAW_PREFIX + baseCode.trim().toUpperCase();
  }

  private static double componentRemainingForShift(
    String shiftId,
    String componentKey,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Double> componentConsumed
  ) {
    if (componentKey == null || componentKey.isBlank()) {
      return Double.POSITIVE_INFINITY;
    }
    double available = cumulativeComponentByShift.getOrDefault(shiftId + "#" + componentKey, 0d);
    double used = componentConsumed.getOrDefault(componentKey, 0d);
    return Math.max(0d, round3(available - used));
  }

  private static void consumeComponent(Map<String, Double> componentConsumed, String componentKey, double qty) {
    if (componentKey == null || componentKey.isBlank() || qty <= EPS) {
      return;
    }
    componentConsumed.put(componentKey, round3(componentConsumed.getOrDefault(componentKey, 0d) + qty));
  }

  private static double componentTotalForOrder(MvpDomain.Order order) {
    if (order == null || order.businessData == null) {
      return Double.POSITIVE_INFINITY;
    }
    MvpDomain.OrderBusinessData businessData = order.businessData;
    double total = Math.max(0d, businessData.semiFinishedInventory + businessData.semiFinishedWip + businessData.pendingInboundQty);
    return round3(total);
  }

  private static Map<String, Double> buildUrgentDemandByProcess(
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
        out.merge(task.processCode, remaining, Double::sum);
      }
    }
    return out;
  }

  private static Map<String, Double> buildUrgentQuotaForShift(
    String shiftId,
    String shiftCode,
    Map<String, MvpDomain.Order> orderByNo,
    List<MvpDomain.Order> schedulableOrders,
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed
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
        urgentDemandByProcess.merge(task.processCode, remaining, Double::sum);
      }
    }

    Map<String, Double> out = new HashMap<>();
    for (Map.Entry<String, Double> entry : urgentDemandByProcess.entrySet()) {
      String processCode = entry.getKey();
      double demand = entry.getValue();
      MvpDomain.ProcessConfig processConfig = processConfigMap.get(processCode);
      if (processConfig == null || demand <= EPS) {
        continue;
      }
      String processUsageKey = shiftId + "#" + processCode;
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
      double baseCapacity = maxGroups
        * processConfig.capacityPerShift
        * shiftCapacityFactor(shiftCode)
        * processCapacityFactor(processCode);
      double quota = Math.min(demand, baseCapacity * URGENT_MIN_SHARE_PER_PROCESS);
      if (quota > EPS) {
        out.put(processCode, round3(quota));
      }
    }
    return out;
  }

  private static void decrementRemainingQtyByOrder(
    Map<String, Double> remainingQtyByOrder,
    String orderNo,
    double qty,
    boolean deduct
  ) {
    if (!deduct || orderNo == null || qty <= EPS) {
      return;
    }
    double next = round3(Math.max(0d, remainingQtyByOrder.getOrDefault(orderNo, 0d) - qty));
    remainingQtyByOrder.put(orderNo, next);
  }

  private static void addProducedByTaskShift(
    Map<String, Map<Integer, Double>> producedByTaskShift,
    String taskKey,
    int shiftIndex,
    double qty
  ) {
    if (taskKey == null || shiftIndex < 0 || qty <= EPS) {
      return;
    }
    producedByTaskShift
      .computeIfAbsent(taskKey, ignored -> new HashMap<>())
      .merge(shiftIndex, round3(qty), Double::sum);
  }

  private static void registerProcessProductUsage(
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess,
    String processUsageKey,
    String productCode
  ) {
    if (processUsageKey == null || processUsageKey.isBlank() || productCode == null || productCode.isBlank()) {
      return;
    }
    lastProductByShiftProcess.put(processUsageKey, productCode);
    productMixByShiftProcess.computeIfAbsent(processUsageKey, ignored -> new HashSet<>()).add(productCode);
  }

  private static double shiftCapacityFactor(String shiftCode) {
    return "N".equalsIgnoreCase(shiftCode) ? NIGHT_SHIFT_CAPACITY_FACTOR : 1d;
  }

  private static double processCapacityFactor(String processCode) {
    if (processCode == null) {
      return 1d;
    }
    if (processCode.toUpperCase().contains("STERILE")) {
      return 0.86d;
    }
    if (processCode.toUpperCase().contains("ASSEMBLY")) {
      return 0.94d;
    }
    return 1d;
  }

  private static void scheduleShiftRounds(
    boolean urgentOnly,
    String shiftId,
    String shiftDate,
    String shiftCode,
    int shiftIndex,
    LocalDate shiftDateValue,
    List<MvpDomain.Order> schedulableOrders,
    Map<String, List<MvpDomain.ScheduleTask>> tasksByOrder,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> shiftMaterialArrivalByProductProcess,
    Map<String, Double> cumulativeMaterialByShiftProductProcess,
    Map<String, Double> shiftMaterialUsed,
    Map<String, Double> materialConsumedByProductProcess,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Double> componentConsumed,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    Set<String> finalTaskKeys,
    Map<String, Double> producedBeforeShift,
    Map<String, Double> remainingQtyByOrder,
    Map<String, Double> urgentDailyProducedByOrderDate,
    Map<String, Double> processQuotaRemaining,
    String strategyCode,
    StrategyProfile strategyProfile,
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess,
    Map<String, ReasonInfo> lastBlockedByTask,
    List<MvpDomain.Allocation> allocations
  ) {
    Map<String, Double> quotaByProcess = new HashMap<>(processQuotaRemaining);
    for (int round = 0; round < MAX_ROUNDS_PER_SHIFT; round += 1) {
      List<CandidateEvaluation> candidates = new ArrayList<>();
      for (MvpDomain.Order order : schedulableOrders) {
        if (order == null || order.frozen) {
          continue;
        }
        if (urgentOnly && !order.urgent) {
          continue;
        }
        List<MvpDomain.ScheduleTask> orderTasks = tasksByOrder.getOrDefault(order.orderNo, List.of());
        for (MvpDomain.ScheduleTask task : orderTasks) {
          double remainingQty = round3(task.targetQty - task.producedQty);
          if (remainingQty <= EPS) {
            lastBlockedByTask.remove(task.taskKey);
            continue;
          }
          if (urgentOnly && quotaByProcess.getOrDefault(task.processCode, 0d) <= EPS) {
            continue;
          }
          CandidateEvaluation evaluation = evaluateCandidateForShift(
            order,
            task,
            shiftId,
            shiftCode,
            shiftIndex,
            shiftDateValue,
            tasks,
            processConfigMap,
            workerByShiftProcess,
            machineByShiftProcess,
            shiftWorkersUsed,
            shiftMachinesUsed,
            shiftMaterialArrivalByProductProcess,
            cumulativeMaterialByShiftProductProcess,
            materialConsumedByProductProcess,
            cumulativeComponentByShift,
            componentConsumed,
            producedByTaskShift,
            finalTaskKeys,
            producedBeforeShift,
            lastProductByShiftProcess,
            productMixByShiftProcess
          );
          if (evaluation.feasibleQty <= EPS) {
            if (evaluation.blockedReason != null) {
              lastBlockedByTask.put(task.taskKey, evaluation.blockedReason);
            }
            continue;
          }
          evaluation.score = taskPriorityScore(
            evaluation,
            shiftDateValue,
            remainingQtyByOrder,
            urgentDailyProducedByOrderDate,
            quotaByProcess,
            strategyCode,
            strategyProfile
          );
          candidates.add(evaluation);
        }
      }
      if (candidates.isEmpty()) {
        return;
      }

      candidates.sort(Comparator
        .comparingDouble((CandidateEvaluation c) -> c.score).reversed()
        .thenComparing((CandidateEvaluation c) -> c.order.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(c -> c.order.orderNo)
        .thenComparing(c -> c.task.taskKey));

      boolean progressed = false;
      for (CandidateEvaluation candidate : candidates) {
        if (urgentOnly && quotaByProcess.getOrDefault(candidate.task.processCode, 0d) <= EPS) {
          continue;
        }
        CandidateEvaluation live = evaluateCandidateForShift(
          candidate.order,
          candidate.task,
          shiftId,
          shiftCode,
          shiftIndex,
          shiftDateValue,
          tasks,
          processConfigMap,
          workerByShiftProcess,
          machineByShiftProcess,
          shiftWorkersUsed,
          shiftMachinesUsed,
          shiftMaterialArrivalByProductProcess,
          cumulativeMaterialByShiftProductProcess,
          materialConsumedByProductProcess,
          cumulativeComponentByShift,
          componentConsumed,
          producedByTaskShift,
          finalTaskKeys,
          producedBeforeShift,
          lastProductByShiftProcess,
          productMixByShiftProcess
        );
        if (live.feasibleQty <= EPS) {
          if (live.blockedReason != null) {
            lastBlockedByTask.put(candidate.task.taskKey, live.blockedReason);
          }
          continue;
        }

        double plannedQty = determineChunkQty(
          live,
          shiftDateValue,
          remainingQtyByOrder,
          urgentDailyProducedByOrderDate,
          quotaByProcess,
          strategyCode
        );
        if (urgentOnly) {
          plannedQty = Math.min(plannedQty, quotaByProcess.getOrDefault(live.task.processCode, 0d));
        }
        plannedQty = round3(Math.min(live.feasibleQty, plannedQty));
        if (plannedQty <= EPS) {
          continue;
        }

        int groupsUsed = (int) Math.ceil(plannedQty / live.groupCapacity);
        groupsUsed = Math.max(1, Math.min(live.maxGroups, groupsUsed));
        int workersUsedDelta = groupsUsed * live.processConfig.requiredWorkers;
        int machinesUsedDelta = groupsUsed * live.processConfig.requiredMachines;
        plannedQty = round3(Math.min(plannedQty, groupsUsed * live.groupCapacity));
        if (plannedQty <= EPS) {
          continue;
        }

        shiftWorkersUsed.put(live.processUsageKey, live.workersUsed + workersUsedDelta);
        shiftMachinesUsed.put(live.processUsageKey, live.machinesUsed + machinesUsedDelta);
        shiftMaterialUsed.put(live.materialShiftKey, round3(shiftMaterialUsed.getOrDefault(live.materialShiftKey, 0d) + plannedQty));
        materialConsumedByProductProcess.put(
          live.materialConsumedKey,
          round3(materialConsumedByProductProcess.getOrDefault(live.materialConsumedKey, 0d) + plannedQty)
        );
        consumeComponent(componentConsumed, live.componentKey, plannedQty);

        live.task.producedQty = round3(live.task.producedQty + plannedQty);
        decrementRemainingQtyByOrder(remainingQtyByOrder, live.task.orderNo, plannedQty, live.finalStep);
        addProducedByTaskShift(producedByTaskShift, live.task.taskKey, shiftIndex, plannedQty);
        registerProcessProductUsage(lastProductByShiftProcess, productMixByShiftProcess, live.processUsageKey, live.task.productCode);

        MvpDomain.Allocation allocation = new MvpDomain.Allocation();
        allocation.taskKey = live.task.taskKey;
        allocation.orderNo = live.task.orderNo;
        allocation.productCode = live.task.productCode;
        allocation.processCode = live.task.processCode;
        allocation.dependencyType = live.task.dependencyType;
        allocation.shiftId = shiftId;
        allocation.date = shiftDate;
        allocation.shiftCode = shiftCode;
        allocation.scheduledQty = plannedQty;
        allocation.workersUsed = workersUsedDelta;
        allocation.machinesUsed = machinesUsedDelta;
        allocation.groupsUsed = groupsUsed;
        allocations.add(allocation);

        if (live.order.urgent) {
          String dailyKey = shiftDate + "#" + live.order.orderNo;
          urgentDailyProducedByOrderDate.put(dailyKey, round3(urgentDailyProducedByOrderDate.getOrDefault(dailyKey, 0d) + plannedQty));
        }
        if (urgentOnly) {
          quotaByProcess.put(
            live.task.processCode,
            round3(Math.max(0d, quotaByProcess.getOrDefault(live.task.processCode, 0d) - plannedQty))
          );
        }

        double remainingAfter = round3(live.task.targetQty - live.task.producedQty);
        if (remainingAfter <= EPS) {
          lastBlockedByTask.remove(live.task.taskKey);
        } else if (live.allowanceQty <= live.capacityQty + EPS && live.allowanceQty <= live.materialRemainingQty + EPS) {
          lastBlockedByTask.put(
            live.task.taskKey,
            dependencyBlockedReason(live.task, tasks, producedBeforeShift, producedByTaskShift, shiftIndex)
          );
        } else if (live.materialRemainingQty <= live.capacityQty + EPS) {
          Map<String, Object> evidence = new LinkedHashMap<>();
          evidence.put("process_code", live.task.processCode);
          evidence.put("product_code", live.task.productCode);
          evidence.put("material_remaining", round3(live.materialRemainingQty));
          evidence.put("remaining_after_shift", remainingAfter);
          lastBlockedByTask.put(
            live.task.taskKey,
            new ReasonInfo(
              REASON_MATERIAL_SHORTAGE,
              "Material cumulative balance is the binding constraint in current shift.",
              "ENGINE",
              "MATERIAL",
              dependencyStatusAtShift(live.task, tasks, producedBeforeShift),
              evidence
            )
          );
        } else {
          String reasonCode = capacityReasonCode(live.groupsByWorkers, live.groupsByMachines);
          Map<String, Object> evidence = new LinkedHashMap<>();
          evidence.put("process_code", live.task.processCode);
          evidence.put("capacity_by_resources", round3(live.capacityQty));
          evidence.put("remaining_after_shift", remainingAfter);
          evidence.put("workers_remaining", live.workersRemaining);
          evidence.put("machines_remaining", live.machinesRemaining);
          evidence.put("workers_required_per_group", live.processConfig.requiredWorkers);
          evidence.put("machines_required_per_group", live.processConfig.requiredMachines);
          if (live.changeoverPenaltyApplied) {
            evidence.put("changeover_penalty_applied", true);
          }
          lastBlockedByTask.put(
            live.task.taskKey,
            new ReasonInfo(
              reasonCode,
              reasonCode.equals(REASON_CAPACITY_MANPOWER)
                ? "Worker capacity is the binding constraint in current shift."
                : reasonCode.equals(REASON_CAPACITY_MACHINE)
                  ? "Machine capacity is the binding constraint in current shift."
                  : "Resource capacity is the binding constraint in current shift.",
              "ENGINE",
              "CAPACITY",
              dependencyStatusAtShift(live.task, tasks, producedBeforeShift),
              evidence
            )
          );
        }
        progressed = true;
      }
      if (!progressed) {
        return;
      }
    }
  }

  private static CandidateEvaluation evaluateCandidateForShift(
    MvpDomain.Order order,
    MvpDomain.ScheduleTask task,
    String shiftId,
    String shiftCode,
    int shiftIndex,
    LocalDate shiftDateValue,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
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
    CandidateEvaluation out = new CandidateEvaluation();
    out.order = order;
    out.task = task;
    out.finalStep = finalTaskKeys.contains(task.taskKey);
    out.remainingQty = round3(task.targetQty - task.producedQty);
    if (out.remainingQty <= EPS) {
      return out;
    }

    LocalDate expectedStartDate = expectedStartDateForOrder(order);
    if (shiftDateValue != null && expectedStartDate != null && shiftDateValue.isBefore(expectedStartDate)) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("order_no", order == null ? "" : order.orderNo);
      evidence.put("task_key", task.taskKey);
      evidence.put("process_code", task.processCode);
      evidence.put("shift_date", shiftDateValue.toString());
      evidence.put("expected_start_date", expectedStartDate.toString());
      out.blockedReason = new ReasonInfo(
        REASON_BEFORE_EXPECTED_START,
        "Order release date has not reached the expected start date.",
        "ENGINE",
        "POLICY",
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
      return out;
    }

    out.processConfig = processConfigMap.get(task.processCode);
    if (out.processConfig == null) {
      out.blockedReason = ReasonInfo.capacity(
        REASON_CAPACITY_UNKNOWN,
        "Process config is missing, capacity cannot be resolved.",
        "UNKNOWN",
        task.processCode,
        0,
        0,
        0d
      );
      return out;
    }

    out.allowanceQty = calcAllowance(task, tasks, producedBeforeShift, producedByTaskShift, shiftIndex);
    if (out.allowanceQty <= EPS) {
      out.blockedReason = dependencyBlockedReason(task, tasks, producedBeforeShift, producedByTaskShift, shiftIndex);
      return out;
    }

    out.processUsageKey = shiftId + "#" + task.processCode;
    out.workersAvailable = workerByShiftProcess.getOrDefault(out.processUsageKey, 0);
    out.machinesAvailable = machineByShiftProcess.getOrDefault(out.processUsageKey, 0);
    out.workersUsed = shiftWorkersUsed.getOrDefault(out.processUsageKey, 0);
    out.machinesUsed = shiftMachinesUsed.getOrDefault(out.processUsageKey, 0);
    out.workersRemaining = Math.max(0, out.workersAvailable - out.workersUsed);
    out.machinesRemaining = Math.max(0, out.machinesAvailable - out.machinesUsed);
    out.groupsByWorkers = out.workersRemaining / Math.max(1, out.processConfig.requiredWorkers);
    out.groupsByMachines = out.machinesRemaining / Math.max(1, out.processConfig.requiredMachines);
    out.maxGroups = Math.min(out.groupsByWorkers, out.groupsByMachines);
    if (out.maxGroups <= 0) {
      String reasonCode = capacityReasonCode(out.groupsByWorkers, out.groupsByMachines);
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("workers_available", out.workersAvailable);
      evidence.put("workers_used", out.workersUsed);
      evidence.put("workers_remaining", out.workersRemaining);
      evidence.put("workers_required_per_group", out.processConfig.requiredWorkers);
      evidence.put("machines_available", out.machinesAvailable);
      evidence.put("machines_used", out.machinesUsed);
      evidence.put("machines_remaining", out.machinesRemaining);
      evidence.put("machines_required_per_group", out.processConfig.requiredMachines);
      evidence.put("remaining_qty", out.remainingQty);
      evidence.put("allowance_qty", out.allowanceQty);
      evidence.put("process_code", task.processCode);
      out.blockedReason = new ReasonInfo(
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
      );
      return out;
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
    out.capacityQty = out.maxGroups * out.groupCapacity;

    out.materialShiftKey = shiftId + "#" + task.productCode + "#" + task.processCode;
    out.materialConsumedKey = task.productCode + "#" + task.processCode;
    double cumulativeMaterialAvailable = cumulativeMaterialByShiftProductProcess.getOrDefault(out.materialShiftKey, 0d);
    double shiftMaterialArrival = shiftMaterialArrivalByProductProcess.getOrDefault(out.materialShiftKey, 0d);
    double materialConsumed = materialConsumedByProductProcess.getOrDefault(out.materialConsumedKey, 0d);
    out.materialRemainingQty = Math.max(0d, cumulativeMaterialAvailable - materialConsumed);
    if (out.materialRemainingQty <= EPS) {
      Map<String, Object> evidence = new LinkedHashMap<>();
      evidence.put("process_code", task.processCode);
      evidence.put("product_code", task.productCode);
      evidence.put("material_arrival_in_shift", round3(shiftMaterialArrival));
      evidence.put("material_available_cumulative", round3(cumulativeMaterialAvailable));
      evidence.put("material_consumed_cumulative", round3(materialConsumed));
      evidence.put("material_remaining", round3(out.materialRemainingQty));
      evidence.put("remaining_qty", out.remainingQty);
      out.blockedReason = new ReasonInfo(
        REASON_MATERIAL_SHORTAGE,
        "Material cumulative balance is exhausted for product/process.",
        "ENGINE",
        "MATERIAL",
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
      return out;
    }

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
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
      return out;
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
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
      return out;
    }

    double minLot = minLotSize(task.processCode);
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
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
      out.feasibleQty = 0d;
      return out;
    }
    return out;
  }

  private static double taskPriorityScore(
    CandidateEvaluation evaluation,
    LocalDate shiftDate,
    Map<String, Double> remainingQtyByOrder,
    Map<String, Double> urgentDailyProducedByOrderDate,
    Map<String, Double> quotaByProcess,
    String strategyCode,
    StrategyProfile profileHint
  ) {
    MvpDomain.Order order = evaluation.order;
    MvpDomain.ScheduleTask task = evaluation.task;
    StrategyProfile profile = profileHint == null ? resolveStrategyProfile(normalizeStrategyCode(strategyCode)) : profileHint;
    double score = 0d;
    if (order.urgent) {
      score += SCORE_URGENT * profile.urgentWeight;
    }
    if (order.dueDate != null && shiftDate != null) {
      long daysToDue = ChronoUnit.DAYS.between(shiftDate, order.dueDate);
      if (daysToDue < 0) {
        score += (SCORE_DUE_ATTENUATION + (-daysToDue) * SCORE_OVERDUE_PER_DAY) * profile.dueWeight;
      } else {
        score += (SCORE_DUE_ATTENUATION / (1d + daysToDue)) * profile.dueWeight;
      }
    }
    double remainingRatio = task.targetQty > EPS ? Math.min(1d, evaluation.remainingQty / task.targetQty) : 0d;
    score += remainingRatio * SCORE_PROGRESS * profile.progressWeight;
    if (task.predecessorTaskKey != null) {
      score += SCORE_WIP
        * profile.wipWeight
        * Math.min(1d, Math.max(0d, evaluation.allowanceQty - evaluation.remainingQty) / Math.max(1d, task.targetQty));
    }
    if (evaluation.changeoverPenaltyApplied) {
      score += SCORE_CHANGEOVER * profile.changeoverWeight;
    }
    if (order.urgent) {
      double orderRemaining = remainingQtyByOrder.getOrDefault(order.orderNo, 0d);
      double dailyTarget = urgentDailyTarget(order, orderRemaining, shiftDate);
      double producedToday = urgentDailyProducedByOrderDate.getOrDefault((shiftDate == null ? "" : shiftDate.toString()) + "#" + order.orderNo, 0d);
      double gap = Math.max(0d, dailyTarget - producedToday);
      if (gap > EPS) {
        score += (SCORE_URGENT_GAP + Math.min(200d, gap)) * profile.urgentGapWeight;
      }
    }
    if (!quotaByProcess.isEmpty()) {
      score += Math.min(120d, quotaByProcess.getOrDefault(task.processCode, 0d)) * profile.quotaWeight;
    }
    score += Math.min(120d, evaluation.feasibleQty * profile.feasibleWeight);
    if (profile.capacityWeight > EPS) {
      score += Math.min(160d, evaluation.capacityQty * profile.capacityWeight);
    }
    return score;
  }

  private static double determineChunkQty(
    CandidateEvaluation evaluation,
    LocalDate shiftDate,
    Map<String, Double> remainingQtyByOrder,
    Map<String, Double> urgentDailyProducedByOrderDate,
    Map<String, Double> quotaByProcess,
    String strategyCode
  ) {
    String normalizedStrategyCode = normalizeStrategyCode(strategyCode);
    double minLot = minLotSize(evaluation.task.processCode);
    double chunk = Math.max(minLot, evaluation.processConfig.capacityPerShift * ALLOCATION_CHUNK_RATIO);
    if (evaluation.order.urgent) {
      double orderRemaining = remainingQtyByOrder.getOrDefault(evaluation.order.orderNo, 0d);
      double dailyTarget = urgentDailyTarget(evaluation.order, orderRemaining, shiftDate);
      double producedToday = urgentDailyProducedByOrderDate.getOrDefault((shiftDate == null ? "" : shiftDate.toString()) + "#" + evaluation.order.orderNo, 0d);
      double gap = Math.max(0d, dailyTarget - producedToday);
      if (gap > EPS) {
        chunk = Math.max(chunk, gap);
      }
    }
    if (!quotaByProcess.isEmpty()) {
      chunk = Math.min(chunk, Math.max(0d, quotaByProcess.getOrDefault(evaluation.task.processCode, 0d)));
    }
    if (evaluation.remainingQty <= minLot + EPS) {
      chunk = evaluation.remainingQty;
    }
    if (STRATEGY_MAX_CAPACITY_FIRST.equals(normalizedStrategyCode)) {
      double capacityDrivenChunk = Math.max(minLot, evaluation.groupCapacity * Math.max(1, Math.min(3, evaluation.maxGroups)));
      chunk = Math.max(chunk, Math.min(evaluation.feasibleQty, capacityDrivenChunk));
    } else if (STRATEGY_MIN_DELAY_FIRST.equals(normalizedStrategyCode) && evaluation.order != null) {
      if (evaluation.order.dueDate != null && shiftDate != null) {
        long daysToDue = ChronoUnit.DAYS.between(shiftDate, evaluation.order.dueDate);
        if (daysToDue <= 1) {
          chunk = evaluation.feasibleQty;
        }
      }
    }
    return round3(Math.max(minLot, chunk));
  }

  private static double urgentDailyTarget(MvpDomain.Order order, double orderRemaining, LocalDate shiftDate) {
    if (order == null || !order.urgent || orderRemaining <= EPS) {
      return 0d;
    }
    long daysLeft = 1L;
    if (order.dueDate != null && shiftDate != null) {
      daysLeft = Math.max(1L, ChronoUnit.DAYS.between(shiftDate, order.dueDate) + 1L);
    }
    double flowTarget = orderRemaining / daysLeft;
    double floorTarget = Math.max(URGENT_MIN_DAILY_OUTPUT_ABS, orderRemaining * URGENT_MIN_DAILY_OUTPUT_RATIO);
    return round3(Math.min(orderRemaining, Math.max(flowTarget, floorTarget)));
  }

  private static int transferBatchSize(String processCode) {
    if (processCode == null) {
      return DEFAULT_TRANSFER_BATCH;
    }
    return processCode.toUpperCase().contains("STERILE") ? STERILE_TRANSFER_BATCH : DEFAULT_TRANSFER_BATCH;
  }

  private static int minLotSize(String processCode) {
    if (processCode == null) {
      return DEFAULT_MIN_LOT;
    }
    return processCode.toUpperCase().contains("STERILE") ? STERILE_MIN_LOT : DEFAULT_MIN_LOT;
  }

  private static int dependencyLagShifts(MvpDomain.ScheduleTask task, Map<String, MvpDomain.ScheduleTask> tasks) {
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

  private static double producedUntilShift(
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

  private static double dependencyReferenceQty(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    int shiftIndex
  ) {
    if (task.predecessorTaskKey == null) {
      return 0d;
    }
    int lag = dependencyLagShifts(task, tasks);
    if ("FS".equals(task.dependencyType)) {
      if (lag <= 0) {
        return round3(producedBeforeShift.getOrDefault(task.predecessorTaskKey, 0d));
      }
      return producedUntilShift(producedByTaskShift, task.predecessorTaskKey, shiftIndex - 1 - lag);
    }
    if (lag <= 0) {
      MvpDomain.ScheduleTask predecessor = tasks.get(task.predecessorTaskKey);
      return round3(predecessor == null ? 0d : predecessor.producedQty);
    }
    return producedUntilShift(producedByTaskShift, task.predecessorTaskKey, shiftIndex - lag);
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
    return calcAllowance(task, tasks, producedBeforeShift, Map.of(), Integer.MAX_VALUE / 4);
  }

  private static double calcAllowance(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    int shiftIndex
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
    double predecessorReferenceQty = dependencyReferenceQty(
      task,
      tasks,
      producedBeforeShift,
      producedByTaskShift,
      shiftIndex
    );
    double released = round3(predecessorReferenceQty - task.producedQty);
    if (released <= EPS) {
      return 0d;
    }
    int minBatch = transferBatchSize(task.processCode);
    if (released + EPS < minBatch && remaining > minBatch) {
      return 0d;
    }
    return Math.max(0d, round3(Math.min(remaining, released)));
  }

  private static ReasonInfo dependencyBlockedReason(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift
  ) {
    return dependencyBlockedReason(task, tasks, producedBeforeShift, Map.of(), Integer.MAX_VALUE / 4);
  }

  private static ReasonInfo dependencyBlockedReason(
    MvpDomain.ScheduleTask task,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, Double> producedBeforeShift,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    int shiftIndex
  ) {
    MvpDomain.ScheduleTask predecessor = task.predecessorTaskKey == null ? null : tasks.get(task.predecessorTaskKey);
    double predecessorBeforeShift = task.predecessorTaskKey == null
      ? 0d
      : dependencyReferenceQty(task, tasks, producedBeforeShift, producedByTaskShift, shiftIndex);
    double predecessorProduced = predecessor == null ? 0d : predecessor.producedQty;
    int lagShifts = dependencyLagShifts(task, tasks);
    int minBatch = transferBatchSize(task.processCode);
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
    if (releasedQty + EPS < minBatch && remaining > minBatch) {
      return new ReasonInfo(
        REASON_TRANSFER_CONSTRAINT,
        "Task is waiting for minimum transfer batch release from predecessor.",
        "ENGINE",
        "DEPENDENCY",
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
    }
    if (lagShifts > 0 && predecessorBeforeShift <= task.producedQty + EPS) {
      return new ReasonInfo(
        REASON_TRANSFER_CONSTRAINT,
        "Task is waiting for mandatory transfer/inspection lag before release.",
        "ENGINE",
        "DEPENDENCY",
        dependencyStatusAtShift(task, tasks, producedBeforeShift),
        evidence
      );
    }
    return new ReasonInfo(
      REASON_DEPENDENCY_BLOCKED,
      "Task is waiting for predecessor output release.",
      "ENGINE",
      "DEPENDENCY",
      dependencyStatusAtShift(task, tasks, producedBeforeShift),
      evidence
    );
  }

  private static ReasonInfo frozenReason(MvpDomain.ScheduleTask task, String dependencyStatus) {
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("order_no", task.orderNo);
    evidence.put("task_key", task.taskKey);
    evidence.put("process_code", task.processCode);
    return new ReasonInfo(
      REASON_FROZEN_BY_POLICY,
      "Task skipped because the order is frozen by policy.",
      "POLICY",
      "POLICY",
      dependencyStatus,
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
      return frozenReason(task, dependencyStatusAtVersionEnd(task, allTasks));
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
    if (task.stepIndex == 0) {
      double componentTotal = componentTotalForOrder(order);
      if (componentTotal <= EPS) {
        return ReasonInfo.capacity(
          REASON_COMPONENT_SHORTAGE,
          "No BOM component inventory/inbound is available in horizon.",
          "MATERIAL",
          task.processCode,
          0,
          0,
          componentTotal
        );
      }
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
      case REASON_CAPACITY_MANPOWER, REASON_CAPACITY_MACHINE, REASON_MATERIAL_SHORTAGE, REASON_COMPONENT_SHORTAGE, REASON_CAPACITY_UNKNOWN -> "CAPACITY_LIMIT";
      case REASON_DEPENDENCY_BLOCKED, REASON_TRANSFER_CONSTRAINT -> "DEPENDENCY_LIMIT";
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

  private static String normalizeStrategyCode(String strategyCode) {
    String normalized = strategyCode == null ? "" : strategyCode.trim().toUpperCase();
    return switch (normalized) {
      case STRATEGY_MAX_CAPACITY_FIRST -> STRATEGY_MAX_CAPACITY_FIRST;
      case STRATEGY_MIN_DELAY_FIRST -> STRATEGY_MIN_DELAY_FIRST;
      default -> STRATEGY_KEY_ORDER_FIRST;
    };
  }

  private static String strategyNameCn(String strategyCode) {
    return switch (normalizeStrategyCode(strategyCode)) {
      case STRATEGY_MAX_CAPACITY_FIRST -> "最大产能优先";
      case STRATEGY_MIN_DELAY_FIRST -> "交期最小延期优先";
      default -> "关键订单优先";
    };
  }

  private static StrategyProfile resolveStrategyProfile(String strategyCode) {
    return switch (normalizeStrategyCode(strategyCode)) {
      case STRATEGY_MAX_CAPACITY_FIRST -> new StrategyProfile(
          0.65d,
          0.55d,
          0.75d,
          0.80d,
          0.70d,
          0.55d,
          0.70d,
          0.20d,
          0.04d
      );
      case STRATEGY_MIN_DELAY_FIRST -> new StrategyProfile(
          0.95d,
          1.65d,
          0.85d,
          1.00d,
          1.10d,
          0.90d,
          0.90d,
          0.06d,
          0.00d
      );
      default -> new StrategyProfile(
          1.20d,
          1.00d,
          1.00d,
          1.00d,
          1.00d,
          1.20d,
          1.00d,
          0.08d,
          0.00d
      );
    };
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
    Map<String, Double> cumulativeMaterial = cumulativeMaterialIndex(state.materialAvailability, schedule.shifts);
    Map<String, Map<Integer, Double>> materialUsageByShift = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      Integer index = shiftIndexById.get(allocation.shiftId);
      if (index == null) {
        continue;
      }
      String key = allocation.productCode + "#" + allocation.processCode;
      materialUsageByShift
        .computeIfAbsent(key, ignored -> new HashMap<>())
        .merge(index, Math.max(0d, allocation.scheduledQty), Double::sum);
    }
    for (Map.Entry<String, Map<Integer, Double>> entry : materialUsageByShift.entrySet()) {
      double cumulativeUsed = 0d;
      for (int index = 0; index < schedule.shifts.size(); index += 1) {
        cumulativeUsed = round3(cumulativeUsed + entry.getValue().getOrDefault(index, 0d));
        String shiftId = shiftIdByIndex.get(index);
        if (shiftId == null) {
          continue;
        }
        double cumulativeAvailable = cumulativeMaterial.getOrDefault(shiftId + "#" + entry.getKey(), 0d);
        if (cumulativeUsed <= cumulativeAvailable + EPS) {
          continue;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "MATERIAL_OVERBOOKED");
        row.put("productProcessKey", entry.getKey());
        row.put("shiftId", shiftId);
        row.put("usedCumulative", round3(cumulativeUsed));
        row.put("availableCumulative", round3(cumulativeAvailable));
        violations.add(row);
        break;
      }
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

  private static final class StrategyProfile {
    final double urgentWeight;
    final double dueWeight;
    final double progressWeight;
    final double wipWeight;
    final double changeoverWeight;
    final double urgentGapWeight;
    final double quotaWeight;
    final double feasibleWeight;
    final double capacityWeight;

    StrategyProfile(
      double urgentWeight,
      double dueWeight,
      double progressWeight,
      double wipWeight,
      double changeoverWeight,
      double urgentGapWeight,
      double quotaWeight,
      double feasibleWeight,
      double capacityWeight
    ) {
      this.urgentWeight = urgentWeight;
      this.dueWeight = dueWeight;
      this.progressWeight = progressWeight;
      this.wipWeight = wipWeight;
      this.changeoverWeight = changeoverWeight;
      this.urgentGapWeight = urgentGapWeight;
      this.quotaWeight = quotaWeight;
      this.feasibleWeight = feasibleWeight;
      this.capacityWeight = capacityWeight;
    }
  }

  private static final class CandidateEvaluation {
    MvpDomain.Order order;
    MvpDomain.ScheduleTask task;
    MvpDomain.ProcessConfig processConfig;
    String processUsageKey;
    String materialShiftKey;
    String materialConsumedKey;
    String componentKey;
    int workersAvailable;
    int workersUsed;
    int workersRemaining;
    int machinesAvailable;
    int machinesUsed;
    int machinesRemaining;
    int groupsByWorkers;
    int groupsByMachines;
    int maxGroups;
    boolean finalStep;
    boolean changeoverPenaltyApplied;
    double groupCapacity;
    double remainingQty;
    double allowanceQty;
    double capacityQty;
    double materialRemainingQty;
    double componentRemainingQty;
    double feasibleQty;
    double score;
    ReasonInfo blockedReason;
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

