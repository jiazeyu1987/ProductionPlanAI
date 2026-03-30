package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningStrategy.normalizeStrategyCode;
import static com.autoproduction.mvp.core.SchedulerPlanningStrategy.resolveStrategyProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningEngine {
  private SchedulerPlanningEngine() {}

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

    SchedulerPlanningEngineIndexSupport.Indexes indexes = SchedulerPlanningEngineIndexSupport.build(state);
    SchedulerPlanningEngineTaskBuildSupport.TaskBuild taskBuild = SchedulerPlanningEngineTaskBuildSupport.build(
      state,
      orders,
      indexes.cumulativeReportedByOrderProductProcess(),
      lockedOrderSet
    );
    SchedulerPlanningEngineShiftScheduleSupport.SchedulingState schedulingState = SchedulerPlanningEngineShiftScheduleSupport.SchedulingState.create();
    SchedulerPlanningEngineShiftScheduleSupport.BaselinePreserveResult baseline = SchedulerPlanningEngineShiftScheduleSupport.preserveBaselineLocks(
      baseVersion,
      lockedOrderSet,
      indexes,
      taskBuild,
      schedulingState
    );
    SchedulerPlanningEngineShiftScheduleSupport.scheduleAllShifts(
      indexes,
      taskBuild,
      schedulingState,
      normalizedStrategyCode,
      strategyProfile
    );
    List<Map<String, Object>> unscheduled = SchedulerPlanningEngineDiagnosticsSupport.populateTasksAndBuildUnscheduled(
      indexes,
      taskBuild,
      lockedOrderSet,
      baseVersion,
      baseline.preservedLockedTaskKeys(),
      schedulingState.lockPreemptedTaskKeys(),
      schedulingState.lastBlockedByTask()
    );
    return SchedulerPlanningEngineResultSupport.buildResult(
      state,
      requestId,
      versionNo,
      indexes,
      taskBuild,
      schedulingState.allocations(),
      unscheduled,
      lockedOrderSet,
      baseVersion,
      baseline,
      normalizedStrategyCode,
      strategyProfile,
      schedulingState.lockPreemptedTaskKeys().size()
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
}

