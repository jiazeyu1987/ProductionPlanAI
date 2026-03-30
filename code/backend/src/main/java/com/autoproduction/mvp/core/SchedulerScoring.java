package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class SchedulerScoring {
  static final double URGENT = 800d;
  static final double OVERDUE_PER_DAY = 120d;
  static final double DUE_ATTENUATION = 200d;
  static final double PROGRESS = 200d;
  static final double WIP = -100d;
  static final double CHANGEOVER = -80d;
  static final double URGENT_GAP = 260d;

  private SchedulerScoring() {}

  static double computePriorityScore(
    LocalDate shiftDate,
    LocalDate dueDate,
    boolean urgent,
    double targetQty,
    double remainingQty,
    boolean hasPredecessor,
    double allowanceQty,
    boolean changeoverPenaltyApplied,
    double dailyTarget,
    double producedToday,
    double quotaByProcess,
    boolean quotaEnabled,
    double feasibleQty,
    double capacityQty,
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
    double score = 0d;
    if (urgent) {
      score += URGENT * urgentWeight;
    }
    if (dueDate != null && shiftDate != null) {
      long daysToDue = ChronoUnit.DAYS.between(shiftDate, dueDate);
      if (daysToDue < 0L) {
        score += (DUE_ATTENUATION + (-daysToDue) * OVERDUE_PER_DAY) * dueWeight;
      } else {
        score += (DUE_ATTENUATION / (1d + daysToDue)) * dueWeight;
      }
    }
    double remainingRatio = targetQty > 1e-9d ? Math.min(1d, remainingQty / targetQty) : 0d;
    score += remainingRatio * PROGRESS * progressWeight;
    if (hasPredecessor) {
      score += WIP * wipWeight * Math.min(1d, Math.max(0d, allowanceQty - remainingQty) / Math.max(1d, targetQty));
    }
    if (changeoverPenaltyApplied) {
      score += CHANGEOVER * changeoverWeight;
    }
    if (urgent) {
      double gap = Math.max(0d, dailyTarget - producedToday);
      if (gap > 1e-9d) {
        score += (URGENT_GAP + Math.min(200d, gap)) * urgentGapWeight;
      }
    }
    if (quotaEnabled) {
      score += Math.min(120d, quotaByProcess) * quotaWeight;
    }
    score += Math.min(120d, feasibleQty * feasibleWeight);
    if (capacityWeight > 1e-9d) {
      score += Math.min(160d, capacityQty * capacityWeight);
    }
    return score;
  }
}
