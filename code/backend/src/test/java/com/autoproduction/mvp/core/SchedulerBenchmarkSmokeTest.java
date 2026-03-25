package com.autoproduction.mvp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SchedulerBenchmarkSmokeTest {

  @Test
  void benchmarkGenerateScheduleAtCommonScales() {
    Assumptions.assumeTrue(
      Boolean.getBoolean("mvp.benchmark"),
      "Set -Dmvp.benchmark=true to run benchmark baseline."
    );

    List<Integer> scales = parseScales(System.getProperty("mvp.benchmark.scales", "100,1000,5000"));
    Map<Integer, Long> thresholds = parseThresholds(System.getProperty("mvp.benchmark.thresholds", ""));
    assertTrue(!scales.isEmpty(), "benchmark scales should not be empty");
    Map<Integer, Long> durationByScale = new LinkedHashMap<>();
    for (Integer scale : scales) {
      int orderCount = scale;
      MvpDomain.State state = buildScaledState(orderCount);
      long start = System.nanoTime();
      MvpDomain.ScheduleVersion version = SchedulerEngine.generate(
        state,
        state.orders,
        "bench-" + orderCount,
        "VBENCH-" + orderCount
      );
      long durationMs = (System.nanoTime() - start) / 1_000_000L;

      durationByScale.put(orderCount, durationMs);
      assertEquals(orderCount, state.orders.size());
      assertTrue(version.tasks.size() >= orderCount, "task volume should scale with order volume");
      assertTrue(version.metrics.containsKey("schedule_completion_rate"));
      Long thresholdMs = thresholds.get(orderCount);
      if (thresholdMs != null) {
        assertTrue(
          durationMs <= thresholdMs,
          "benchmark threshold exceeded at orders=%d, duration_ms=%d, threshold_ms=%d".formatted(
              orderCount,
              durationMs,
              thresholdMs
            )
        );
      }
      System.out.printf(
        "[BENCH] orders=%d duration_ms=%d tasks=%d allocations=%d unscheduled=%d%n",
        orderCount,
        durationMs,
        version.tasks.size(),
        version.allocations.size(),
        version.unscheduled.size()
      );
    }

    StringBuilder summary = new StringBuilder("[BENCH] summary p95_proxy_ms:");
    for (Map.Entry<Integer, Long> entry : durationByScale.entrySet()) {
      summary.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
    }
    System.out.println(summary);
  }

  private static List<Integer> parseScales(String raw) {
    List<Integer> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return out;
    }
    for (String part : raw.split(",")) {
      String value = part.trim();
      if (value.isBlank()) {
        continue;
      }
      out.add(Integer.parseInt(value));
    }
    return out;
  }

  private static Map<Integer, Long> parseThresholds(String raw) {
    Map<Integer, Long> out = new LinkedHashMap<>();
    if (raw == null || raw.isBlank()) {
      return out;
    }
    for (String pair : raw.split(",")) {
      String text = pair.trim();
      if (text.isBlank() || !text.contains("=")) {
        continue;
      }
      String[] parts = text.split("=", 2);
      out.put(Integer.parseInt(parts[0].trim()), Long.parseLong(parts[1].trim()));
    }
    return out;
  }

  private static MvpDomain.State buildScaledState(int orderCount) {
    MvpDomain.State seed = SeedDataFactory.build();
    MvpDomain.State scaled = new MvpDomain.State();
    scaled.startDate = seed.startDate;
    scaled.horizonDays = seed.horizonDays;
    scaled.shiftsPerDay = seed.shiftsPerDay;
    scaled.shiftHours = seed.shiftHours;
    scaled.strictRoute = seed.strictRoute;
    scaled.processes = copyProcesses(seed.processes);
    scaled.processRoutes = copyRoutes(seed.processRoutes);
    scaled.shiftCalendar = copyShiftCalendar(seed.shiftCalendar);
    scaled.workerPools = copyResourceRows(seed.workerPools);
    scaled.machinePools = copyResourceRows(seed.machinePools);
    scaled.materialAvailability = copyMaterialRows(seed.materialAvailability);
    scaled.orders = expandOrders(seed.orders, orderCount);
    return scaled;
  }

  private static List<MvpDomain.Order> expandOrders(List<MvpDomain.Order> seedOrders, int orderCount) {
    List<MvpDomain.Order> out = new ArrayList<>();
    for (int i = 0; i < orderCount; i += 1) {
      MvpDomain.Order template = seedOrders.get(i % seedOrders.size());
      String suffix = String.format("%05d", i + 1);
      List<MvpDomain.OrderItem> items = new ArrayList<>();
      for (MvpDomain.OrderItem item : template.items) {
        double qtyFactor = 0.8d + ((i % 5) * 0.1d);
        items.add(new MvpDomain.OrderItem(item.productCode, round3(item.qty * qtyFactor), item.completedQty));
      }

      MvpDomain.OrderBusinessData businessData = new MvpDomain.OrderBusinessData(template.businessData);
      businessData.productionBatchNo = template.orderNo + "-B-" + suffix;
      businessData.salesOrderNo = template.orderNo + "-SO-" + suffix;
      businessData.marketDemand = items.stream().mapToDouble(v -> v.qty).sum();
      businessData.semiFinishedDemand = businessData.marketDemand;
      businessData.needOrderQty = Math.max(
        0d,
        businessData.semiFinishedDemand - businessData.semiFinishedInventory - businessData.semiFinishedWip
      );

      boolean urgent = i % 10 == 0 || template.urgent;
      boolean frozen = i % 37 == 0 && !urgent;
      boolean locked = !frozen && i % 53 == 0;
      out.add(new MvpDomain.Order(
        template.orderNo + "-" + suffix,
        template.orderType,
        template.dueDate.plusDays(i % 7),
        template.expectedStartDate == null ? null : template.expectedStartDate.plusDays(i % 3),
        urgent,
        frozen,
        locked,
        template.status,
        items,
        businessData
      ));
    }
    return out;
  }

  private static List<MvpDomain.ProcessConfig> copyProcesses(List<MvpDomain.ProcessConfig> rows) {
    List<MvpDomain.ProcessConfig> out = new ArrayList<>();
    for (MvpDomain.ProcessConfig row : rows) {
      out.add(new MvpDomain.ProcessConfig(row.processCode, row.capacityPerShift, row.requiredWorkers, row.requiredMachines));
    }
    return out;
  }

  private static Map<String, List<MvpDomain.ProcessStep>> copyRoutes(Map<String, List<MvpDomain.ProcessStep>> routes) {
    Map<String, List<MvpDomain.ProcessStep>> out = new LinkedHashMap<>();
    for (Map.Entry<String, List<MvpDomain.ProcessStep>> entry : routes.entrySet()) {
      List<MvpDomain.ProcessStep> steps = new ArrayList<>();
      for (MvpDomain.ProcessStep step : entry.getValue()) {
        steps.add(new MvpDomain.ProcessStep(step.processCode, step.dependencyType));
      }
      out.put(entry.getKey(), steps);
    }
    return out;
  }

  private static List<MvpDomain.ShiftRow> copyShiftCalendar(List<MvpDomain.ShiftRow> rows) {
    List<MvpDomain.ShiftRow> out = new ArrayList<>();
    for (MvpDomain.ShiftRow row : rows) {
      out.add(new MvpDomain.ShiftRow(row.date, row.shiftCode, row.open));
    }
    return out;
  }

  private static List<MvpDomain.ResourceRow> copyResourceRows(List<MvpDomain.ResourceRow> rows) {
    List<MvpDomain.ResourceRow> out = new ArrayList<>();
    for (MvpDomain.ResourceRow row : rows) {
      out.add(new MvpDomain.ResourceRow(row.date, row.shiftCode, row.processCode, row.available));
    }
    return out;
  }

  private static List<MvpDomain.MaterialRow> copyMaterialRows(List<MvpDomain.MaterialRow> rows) {
    List<MvpDomain.MaterialRow> out = new ArrayList<>();
    for (MvpDomain.MaterialRow row : rows) {
      out.add(new MvpDomain.MaterialRow(row.date, row.shiftCode, row.productCode, row.processCode, row.availableQty));
    }
    return out;
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }
}
