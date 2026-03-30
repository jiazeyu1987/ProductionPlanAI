package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class MvpStoreMasterdataProcessConfigSupport extends MvpStoreMasterdataPlanningWindowSupport {
  protected MvpStoreMasterdataProcessConfigSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected void applyProcessConfigPatch(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      throw badRequest("process_configs cannot be empty.");
    }

    Map<String, MvpDomain.ProcessConfig> processByCode = new HashMap<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      processByCode.put(normalizeCode(process.processCode), process);
    }

    Map<String, MvpDomain.ProcessConfig> nextByCode = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw badRequest("process_code is required in process_configs.");
      }
      if (nextByCode.containsKey(processCode)) {
        throw badRequest("Duplicate process_code in process_configs: " + processCode);
      }
      MvpDomain.ProcessConfig process = processByCode.get(processCode);
      double defaultCapacity = process == null ? 1d : process.capacityPerShift;
      int defaultWorkers = process == null ? 1 : Math.max(1, process.requiredWorkers);
      int defaultMachines = process == null ? 1 : Math.max(1, process.requiredMachines);

      double capacityPerShift = number(
        row,
        "capacity_per_shift",
        number(row, "capacityPerShift", defaultCapacity)
      );
      int requiredWorkers = (int) Math.round(number(
        row,
        "required_workers",
        number(row, "required_manpower_per_group", number(row, "requiredWorkers", defaultWorkers))
      ));
      int requiredMachines = (int) Math.round(number(
        row,
        "required_machines",
        number(row, "required_equipment_count", number(row, "requiredMachines", defaultMachines))
      ));

      if (capacityPerShift <= 0d) {
        throw badRequest("capacity_per_shift must be > 0 for " + processCode);
      }
      if (requiredWorkers <= 0) {
        throw badRequest("required_workers must be > 0 for " + processCode);
      }
      if (requiredMachines <= 0) {
        throw badRequest("required_machines must be > 0 for " + processCode);
      }

      nextByCode.put(
        processCode,
        new MvpDomain.ProcessConfig(
          processCode,
          round2(capacityPerShift),
          requiredWorkers,
          requiredMachines
        )
      );
    }

    List<MvpDomain.ProcessConfig> nextRows = new ArrayList<>(nextByCode.values());
    nextRows.sort((a, b) -> normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode)));
    state.processes = nextRows;

    Set<String> validProcessCodes = new HashSet<>(nextByCode.keySet());
    pruneMasterdataRowsByProcessCodes(validProcessCodes);
  }

  protected void pruneMasterdataRowsByProcessCodes(Set<String> validProcessCodes) {
    List<MvpDomain.LineProcessBinding> nextLineBindings = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String processCode = normalizeCode(binding.processCode);
      if (validProcessCodes.contains(processCode)) {
        nextLineBindings.add(binding);
      }
    }
    nextLineBindings.sort((a, b) -> {
      int byWorkshop = normalizeCode(a.workshopCode).compareTo(normalizeCode(b.workshopCode));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      int byLine = normalizeCode(a.lineCode).compareTo(normalizeCode(b.lineCode));
      if (byLine != 0) {
        return byLine;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    state.lineProcessBindings = nextLineBindings;

    state.workerPools = filterResourceRowsByProcessCodes(state.workerPools, validProcessCodes);
    state.machinePools = filterResourceRowsByProcessCodes(state.machinePools, validProcessCodes);
    state.initialWorkerOccupancy = filterResourceRowsByProcessCodes(state.initialWorkerOccupancy, validProcessCodes);
    state.initialMachineOccupancy = filterResourceRowsByProcessCodes(state.initialMachineOccupancy, validProcessCodes);

    List<MvpDomain.MaterialRow> nextMaterialRows = new ArrayList<>();
    for (MvpDomain.MaterialRow row : state.materialAvailability) {
      String processCode = normalizeCode(row.processCode);
      if (validProcessCodes.contains(processCode)) {
        nextMaterialRows.add(new MvpDomain.MaterialRow(row.date, normalizeShiftCode(row.shiftCode), normalizeCode(row.productCode), processCode, round2(row.availableQty)));
      }
    }
    nextMaterialRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(shiftSortIndex(a.shiftCode), shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      int byProduct = normalizeCode(a.productCode).compareTo(normalizeCode(b.productCode));
      if (byProduct != 0) {
        return byProduct;
      }
      return normalizeCode(a.processCode).compareTo(normalizeCode(b.processCode));
    });
    state.materialAvailability = nextMaterialRows;
  }
}

