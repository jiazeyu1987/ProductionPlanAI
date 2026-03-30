package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreMasterdataPatchLineTopologySupport {
  private MvpStoreMasterdataPatchLineTopologySupport() {}

  static void applyLineTopologyPatch(MvpStoreMasterdataPatchSupport store, List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = store.routeProcessCodeSet();
    if (validProcessCodes.isEmpty()) {
      for (MvpDomain.ProcessConfig process : store.state.processes) {
        validProcessCodes.add(store.normalizeCode(process.processCode));
      }
    }

    Map<String, MvpDomain.LineProcessBinding> byKey = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String companyCode = store.normalizeCompanyCode(store.string(row, "company_code", store.string(row, "companyCode", null)));
      String workshopCode = store.normalizeCode(store.string(row, "workshop_code", store.string(row, "workshopCode", null)));
      String lineCode = store.normalizeCode(store.string(row, "line_code", store.string(row, "lineCode", null)));
      String processCode = store.normalizeCode(store.string(row, "process_code", store.string(row, "processCode", null)));
      if (workshopCode.isBlank() || lineCode.isBlank() || processCode.isBlank()) {
        throw store.badRequest("workshop_code, line_code and process_code are required in line_topology.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw store.badRequest("Unknown process_code in line_topology: " + processCode);
      }
      MvpDomain.ProcessConfig processConfig = store.processConfigByCode(processCode);
      double defaultCapacity = processConfig == null ? 1d : processConfig.capacityPerShift;
      int defaultWorkers = processConfig == null ? 1 : Math.max(1, processConfig.requiredWorkers);
      int defaultMachines = processConfig == null ? 1 : Math.max(1, processConfig.requiredMachines);
      double capacityPerShift = store.number(
        row,
        "capacity_per_shift",
        store.number(row, "capacityPerShift", store.number(row, "line_capacity_per_shift", defaultCapacity))
      );
      int requiredWorkers = (int) Math.round(store.number(
        row,
        "required_workers",
        store.number(row, "requiredWorkers", store.number(row, "required_manpower_per_group", defaultWorkers))
      ));
      int requiredMachines = (int) Math.round(store.number(
        row,
        "required_machines",
        store.number(row, "requiredMachines", store.number(row, "required_equipment_count", defaultMachines))
      ));
      if (capacityPerShift <= 0d) {
        throw store.badRequest("capacity_per_shift must be > 0 in line_topology for " + processCode + ".");
      }
      if (requiredWorkers <= 0) {
        throw store.badRequest("required_workers must be > 0 in line_topology for " + processCode + ".");
      }
      if (requiredMachines <= 0) {
        throw store.badRequest("required_machines must be > 0 in line_topology for " + processCode + ".");
      }
      String lineName = store.string(row, "line_name", store.string(row, "lineName", lineCode));
      boolean enabled = store.bool(row, "enabled_flag", store.bool(row, "enabledFlag", store.bool(row, "enabled", true)));
      String key = companyCode + "#" + workshopCode + "#" + lineCode + "#" + processCode;
      byKey.put(
        key,
        new MvpDomain.LineProcessBinding(
          companyCode,
          workshopCode,
          lineCode,
          lineName == null || lineName.isBlank() ? lineCode : lineName.trim(),
          processCode,
          enabled,
          store.round2(capacityPerShift),
          requiredWorkers,
          requiredMachines
        )
      );
    }

    List<MvpDomain.LineProcessBinding> nextRows = new ArrayList<>(byKey.values());
    nextRows.sort((a, b) -> {
      int byCompany = store.normalizeCompanyCode(a.companyCode).compareTo(store.normalizeCompanyCode(b.companyCode));
      if (byCompany != 0) {
        return byCompany;
      }
      int byWorkshop = store.normalizeCode(a.workshopCode).compareTo(store.normalizeCode(b.workshopCode));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      int byLine = store.normalizeCode(a.lineCode).compareTo(store.normalizeCode(b.lineCode));
      if (byLine != 0) {
        return byLine;
      }
      return store.normalizeCode(a.processCode).compareTo(store.normalizeCode(b.processCode));
    });
    store.state.lineProcessBindings = nextRows;
  }
}

