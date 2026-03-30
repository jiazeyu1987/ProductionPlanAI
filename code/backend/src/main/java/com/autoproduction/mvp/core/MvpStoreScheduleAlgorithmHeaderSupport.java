package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MvpStoreScheduleAlgorithmHeaderSupport {
  private MvpStoreScheduleAlgorithmHeaderSupport() {}

  static String resolveStrategyCode(MvpStoreScheduleAlgorithmDomain store, MvpDomain.ScheduleVersion schedule) {
    return store.normalizeScheduleStrategy(
      store.firstString(
        schedule.metadata,
        "schedule_strategy_code",
        "scheduleStrategyCode",
        "strategy_code",
        "strategyCode"
      )
    );
  }

  static Map<String, Object> buildSummary(
    MvpStoreScheduleAlgorithmDomain store,
    MvpDomain.ScheduleVersion schedule,
    Map<String, Object> observabilityMetrics
  ) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("task_count", schedule.tasks.size());
    summary.put("allocation_count", schedule.allocations.size());
    summary.put("order_count", schedule.tasks.stream().map(task -> task.orderNo).filter(Objects::nonNull).distinct().count());
    summary.put("target_qty", store.round2(store.number(schedule.metrics, "targetQty", 0d)));
    summary.put("scheduled_qty", store.round2(store.number(schedule.metrics, "scheduledQty", 0d)));
    summary.put("schedule_completion_rate", store.round2(store.number(observabilityMetrics, "schedule_completion_rate", 0d)));
    summary.put("unscheduled_task_count", (int) store.number(
      observabilityMetrics,
      "unscheduled_task_count",
      schedule.unscheduled.size()
    ));
    summary.put(
      "schedule_generate_duration_ms",
      (long) store.number(schedule.metrics, "schedule_generate_duration_ms", 0d)
    );
    summary.put(
      "schedule_generate_phase_duration_ms",
      schedule.metrics.get("schedule_generate_phase_duration_ms") instanceof Map<?, ?> phaseDuration
        ? store.deepCopyMap((Map<String, Object>) phaseDuration)
        : new LinkedHashMap<String, Object>()
    );
    summary.put(
      "unscheduled_reason_distribution",
      observabilityMetrics.get("unscheduled_reason_distribution") instanceof Map<?, ?> reasons
        ? store.deepCopyMap((Map<String, Object>) reasons)
        : new LinkedHashMap<String, Object>()
    );
    summary.put("locked_or_frozen_impact_count", (int) store.number(observabilityMetrics, "locked_or_frozen_impact_count", 0d));
    summary.put("publish_count", (int) store.number(observabilityMetrics, "publish_count", 0d));
    summary.put("rollback_count", (int) store.number(observabilityMetrics, "rollback_count", 0d));
    summary.put("publish_rollback_count", (int) store.number(observabilityMetrics, "publish_rollback_count", 0d));
    summary.put("replan_failure_rate", store.number(observabilityMetrics, "replan_failure_rate", 0d));
    summary.put("api_error_rate", store.number(observabilityMetrics, "api_error_rate", 0d));
    return summary;
  }

  static List<String> buildLogic(MvpDomain.ScheduleVersion schedule, String strategyCode, String strategyNameCn) {
    List<String> logic = new ArrayList<>();
    logic.add("Current strategy: " + strategyNameCn + " (" + strategyCode + ").");
    logic.add("Dispatch objective uses weighted scoring: urgent guarantee + due-date risk + WIP reduction + changeover penalty.");
    logic.add("Urgent policy reserves a minimum process share and daily output floor before regular orders are fully expanded.");
    logic.add("Locked baseline is preserved by default, but can be partially released when urgent protection must be satisfied.");
    if (schedule.metadata.containsKey("hardConstraints")) {
      logic.add("Hard constraints: " + schedule.metadata.get("hardConstraints"));
    } else {
      logic.add("Hard constraints: MAN, MACHINE, MATERIAL.");
    }
    if (schedule.metadata.containsKey("dependencyTypes")) {
      logic.add("Dependency rules: " + schedule.metadata.get("dependencyTypes"));
    } else {
      logic.add("Dependency rules: FS/SS.");
    }
    logic.add("Transfer rules include minimum transfer batch, minimum lot size, and sterilization lag surrogate.");
    logic.add("Capacity is adjusted by shift efficiency and changeover/product-mix penalties.");
    logic.add("Material is checked by cumulative arrival and cumulative consumption, not only single-shift snapshots.");
    logic.add("Component kit check is applied on first-process tasks using BOM-like inventory + inbound-time approximation.");
    logic.add("Unscheduled tasks keep structured reason codes, for example TRANSFER_CONSTRAINT/LOCK_PREEMPTED_BY_URGENT.");
    return logic;
  }

  static List<Map<String, Object>> buildPriorityPreview(MvpStoreScheduleAlgorithmDomain store, String strategyCode) {
    Comparator<MvpDomain.Order> priorityComparator = switch (strategyCode) {
      case MvpStoreScheduleAlgorithmDomain.STRATEGY_MAX_CAPACITY_FIRST -> Comparator
          .comparingDouble((MvpDomain.Order o) -> {
            double totalQty = o.items == null ? 0d : o.items.stream().mapToDouble(item -> Math.max(0d, item.qty - item.completedQty)).sum();
            return -totalQty;
          })
          .thenComparing((MvpDomain.Order o) -> !o.urgent)
          .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(o -> o.orderNo);
      case MvpStoreScheduleAlgorithmDomain.STRATEGY_MIN_DELAY_FIRST -> Comparator
          .comparing((MvpDomain.Order o) -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing((MvpDomain.Order o) -> !o.urgent)
          .thenComparing(o -> o.orderNo);
      default -> Comparator
          .comparing((MvpDomain.Order o) -> !o.urgent)
          .thenComparing(o -> o.dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(o -> o.orderNo);
    };

    return store.state.orders.stream()
      .sorted(priorityComparator)
      .limit(10)
      .map(order -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("urgent_flag", order.urgent ? 1 : 0);
        row.put("due_date", order.dueDate == null ? null : order.dueDate.toString());
        row.put("status", order.status);
        row.put("status_name_cn", store.statusNameCn(order.status));
        row.put(
          "remaining_qty",
          store.round2(order.items == null ? 0d : order.items.stream().mapToDouble(item -> Math.max(0d, item.qty - item.completedQty)).sum())
        );
        return row;
      })
      .toList();
  }
}

