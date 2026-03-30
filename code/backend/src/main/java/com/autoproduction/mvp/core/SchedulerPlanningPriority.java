package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.ALLOCATION_CHUNK_RATIO;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STRATEGY_MAX_CAPACITY_FIRST;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.STRATEGY_MIN_DELAY_FIRST;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.URGENT_MIN_DAILY_OUTPUT_ABS;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.URGENT_MIN_DAILY_OUTPUT_RATIO;
import static com.autoproduction.mvp.core.SchedulerPlanningStrategy.resolveStrategyProfile;
import static com.autoproduction.mvp.core.SchedulerPlanningStrategy.normalizeStrategyCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

final class SchedulerPlanningPriority {
  private SchedulerPlanningPriority() {}

  static double taskPriorityScore(
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
    double orderRemaining = remainingQtyByOrder.getOrDefault(order.orderNo, 0d);
    double dailyTarget = urgentDailyTarget(order, orderRemaining, shiftDate);
    double producedToday = urgentDailyProducedByOrderDate.getOrDefault((shiftDate == null ? "" : shiftDate.toString()) + "#" + order.orderNo, 0d);
    boolean quotaEnabled = !quotaByProcess.isEmpty();
    double processQuota = quotaEnabled ? quotaByProcess.getOrDefault(normalizeProcessCode(task.processCode), 0d) : 0d;
    return SchedulerScoring.computePriorityScore(
      shiftDate,
      order.dueDate,
      order.urgent,
      task.targetQty,
      evaluation.remainingQty,
      task.predecessorTaskKey != null,
      evaluation.allowanceQty,
      evaluation.changeoverPenaltyApplied,
      dailyTarget,
      producedToday,
      processQuota,
      quotaEnabled,
      evaluation.feasibleQty,
      evaluation.capacityQty,
      profile.urgentWeight,
      profile.dueWeight,
      profile.progressWeight,
      profile.wipWeight,
      profile.changeoverWeight,
      profile.urgentGapWeight,
      profile.quotaWeight,
      profile.feasibleWeight,
      profile.capacityWeight
    );
  }

  static double determineChunkQty(
    CandidateEvaluation evaluation,
    LocalDate shiftDate,
    Map<String, Double> remainingQtyByOrder,
    Map<String, Double> urgentDailyProducedByOrderDate,
    Map<String, Double> quotaByProcess,
    String strategyCode
  ) {
    String normalizedStrategyCode = normalizeStrategyCode(strategyCode);
    double minLot = SchedulerDependencySupport.minLotSize(evaluation.task.processCode);
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
      chunk = Math.min(chunk, Math.max(0d, quotaByProcess.getOrDefault(normalizeProcessCode(evaluation.task.processCode), 0d)));
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

  static double urgentDailyTarget(MvpDomain.Order order, double orderRemaining, LocalDate shiftDate) {
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
}

