package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class MvpStoreSimulationRunSupport {
  private MvpStoreSimulationRunSupport() {}

  static Map<String, Object> resetSimulation(
    MvpStoreSimulationDomain store,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    long seed = (long) store.number(payload, "seed", store.DEFAULT_SIM_SEED);
    String scenario = store.normalizeScenario(store.string(payload, "scenario", store.DEFAULT_SIM_SCENARIO));
    int dailySales = store.clampInt(
      (int) store.number(payload, "daily_sales_order_count", store.DEFAULT_SIM_DAILY_SALES),
      1,
      500
    );
    store.resetSimulationState(seed, scenario, dailySales);
    store.appendSimulationEvent(
      store.simulationState.currentDate,
      "SIM_RESET",
      "\u4eff\u771f\u72b6\u6001\u5df2\u91cd\u7f6e\u3002",
      requestId,
      Map.of()
    );
    store.appendAudit("SIMULATION", "SIMULATION_STATE", "RESET_SIMULATION", operator, requestId, null);
    Map<String, Object> out = store.buildSimulationStateResponse(requestId);
    out.put("message", "娴犺法婀￠悩鑸碘偓浣稿嚒闁插秶鐤嗛妴");
    return out;
  }

  static Map<String, Object> runSimulation(
    MvpStoreSimulationDomain store,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    int days = store.clampInt((int) store.number(payload, "days", 7d), 1, 30);
    int dailySales = store.clampInt(
      (int) store.number(payload, "daily_sales_order_count", store.simulationState.dailySalesOrderCount),
      1,
      500
    );
    String scenario = store.normalizeScenario(store.string(payload, "scenario", store.simulationState.scenario));
    long seed = store.simulationState.seed;
    if (payload != null && payload.containsKey("seed")) {
      seed = (long) store.number(payload, "seed", seed);
    }

    store.simulationState.seed = seed;
    store.simulationState.scenario = scenario;
    store.simulationState.dailySalesOrderCount = dailySales;

    LocalDate startDate = store.simulationState.currentDate;
    int totalSales = 0;
    int totalConverted = 0;
    int totalVersions = 0;
    int totalReportings = 0;
    int delayedOrders = 0;
    double capacityFactorSum = 0d;
    List<Map<String, Object>> dailySummaries = new ArrayList<>();

    for (int i = 0; i < days; i += 1) {
      LocalDate businessDate = store.simulationState.currentDate;
      Random dayRandom = new Random(seed + businessDate.toEpochDay() * 997L + i * 131L);

      int generated = store.generateDailySalesAndProductionOrders(
        businessDate,
        dailySales,
        dayRandom,
        requestId + ":sales:" + i
      );
      totalSales += generated;
      totalConverted += generated;

      Map<String, Object> capacity = store.rebuildPlanningHorizon(businessDate, scenario, dayRandom, requestId + ":capacity:" + i);
      double capacityFactor = store.number(capacity, "capacity_factor", 1d);
      capacityFactorSum += capacityFactor;

      String baseVersionNo = store.state.schedules.isEmpty()
        ? null
        : store.state.schedules.get(store.state.schedules.size() - 1).versionNo;
      Map<String, Object> generatePayload = new LinkedHashMap<>();
      generatePayload.put("base_version_no", baseVersionNo);
      generatePayload.put("autoReplan", false);
      generatePayload.put("reason", "SIM_AUTO_DAILY");
      generatePayload.put("request_id", requestId + ":generate:" + i);
      generatePayload.put("compact_response", true);
      Map<String, Object> schedule = store.generateSchedule(generatePayload, requestId + ":generate:" + i, "simulator");
      String versionNo = store.string(schedule, "version_no", store.string(schedule, "versionNo", null));
      totalVersions += 1;
      store.appendSimulationEvent(
        businessDate,
        "SCHEDULE_GENERATED",
        "\u5df2\u751f\u6210\u6392\u4ea7\u7248\u672c\u3002",
        requestId,
        Map.of("version_no", versionNo, "base_version_no", baseVersionNo == null ? "" : baseVersionNo)
      );

      Map<String, Object> publishPayload = new LinkedHashMap<>();
      publishPayload.put("request_id", requestId + ":publish:" + i);
      publishPayload.put("operator", "simulator");
      publishPayload.put("reason", "SIM_AUTO_DAILY");
      store.publishSchedule(versionNo, publishPayload, requestId + ":publish:" + i, "simulator");
      store.appendSimulationEvent(
        businessDate,
        "SCHEDULE_PUBLISHED",
        "\u5df2\u53d1\u5e03\u6392\u4ea7\u7248\u672c\u3002",
        requestId,
        Map.of("version_no", versionNo)
      );

      String breakdownProcess = store.string(capacity, "breakdown_process", null);
      if (breakdownProcess != null) {
        store.createAlert("EQUIPMENT_DOWN", "CRITICAL", "", breakdownProcess, versionNo, 1d, 1d, null, null);
      }

      int reportingCount = store.simulateDailyReporting(businessDate, versionNo, scenario, dayRandom, requestId + ":report:" + i);
      totalReportings += reportingCount;

      store.refreshOrderStatuses(businessDate.plusDays(1));
      delayedOrders = store.countDelayedOrders(businessDate.plusDays(1));

      Map<String, Object> daySummary = new LinkedHashMap<>();
      daySummary.put("date", businessDate.toString());
      daySummary.put("sales_orders", generated);
      daySummary.put("converted_orders", generated);
      daySummary.put("version_no", versionNo);
      daySummary.put("reportings", reportingCount);
      daySummary.put("capacity_factor", store.round2(capacityFactor));
      daySummary.put("delayed_orders", delayedOrders);
      dailySummaries.add(daySummary);

      store.simulationState.currentDate = businessDate.plusDays(1);
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("request_id", requestId);
    summary.put("days", days);
    summary.put("scenario", scenario);
    summary.put("seed", seed);
    summary.put("daily_sales_order_count", dailySales);
    summary.put("start_date", startDate.toString());
    summary.put("end_date", store.simulationState.currentDate.toString());
    summary.put("new_sales_orders", totalSales);
    summary.put("new_production_orders", totalConverted);
    summary.put("generated_versions", totalVersions);
    summary.put("reporting_count", totalReportings);
    summary.put("delayed_orders", delayedOrders);
    summary.put("avg_capacity_factor", days > 0 ? store.round2(capacityFactorSum / days) : 1d);
    summary.put("daily_kpis", dailySummaries);
    store.simulationState.lastRunSummary = store.deepCopyMap(summary);

    store.appendAudit("SIMULATION", "SIM-RUN", "RUN_SIMULATION", operator, requestId, "娴犺法婀℃潻鎰攽鐎瑰本鍨氒妴");
    store.appendSimulationEvent(
      store.simulationState.currentDate.minusDays(1),
      "SIM_RUN_DONE",
      "\u4eff\u771f\u8fd0\u884c\u5b8c\u6210\u3002",
      requestId,
      Map.of(
        "days", days,
        "new_sales_orders", totalSales,
        "generated_versions", totalVersions
      )
    );

    Map<String, Object> out = new LinkedHashMap<>(summary);
    out.put("state", store.buildSimulationStateResponse(requestId));
    return out;
  }
}
