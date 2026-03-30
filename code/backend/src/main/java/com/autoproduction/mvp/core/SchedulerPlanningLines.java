package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.SchedulerPlanningConstants.EPS;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeLineToken;
import static com.autoproduction.mvp.core.SchedulerPlanningUtil.normalizeProcessCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SchedulerPlanningLines {
  private SchedulerPlanningLines() {}

  static Map<String, List<MvpDomain.LineProcessBinding>> lineBindingsByProcess(
    MvpDomain.State state,
    Map<String, MvpDomain.ProcessConfig> processConfigMap
  ) {
    Map<String, List<MvpDomain.LineProcessBinding>> out = new HashMap<>();
    if (state != null && state.lineProcessBindings != null) {
      for (MvpDomain.LineProcessBinding binding : state.lineProcessBindings) {
        if (binding == null || !binding.enabled) {
          continue;
        }
        String processCode = normalizeProcessCode(binding.processCode);
        if (processCode == null) {
          continue;
        }
        MvpDomain.ProcessConfig processConfig = processConfigMap.get(processCode);
        double fallbackCapacity = processConfig == null ? 1d : Math.max(EPS, processConfig.capacityPerShift);
        int fallbackWorkers = processConfig == null ? 1 : Math.max(1, processConfig.requiredWorkers);
        int fallbackMachines = processConfig == null ? 1 : Math.max(1, processConfig.requiredMachines);
        MvpDomain.LineProcessBinding normalized = new MvpDomain.LineProcessBinding(
          normalizeLineToken(binding.companyCode, "COMPANY-MAIN"),
          normalizeLineToken(binding.workshopCode, "WS-PRODUCTION"),
          normalizeLineToken(binding.lineCode, "LINE-MIXED"),
          (binding.lineName == null || binding.lineName.isBlank()) ? normalizeLineToken(binding.lineCode, "LINE-MIXED") : binding.lineName.trim(),
          processCode,
          true,
          binding.capacityPerShift > EPS ? binding.capacityPerShift : fallbackCapacity,
          binding.requiredWorkers > 0 ? binding.requiredWorkers : fallbackWorkers,
          binding.requiredMachines > 0 ? binding.requiredMachines : fallbackMachines
        );
        out.computeIfAbsent(processCode, ignored -> new ArrayList<>()).add(normalized);
      }
    }
    for (Map.Entry<String, MvpDomain.ProcessConfig> entry : processConfigMap.entrySet()) {
      String processCode = normalizeProcessCode(entry.getKey());
      if (processCode == null) {
        continue;
      }
      out.computeIfAbsent(processCode, ignored -> new ArrayList<>());
      if (out.get(processCode).isEmpty()) {
        out.get(processCode).add(defaultLineBindingForProcess(processCode, entry.getValue()));
      }
    }
    for (List<MvpDomain.LineProcessBinding> bindings : out.values()) {
      bindings.sort(Comparator
        .comparing((MvpDomain.LineProcessBinding row) -> normalizeLineToken(row.companyCode, "COMPANY-MAIN"))
        .thenComparing(row -> normalizeLineToken(row.workshopCode, "WS-PRODUCTION"))
        .thenComparing(row -> normalizeLineToken(row.lineCode, "LINE-MIXED")));
    }
    return out;
  }

  static MvpDomain.LineProcessBinding defaultLineBindingForProcess(
    String processCode,
    MvpDomain.ProcessConfig processConfig
  ) {
    String normalizedProcessCode = normalizeProcessCode(processCode);
    if (normalizedProcessCode == null) {
      normalizedProcessCode = "PROC_UNKNOWN";
    }
    double capacityPerShift = processConfig == null ? 1d : Math.max(EPS, processConfig.capacityPerShift);
    int requiredWorkers = processConfig == null ? 1 : Math.max(1, processConfig.requiredWorkers);
    int requiredMachines = processConfig == null ? 1 : Math.max(1, processConfig.requiredMachines);
    String workshopCode = normalizedProcessCode.contains("STERILE") ? "WS-STERILE" : "WS-PRODUCTION";
    return new MvpDomain.LineProcessBinding(
      "COMPANY-MAIN",
      workshopCode,
      "LINE-" + normalizedProcessCode,
      "LINE-" + normalizedProcessCode,
      normalizedProcessCode,
      true,
      capacityPerShift,
      requiredWorkers,
      requiredMachines
    );
  }

  static MvpDomain.LineProcessBinding pickBaselineLineBinding(
    MvpDomain.Allocation source,
    List<MvpDomain.LineProcessBinding> bindings
  ) {
    if (bindings == null || bindings.isEmpty()) {
      return null;
    }
    if (source == null) {
      return bindings.get(0);
    }
    String sourceLineCode = normalizeProcessCode(source.lineCode);
    if (sourceLineCode == null) {
      return bindings.get(0);
    }
    String sourceWorkshopCode = normalizeProcessCode(source.workshopCode);
    String sourceCompanyCode = normalizeProcessCode(source.companyCode);
    for (MvpDomain.LineProcessBinding binding : bindings) {
      String lineCode = normalizeProcessCode(binding.lineCode);
      if (lineCode == null || !lineCode.equals(sourceLineCode)) {
        continue;
      }
      if (sourceWorkshopCode != null) {
        String workshopCode = normalizeProcessCode(binding.workshopCode);
        if (workshopCode == null || !workshopCode.equals(sourceWorkshopCode)) {
          continue;
        }
      }
      if (sourceCompanyCode != null) {
        String companyCode = normalizeProcessCode(binding.companyCode);
        if (companyCode == null || !companyCode.equals(sourceCompanyCode)) {
          continue;
        }
      }
      return binding;
    }
    return bindings.get(0);
  }

  static String lineUsageKey(String shiftId, MvpDomain.LineProcessBinding binding, String processCode) {
    return normalizeLineToken(shiftId, "")
      + "#"
      + normalizeLineToken(binding == null ? null : binding.companyCode, "COMPANY-MAIN")
      + "#"
      + normalizeLineToken(binding == null ? null : binding.workshopCode, "WS-PRODUCTION")
      + "#"
      + normalizeLineToken(binding == null ? null : binding.lineCode, "LINE-MIXED")
      + "#"
      + normalizeLineToken(processCode, "PROC_UNKNOWN");
  }
}

