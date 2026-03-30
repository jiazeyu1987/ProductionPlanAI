package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningBaselineLockPreserver.applyBaselineLockedAllocations;
import static com.autoproduction.mvp.core.SchedulerPlanningIndexes.snapshotProduced;
import static com.autoproduction.mvp.core.SchedulerPlanningShiftRounds.scheduleShiftRounds;
import static com.autoproduction.mvp.core.SchedulerPlanningUrgency.buildUrgentDemandByProcess;
import static com.autoproduction.mvp.core.SchedulerPlanningUrgency.buildUrgentQuotaForShift;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.parseDate;

import com.autoproduction.mvp.core.SchedulerPlanningSupport.ReasonInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningEngineShiftScheduleSupport {
  private SchedulerPlanningEngineShiftScheduleSupport() {}

  record SchedulingState(
    Map<String, Integer> shiftWorkersUsed,
    Map<String, Integer> shiftMachinesUsed,
    Map<String, Double> lineShiftScheduledQty,
    Map<String, Double> shiftMaterialUsed,
    Map<String, Double> materialConsumedByProductProcess,
    Map<String, Double> componentConsumed,
    Map<String, Map<Integer, Double>> producedByTaskShift,
    Map<String, String> lastProductByShiftProcess,
    Map<String, Set<String>> productMixByShiftProcess,
    Map<String, ReasonInfo> lastBlockedByTask,
    List<MvpDomain.Allocation> allocations,
    Set<String> lockPreemptedTaskKeys,
    Map<String, Double> urgentDailyProducedByOrderDate
  ) {
    static SchedulingState create() {
      return new SchedulingState(
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new ArrayList<>(),
        new HashSet<>(),
        new HashMap<>()
      );
    }
  }

  record BaselinePreserveResult(int preservedLockedAllocationCount, Set<String> preservedLockedTaskKeys) {}

  static BaselinePreserveResult preserveBaselineLocks(
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrderSet,
    SchedulerPlanningEngineIndexSupport.Indexes indexes,
    SchedulerPlanningEngineTaskBuildSupport.TaskBuild taskBuild,
    SchedulingState schedulingState
  ) {
    Set<String> openShiftIds = new HashSet<>();
    for (Map<String, Object> shift : indexes.shifts()) {
      openShiftIds.add(String.valueOf(shift.get("shiftId")));
    }
    Map<String, Double> urgentDemandByProcess = buildUrgentDemandByProcess(
      taskBuild.tasksByOrder(),
      taskBuild.orderByNo(),
      lockedOrderSet
    );
    Set<String> preservedLockedTaskKeys = new HashSet<>();
    int preservedLockedAllocationCount = applyBaselineLockedAllocations(
      baseVersion,
      lockedOrderSet,
      taskBuild.tasks(),
      taskBuild.orderByNo(),
      openShiftIds,
      indexes.processConfigMap(),
      indexes.lineBindingsByProcess(),
      indexes.workerByShiftProcess(),
      indexes.machineByShiftProcess(),
      indexes.cumulativeMaterialByShiftProductProcess(),
      indexes.cumulativeComponentByShift(),
      indexes.shiftIndexByShiftId(),
      urgentDemandByProcess,
      schedulingState.shiftWorkersUsed(),
      schedulingState.shiftMachinesUsed(),
      schedulingState.lineShiftScheduledQty(),
      schedulingState.shiftMaterialUsed(),
      schedulingState.materialConsumedByProductProcess(),
      schedulingState.componentConsumed(),
      schedulingState.allocations(),
      preservedLockedTaskKeys,
      schedulingState.lockPreemptedTaskKeys(),
      schedulingState.producedByTaskShift(),
      taskBuild.finalTaskKeys(),
      taskBuild.remainingQtyByOrder(),
      schedulingState.lastProductByShiftProcess(),
      schedulingState.productMixByShiftProcess()
    );
    return new BaselinePreserveResult(preservedLockedAllocationCount, preservedLockedTaskKeys);
  }

  static void scheduleAllShifts(
    SchedulerPlanningEngineIndexSupport.Indexes indexes,
    SchedulerPlanningEngineTaskBuildSupport.TaskBuild taskBuild,
    SchedulingState schedulingState,
    String normalizedStrategyCode,
    StrategyProfile strategyProfile
  ) {
    for (Map<String, Object> shift : indexes.shifts()) {
      String shiftId = String.valueOf(shift.get("shiftId"));
      String shiftDate = String.valueOf(shift.get("date"));
      String shiftCode = String.valueOf(shift.get("shiftCode"));
      int shiftIndex = indexes.shiftIndexByShiftId().getOrDefault(shiftId, 0);
      LocalDate shiftDateValue = parseDate(shiftDate);
      Map<String, Double> producedBeforeShift = snapshotProduced(taskBuild.tasks());

      Map<String, Double> urgentQuotaByProcess = buildUrgentQuotaForShift(
        shiftId,
        shiftCode,
        taskBuild.orderByNo(),
        taskBuild.schedulableOrders(),
        taskBuild.tasksByOrder(),
        indexes.lineBindingsByProcess(),
        indexes.workerByShiftProcess(),
        indexes.machineByShiftProcess(),
        schedulingState.shiftWorkersUsed(),
        schedulingState.shiftMachinesUsed(),
        schedulingState.lineShiftScheduledQty()
      );
      scheduleShiftRounds(
        true,
        shiftId,
        shiftDate,
        shiftCode,
        shiftIndex,
        shiftDateValue,
        taskBuild.schedulableOrders(),
        taskBuild.tasksByOrder(),
        taskBuild.tasks(),
        indexes.processConfigMap(),
        indexes.lineBindingsByProcess(),
        indexes.workerByShiftProcess(),
        indexes.machineByShiftProcess(),
        schedulingState.shiftWorkersUsed(),
        schedulingState.shiftMachinesUsed(),
        schedulingState.lineShiftScheduledQty(),
        indexes.shiftMaterialArrivalByProductProcess(),
        indexes.cumulativeMaterialByShiftProductProcess(),
        schedulingState.shiftMaterialUsed(),
        schedulingState.materialConsumedByProductProcess(),
        indexes.cumulativeComponentByShift(),
        schedulingState.componentConsumed(),
        schedulingState.producedByTaskShift(),
        taskBuild.finalTaskKeys(),
        producedBeforeShift,
        taskBuild.remainingQtyByOrder(),
        schedulingState.urgentDailyProducedByOrderDate(),
        urgentQuotaByProcess,
        normalizedStrategyCode,
        strategyProfile,
        schedulingState.lastProductByShiftProcess(),
        schedulingState.productMixByShiftProcess(),
        schedulingState.lastBlockedByTask(),
        schedulingState.allocations()
      );
      scheduleShiftRounds(
        false,
        shiftId,
        shiftDate,
        shiftCode,
        shiftIndex,
        shiftDateValue,
        taskBuild.schedulableOrders(),
        taskBuild.tasksByOrder(),
        taskBuild.tasks(),
        indexes.processConfigMap(),
        indexes.lineBindingsByProcess(),
        indexes.workerByShiftProcess(),
        indexes.machineByShiftProcess(),
        schedulingState.shiftWorkersUsed(),
        schedulingState.shiftMachinesUsed(),
        schedulingState.lineShiftScheduledQty(),
        indexes.shiftMaterialArrivalByProductProcess(),
        indexes.cumulativeMaterialByShiftProductProcess(),
        schedulingState.shiftMaterialUsed(),
        schedulingState.materialConsumedByProductProcess(),
        indexes.cumulativeComponentByShift(),
        schedulingState.componentConsumed(),
        schedulingState.producedByTaskShift(),
        taskBuild.finalTaskKeys(),
        producedBeforeShift,
        taskBuild.remainingQtyByOrder(),
        schedulingState.urgentDailyProducedByOrderDate(),
        Collections.emptyMap(),
        normalizedStrategyCode,
        strategyProfile,
        schedulingState.lastProductByShiftProcess(),
        schedulingState.productMixByShiftProcess(),
        schedulingState.lastBlockedByTask(),
        schedulingState.allocations()
      );
    }
  }
}
