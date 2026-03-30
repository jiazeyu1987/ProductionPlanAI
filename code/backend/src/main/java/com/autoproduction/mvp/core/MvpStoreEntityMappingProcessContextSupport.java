package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreEntityMappingProcessContextSupport {
  private MvpStoreEntityMappingProcessContextSupport() {}

  static List<Map<String, Object>> buildProcessContextsForProduct(MvpStoreEntityMappingSupport store, String productCode) {
    String normalizedProductCode = MvpStoreCoreNormalizationSupport.normalizeCode(productCode);
    List<MvpDomain.ProcessStep> route = store.state.processRoutes.getOrDefault(normalizedProductCode, List.of());
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < route.size(); i += 1) {
      MvpDomain.ProcessStep step = route.get(i);
      String processCode = MvpStoreCoreNormalizationSupport.normalizeCode(step.processCode);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("sequence_no", i + 1);
      row.put("product_code", normalizedProductCode);
      row.put("product_name_cn", MvpStoreLocalizationAndExportSupport.productNameCn(normalizedProductCode));
      row.put("process_code", processCode);
      row.put("process_name_cn", MvpStoreLocalizationAndExportSupport.processNameCn(processCode));
      row.put("dependency_type", step.dependencyType == null ? "FS" : step.dependencyType);
      row.put("company_code", store.companyCodeForProcess(processCode));
      row.put("workshop_code", store.workshopCodeForProcess(processCode));
      row.put("line_code", store.lineCodeForProcess(processCode));
      row.put("company_codes", store.companyCodesForProcessSummary(processCode));
      row.put("workshop_codes", store.workshopCodesForProcessSummary(processCode));
      row.put("line_codes", store.lineCodesForProcessSummary(processCode));
      rows.add(row);
    }
    return rows;
  }

  static String summarizeProcessContexts(MvpStoreEntityMappingSupport store, List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String processCode = MvpStoreRuntimeBase.firstString(row, "process_code");
      String processName = MvpStoreRuntimeBase.firstString(row, "process_name_cn");
      String companyCode = MvpStoreRuntimeBase.firstString(row, "company_codes", "company_code");
      String workshopCode = MvpStoreRuntimeBase.firstString(row, "workshop_codes", "workshop_code");
      String lineCode = MvpStoreRuntimeBase.firstString(row, "line_codes", "line_code");
      String label = (processName == null || processName.isBlank()) ? (processCode == null ? "-" : processCode) : processName;
      parts.add(
        label
          + " ("
          + (companyCode == null ? "-" : companyCode)
          + "/"
          + (workshopCode == null ? "-" : workshopCode)
          + "/"
          + (lineCode == null ? "-" : lineCode)
          + ")"
      );
    }
    return String.join(" -> ", parts);
  }

  static String joinContextValues(MvpStoreEntityMappingSupport store, List<Map<String, Object>> rows, String key) {
    if (rows == null || rows.isEmpty()) {
      return "";
    }
    List<String> values = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String value = MvpStoreRuntimeBase.firstString(row, key);
      if (value == null || value.isBlank()) {
        continue;
      }
      String[] parts = value.split(",");
      for (String part : parts) {
        String normalized = part == null ? "" : part.trim();
        if (normalized.isBlank() || values.contains(normalized)) {
          continue;
        }
        values.add(normalized);
      }
    }
    return String.join(",", values);
  }
}
