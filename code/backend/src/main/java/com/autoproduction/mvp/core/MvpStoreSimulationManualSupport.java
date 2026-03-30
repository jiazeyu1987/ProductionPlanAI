package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class MvpStoreSimulationManualSupport {
  private MvpStoreSimulationManualSupport() {}

  static Map<String, Object> addManualProductionOrder(
    MvpStoreSimulationDomain store,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    store.ensureManualSimulationSnapshot();
    LocalDate businessDate = store.simulationState.currentDate;
    Random random = new Random(store.simulationState.seed + businessDate.toEpochDay() * 1103L + store.productionSeq.get() * 31L);
    String productCode = store.pickProductCode(random);
    double qty = (2 + random.nextInt(10)) * 100d;
    boolean urgent = store.bool(payload, "urgent_flag", false);
    LocalDate dueDate = businessDate.plusDays(urgent ? 1 + random.nextInt(2) : 2 + random.nextInt(5));

    String salesOrderNo = "SO-MANUAL-%05d".formatted(store.salesSeq.incrementAndGet());
    String productionOrderNo = "MO-MANUAL-%05d".formatted(store.productionSeq.incrementAndGet());

    MvpDomain.OrderBusinessData data = store.buildSimulationBusinessData(businessDate, dueDate, salesOrderNo, productCode, qty);
    data.customerRemark = "閹靛濮╁Ο鈩冨珯鐠併垹宕";
    data.weeklyMonthlyPlanRemark = "閹靛濮╁Ο鈩冨珯";
    data.note = "MANUAL_SIM";

    MvpDomain.Order order = new MvpDomain.Order(
      productionOrderNo,
      "production",
      dueDate,
      businessDate,
      urgent,
      false,
      false,
      "OPEN",
      List.of(new MvpDomain.OrderItem(productCode, qty, 0d)),
      data
    );
    store.state.orders.add(order);

    store.appendSimulationEvent(
      businessDate,
      "ORDER_CONVERTED",
      "\u5df2\u5c06\u9500\u552e\u8ba2\u5355\u8f6c\u6362\u4e3a\u751f\u4ea7\u8ba2\u5355\u3002",
      requestId,
      Map.of(
        "production_order_no", productionOrderNo,
        "sales_order_no", salesOrderNo,
        "product_code", productCode,
        "product_name_cn", store.productNameCn(productCode),
        "order_qty", qty
      )
    );
    store.appendAudit(
      "SIMULATION",
      productionOrderNo,
      "CREATE_ORDER",
      operator,
      requestId,
      "閹靛濮╁Ο鈩冨珯閺傛澘顤冮悽鐔堕獓鐠併垹宕"
    );

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("production_order_no", productionOrderNo);
    out.put("sales_order_no", salesOrderNo);
    out.put("product_code", productCode);
    out.put("product_name_cn", store.productNameCn(productCode));
    out.put("order_qty", qty);
    out.put("due_date", dueDate.toString());
    out.put("state", store.buildSimulationStateResponse(requestId));
    return out;
  }

  static Map<String, Object> advanceManualOneDay(
    MvpStoreSimulationDomain store,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    store.ensureManualSimulationSnapshot();
    long totalStart = System.nanoTime();
    Map<String, Long> phaseDurationMs = new LinkedHashMap<>();
    long phaseStart = System.nanoTime();

    String scenario = store.normalizeScenario(store.string(payload, "scenario", store.simulationState.scenario));
    long seed = payload != null && payload.containsKey("seed")
      ? (long) store.number(payload, "seed", store.simulationState.seed)
      : store.simulationState.seed;
    int dailySales = store.clampInt(
      (int) store.number(payload, "daily_sales_order_count", store.simulationState.dailySalesOrderCount),
      1,
      500
    );
    store.simulationState.seed = seed;
    store.simulationState.scenario = scenario;
    store.simulationState.dailySalesOrderCount = dailySales;
    phaseDurationMs.put("prepare_input", store.elapsedMillis(phaseStart));

    LocalDate businessDate = store.simulationState.currentDate == null ? store.state.startDate : store.simulationState.currentDate;
    String clientDateText = store.string(payload, "client_date", store.string(payload, "clientDate", null));
    LocalDate clientDate = store.parseLocalDateFlexible(clientDateText, null);
    LocalDate baseClientDate = clientDate == null ? LocalDate.now() : clientDate;
    LocalDate nextDayFromClientDate = baseClientDate.plusDays(1);
    if (businessDate.isBefore(nextDayFromClientDate)) {
      businessDate = nextDayFromClientDate;
    }
    Random dayRandom = new Random(seed + businessDate.toEpochDay() * 997L + 17L);
    phaseStart = System.nanoTime();
    Map<String, Object> capacity = store.rebuildPlanningHorizon(businessDate, scenario, dayRandom, requestId + ":manual:capacity");
    double capacityFactor = store.number(capacity, "capacity_factor", 1d);
    phaseDurationMs.put("rebuild_planning_horizon", store.elapsedMillis(phaseStart));

    String baseVersionNo = store.state.schedules.isEmpty()
      ? null
      : store.state.schedules.get(store.state.schedules.size() - 1).versionNo;
    Map<String, Object> generatePayload = new LinkedHashMap<>();
    generatePayload.put("base_version_no", baseVersionNo);
    generatePayload.put("autoReplan", false);
    generatePayload.put("reason", "MANUAL_SIM_ADVANCE");
    generatePayload.put("request_id", requestId + ":manual:generate");
    generatePayload.put("compact_response", true);
    phaseStart = System.nanoTime();
    Map<String, Object> schedule = store.generateSchedule(generatePayload, requestId + ":manual:generate", "simulator");
    String versionNo = store.string(schedule, "version_no", store.string(schedule, "versionNo", null));
    phaseDurationMs.put("generate_schedule", store.elapsedMillis(phaseStart));

    Map<String, Object> publishPayload = new LinkedHashMap<>();
    publishPayload.put("request_id", requestId + ":manual:publish");
    publishPayload.put("operator", "simulator");
    publishPayload.put("reason", "MANUAL_SIM_ADVANCE");
    phaseStart = System.nanoTime();
    store.publishSchedule(versionNo, publishPayload, requestId + ":manual:publish", "simulator");
    phaseDurationMs.put("publish_schedule", store.elapsedMillis(phaseStart));

    phaseStart = System.nanoTime();
    int reportingCount = store.simulateDailyReporting(
      businessDate,
      versionNo,
      scenario,
      dayRandom,
      requestId + ":manual:report"
    );
    phaseDurationMs.put("simulate_reporting", store.elapsedMillis(phaseStart));

    phaseStart = System.nanoTime();
    store.refreshOrderStatuses(businessDate.plusDays(1));
    int delayedOrders = store.countDelayedOrders(businessDate.plusDays(1));
    store.simulationState.currentDate = businessDate.plusDays(1);
    phaseDurationMs.put("refresh_order_status", store.elapsedMillis(phaseStart));
    MvpDomain.ScheduleVersion generatedSchedule = store.getScheduleEntity(versionNo);
    Map<String, Object> generatedObservability = store.buildScheduleObservabilityMetrics(generatedSchedule, null, null);
    long manualAdvanceDurationMs = store.elapsedMillis(totalStart);

    Map<String, Object> dailySummary = new LinkedHashMap<>();
    dailySummary.put("date", businessDate.toString());
    dailySummary.put("sales_orders", 0);
    dailySummary.put("converted_orders", 0);
    dailySummary.put("version_no", versionNo);
    dailySummary.put("reportings", reportingCount);
    dailySummary.put("capacity_factor", store.round2(capacityFactor));
    dailySummary.put("delayed_orders", delayedOrders);

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("request_id", requestId);
    summary.put("days", 1);
    summary.put("scenario", scenario);
    summary.put("seed", seed);
    summary.put("daily_sales_order_count", dailySales);
    summary.put("start_date", businessDate.toString());
    summary.put("end_date", store.simulationState.currentDate.toString());
    summary.put("new_sales_orders", 0);
    summary.put("new_production_orders", 0);
    summary.put("generated_versions", 1);
    summary.put("reporting_count", reportingCount);
    summary.put("delayed_orders", delayedOrders);
    summary.put("avg_capacity_factor", store.round2(capacityFactor));
    summary.put("daily_kpis", List.of(dailySummary));
    summary.put("manual_advance_duration_ms", manualAdvanceDurationMs);
    summary.put("manual_advance_phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
    summary.put(
      "schedule_generate_duration_ms",
      (long) store.number(generatedSchedule.metrics, "schedule_generate_duration_ms", 0d)
    );
    summary.put(
      "schedule_generate_phase_duration_ms",
      generatedSchedule.metrics.get("schedule_generate_phase_duration_ms") instanceof Map<?, ?> phases
        ? store.deepCopyMap((Map<String, Object>) phases)
        : new LinkedHashMap<String, Object>()
    );
    summary.put("unscheduled_task_count", (int) store.number(generatedObservability, "unscheduled_task_count", 0d));
    summary.put(
      "unscheduled_reason_distribution",
      generatedObservability.get("unscheduled_reason_distribution") instanceof Map<?, ?> reasons
        ? store.deepCopyMap((Map<String, Object>) reasons)
        : new LinkedHashMap<String, Object>()
    );
    summary.put(
      "schedule_completion_rate",
      store.number(
        generatedObservability,
        "schedule_completion_rate",
        store.number(generatedSchedule.metrics, "scheduleCompletionRate", 0d)
      )
    );
    summary.put(
      "locked_or_frozen_impact_count",
      (int) store.number(generatedObservability, "locked_or_frozen_impact_count", 0d)
    );
    summary.put("publish_count", (int) store.number(generatedObservability, "publish_count", 0d));
    summary.put("rollback_count", (int) store.number(generatedObservability, "rollback_count", 0d));
    summary.put("publish_rollback_count", (int) store.number(generatedObservability, "publish_rollback_count", 0d));
    summary.put("replan_failure_rate", store.number(generatedObservability, "replan_failure_rate", 0d));
    summary.put("api_error_rate", store.number(generatedObservability, "api_error_rate", 0d));
    store.simulationState.lastRunSummary = store.deepCopyMap(summary);

    store.appendSimulationEvent(
      businessDate,
      "SIM_RUN_DONE",
      "\u4eff\u771f\u8fd0\u884c\u5b8c\u6210\u3002",
      requestId,
      Map.of(
        "version_no",
        versionNo,
        "days",
        1,
        "reporting_count",
        reportingCount,
        "manual_advance_duration_ms",
        manualAdvanceDurationMs,
        "manual_advance_phase_duration_ms",
        new LinkedHashMap<>(phaseDurationMs)
      )
    );
    Map<String, Object> perfContext = new LinkedHashMap<>();
    perfContext.put("request_id", requestId);
    perfContext.put("version_no", versionNo);
    perfContext.put("phase", "manual_advance_day");
    perfContext.put("duration_ms", manualAdvanceDurationMs);
    perfContext.put("phase_duration_ms", new LinkedHashMap<>(phaseDurationMs));
    store.appendAudit(
      "SIMULATION",
      "SIM-MANUAL-ADVANCE",
      "RUN_SIMULATION",
      operator,
      requestId,
      "MANUAL_ADVANCE_DAY",
      perfContext
    );

    Map<String, Object> out = new LinkedHashMap<>(summary);
    out.put("version_no", versionNo);
    out.put("state", store.buildSimulationStateResponse(requestId));
    return out;
  }

  static Map<String, Object> resetManualSimulation(
    MvpStoreSimulationDomain store,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    if (store.manualSimulationSnapshot == null) {
      Map<String, Object> out = store.buildSimulationStateResponse(requestId);
      out.put("message", "瑜版挸澧犲▽鈩冩箒閸欘垶鍣哥純顔炬畱閹靛濮╁Ο鈩冨珯娴兼俺鐦介妴");
      return out;
    }
    store.restoreManualSimulationSnapshot();
    Map<String, Object> out = store.buildSimulationStateResponse(requestId);
    out.put("message", "瀹稿弶浠径宥呭煂閹靛濮╁Ο鈩冨珯閸撳秶娈戦弫鐗堝祦閻樿埖鈧降鈧");
    return out;
  }
}

