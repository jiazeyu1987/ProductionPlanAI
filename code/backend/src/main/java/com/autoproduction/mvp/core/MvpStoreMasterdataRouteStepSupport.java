package com.autoproduction.mvp.core;

import com.autoproduction.mvp.module.integration.erp.ErpDataManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class MvpStoreMasterdataRouteStepSupport extends MvpStoreMasterdataPatchSupport {
  protected MvpStoreMasterdataRouteStepSupport(ErpDataManager erpDataManager) {
    super(erpDataManager);
  }

  protected List<Map<String, Object>> extractRouteStepRows(Map<String, Object> payload) {
    List<Map<String, Object>> rows = maps(payload == null ? null : payload.get("steps"));
    if (rows.isEmpty()) {
      rows = maps(payload == null ? null : payload.get("process_steps"));
    }
    if (rows.isEmpty()) {
      rows = maps(payload == null ? null : payload.get("route_steps"));
    }
    return rows;
  }

  protected List<MvpDomain.ProcessStep> parseRouteSteps(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      throw badRequest("steps is required and cannot be empty.");
    }

    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : state.processes) {
      validProcessCodes.add(normalizeCode(process.processCode));
    }

    List<Map<String, Object>> normalizedRows = new ArrayList<>();
    for (int i = 0; i < rows.size(); i += 1) {
      Map<String, Object> row = rows.get(i);
      String processCode = normalizeCode(string(row, "process_code", string(row, "processCode", null)));
      if (processCode.isBlank()) {
        throw badRequest("process_code is required in steps.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw badRequest("Unknown process_code in steps: " + processCode);
      }
      String dependencyType = normalizeRouteDependencyType(
        string(row, "dependency_type", string(row, "dependencyType", "FS"))
      );
      double sequenceNo = number(row, "sequence_no", number(row, "sequenceNo", i + 1d));

      Map<String, Object> normalized = new LinkedHashMap<>();
      normalized.put("process_code", processCode);
      normalized.put("dependency_type", dependencyType);
      normalized.put("sequence_no", sequenceNo);
      normalized.put("input_index", i);
      normalizedRows.add(normalized);
    }

    normalizedRows.sort((a, b) -> {
      int bySequence = Double.compare(number(a, "sequence_no", 0d), number(b, "sequence_no", 0d));
      if (bySequence != 0) {
        return bySequence;
      }
      return Integer.compare((int) number(a, "input_index", 0d), (int) number(b, "input_index", 0d));
    });

    Set<String> uniqueProcessCodes = new HashSet<>();
    List<MvpDomain.ProcessStep> steps = new ArrayList<>();
    for (Map<String, Object> row : normalizedRows) {
      String processCode = normalizeCode(string(row, "process_code", null));
      if (!uniqueProcessCodes.add(processCode)) {
        throw badRequest("Duplicate process_code in steps: " + processCode);
      }
      steps.add(new MvpDomain.ProcessStep(
        processCode,
        normalizeRouteDependencyType(string(row, "dependency_type", "FS"))
      ));
    }
    return steps;
  }

  protected String normalizeRouteDependencyType(String dependencyType) {
    String normalized = normalizeCode(dependencyType);
    if (normalized.isBlank()) {
      return "FS";
    }
    if ("FS".equals(normalized) || "SS".equals(normalized)) {
      return normalized;
    }
    throw badRequest("dependency_type must be FS or SS.");
  }

  protected void upsertProcessRoute(String productCode, List<MvpDomain.ProcessStep> steps) {
    Map<String, List<MvpDomain.ProcessStep>> nextRoutes = mutableProcessRoutesCopy();
    nextRoutes.put(productCode, cloneProcessSteps(steps));
    state.processRoutes = sortProcessRoutes(nextRoutes);
  }

  protected Map<String, List<MvpDomain.ProcessStep>> mutableProcessRoutesCopy() {
    Map<String, List<MvpDomain.ProcessStep>> out = new LinkedHashMap<>();
    for (Map.Entry<String, List<MvpDomain.ProcessStep>> entry : state.processRoutes.entrySet()) {
      out.put(entry.getKey(), cloneProcessSteps(entry.getValue()));
    }
    return out;
  }

  protected List<MvpDomain.ProcessStep> cloneProcessSteps(List<MvpDomain.ProcessStep> source) {
    List<MvpDomain.ProcessStep> out = new ArrayList<>();
    if (source == null) {
      return out;
    }
    for (MvpDomain.ProcessStep step : source) {
      out.add(new MvpDomain.ProcessStep(step.processCode, step.dependencyType));
    }
    return out;
  }

  protected Map<String, List<MvpDomain.ProcessStep>> sortProcessRoutes(Map<String, List<MvpDomain.ProcessStep>> routes) {
    List<String> products = new ArrayList<>(routes.keySet());
    products.sort(String::compareTo);
    Map<String, List<MvpDomain.ProcessStep>> ordered = new LinkedHashMap<>();
    for (String productCode : products) {
      ordered.put(productCode, cloneProcessSteps(routes.get(productCode)));
    }
    return ordered;
  }
}

