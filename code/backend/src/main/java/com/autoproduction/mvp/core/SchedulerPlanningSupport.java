package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.DEPENDENCY_READY;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchedulerPlanningSupport {
  private SchedulerPlanningSupport() {}

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo
  ) {
    return generate(state, orders, requestId, versionNo, null, Set.of(), SchedulerPlanningConstants.STRATEGY_KEY_ORDER_FIRST);
  }

  static MvpDomain.ScheduleVersion generate(
    MvpDomain.State state,
    List<MvpDomain.Order> orders,
    String requestId,
    String versionNo,
    MvpDomain.ScheduleVersion baseVersion,
    Set<String> lockedOrders
  ) {
    return generate(state, orders, requestId, versionNo, baseVersion, lockedOrders, SchedulerPlanningConstants.STRATEGY_KEY_ORDER_FIRST);
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
    return SchedulerPlanningEngine.generate(state, orders, requestId, versionNo, baseVersion, lockedOrders, strategyCode);
  }

  static Map<String, Object> validate(MvpDomain.State state, MvpDomain.ScheduleVersion schedule) {
    return SchedulerPlanningValidation.validate(state, schedule);
  }

  static final class ReasonInfo {
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
}

