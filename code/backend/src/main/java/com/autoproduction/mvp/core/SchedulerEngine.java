package com.autoproduction.mvp.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerEngine {

  private SchedulerEngine() {}

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo
  ) {
    return SchedulerPlanningSupport.generate(state, orders, requestId, versionNo);
  }

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo,
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrders
  ) {
    return SchedulerPlanningSupport.generate(state, orders, requestId, versionNo, baseVersion, lockedOrders);
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
    return SchedulerPlanningSupport.generate(
      state,
      orders,
      requestId,
      versionNo,
      baseVersion,
      lockedOrders,
      strategyCode
    );
  }

  static Map<String, Object> validate(MvpDomain.State state, MvpDomain.ScheduleVersion schedule) {
    return SchedulerPlanningSupport.validate(state, schedule);
  }
}
