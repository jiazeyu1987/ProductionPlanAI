package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MvpStoreLineBindingLookupSupport {
  private MvpStoreLineBindingLookupSupport() {}

  static String companyCodeForProcess(MvpStoreLocalizationAndExportSupport store, String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(store, processCode, true);
    if (!bindings.isEmpty()) {
      return store.normalizeCompanyCode(bindings.get(0).companyCode);
    }
    return store.normalizeCompanyCode(defaultLineBindingForProcess(store, processCode).companyCode);
  }

  static String workshopCodeForProcess(MvpStoreLocalizationAndExportSupport store, String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(store, processCode, true);
    if (!bindings.isEmpty()) {
      return store.normalizeCode(bindings.get(0).workshopCode);
    }
    return store.normalizeCode(defaultLineBindingForProcess(store, processCode).workshopCode);
  }

  static String lineCodeForProcess(MvpStoreLocalizationAndExportSupport store, String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(store, processCode, true);
    if (!bindings.isEmpty()) {
      return store.normalizeCode(bindings.get(0).lineCode);
    }
    return store.normalizeCode(defaultLineBindingForProcess(store, processCode).lineCode);
  }

  static String lineNameForCode(MvpStoreLocalizationAndExportSupport store, String lineCode) {
    String normalizedLineCode = store.normalizeCode(lineCode);
    for (MvpDomain.LineProcessBinding binding : store.state.lineProcessBindings) {
      if (!store.normalizeCode(binding.lineCode).equals(normalizedLineCode)) {
        continue;
      }
      if (binding.lineName != null && !binding.lineName.isBlank()) {
        return binding.lineName.trim();
      }
    }
    return normalizedLineCode;
  }

  static String lineCodesForProcessSummary(MvpStoreLocalizationAndExportSupport store, String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(store, processCode, true);
    if (bindings.isEmpty()) {
      return store.normalizeCode(defaultLineBindingForProcess(store, processCode).lineCode);
    }
    List<String> lineCodes = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : bindings) {
      String lineCode = store.normalizeCode(binding.lineCode);
      if (!lineCode.isBlank() && !lineCodes.contains(lineCode)) {
        lineCodes.add(lineCode);
      }
    }
    return String.join(",", lineCodes);
  }

  static String workshopCodesForProcessSummary(MvpStoreLocalizationAndExportSupport store, String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(store, processCode, true);
    if (bindings.isEmpty()) {
      return store.normalizeCode(defaultLineBindingForProcess(store, processCode).workshopCode);
    }
    List<String> workshopCodes = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : bindings) {
      String workshopCode = store.normalizeCode(binding.workshopCode);
      if (!workshopCode.isBlank() && !workshopCodes.contains(workshopCode)) {
        workshopCodes.add(workshopCode);
      }
    }
    return String.join(",", workshopCodes);
  }

  static String companyCodesForProcessSummary(MvpStoreLocalizationAndExportSupport store, String processCode) {
    List<MvpDomain.LineProcessBinding> bindings = lineBindingsForProcess(store, processCode, true);
    if (bindings.isEmpty()) {
      return store.normalizeCompanyCode(defaultLineBindingForProcess(store, processCode).companyCode);
    }
    List<String> companyCodes = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : bindings) {
      String companyCode = store.normalizeCompanyCode(binding.companyCode);
      if (!companyCode.isBlank() && !companyCodes.contains(companyCode)) {
        companyCodes.add(companyCode);
      }
    }
    return String.join(",", companyCodes);
  }

  static String companyCodesSummaryAll(MvpStoreLocalizationAndExportSupport store) {
    List<String> companyCodes = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : store.state.lineProcessBindings) {
      String companyCode = store.normalizeCompanyCode(binding.companyCode);
      if (!companyCode.isBlank() && !companyCodes.contains(companyCode)) {
        companyCodes.add(companyCode);
      }
    }
    if (companyCodes.isEmpty()) {
      companyCodes.add(MvpStoreRuntimeBase.DEFAULT_COMPANY_CODE);
    }
    return String.join(",", companyCodes);
  }

  static String workshopCodesSummaryAll(MvpStoreLocalizationAndExportSupport store) {
    List<String> workshops = new ArrayList<>();
    for (MvpDomain.LineProcessBinding binding : store.state.lineProcessBindings) {
      String workshopCode = store.normalizeCode(binding.workshopCode);
      if (!workshopCode.isBlank() && !workshops.contains(workshopCode)) {
        workshops.add(workshopCode);
      }
    }
    if (workshops.isEmpty()) {
      workshops.add("WS-PRODUCTION");
    }
    return String.join(",", workshops);
  }

  static List<MvpDomain.LineProcessBinding> lineBindingsForProcess(
    MvpStoreLocalizationAndExportSupport store,
    String processCode,
    boolean enabledOnly
  ) {
    String normalizedProcessCode = store.normalizeCode(processCode);
    if (normalizedProcessCode.isBlank()) {
      return List.of();
    }
    List<MvpDomain.LineProcessBinding> out = new ArrayList<>();
    Set<String> dedupe = new HashSet<>();
    for (MvpDomain.LineProcessBinding binding : store.state.lineProcessBindings) {
      String bindingProcessCode = store.normalizeCode(binding.processCode);
      if (!bindingProcessCode.equals(normalizedProcessCode)) {
        continue;
      }
      if (enabledOnly && !binding.enabled) {
        continue;
      }
      String companyCode = store.normalizeCompanyCode(binding.companyCode);
      String workshopCode = store.normalizeCode(binding.workshopCode);
      String lineCode = store.normalizeCode(binding.lineCode);
      if (workshopCode.isBlank() || lineCode.isBlank()) {
        continue;
      }
      String key = companyCode + "#" + workshopCode + "#" + lineCode + "#" + bindingProcessCode;
      if (!dedupe.add(key)) {
        continue;
      }
      out.add(
        new MvpDomain.LineProcessBinding(
          companyCode,
          workshopCode,
          lineCode,
          binding.lineName == null || binding.lineName.isBlank() ? lineCode : binding.lineName.trim(),
          bindingProcessCode,
          binding.enabled,
          binding.capacityPerShift,
          binding.requiredWorkers,
          binding.requiredMachines
        )
      );
    }
    out.sort((a, b) -> {
      int byCompany = store.normalizeCompanyCode(a.companyCode).compareTo(store.normalizeCompanyCode(b.companyCode));
      if (byCompany != 0) {
        return byCompany;
      }
      int byWorkshop = store.normalizeCode(a.workshopCode).compareTo(store.normalizeCode(b.workshopCode));
      if (byWorkshop != 0) {
        return byWorkshop;
      }
      return store.normalizeCode(a.lineCode).compareTo(store.normalizeCode(b.lineCode));
    });
    return out;
  }

  static MvpDomain.LineProcessBinding defaultLineBindingForProcess(MvpStoreLocalizationAndExportSupport store, String processCode) {
    String normalized = store.normalizeCode(processCode);
    String workshopCode = normalized.contains("STERILE") ? "WS-STERILE" : "WS-PRODUCTION";
    String lineCode = switch (normalized) {
      case "PROC_TUBE" -> "LINE-TUBE";
      case "PROC_ASSEMBLY" -> "LINE-ASSEMBLY";
      case "PROC_BALLOON" -> "LINE-BALLOON";
      case "PROC_STENT" -> "LINE-STENT";
      case "PROC_STERILE" -> "LINE-STERILE";
      default -> "LINE-MIXED";
    };
    MvpDomain.ProcessConfig processConfig = store.processConfigByCode(normalized);
    double capacityPerShift = processConfig == null ? 1d : Math.max(1d, processConfig.capacityPerShift);
    int requiredWorkers = processConfig == null ? 1 : Math.max(1, processConfig.requiredWorkers);
    int requiredMachines = processConfig == null ? 1 : Math.max(1, processConfig.requiredMachines);
    return new MvpDomain.LineProcessBinding(
      MvpStoreRuntimeBase.DEFAULT_COMPANY_CODE,
      workshopCode,
      lineCode,
      lineCode,
      normalized,
      true,
      capacityPerShift,
      requiredWorkers,
      requiredMachines
    );
  }
}

