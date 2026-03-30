package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.List;

final class MvpStoreEntityMappingEstimationSupport {
  private MvpStoreEntityMappingEstimationSupport() {}

  static LocalDate resolveExpectedStartDate(MvpStoreEntityMappingSupport store, MvpDomain.Order order) {
    if (order == null) {
      return store.state.startDate;
    }
    if (order.expectedStartDate != null) {
      return order.expectedStartDate;
    }
    MvpDomain.OrderBusinessData business = store.businessData(order);
    if (business.orderDate != null) {
      if (store.state.startDate == null) {
        return business.orderDate;
      }
      return business.orderDate.isBefore(store.state.startDate) ? store.state.startDate : business.orderDate;
    }
    if (store.state.startDate != null) {
      return store.state.startDate;
    }
    return order.dueDate;
  }

  static String estimateExpectedFinishTime(MvpStoreEntityMappingSupport store, MvpDomain.Order order) {
    LocalDate startDate = store.resolveExpectedStartDate(order);
    if (startDate == null) {
      return null;
    }
    int requiredShifts = store.estimateRequiredShifts(order);
    int shiftsPerDay = Math.max(1, Math.min(2, store.state.shiftsPerDay));
    int finishShiftIndex = Math.max(0, requiredShifts <= 0 ? 0 : requiredShifts - 1);
    LocalDate finishDate = startDate.plusDays(finishShiftIndex / shiftsPerDay);
    String finishShiftCode = shiftsPerDay == 1 ? "D" : (finishShiftIndex % shiftsPerDay == 0 ? "D" : "N");
    return MvpStoreRuntimeBase.toDateTime(finishDate.toString(), finishShiftCode, false);
  }

  static int estimateRequiredShifts(MvpStoreEntityMappingSupport store, MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return 0;
    }
    int totalShifts = 0;
    for (MvpDomain.OrderItem item : order.items) {
      double remainingQty = Math.max(0d, item.qty - item.completedQty);
      if (remainingQty <= 1e-9d) {
        continue;
      }
      List<MvpDomain.ProcessStep> route = store.state.processRoutes.getOrDefault(item.productCode, List.of());
      if (route.isEmpty()) {
        totalShifts += Math.max(1, (int) Math.ceil(remainingQty / 1000d));
        continue;
      }
      for (MvpDomain.ProcessStep step : route) {
        double shiftCapacity = store.estimateProcessCapacityPerShift(step.processCode);
        double normalizedCapacity = shiftCapacity <= 1e-9d ? 1d : shiftCapacity;
        totalShifts += Math.max(1, (int) Math.ceil(remainingQty / normalizedCapacity));
      }
    }
    return totalShifts;
  }

  static double estimateProcessCapacityPerShift(MvpStoreEntityMappingSupport store, String processCode) {
    String normalizedProcessCode = MvpStoreCoreNormalizationSupport.normalizeCode(processCode);
    if (normalizedProcessCode.isBlank()) {
      return 0d;
    }
    MvpDomain.ProcessConfig config = store.state.processes.stream()
      .filter(row -> normalizedProcessCode.equals(MvpStoreCoreNormalizationSupport.normalizeCode(row.processCode)))
      .findFirst()
      .orElse(null);
    if (config == null) {
      return 0d;
    }

    int requiredWorkers = Math.max(1, config.requiredWorkers);
    int requiredMachines = Math.max(1, config.requiredMachines);
    int maxWorkers = store.maxResourceForProcess(store.state.workerPools, normalizedProcessCode);
    int maxMachines = store.maxResourceForProcess(store.state.machinePools, normalizedProcessCode);
    if (maxWorkers <= 0) {
      maxWorkers = requiredWorkers;
    }
    if (maxMachines <= 0) {
      maxMachines = requiredMachines;
    }
    int groupsByWorkers = Math.max(1, maxWorkers / requiredWorkers);
    int groupsByMachines = Math.max(1, maxMachines / requiredMachines);
    int groups = Math.max(1, Math.min(groupsByWorkers, groupsByMachines));
    return MvpStoreCoreNormalizationSupport.round2(config.capacityPerShift * groups);
  }

  static int maxResourceForProcess(MvpStoreEntityMappingSupport store, List<MvpDomain.ResourceRow> rows, String processCode) {
    int max = 0;
    for (MvpDomain.ResourceRow row : rows) {
      if (MvpStoreCoreNormalizationSupport.normalizeCode(row.processCode).equals(processCode)) {
        max = Math.max(max, Math.max(0, row.available));
      }
    }
    return max;
  }

  static String orderPrimaryProductCode(MvpStoreEntityMappingSupport store, MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return "UNKNOWN";
    }
    return MvpStoreCoreNormalizationSupport.normalizeCode(order.items.get(0).productCode);
  }
}

