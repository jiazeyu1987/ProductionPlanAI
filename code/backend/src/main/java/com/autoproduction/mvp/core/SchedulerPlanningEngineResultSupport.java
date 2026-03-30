package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.DEPENDENCY_BLOCKED_PREDECESSOR;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.DEPENDENCY_READY;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.DEPENDENCY_WAIT_PREDECESSOR;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.LOCKED_MAX_SHARE_WHEN_URGENT;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_BEFORE_EXPECTED_START;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_MACHINE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_MANPOWER;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_CAPACITY_UNKNOWN;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_COMPONENT_SHORTAGE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_DEPENDENCY_BLOCKED;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_FROZEN_BY_POLICY;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_LOCK_PREEMPTED;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_LOCKED_PRESERVED;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_MATERIAL_SHORTAGE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_TRANSFER_CONSTRAINT;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.REASON_URGENT_GUARANTEE;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STERILE_MIN_LOT;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STERILE_RELEASE_LAG_SHIFTS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STERILE_TRANSFER_BATCH;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.URGENT_MIN_DAILY_OUTPUT_ABS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.URGENT_MIN_DAILY_OUTPUT_RATIO;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.URGENT_MIN_SHARE_PER_PROCESS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.DEFAULT_MIN_LOT;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.DEFAULT_TRANSFER_BATCH;
import static com.autoproduction.mvp.core.SchedulerPlanningStrategy.strategyNameCn;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningEngineResultSupport {
  private SchedulerPlanningEngineResultSupport() {}

  static MvpDomain.ScheduleVersion buildResult(
    MvpDomain.State state,
    String requestId,
    String versionNo,
    SchedulerPlanningEngineIndexSupport.Indexes indexes,
    SchedulerPlanningEngineTaskBuildSupport.TaskBuild taskBuild,
    List<MvpDomain.Allocation> allocations,
    List<Map<String, Object>> unscheduled,
    Set<String> lockedOrderSet,
    MvpDomain.ScheduleVersion baseVersion,
    SchedulerPlanningEngineShiftScheduleSupport.BaselinePreserveResult baseline,
    String normalizedStrategyCode,
    StrategyProfile strategyProfile,
    int lockPreemptedTaskCount
  ) {
    MvpDomain.ScheduleVersion result = new MvpDomain.ScheduleVersion();
    result.requestId = requestId;
    result.versionNo = versionNo;
    result.generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    result.shiftHours = state.shiftHours;
    result.shiftsPerDay = state.shiftsPerDay;
    result.shifts = indexes.shifts();
    result.tasks = new ArrayList<>(taskBuild.tasks().values());
    result.allocations = allocations;
    result.unscheduled = unscheduled;
    result.metrics = SchedulerMetricsSupport.buildMetrics(taskBuild.tasks().values(), allocations, unscheduled, EPS);
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
    result.metadata.put("filteredFrozenOrders", taskBuild.sortedOrders().stream().filter(o -> o.frozen).map(o -> o.orderNo).toList());
    List<String> lockedOrderList = new ArrayList<>(lockedOrderSet);
    lockedOrderList.sort(String::compareTo);
    result.metadata.put("lockedOrders", lockedOrderList);
    result.metadata.put("baselineVersionNo", baseVersion == null ? null : baseVersion.versionNo);
    result.metadata.put("preservedLockedTaskCount", baseline.preservedLockedTaskKeys().size());
    result.metadata.put("preservedLockedAllocationCount", baseline.preservedLockedAllocationCount());
    result.metadata.put("lockPreemptedTaskCount", lockPreemptedTaskCount);
    result.metadata.put("objectiveWeights", Map.of(
      "urgent", SchedulerScoring.URGENT,
      "overduePerDay", SchedulerScoring.OVERDUE_PER_DAY,
      "dueAttenuation", SchedulerScoring.DUE_ATTENUATION,
      "progress", SchedulerScoring.PROGRESS,
      "wip", SchedulerScoring.WIP,
      "changeover", SchedulerScoring.CHANGEOVER,
      "urgentGap", SchedulerScoring.URGENT_GAP
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
}

