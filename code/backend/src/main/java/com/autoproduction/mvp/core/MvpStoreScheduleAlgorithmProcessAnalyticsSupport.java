package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreScheduleAlgorithmProcessAnalyticsSupport {
  private MvpStoreScheduleAlgorithmProcessAnalyticsSupport() {}

  record Analytics(
    List<Map<String, Object>> processSummary,
    List<Map<String, Object>> unscheduledReasonSummary,
    List<Map<String, Object>> unscheduledSamples
  ) {}

  static Analytics buildAnalytics(MvpStoreScheduleAlgorithmDomain store, MvpDomain.ScheduleVersion schedule) {
    Map<String, Integer> shiftIndexByShiftId = new HashMap<>();
    for (int i = 0; i < schedule.shifts.size(); i += 1) {
      Map<String, Object> shift = schedule.shifts.get(i);
      String shiftId = store.string(shift, "shiftId", null);
      if (shiftId != null && !shiftId.isBlank()) {
        shiftIndexByShiftId.put(shiftId, i);
      }
    }

    Map<String, MvpDomain.ScheduleTask> taskByTaskKey = new HashMap<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      taskByTaskKey.put(task.taskKey, task);
    }

    Map<String, MvpDomain.ProcessConfig> processConfigByCode = new HashMap<>();
    for (MvpDomain.ProcessConfig process : store.state.processes) {
      processConfigByCode.put(store.normalizeCode(process.processCode), process);
    }

    Map<String, Integer> workersAvailableByShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.workerPools) {
      workersAvailableByShiftProcess.put(row.date + "#" + row.shiftCode + "#" + store.normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> machinesAvailableByShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.machinePools) {
      machinesAvailableByShiftProcess.put(row.date + "#" + row.shiftCode + "#" + store.normalizeCode(row.processCode), row.available);
    }
    Map<String, Double> materialAvailableByShiftProductProcess = new HashMap<>();
    for (MvpDomain.MaterialRow row : store.state.materialAvailability) {
      materialAvailableByShiftProductProcess.put(
        row.date + "#" + row.shiftCode + "#" + row.productCode + "#" + store.normalizeCode(row.processCode),
        row.availableQty
      );
    }

    Map<String, Double> targetQtyByProcess = new HashMap<>();
    Map<String, Set<String>> orderSetByProcess = new HashMap<>();
    for (MvpDomain.ScheduleTask task : schedule.tasks) {
      String processCode = store.normalizeCode(task.processCode);
      if (processCode.isBlank()) {
        continue;
      }
      targetQtyByProcess.merge(processCode, task.targetQty, Double::sum);
      orderSetByProcess.computeIfAbsent(processCode, key -> new HashSet<>()).add(task.orderNo);
    }

    Map<String, Double> scheduledQtyByProcess = new HashMap<>();
    Map<String, MvpDomain.Allocation> maxAllocationByProcess = new HashMap<>();
    Map<String, List<MvpDomain.Allocation>> allocationsByTaskKey = new HashMap<>();
    Map<String, Double> totalScheduledByTaskKey = new HashMap<>();
    Map<String, Double> maxAllocationQtyByProcess = new HashMap<>();
    Map<String, Integer> allocationCountByProcess = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      String processCode = store.normalizeCode(allocation.processCode);
      if (processCode.isBlank()) {
        continue;
      }
      scheduledQtyByProcess.merge(processCode, allocation.scheduledQty, Double::sum);
      maxAllocationQtyByProcess.merge(processCode, allocation.scheduledQty, Math::max);
      allocationCountByProcess.merge(processCode, 1, Integer::sum);
      allocationsByTaskKey.computeIfAbsent(allocation.taskKey, key -> new ArrayList<>()).add(allocation);
      totalScheduledByTaskKey.merge(allocation.taskKey, allocation.scheduledQty, Double::sum);
      MvpDomain.Allocation existing = maxAllocationByProcess.get(processCode);
      if (
        existing == null
          || allocation.scheduledQty > existing.scheduledQty + 1e-9
          || (
            Math.abs(allocation.scheduledQty - existing.scheduledQty) <= 1e-9
              && shiftIndexByShiftId.getOrDefault(allocation.shiftId, Integer.MAX_VALUE)
                < shiftIndexByShiftId.getOrDefault(existing.shiftId, Integer.MAX_VALUE)
          )
      ) {
        maxAllocationByProcess.put(processCode, allocation);
      }
    }

    Map<String, Integer> reasonCount = new HashMap<>();
    Map<String, Double> unscheduledQtyByProcess = new HashMap<>();
    Map<String, Map<String, Integer>> reasonCountByProcess = new HashMap<>();
    List<Map<String, Object>> unscheduledSamples = new ArrayList<>();
    for (Map<String, Object> row : schedule.unscheduled) {
      String processCode = store.normalizeCode(store.string(row, "processCode", ""));
      if (!processCode.isBlank()) {
        unscheduledQtyByProcess.merge(processCode, store.number(row, "remainingQty", 0d), Double::sum);
      }

      String reasonCode = store.resolveUnscheduledReasonCode(row);
      if (reasonCode == null || reasonCode.isBlank()) {
        reasonCode = "UNKNOWN";
      }
      reasonCount.merge(reasonCode, 1, Integer::sum);
      if (!processCode.isBlank()) {
        reasonCountByProcess.computeIfAbsent(processCode, key -> new HashMap<>()).merge(reasonCode, 1, Integer::sum);
      }

      if (unscheduledSamples.size() >= 10) {
        continue;
      }
      Map<String, Object> sample = new LinkedHashMap<>();
      sample.put("task_key", store.string(row, "taskKey", null));
      sample.put("order_no", store.string(row, "orderNo", null));
      sample.put("process_code", store.string(row, "processCode", null));
      sample.put("remaining_qty", store.round2(store.number(row, "remainingQty", 0d)));
      sample.put("reason_code", reasonCode);
      sample.put("reason_name_cn", store.scheduleReasonNameCn(reasonCode));
      sample.put("reason_detail", store.string(row, "reason_detail", null));
      unscheduledSamples.add(store.localizeRow(sample));
    }

    Set<String> processCodeSet = new HashSet<>();
    processCodeSet.addAll(targetQtyByProcess.keySet());
    processCodeSet.addAll(scheduledQtyByProcess.keySet());
    processCodeSet.addAll(unscheduledQtyByProcess.keySet());
    List<String> processCodes = new ArrayList<>(processCodeSet);
    processCodes.sort((a, b) -> {
      int byTarget = Double.compare(targetQtyByProcess.getOrDefault(b, 0d), targetQtyByProcess.getOrDefault(a, 0d));
      if (byTarget != 0) {
        return byTarget;
      }
      int byScheduled = Double.compare(scheduledQtyByProcess.getOrDefault(b, 0d), scheduledQtyByProcess.getOrDefault(a, 0d));
      if (byScheduled != 0) {
        return byScheduled;
      }
      return a.compareTo(b);
    });

    List<Map<String, Object>> processSummary = new ArrayList<>();
    for (String processCode : processCodes) {
      double targetQty = store.round2(targetQtyByProcess.getOrDefault(processCode, 0d));
      double scheduledQty = store.round2(scheduledQtyByProcess.getOrDefault(processCode, 0d));
      double unscheduledQty = store.round2(Math.max(unscheduledQtyByProcess.getOrDefault(processCode, 0d), targetQty - scheduledQty));
      double scheduleRate = targetQty > 1e-9 ? store.round2(Math.min(100d, Math.max(0d, (scheduledQty / targetQty) * 100d))) : 100d;
      int orderCount = orderSetByProcess.getOrDefault(processCode, Set.of()).size();
      String topReasonCode = store.topReasonCode(reasonCountByProcess.get(processCode));
      String topReasonCn = topReasonCode == null ? null : store.scheduleReasonNameCn(topReasonCode);
      MvpDomain.Allocation maxAllocation = maxAllocationByProcess.get(processCode);

      Map<String, Object> processRow = new LinkedHashMap<>();
      processRow.put("process_code", processCode);
      processRow.put("target_qty", targetQty);
      processRow.put("max_allocation_qty", store.round2(maxAllocationQtyByProcess.getOrDefault(processCode, 0d)));
      processRow.put("scheduled_qty", scheduledQty);
      processRow.put("unscheduled_qty", unscheduledQty);
      processRow.put("schedule_rate", scheduleRate);
      processRow.put("allocation_count", allocationCountByProcess.getOrDefault(processCode, 0));
      processRow.put("order_count", orderCount);
      processRow.put("top_block_reason_code", topReasonCode);
      processRow.put("top_block_reason_cn", topReasonCn);
      if (maxAllocation != null) {
        int workersAvailable = workersAvailableByShiftProcess.getOrDefault(
          maxAllocation.date + "#" + maxAllocation.shiftCode + "#" + processCode,
          0
        );
        int machinesAvailable = machinesAvailableByShiftProcess.getOrDefault(
          maxAllocation.date + "#" + maxAllocation.shiftCode + "#" + processCode,
          0
        );
        double materialAvailable = materialAvailableByShiftProductProcess.getOrDefault(
          maxAllocation.date + "#" + maxAllocation.shiftCode + "#" + maxAllocation.productCode + "#" + processCode,
          0d
        );
        MvpDomain.ProcessConfig processConfig = processConfigByCode.get(processCode);
        double resourceCapacity = store.estimateResourceCapacity(processConfig, workersAvailable, machinesAvailable);
        MvpDomain.ScheduleTask task = taskByTaskKey.get(maxAllocation.taskKey);
        double taskTargetQty = task == null ? 0d : task.targetQty;
        double producedBeforeShift = store.calcTaskProducedBeforeShift(
          maxAllocation,
          allocationsByTaskKey.get(maxAllocation.taskKey),
          shiftIndexByShiftId
        );
        double remainingBeforeShift = Math.max(0d, taskTargetQty - producedBeforeShift);

        processRow.put("max_allocation_task_key", maxAllocation.taskKey);
        processRow.put("max_allocation_order_no", maxAllocation.orderNo);
        processRow.put("max_allocation_product_code", maxAllocation.productCode);
        processRow.put("max_allocation_date", maxAllocation.date);
        processRow.put("max_allocation_shift_code", maxAllocation.shiftCode);
        processRow.put("max_allocation_shift_name_cn", store.shiftNameCn(maxAllocation.shiftCode));
        processRow.put("max_allocation_workers_used", maxAllocation.workersUsed);
        processRow.put("max_allocation_machines_used", maxAllocation.machinesUsed);
        processRow.put("max_allocation_groups_used", maxAllocation.groupsUsed);
        processRow.put("max_allocation_workers_available", workersAvailable);
        processRow.put("max_allocation_machines_available", machinesAvailable);
        processRow.put("max_allocation_resource_capacity", store.round2(resourceCapacity));
        processRow.put("max_allocation_material_available", store.round2(materialAvailable));
        processRow.put("max_allocation_task_target_qty", store.round2(taskTargetQty));
        processRow.put("max_allocation_task_produced_before_shift", store.round2(producedBeforeShift));
        processRow.put("max_allocation_task_remaining_before_shift", store.round2(remainingBeforeShift));
        processRow.put("max_allocation_task_total_scheduled", store.round2(totalScheduledByTaskKey.getOrDefault(maxAllocation.taskKey, 0d)));
        processRow.put(
          "max_allocation_explain_cn",
          store.buildMaxAllocationExplainCn(
            processCode,
            maxAllocation,
            remainingBeforeShift,
            resourceCapacity,
            materialAvailable
          )
        );
      }
      processRow.put(
        "explain_cn",
        store.buildProcessAllocationExplainCn(processCode, targetQty, scheduledQty, unscheduledQty, orderCount, topReasonCode)
      );
      processSummary.add(store.localizeRow(processRow));
    }

    List<Map<String, Object>> unscheduledReasonSummary = reasonCount.entrySet().stream()
      .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
      .map(entry -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reason_code", entry.getKey());
        row.put("reason_name_cn", store.scheduleReasonNameCn(entry.getKey()));
        row.put("count", entry.getValue());
        return row;
      })
      .toList();

    return new Analytics(processSummary, unscheduledReasonSummary, unscheduledSamples);
  }
}

