package com.autoproduction.mvp.core;

final class CandidateEvaluation {
  MvpDomain.Order order;
  MvpDomain.ScheduleTask task;
  MvpDomain.ProcessConfig processConfig;
  MvpDomain.LineProcessBinding lineBinding;
  String processUsageKey;
  String resourceUsageKey;
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
  int workersNeededDelta;
  int machinesNeededDelta;
  boolean finalStep;
  boolean changeoverPenaltyApplied;
  double groupCapacity;
  double lineScheduledQty;
  double remainingQty;
  double allowanceQty;
  double capacityQty;
  double materialRemainingQty;
  double componentRemainingQty;
  double feasibleQty;
  double score;
  SchedulerPlanningSupport.ReasonInfo blockedReason;
}

