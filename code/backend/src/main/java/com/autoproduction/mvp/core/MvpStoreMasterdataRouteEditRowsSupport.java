package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class MvpStoreMasterdataRouteEditRowsSupport extends MvpStoreMasterdataRouteStepSupport {
  protected MvpStoreMasterdataRouteEditRowsSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected List<Map<String, Object>> listProcessConfigRowsForEdit() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("process_code", process.processCode);
      row.put("capacity_per_shift", round2(process.capacityPerShift));
      row.put("required_workers", process.requiredWorkers);
      row.put("required_machines", process.requiredMachines);
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code"))));
    return rows;
  }

  protected List<Map<String, Object>> listLineTopologyRowsForEdit() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
      String companyCode = normalizeCompanyCode(binding.companyCode);
      String workshopCode = normalizeCode(binding.workshopCode);
      String lineCode = normalizeCode(binding.lineCode);
      String lineName = binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim();
      String processCode = normalizeCode(binding.processCode);
      if (workshopCode.isBlank() || lineCode.isBlank() || processCode.isBlank()) {
        continue;
      }
      MvpDomain.ProcessConfig processConfig = processConfigByCode(processCode);
      double capacityPerShift = binding.capacityPerShift > 0d
        ? binding.capacityPerShift
        : (processConfig == null ? 1d : processConfig.capacityPerShift);
      int requiredWorkers = binding.requiredWorkers > 0
        ? binding.requiredWorkers
        : (processConfig == null ? 1 : Math.max(1, processConfig.requiredWorkers));
      int requiredMachines = binding.requiredMachines > 0
        ? binding.requiredMachines
        : (processConfig == null ? 1 : Math.max(1, processConfig.requiredMachines));
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("company_code", companyCode);
      row.put("workshop_code", workshopCode);
      row.put("line_code", lineCode);
      row.put("line_name", lineName);
      row.put("process_code", processCode);
      row.put("capacity_per_shift", round2(capacityPerShift));
      row.put("required_workers", requiredWorkers);
      row.put("required_machines", requiredMachines);
      row.put("enabled_flag", binding.enabled ? 1 : 0);
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> {
      int byCompany = String.valueOf(a.get("company_code")).compareTo(String.valueOf(b.get("company_code")));
      if (byCompany != 0) {
        return byCompany;
      }
      int byWorkshop = String.valueOf(a.get("workshop_code")).compareTo(String.valueOf(b.get("workshop_code")));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      int byLine = String.valueOf(a.get("line_code")).compareTo(String.valueOf(b.get("line_code")));
      if (byLine != 0) {
        return byLine;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }

  protected List<Map<String, Object>> listInitialCarryoverRowsForEdit() {
    Map<String, Integer> occupiedWorkersByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialWorkerOccupancy) {
      occupiedWorkersByKey.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }
    Map<String, Integer> occupiedMachinesByKey = new HashMap<>();
    for (MvpDomain.ResourceRow row : state.initialMachineOccupancy) {
      occupiedMachinesByKey.put(row.date + "#" + normalizeShiftCodeLabel(row.shiftCode) + "#" + normalizeCode(row.processCode), row.available);
    }

    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(occupiedWorkersByKey.keySet());
    allKeys.addAll(occupiedMachinesByKey.keySet());

    List<Map<String, Object>> rows = new ArrayList<>();
    for (String key : allKeys) {
      String[] parts = key.split("#", 3);
      if (parts.length < 3) {
        continue;
      }
      String date = parts[0];
      String shiftCode = parts[1];
      String processCode = parts[2];
      if (date.isBlank() || shiftCode.isBlank() || processCode.isBlank()) {
        continue;
      }
      int occupiedWorkers = Math.max(0, occupiedWorkersByKey.getOrDefault(key, 0));
      int occupiedMachines = Math.max(0, occupiedMachinesByKey.getOrDefault(key, 0));
      if (occupiedWorkers <= 0 && occupiedMachines <= 0) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("calendar_date", date);
      row.put("shift_code", shiftCode);
      row.put("process_code", processCode);
      row.put("occupied_workers", occupiedWorkers);
      row.put("occupied_machines", occupiedMachines);
      rows.add(localizeRow(row));
    }

    rows.sort((a, b) -> {
      String aDate = String.valueOf(a.get("calendar_date"));
      String bDate = String.valueOf(b.get("calendar_date"));
      int byDate = aDate.compareTo(bDate);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(
        shiftSortIndex(String.valueOf(a.get("shift_code"))),
        shiftSortIndex(String.valueOf(b.get("shift_code")))
      );
      if (byShift != 0) {
        return byShift;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }

  protected List<Map<String, Object>> listMaterialAvailabilityRowsForEdit() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MvpDomain.MaterialRow rowData : state.materialAvailability) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("calendar_date", rowData.date.toString());
      row.put("shift_code", normalizeShiftCodeLabel(rowData.shiftCode));
      row.put("product_code", rowData.productCode);
      row.put("process_code", rowData.processCode);
      row.put("available_qty", round2(rowData.availableQty));
      rows.add(localizeRow(row));
    }
    rows.sort((a, b) -> {
      String aDate = String.valueOf(a.get("calendar_date"));
      String bDate = String.valueOf(b.get("calendar_date"));
      int byDate = aDate.compareTo(bDate);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(
        shiftSortIndex(String.valueOf(a.get("shift_code"))),
        shiftSortIndex(String.valueOf(b.get("shift_code")))
      );
      if (byShift != 0) {
        return byShift;
      }
      int byProduct = String.valueOf(a.get("product_code")).compareTo(String.valueOf(b.get("product_code")));
      if (byProduct != 0) {
        return byProduct;
      }
      return String.valueOf(a.get("process_code")).compareTo(String.valueOf(b.get("process_code")));
    });
    return rows;
  }
}

