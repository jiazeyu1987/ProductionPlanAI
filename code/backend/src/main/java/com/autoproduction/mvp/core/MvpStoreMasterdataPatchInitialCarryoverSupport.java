package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreMasterdataPatchInitialCarryoverSupport {
  private MvpStoreMasterdataPatchInitialCarryoverSupport() {}

  static void applyInitialCarryoverPatch(MvpStoreMasterdataPatchSupport store, List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : store.state.processes) {
      validProcessCodes.add(store.normalizeCode(process.processCode));
    }

    Map<String, Integer> workerCapacityByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.workerPools) {
      workerCapacityByKey.put(
        row.date + "#" + store.normalizeShiftCode(row.shiftCode) + "#" + store.normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }
    Map<String, Integer> machineCapacityByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : store.state.machinePools) {
      machineCapacityByKey.put(
        row.date + "#" + store.normalizeShiftCode(row.shiftCode) + "#" + store.normalizeCode(row.processCode),
        Math.max(0, row.available)
      );
    }

    Map<String, Integer> occupiedWorkersByKey = new LinkedHashMap<>();
    Map<String, Integer> occupiedMachinesByKey = new LinkedHashMap<>();
    Map<String, LocalDate> dateByKey = new HashMap<>();
    Map<String, String> shiftByKey = new HashMap<>();
    Map<String, String> processByKey = new HashMap<>();

    for (Map<String, Object> row : rows) {
      String dateText = store.string(row, "calendar_date", store.string(row, "date", null));
      if (dateText == null || dateText.isBlank()) {
        throw store.badRequest("calendar_date is required in initial_carryover_occupancy.");
      }
      LocalDate date = store.parseConfigDate(dateText);
      String shiftCode = store.normalizeShiftCode(store.string(row, "shift_code", store.string(row, "shiftCode", null)));
      if (shiftCode.isBlank()) {
        throw store.badRequest("shift_code is required in initial_carryover_occupancy.");
      }
      if (!store.isDateInCurrentHorizon(date) || !store.isShiftEnabledInCurrentSetting(shiftCode)) {
        continue;
      }

      String processCode = store.normalizeCode(store.string(row, "process_code", store.string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw store.badRequest("process_code is required in initial_carryover_occupancy.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw store.badRequest("Unknown process_code in initial_carryover_occupancy: " + processCode);
      }

      int occupiedWorkers = (int) Math.round(store.number(
        row,
        "occupied_workers",
        store.number(row, "occupiedWorkers", store.number(row, "carryover_workers", 0d))
      ));
      int occupiedMachines = (int) Math.round(store.number(
        row,
        "occupied_machines",
        store.number(row, "occupiedMachines", store.number(row, "carryover_machines", 0d))
      ));
      if (occupiedWorkers < 0 || occupiedMachines < 0) {
        throw store.badRequest("occupied_workers and occupied_machines must be >= 0.");
      }

      String key = date + "#" + shiftCode + "#" + processCode;
      if (occupiedWorkersByKey.containsKey(key)) {
        throw store.badRequest(
          "Duplicate date/shift/process in initial_carryover_occupancy: "
            + date + "/" + store.normalizeShiftCodeLabel(shiftCode) + "/" + processCode
        );
      }

      int workerCap = workerCapacityByKey.getOrDefault(key, 0);
      int machineCap = machineCapacityByKey.getOrDefault(key, 0);
      int nextOccupiedWorkers = Math.min(Math.max(0, occupiedWorkers), workerCap);
      int nextOccupiedMachines = Math.min(Math.max(0, occupiedMachines), machineCap);
      occupiedWorkersByKey.put(key, nextOccupiedWorkers);
      occupiedMachinesByKey.put(key, nextOccupiedMachines);
      dateByKey.put(key, date);
      shiftByKey.put(key, shiftCode);
      processByKey.put(key, processCode);
    }

    List<MvpDomain.ResourceRow> nextWorkerRows = new ArrayList<>();
    List<MvpDomain.ResourceRow> nextMachineRows = new ArrayList<>();
    for (String key : occupiedWorkersByKey.keySet()) {
      LocalDate date = dateByKey.get(key);
      String shiftCode = shiftByKey.get(key);
      String processCode = processByKey.get(key);
      int occupiedWorkers = occupiedWorkersByKey.getOrDefault(key, 0);
      int occupiedMachines = occupiedMachinesByKey.getOrDefault(key, 0);
      if (occupiedWorkers > 0) {
        nextWorkerRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedWorkers));
      }
      if (occupiedMachines > 0) {
        nextMachineRows.add(new MvpDomain.ResourceRow(date, shiftCode, processCode, occupiedMachines));
      }
    }
    nextWorkerRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(store.shiftSortIndex(a.shiftCode), store.shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return store.normalizeCode(a.processCode).compareTo(store.normalizeCode(b.processCode));
    });
    nextMachineRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(store.shiftSortIndex(a.shiftCode), store.shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      return store.normalizeCode(a.processCode).compareTo(store.normalizeCode(b.processCode));
    });

    store.state.initialWorkerOccupancy = nextWorkerRows;
    store.state.initialMachineOccupancy = nextMachineRows;
  }
}

