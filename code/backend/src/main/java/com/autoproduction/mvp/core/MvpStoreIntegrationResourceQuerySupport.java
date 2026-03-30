package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreIntegrationResourceQuerySupport {
  private MvpStoreIntegrationResourceQuerySupport() {}

  static List<Map<String, Object>> listMaterialAvailability(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.MaterialRow rowData : store.state.materialAvailability) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("material_code", rowData.productCode);
        row.put("order_no", "");
        row.put("process_code", rowData.processCode);
        row.put("available_qty", rowData.availableQty);
        row.put("available_time", MvpStoreRuntimeBase.toDateTime(rowData.date.toString(), rowData.shiftCode, true));
        row.put("ready_flag", rowData.availableQty > 0 ? 1 : 0);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listEquipments(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      List<Map<String, Object>> rows = new ArrayList<>();
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      Map<String, Integer> maxCountByProcess = new LinkedHashMap<>();
      for (MvpDomain.ResourceRow row : store.state.machinePools) {
        String processCode = store.normalizeCode(row.processCode);
        int current = maxCountByProcess.getOrDefault(processCode, 0);
        maxCountByProcess.put(processCode, Math.max(current, Math.max(1, row.available)));
      }

      for (Map.Entry<String, Integer> entry : maxCountByProcess.entrySet()) {
        String processCode = entry.getKey();
        int count = entry.getValue();
        List<MvpDomain.LineProcessBinding> lineBindings = store.lineBindingsForProcess(processCode, true);
        if (lineBindings.isEmpty()) {
          lineBindings = List.of(store.defaultLineBindingForProcess(processCode));
        }
        for (int i = 1; i <= count; i += 1) {
          MvpDomain.LineProcessBinding binding = lineBindings.get((i - 1) % lineBindings.size());
          String companyCode = store.normalizeCompanyCode(binding.companyCode);
          String lineCode = store.normalizeCode(binding.lineCode);
          String workshopCode = store.normalizeCode(binding.workshopCode);
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("equipment_code", lineCode + "-" + processCode + "-EQ-" + i);
          row.put("process_code", processCode);
          row.put("company_code", companyCode);
          row.put("line_code", lineCode);
          row.put("line_name", binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim());
          row.put("workshop_code", workshopCode);
          row.put("status", "AVAILABLE");
          row.put("capacity_per_shift", 1);
          row.put("last_update_time", now);
          rows.add(store.localizeRow(row));
        }
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listProcessRoutes(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Map.Entry<String, List<MvpDomain.ProcessStep>> route : store.state.processRoutes.entrySet()) {
        for (int i = 0; i < route.getValue().size(); i += 1) {
          MvpDomain.ProcessStep step = route.getValue().get(i);
          MvpDomain.ProcessConfig config = store.state.processes.stream()
            .filter(process -> process.processCode.equals(step.processCode))
            .findFirst()
            .orElse(null);
          rows.add(store.localizeRow(Map.of(
            "route_no", "ROUTE-" + route.getKey(),
            "product_code", route.getKey(),
            "process_code", step.processCode,
            "sequence_no", i + 1,
            "dependency_type", step.dependencyType,
            "capacity_per_shift", config == null ? 0 : config.capacityPerShift,
            "required_manpower_per_group", config == null ? 0 : config.requiredWorkers,
            "required_equipment_count", config == null ? 0 : config.requiredMachines,
            "enabled_flag", 1,
            "last_update_time", now
          )));
        }
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listEquipmentProcessCapabilities(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.ProcessConfig process : store.state.processes) {
        List<MvpDomain.LineProcessBinding> lineBindings = store.lineBindingsForProcess(process.processCode, false);
        if (lineBindings.isEmpty()) {
          lineBindings = List.of(store.defaultLineBindingForProcess(process.processCode));
        }
        for (MvpDomain.LineProcessBinding binding : lineBindings) {
          String companyCode = store.normalizeCompanyCode(binding.companyCode);
          String lineCode = store.normalizeCode(binding.lineCode);
          String workshopCode = store.normalizeCode(binding.workshopCode);
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("equipment_code", lineCode + "-" + process.processCode + "-EQ-1");
          row.put("process_code", process.processCode);
          row.put("company_code", companyCode);
          row.put("line_code", lineCode);
          row.put("line_name", binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim());
          row.put("workshop_code", workshopCode);
          row.put("enabled_flag", binding.enabled ? 1 : 0);
          row.put("capacity_factor", 1);
          row.put("last_update_time", now);
          rows.add(store.localizeRow(row));
        }
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listEmployeeSkills(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.ProcessConfig process : store.state.processes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("employee_id", process.processCode + "-EMP-1");
        row.put("process_code", process.processCode);
        row.put("skill_level", "INDEPENDENT");
        row.put("efficiency_factor", 1.0);
        row.put("active_flag", 1);
        row.put("last_update_time", now);
        rows.add(store.localizeRow(row));
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listShiftCalendar(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.ShiftRow rowData : store.state.shiftCalendar) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("calendar_date", rowData.date.toString());
        row.put("shift_code", "D".equals(rowData.shiftCode) ? "DAY" : "NIGHT");
        row.put("shift_start_time", MvpStoreRuntimeBase.toDateTime(rowData.date.toString(), rowData.shiftCode, true));
        row.put("shift_end_time", MvpStoreRuntimeBase.toDateTime(rowData.date.toString(), rowData.shiftCode, false));
        row.put("open_flag", rowData.open ? 1 : 0);
        row.put("company_code", store.companyCodesSummaryAll());
        row.put("workshop_code", store.workshopCodesSummaryAll());
        row.put("last_update_time", now);
        rows.add(store.localizeRow(row));
      }
      return rows;
    }
  }
}

