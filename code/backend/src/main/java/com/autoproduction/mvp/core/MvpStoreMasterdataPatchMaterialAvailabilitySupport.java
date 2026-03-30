package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MvpStoreMasterdataPatchMaterialAvailabilitySupport {
  private MvpStoreMasterdataPatchMaterialAvailabilitySupport() {}

  static void applyMaterialAvailabilityPatch(MvpStoreMasterdataPatchSupport store, List<Map<String, Object>> rows) {
    Set<String> validProcessCodes = new HashSet<>();
    for (MvpDomain.ProcessConfig process : store.state.processes) {
      validProcessCodes.add(store.normalizeCode(process.processCode));
    }

    Map<String, MvpDomain.MaterialRow> nextRowsByKey = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String dateText = store.string(row, "calendar_date", store.string(row, "date", null));
      if (dateText == null || dateText.isBlank()) {
        throw store.badRequest("calendar_date is required in material_availability.");
      }
      LocalDate date = store.parseConfigDate(dateText);
      String shiftCode = store.normalizeShiftCode(store.string(row, "shift_code", store.string(row, "shiftCode", null)));
      if (shiftCode.isBlank()) {
        throw store.badRequest("shift_code is required in material_availability.");
      }
      if (!store.isDateInCurrentHorizon(date) || !store.isShiftEnabledInCurrentSetting(shiftCode)) {
        continue;
      }
      String productCode = store.normalizeCode(store.string(row, "product_code", store.string(row, "productCode", null)));
      String processCode = store.normalizeCode(store.string(row, "process_code", store.string(row, "processCode", null)));
      if (productCode.isBlank() || processCode.isBlank()) {
        throw store.badRequest("product_code and process_code are required in material_availability.");
      }
      if (!validProcessCodes.contains(processCode)) {
        throw store.badRequest("Unknown process_code in material_availability: " + processCode);
      }
      double availableQty = store.number(row, "available_qty", store.number(row, "availableQty", -1d));
      if (availableQty < 0d) {
        throw store.badRequest("available_qty must be >= 0 in material_availability.");
      }

      String key = date + "#" + shiftCode + "#" + productCode + "#" + processCode;
      if (nextRowsByKey.containsKey(key)) {
        throw store.badRequest(
          "Duplicate date/shift/product/process in material_availability: "
            + date + "/" + store.normalizeShiftCodeLabel(shiftCode) + "/" + productCode + "/" + processCode
        );
      }
      nextRowsByKey.put(key, new MvpDomain.MaterialRow(date, shiftCode, productCode, processCode, store.round2(availableQty)));
    }

    List<MvpDomain.MaterialRow> nextRows = new ArrayList<>(nextRowsByKey.values());
    nextRows.sort((a, b) -> {
      int byDate = a.date.compareTo(b.date);
      if (byDate != 0) {
        return byDate;
      }
      int byShift = Integer.compare(store.shiftSortIndex(a.shiftCode), store.shiftSortIndex(b.shiftCode));
      if (byShift != 0) {
        return byShift;
      }
      int byProduct = store.normalizeCode(a.productCode).compareTo(store.normalizeCode(b.productCode));
      if (byProduct != 0) {
        return byProduct;
      }
      return store.normalizeCode(a.processCode).compareTo(store.normalizeCode(b.processCode));
    });
    store.state.materialAvailability = nextRows;
  }
}

