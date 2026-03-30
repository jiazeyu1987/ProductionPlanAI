package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class MvpStoreSimulationReportingSupport {
  private MvpStoreSimulationReportingSupport() {}

  static int simulateDailyReporting(
    MvpStoreSimulationEngineSupport domain,
    LocalDate businessDate,
    String versionNo,
    String scenario,
    Random random,
    String requestIdPrefix
  ) {
    MvpDomain.ScheduleVersion schedule = domain.getScheduleEntity(versionNo);
    Map<String, Map<String, Double>> plannedByOrderProcess = new LinkedHashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      String orderNo = allocation.orderNo;
      String processCode = MvpStoreCoreNormalizationSupport.normalizeCode(allocation.processCode);
      if (orderNo == null || orderNo.isBlank() || processCode.isBlank()) {
        continue;
      }
      plannedByOrderProcess
        .computeIfAbsent(orderNo, key -> new LinkedHashMap<>())
        .merge(processCode, allocation.scheduledQty, Double::sum);
    }

    Map<String, Double> cumulativeReportedByOrderProcess = new HashMap<>();
    for (MvpDomain.Reporting reporting : domain.state.reportings) {
      String key = MvpStoreCoreNormalizationSupport.orderProcessKey(reporting.orderNo, reporting.processCode);
      cumulativeReportedByOrderProcess.merge(key, reporting.reportQty, Double::sum);
    }

    int reportingCount = 0;
    for (Map.Entry<String, Map<String, Double>> orderEntry : plannedByOrderProcess.entrySet()) {
      String orderNo = orderEntry.getKey();
      Map<String, Double> processPlan = orderEntry.getValue();
      MvpDomain.Order order = domain.state.orders.stream().filter(it -> it.orderNo.equals(orderNo)).findFirst().orElse(null);
      if (order == null || order.items.isEmpty()) {
        continue;
      }
      double orderQty = order.items.stream().mapToDouble(item -> item.qty).sum();
      if (orderQty <= 0d) {
        continue;
      }
      String productCode = order.items.get(0).productCode;

      List<MvpDomain.ProcessStep> route = domain.state.processRoutes.getOrDefault(productCode, List.of());
      Set<String> handled = new HashSet<>();
      String predecessorProcessCode = null;

      for (MvpDomain.ProcessStep step : route) {
        String processCode = MvpStoreCoreNormalizationSupport.normalizeCode(step.processCode);
        if (processCode.isBlank()) {
          continue;
        }
        double plannedQty = processPlan.getOrDefault(processCode, 0d);
        if (plannedQty <= 0d) {
          plannedQty = orderQty;
        }
        String requestId = requestIdPrefix + ":" + reportingCount;
        if (
          createSimulatedReporting(
            domain,
            orderNo,
            productCode,
            processCode,
            plannedQty,
            orderQty,
            predecessorProcessCode,
            scenario,
            random,
            requestId,
            cumulativeReportedByOrderProcess
          )
        ) {
          reportingCount += 1;
        }
        handled.add(processCode);
        predecessorProcessCode = processCode;
      }

      for (Map.Entry<String, Double> processEntry : processPlan.entrySet()) {
        String processCode = processEntry.getKey();
        if (handled.contains(processCode)) {
          continue;
        }
        String requestId = requestIdPrefix + ":" + reportingCount;
        if (
          createSimulatedReporting(
            domain,
            orderNo,
            productCode,
            processCode,
            processEntry.getValue(),
            orderQty,
            null,
            scenario,
            random,
            requestId,
            cumulativeReportedByOrderProcess
          )
        ) {
          reportingCount += 1;
        }
      }
    }

    domain.appendSimulationEvent(
      businessDate,
      "EXECUTION_PROGRESS",
      "已根据仿真执行结果自动生成报工。",
      requestIdPrefix,
      Map.of("reporting_count", reportingCount, "version_no", versionNo)
    );
    return reportingCount;
  }

  static boolean createSimulatedReporting(
    MvpStoreSimulationEngineSupport domain,
    String orderNo,
    String productCode,
    String processCode,
    double plannedQty,
    double orderQty,
    String predecessorProcessCode,
    String scenario,
    Random random,
    String requestId,
    Map<String, Double> cumulativeReportedByOrderProcess
  ) {
    String normalizedProcessCode = MvpStoreCoreNormalizationSupport.normalizeCode(processCode);
    if (normalizedProcessCode.isBlank() || plannedQty <= 0d || orderQty <= 0d) {
      return false;
    }

    double executionRate = MvpStoreCoreNormalizationSupport.scenarioExecutionRate(scenario, random);
    double simulatedQty = Math.round(plannedQty * executionRate);
    if (simulatedQty <= 0d) {
      return false;
    }

    String processKey = MvpStoreCoreNormalizationSupport.orderProcessKey(orderNo, normalizedProcessCode);
    double alreadyReported = cumulativeReportedByOrderProcess.getOrDefault(processKey, 0d);
    double remainingByOrder = Math.max(0d, orderQty - alreadyReported);
    if (remainingByOrder <= 0d) {
      return false;
    }

    double cappedQty = Math.min(simulatedQty, remainingByOrder);
    if (predecessorProcessCode != null && !predecessorProcessCode.isBlank()) {
      String predecessorKey = MvpStoreCoreNormalizationSupport.orderProcessKey(orderNo, predecessorProcessCode);
      double predecessorReported = cumulativeReportedByOrderProcess.getOrDefault(predecessorKey, 0d);
      double availableWip = Math.max(0d, predecessorReported - alreadyReported);
      cappedQty = Math.min(cappedQty, availableWip);
    }

    long reportQty = (long) Math.floor(cappedQty);
    if (reportQty <= 0L) {
      return false;
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("order_no", orderNo);
    payload.put("product_code", productCode);
    payload.put("process_code", normalizedProcessCode);
    payload.put("report_qty", reportQty);
    domain.recordReporting(payload, requestId, "simulator");
    cumulativeReportedByOrderProcess.put(processKey, alreadyReported + reportQty);
    return true;
  }
}

