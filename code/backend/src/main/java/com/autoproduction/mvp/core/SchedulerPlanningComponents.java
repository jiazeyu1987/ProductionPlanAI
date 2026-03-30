package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.COMPONENT_RAW_PREFIX;
import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.parseFlexibleDate;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.round3;

import java.time.LocalDate;
import java.util.Map;

final class SchedulerPlanningComponents {
  private SchedulerPlanningComponents() {}

  static String componentKeyFromOrder(MvpDomain.Order order) {
    if (order == null) {
      return null;
    }
    String baseCode = null;
    if (order.businessData != null && order.businessData.semiFinishedCode != null && !order.businessData.semiFinishedCode.isBlank()) {
      baseCode = order.businessData.semiFinishedCode;
    }
    if (baseCode == null || baseCode.isBlank()) {
      return null;
    }
    return COMPONENT_RAW_PREFIX + baseCode.trim().toUpperCase();
  }

  static LocalDate componentArrivalDate(MvpDomain.Order order, LocalDate defaultDate) {
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

  static String componentKeyForTask(
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
    }
    if (baseCode == null || baseCode.isBlank()) {
      return null;
    }
    return COMPONENT_RAW_PREFIX + baseCode.trim().toUpperCase();
  }

  static double componentRemainingForShift(
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

  static void consumeComponent(Map<String, Double> componentConsumed, String componentKey, double qty) {
    if (componentKey == null || componentKey.isBlank() || qty <= EPS) {
      return;
    }
    componentConsumed.put(componentKey, round3(componentConsumed.getOrDefault(componentKey, 0d) + qty));
  }

  static double componentTotalForOrder(MvpDomain.Order order) {
    if (order == null || order.businessData == null) {
      return Double.POSITIVE_INFINITY;
    }
    MvpDomain.OrderBusinessData businessData = order.businessData;
    if (businessData.semiFinishedCode == null || businessData.semiFinishedCode.isBlank()) {
      return Double.POSITIVE_INFINITY;
    }
    double total = Math.max(0d, businessData.semiFinishedInventory + businessData.semiFinishedWip + businessData.pendingInboundQty);
    return round3(total);
  }
}
