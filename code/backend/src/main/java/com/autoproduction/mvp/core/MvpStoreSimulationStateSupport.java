package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MvpStoreSimulationStateSupport {
  private MvpStoreSimulationStateSupport() {}

  static void resetSimulationState(MvpStoreSimulationEngineSupport domain, long seed, String scenario, int dailySales) {
    domain.simulationState.currentDate = domain.state.startDate;
    domain.simulationState.seed = seed;
    domain.simulationState.scenario = MvpStoreCoreNormalizationSupport.normalizeScenario(scenario);
    domain.simulationState.dailySalesOrderCount = dailySales;
    domain.simulationState.salesOrders.clear();
    domain.simulationState.events.clear();
    domain.simulationState.lastRunSummary = new LinkedHashMap<>();
  }

  static Map<String, Object> buildSimulationStateResponse(MvpStoreSimulationEngineSupport domain, String requestId) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("request_id", requestId);
    out.put("current_sim_date", domain.simulationState.currentDate.toString());
    out.put("seed", domain.simulationState.seed);
    out.put("scenario", domain.simulationState.scenario);
    out.put("scenario_name_cn", MvpStoreRuntimeBase.scenarioNameCn(domain.simulationState.scenario));
    out.put("daily_sales_order_count", domain.simulationState.dailySalesOrderCount);
    out.put("sales_order_total", domain.simulationState.salesOrders.size());
    out.put("production_order_total", domain.state.orders.size());
    out.put("order_pool_total", domain.state.orders.stream().filter(order -> !"DONE".equals(order.status)).count());
    out.put("event_total", domain.simulationState.events.size());
    out.put("open_alert_total", domain.state.alerts.stream().filter(row -> Objects.equals("OPEN", row.get("status"))).count());
    out.put("latest_version_no", domain.state.schedules.isEmpty() ? null : domain.state.schedules.get(domain.state.schedules.size() - 1).versionNo);
    out.put("published_version_no", domain.state.publishedVersionNo);
    out.put("manual_session_active", domain.manualSimulationSnapshot != null);
    out.put("manual_snapshot_at", domain.manualSimulationSnapshot == null ? null : domain.manualSimulationSnapshot.snapshotAt);
    out.put("last_run_summary", domain.deepCopyMap(domain.simulationState.lastRunSummary));
    return out;
  }

  static void appendSimulationEvent(
    MvpStoreSimulationEngineSupport domain,
    LocalDate date,
    String eventType,
    String message,
    String requestId,
    Map<String, Object> details
  ) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("event_id", "SIM-EVT-%06d".formatted(domain.simulationEventSeq.incrementAndGet()));
    row.put("event_date", date.toString());
    row.put("event_type", eventType);
    row.put("event_type_name_cn", MvpStoreRuntimeBase.eventTypeNameCn(eventType));
    row.put("message", message);
    row.put("message_cn", message);
    row.put("request_id", requestId);
    row.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
    if (details != null) {
      row.putAll(details);
    }
    domain.simulationState.events.add(domain.localizeRow(row));
  }
}

