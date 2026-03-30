package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentKeyForTask;
import static com.autoproduction.mvp.core.SchedulerPlanningComponents.componentRemainingForShift;
import static com.autoproduction.mvp.core.SchedulerPlanningComponents.consumeComponent;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.CHANGEOVER_CAPACITY_FACTOR;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.LOCKED_MAX_SHARE_WHEN_URGENT;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.PRODUCT_MIX_CAPACITY_FACTOR;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.snapshotProduced;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.defaultLineBindingForProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.lineUsageKey;
import static com.autoproduction.mvp.core.SchedulerPlanningLines.pickBaselineLineBinding;
import static com.autoproduction.mvp.core.SchedulerPlanningMutations.addProducedByTaskShift;
import static com.autoproduction.mvp.core.SchedulerPlanningMutations.decrementRemainingQtyByOrder;
import static com.autoproduction.mvp.core.SchedulerPlanningMutations.registerProcessProductUsage;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeShiftCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.processCapacityFactor;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.shiftCapacityFactor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SchedulerPlanningBaselineLockPreserver {
  private SchedulerPlanningBaselineLockPreserver() {}

  static int applyBaselineLockedAllocations(
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrderSet,
    Map<String, MvpDomain.ScheduleTask> tasks,
    Map<String, MvpDomain.Order> orderByNo,
    Set<String> openShiftIds,
    Map<String, MvpDomain.ProcessConfig> processConfigMap,
    Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess,
    Map<String, Integer> workerByShiftProcess,
    Map<String, Integer> machineByShiftProcess,
    Map<String, Double> cumulativeMaterialByShiftProductProcess,
    Map<String, Double> cumulativeComponentByShift,
    Map<String, Integer> shiftIndexByShiftId,
    Map<String, Double> urgentDemandByProcess,
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> lineShiftScheduledQty,
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
      double allowance = SchedulerDependencySupport.calcAllowance(
        task,
        tasks,
        producedBeforeShift,
        producedByTaskShift,
        shiftIndex,
        EPS
      );
      if (allowance <= EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }
      double preservedQty = round3(Math.min(Math.min(remainingQty, baselineQty), allowance));
      if (preservedQty <= EPS) {
        continue;
      }

      String normalizedProcessCode = normalizeProcessCode(task.processCode);
      List<MvpDomain.LineProcessBinding> bindings = lineBindingsByProcess.getOrDefault(
        normalizedProcessCode == null ? task.processCode : normalizedProcessCode,
        List.of()
      );
      if (bindings.isEmpty()) {
        bindings = List.of(defaultLineBindingForProcess(task.processCode, processConfigMap.get(normalizedProcessCode)));
      }
      MvpDomain.LineProcessBinding selectedBinding = pickBaselineLineBinding(source, bindings);
      if (selectedBinding == null) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }
      String shiftCode = source.shiftCode;
      if (shiftCode == null || shiftCode.isBlank()) {
        int split = source.shiftId == null ? -1 : source.shiftId.indexOf('#');
        shiftCode = split >= 0 && split + 1 < source.shiftId.length() ? source.shiftId.substring(split + 1) : "D";
      }
      shiftCode = normalizeShiftCode(shiftCode);

      String processResourceKey = source.shiftId + "#" + task.processCode;
      String processUsageKey = lineUsageKey(source.shiftId, selectedBinding, task.processCode);
      int workersAvailable = workerByShiftProcess.getOrDefault(processResourceKey, 0);
      int machinesAvailable = machineByShiftProcess.getOrDefault(processResourceKey, 0);
      int workersUsed = shiftWorkersUsed.getOrDefault(processResourceKey, 0);
      int machinesUsed = shiftMachinesUsed.getOrDefault(processResourceKey, 0);
      int workersRemaining = Math.max(0, workersAvailable - workersUsed);
      int machinesRemaining = Math.max(0, machinesAvailable - machinesUsed);
      double alreadyScheduledOnLine = lineShiftScheduledQty.getOrDefault(processUsageKey, 0d);
      boolean lineAlreadyReserved = alreadyScheduledOnLine > EPS;
      int workersToUse = lineAlreadyReserved ? 0 : Math.max(1, selectedBinding.requiredWorkers);
      int machinesToUse = lineAlreadyReserved ? 0 : Math.max(1, selectedBinding.requiredMachines);
      if (workersRemaining < workersToUse || machinesRemaining < machinesToUse) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }

      double capacityFactor = shiftCapacityFactor(shiftCode) * processCapacityFactor(task.processCode);
      String latestProduct = lastProductByShiftProcess.get(processUsageKey);
      if (latestProduct != null && !latestProduct.equals(task.productCode)) {
        capacityFactor *= CHANGEOVER_CAPACITY_FACTOR;
      }
      Set<String> productMix = productMixByShiftProcess.getOrDefault(processUsageKey, Set.of());
      if (!productMix.isEmpty() && !productMix.contains(task.productCode)) {
        capacityFactor *= PRODUCT_MIX_CAPACITY_FACTOR;
      }
      double lineCapacity = Math.max(EPS, selectedBinding.capacityPerShift * capacityFactor);
      double capacityByResources = Math.max(0d, lineCapacity - alreadyScheduledOnLine);
      if (capacityByResources <= EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }

      double urgentLockedCap = capacityByResources;
      if (urgentDemandByProcess.getOrDefault(normalizeProcessCode(task.processCode), 0d) > EPS) {
        double maxLockedShare = capacityByResources * LOCKED_MAX_SHARE_WHEN_URGENT;
        double usedLockedShare = lockedQtyByShiftProcess.getOrDefault(processResourceKey, 0d);
        urgentLockedCap = Math.max(0d, maxLockedShare - usedLockedShare);
      }

      String materialKey = source.shiftId + "#" + task.productCode + "#" + task.processCode;
      String materialConsumedKey = task.productCode + "#" + task.processCode;
      double materialRemaining = Double.POSITIVE_INFINITY;
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

      feasibleQty = round3(feasibleQty);
      if (feasibleQty <= EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
        continue;
      }

      shiftWorkersUsed.put(processResourceKey, workersUsed + workersToUse);
      shiftMachinesUsed.put(processResourceKey, machinesUsed + machinesToUse);
      lineShiftScheduledQty.put(processUsageKey, round3(alreadyScheduledOnLine + feasibleQty));
      shiftMaterialUsed.put(materialKey, round3(shiftMaterialUsed.getOrDefault(materialKey, 0d) + feasibleQty));
      materialConsumedByProductProcess.put(
        materialConsumedKey,
        round3(materialConsumedByProductProcess.getOrDefault(materialConsumedKey, 0d) + feasibleQty)
      );
      consumeComponent(componentConsumed, componentKey, feasibleQty);
      lockedQtyByShiftProcess.put(
        processResourceKey,
        round3(lockedQtyByShiftProcess.getOrDefault(processResourceKey, 0d) + feasibleQty)
      );

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
      preserved.companyCode = selectedBinding.companyCode;
      preserved.workshopCode = selectedBinding.workshopCode;
      preserved.lineCode = selectedBinding.lineCode;
      preserved.lineName = selectedBinding.lineName;
      preserved.dependencyType = task.dependencyType;
      preserved.shiftId = source.shiftId;
      preserved.date = source.date;
      preserved.shiftCode = shiftCode;
      preserved.scheduledQty = feasibleQty;
      preserved.workersUsed = workersToUse;
      preserved.machinesUsed = machinesToUse;
      preserved.groupsUsed = (workersToUse > 0 || machinesToUse > 0) ? 1 : 0;
      allocations.add(preserved);

      preservedLockedTaskKeys.add(task.taskKey);
      if (source.scheduledQty - feasibleQty > EPS) {
        lockPreemptedTaskKeys.add(task.taskKey);
      }
      preservedCount += 1;
    }
    return preservedCount;
  }

  @SuppressWarnings("unused")
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

  @SuppressWarnings("unused")
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
}

