package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreScheduleProcessLoadSupport {
  private MvpStoreScheduleProcessLoadSupport() {}

  static List<Map<String, Object>> listScheduleDailyProcessLoad(MvpStoreScheduleLoadDomain store, String versionNo) {
    MvpDomain.ScheduleVersion schedule = store.getScheduleEntity(versionNo);

    Map<String, MvpDomain.ProcessConfig> processByCode = new HashMap<>();
    List<String> processOrder = new ArrayList<>();
    for (MvpDomain.ProcessConfig process : store.state.processes) {
      String processCode = store.normalizeCode(process.processCode);
      processByCode.put(processCode, process);
      processOrder.add(processCode);
    }

    Map<String, Integer> workersByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.workerPools) {
      workersByDateShiftProcess.put(
        row.date + "#" + row.shiftCode + "#" + store.normalizeCode(row.processCode),
        row.available
      );
    }
    Map<String, Integer> machinesByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.machinePools) {
      machinesByDateShiftProcess.put(
        row.date + "#" + row.shiftCode + "#" + store.normalizeCode(row.processCode),
        row.available
      );
    }
    Map<String, Integer> occupiedWorkersByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.initialWorkerOccupancy) {
      occupiedWorkersByDateShiftProcess.put(
        row.date + "#" + row.shiftCode + "#" + store.normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Integer> occupiedMachinesByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.initialMachineOccupancy) {
      occupiedMachinesByDateShiftProcess.put(
        row.date + "#" + row.shiftCode + "#" + store.normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }

    Set<String> scheduleDates = new HashSet<>();
    for (Map<String, Object> shift : schedule.shifts) {
      String date = store.string(shift, "date", null);
      if (date != null && !date.isBlank()) {
        scheduleDates.add(date);
      }
    }
    if (scheduleDates.isEmpty()) {
      for (MvpDomain.ShiftRow shift : store.state.shiftCalendar) {
        scheduleDates.add(shift.date.toString());
      }
    }

    Map<String, Double> scheduledQtyByDateProcess = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      if (allocation.date == null || allocation.date.isBlank()) {
        continue;
      }
      String date = allocation.date;
      if (!scheduleDates.contains(date)) {
        continue;
      }
      String processCode = store.normalizeCode(allocation.processCode);
      if (processCode.isBlank()) {
        continue;
      }
      scheduledQtyByDateProcess.merge(date + "#" + processCode, allocation.scheduledQty, Double::sum);
    }

    Map<String, Double> maxCapacityByDateProcess = new HashMap<>();
    Map<String, Integer> openShiftCountByDateProcess = new HashMap<>();
    for (MvpDomain.ShiftRow shift : store.state.shiftCalendar) {
      String date = shift.date.toString();
      if (!scheduleDates.contains(date) || !shift.open) {
        continue;
      }
      for (String processCode : processOrder) {
        MvpDomain.ProcessConfig process = processByCode.get(processCode);
        if (process == null) {
          continue;
        }
        String usageKey = date + "#" + shift.shiftCode + "#" + processCode;
        int workersAvailable = workersByDateShiftProcess.getOrDefault(usageKey, 0);
        int machinesAvailable = machinesByDateShiftProcess.getOrDefault(usageKey, 0);
        int occupiedWorkers = occupiedWorkersByDateShiftProcess.getOrDefault(usageKey, 0);
        int occupiedMachines = occupiedMachinesByDateShiftProcess.getOrDefault(usageKey, 0);
        int effectiveWorkers = Math.max(0, workersAvailable - occupiedWorkers);
        int effectiveMachines = Math.max(0, machinesAvailable - occupiedMachines);
        int groupsByWorkers = effectiveWorkers / Math.max(1, process.requiredWorkers);
        int groupsByMachines = effectiveMachines / Math.max(1, process.requiredMachines);
        int maxGroups = Math.min(groupsByWorkers, groupsByMachines);
        double shiftCapacity = Math.max(0, maxGroups) * process.capacityPerShift;
        String dayProcessKey = date + "#" + processCode;
        maxCapacityByDateProcess.merge(dayProcessKey, shiftCapacity, Double::sum);
        openShiftCountByDateProcess.merge(dayProcessKey, 1, Integer::sum);
      }
    }

    List<String> dates = new ArrayList<>(scheduleDates);
    dates.sort(String::compareTo);

    List<Map<String, Object>> rows = new ArrayList<>();
    for (String date : dates) {
      for (String processCode : processOrder) {
        String key = date + "#" + processCode;
        double scheduledQty = store.round2(scheduledQtyByDateProcess.getOrDefault(key, 0d));
        double maxCapacityQty = store.round2(maxCapacityByDateProcess.getOrDefault(key, 0d));
        if (scheduledQty <= 1e-9 && maxCapacityQty <= 1e-9) {
          continue;
        }
        double loadRate = maxCapacityQty > 1e-9
          ? store.round2(Math.max(0d, (scheduledQty / maxCapacityQty) * 100d))
          : (scheduledQty > 1e-9 ? 100d : 0d);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version_no", schedule.versionNo);
        row.put("calendar_date", date);
        row.put("process_code", processCode);
        row.put("scheduled_qty", scheduledQty);
        row.put("max_capacity_qty", maxCapacityQty);
        row.put("load_rate", loadRate);
        row.put("open_shift_count", openShiftCountByDateProcess.getOrDefault(key, 0));
        rows.add(store.localizeRow(row));
      }
    }
    return rows;
  }

  static List<Map<String, Object>> listScheduleShiftProcessLoad(MvpStoreScheduleLoadDomain store, String versionNo) {
    MvpDomain.ScheduleVersion schedule = store.getScheduleEntity(versionNo);

    Map<String, MvpDomain.ProcessConfig> processByCode = new HashMap<>();
    List<String> processOrder = new ArrayList<>();
    for (MvpDomain.ProcessConfig process : store.state.processes) {
      String processCode = store.normalizeCode(process.processCode);
      if (processCode.isBlank()) {
        continue;
      }
      processByCode.put(processCode, process);
      processOrder.add(processCode);
    }

    Map<String, Integer> workersByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.workerPools) {
      workersByDateShiftProcess.put(
        row.date + "#" + store.normalizeShiftCode(row.shiftCode) + "#" + store.normalizeCode(row.processCode),
        row.available
      );
    }
    Map<String, Integer> machinesByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.machinePools) {
      machinesByDateShiftProcess.put(
        row.date + "#" + store.normalizeShiftCode(row.shiftCode) + "#" + store.normalizeCode(row.processCode),
        row.available
      );
    }
    Map<String, Integer> occupiedWorkersByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.initialWorkerOccupancy) {
      occupiedWorkersByDateShiftProcess.put(
        row.date + "#" + store.normalizeShiftCode(row.shiftCode) + "#" + store.normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Integer> occupiedMachinesByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.initialMachineOccupancy) {
      occupiedMachinesByDateShiftProcess.put(
        row.date + "#" + store.normalizeShiftCode(row.shiftCode) + "#" + store.normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }

    Set<String> scheduleDateShifts = new HashSet<>();
    for (Map<String, Object> shift : schedule.shifts) {
      String date = store.string(shift, "date", null);
      String shiftCode = store.normalizeShiftCode(store.string(shift, "shiftCode", null));
      if (date == null || date.isBlank() || shiftCode.isBlank()) {
        continue;
      }
      scheduleDateShifts.add(date + "#" + shiftCode);
    }
    if (scheduleDateShifts.isEmpty()) {
      for (MvpDomain.ShiftRow shift : store.state.shiftCalendar) {
        if (!shift.open) {
          continue;
        }
        String shiftCode = store.normalizeShiftCode(shift.shiftCode);
        if (shiftCode.isBlank()) {
          continue;
        }
        scheduleDateShifts.add(shift.date + "#" + shiftCode);
      }
    }

    Map<String, Double> scheduledQtyByDateShiftProcess = new HashMap<>();
    for (MvpDomain.Allocation allocation : schedule.allocations) {
      if (allocation.date == null || allocation.date.isBlank()) {
        continue;
      }
      String shiftCode = store.normalizeShiftCode(allocation.shiftCode);
      if (shiftCode.isBlank()) {
        continue;
      }
      String dateShiftKey = allocation.date + "#" + shiftCode;
      if (!scheduleDateShifts.contains(dateShiftKey)) {
        continue;
      }
      String processCode = store.normalizeCode(allocation.processCode);
      if (processCode.isBlank()) {
        continue;
      }
      scheduledQtyByDateShiftProcess.merge(dateShiftKey + "#" + processCode, allocation.scheduledQty, Double::sum);
    }

    Map<String, Double> maxCapacityByDateShiftProcess = new HashMap<>();
    Map<String, Integer> maxGroupsByDateShiftProcess = new HashMap<>();
    for (MvpDomain.ShiftRow shift : store.state.shiftCalendar) {
      if (!shift.open) {
        continue;
      }
      String date = shift.date.toString();
      String shiftCode = store.normalizeShiftCode(shift.shiftCode);
      if (shiftCode.isBlank()) {
        continue;
      }
      String dateShiftKey = date + "#" + shiftCode;
      if (!scheduleDateShifts.contains(dateShiftKey)) {
        continue;
      }
      for (String processCode : processOrder) {
        MvpDomain.ProcessConfig process = processByCode.get(processCode);
        if (process == null) {
          continue;
        }
        String usageKey = dateShiftKey + "#" + processCode;
        int workersAvailable = workersByDateShiftProcess.getOrDefault(usageKey, 0);
        int machinesAvailable = machinesByDateShiftProcess.getOrDefault(usageKey, 0);
        int occupiedWorkers = occupiedWorkersByDateShiftProcess.getOrDefault(usageKey, 0);
        int occupiedMachines = occupiedMachinesByDateShiftProcess.getOrDefault(usageKey, 0);
        int effectiveWorkers = Math.max(0, workersAvailable - occupiedWorkers);
        int effectiveMachines = Math.max(0, machinesAvailable - occupiedMachines);
        int groupsByWorkers = effectiveWorkers / Math.max(1, process.requiredWorkers);
        int groupsByMachines = effectiveMachines / Math.max(1, process.requiredMachines);
        int maxGroups = Math.max(0, Math.min(groupsByWorkers, groupsByMachines));
        maxCapacityByDateShiftProcess.put(usageKey, Math.max(0, maxGroups) * process.capacityPerShift);
        maxGroupsByDateShiftProcess.put(usageKey, maxGroups);
      }
    }

    List<String> orderedDateShifts = new ArrayList<>(scheduleDateShifts);
    orderedDateShifts.sort((left, right) -> {
      String[] leftParts = left.split("#", 2);
      String[] rightParts = right.split("#", 2);
      String leftDate = leftParts.length > 0 ? leftParts[0] : "";
      String rightDate = rightParts.length > 0 ? rightParts[0] : "";
      int byDate = leftDate.compareTo(rightDate);
      if (byDate != 0) {
        return byDate;
      }
      String leftShift = leftParts.length > 1 ? leftParts[1] : "";
      String rightShift = rightParts.length > 1 ? rightParts[1] : "";
      int byShift = Integer.compare(store.shiftSortIndex(leftShift), store.shiftSortIndex(rightShift));
      if (byShift != 0) {
        return byShift;
      }
      return leftShift.compareTo(rightShift);
    });

    List<Map<String, Object>> rows = new ArrayList<>();
    for (String dateShift : orderedDateShifts) {
      String[] parts = dateShift.split("#", 2);
      String date = parts.length > 0 ? parts[0] : "";
      String shiftCode = parts.length > 1 ? parts[1] : "";
      if (date.isBlank() || shiftCode.isBlank()) {
        continue;
      }
      for (String processCode : processOrder) {
        String key = dateShift + "#" + processCode;
        double scheduledQty = store.round2(scheduledQtyByDateShiftProcess.getOrDefault(key, 0d));
        double maxCapacityQty = store.round2(maxCapacityByDateShiftProcess.getOrDefault(key, 0d));
        if (scheduledQty <= 1e-9 && maxCapacityQty <= 1e-9) {
          continue;
        }
        double loadRate = maxCapacityQty > 1e-9
          ? store.round2(Math.max(0d, (scheduledQty / maxCapacityQty) * 100d))
          : (scheduledQty > 1e-9 ? 100d : 0d);
        MvpDomain.ProcessConfig process = processByCode.get(processCode);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version_no", schedule.versionNo);
        row.put("calendar_date", date);
        row.put("shift_code", store.normalizeShiftCodeLabel(shiftCode));
        row.put("process_code", processCode);
        row.put("scheduled_qty", scheduledQty);
        row.put("max_capacity_qty", maxCapacityQty);
        row.put("load_rate", loadRate);
        row.put("capacity_per_shift", process == null ? 0d : store.round2(process.capacityPerShift));
        row.put("required_workers", process == null ? 0 : process.requiredWorkers);
        row.put("required_machines", process == null ? 0 : process.requiredMachines);
        int grossWorkers = workersByDateShiftProcess.getOrDefault(key, 0);
        int grossMachines = machinesByDateShiftProcess.getOrDefault(key, 0);
        int occupiedWorkers = occupiedWorkersByDateShiftProcess.getOrDefault(key, 0);
        int occupiedMachines = occupiedMachinesByDateShiftProcess.getOrDefault(key, 0);
        row.put("available_workers", Math.max(0, grossWorkers - occupiedWorkers));
        row.put("available_machines", Math.max(0, grossMachines - occupiedMachines));
        row.put("occupied_workers", occupiedWorkers);
        row.put("occupied_machines", occupiedMachines);
        row.put("gross_workers", grossWorkers);
        row.put("gross_machines", grossMachines);
        row.put("max_group_count", maxGroupsByDateShiftProcess.getOrDefault(key, 0));
        rows.add(store.localizeRow(row));
      }
    }
    return rows;
  }
}

