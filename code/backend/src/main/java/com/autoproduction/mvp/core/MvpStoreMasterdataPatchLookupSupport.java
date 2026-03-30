package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class MvpStoreMasterdataPatchLookupSupport {
  private MvpStoreMasterdataPatchLookupSupport() {}

  static List<MvpDomain.ResourceRow> filterResourceRowsByProcessCodes(
    MvpStoreMasterdataPatchSupport store,
    List<MvpDomain.ResourceRow> rows,
    Set<String> validProcessCodes
  ) {
    List<MvpDomain.ResourceRow> nextRows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (MvpDomain.ResourceRow row : rows) {
      String processCode = store.normalizeCode(row.processCode);
      if (!validProcessCodes.contains(processCode)) {
        continue;
      }
      String shiftCode = store.normalizeShiftCode(row.shiftCode);
      String key = row.date + "#" + shiftCode + "#" + processCode;
      if (!seen.add(key)) {
        continue;
      }
      nextRows.add(new MvpDomain.ResourceRow(row.date, shiftCode, processCode, Math.max(0, row.available)));
    }
    nextRows.sort((a, b) -> {
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
    return nextRows;
  }

  static Set<String> routeProcessCodeSet(MvpStoreMasterdataPatchSupport store) {
    Set<String> out = new HashSet<>();
    for (List<MvpDomain.ProcessStep> steps : store.state.processRoutes.values()) {
      if (steps == null) {
        continue;
      }
      for (MvpDomain.ProcessStep step : steps) {
        String processCode = store.normalizeCode(step.processCode);
        if (!processCode.isBlank()) {
          out.add(processCode);
        }
      }
    }
    return out;
  }

  static MvpDomain.ProcessConfig processConfigByCode(MvpStoreMasterdataPatchSupport store, String processCode) {
    String normalized = store.normalizeCode(processCode);
    if (normalized.isBlank()) {
      return null;
    }
    for (MvpDomain.ProcessConfig process : store.state.processes) {
      if (store.normalizeCode(process.processCode).equals(normalized)) {
        return process;
      }
    }
    return null;
  }

  static MvpDomain.ResourceRow findResourceRow(
    MvpStoreMasterdataPatchSupport store,
    List<MvpDomain.ResourceRow> pool,
    LocalDate date,
    String shiftCode,
    String processCode
  ) {
    for (MvpDomain.ResourceRow row : pool) {
      if (
        Objects.equals(row.date, date)
          && store.normalizeShiftCode(row.shiftCode).equals(store.normalizeShiftCode(shiftCode))
          && store.normalizeCode(row.processCode).equals(store.normalizeCode(processCode))
      ) {
        return row;
      }
    }
    return null;
  }

  static MvpDomain.ShiftRow findShiftRow(MvpStoreMasterdataPatchSupport store, LocalDate date, String shiftCode) {
    for (MvpDomain.ShiftRow row : store.state.shiftCalendar) {
      if (Objects.equals(row.date, date) && store.normalizeShiftCode(row.shiftCode).equals(store.normalizeShiftCode(shiftCode))) {
        return row;
      }
    }
    return null;
  }

  static MvpDomain.MaterialRow findMaterialRow(
    MvpStoreMasterdataPatchSupport store,
    LocalDate date,
    String shiftCode,
    String productCode,
    String processCode
  ) {
    for (MvpDomain.MaterialRow row : store.state.materialAvailability) {
      if (
        Objects.equals(row.date, date)
          && store.normalizeShiftCode(row.shiftCode).equals(store.normalizeShiftCode(shiftCode))
          && store.normalizeCode(row.productCode).equals(store.normalizeCode(productCode))
          && store.normalizeCode(row.processCode).equals(store.normalizeCode(processCode))
      ) {
        return row;
      }
    }
    return null;
  }
}

