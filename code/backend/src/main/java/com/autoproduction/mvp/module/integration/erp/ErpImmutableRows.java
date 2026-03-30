package com.autoproduction.mvp.module.integration.erp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ErpImmutableRows {
  private ErpImmutableRows() {}

  static List<Map<String, Object>> safeRows(List<Map<String, Object>> rows) {
    return rows == null ? List.of() : rows;
  }

  static List<Map<String, Object>> freezeRows(List<Map<String, Object>> rows) {
    List<Map<String, Object>> frozen = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      frozen.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
    }
    return Collections.unmodifiableList(frozen);
  }

  static Map<String, Object> freezeRow(Map<String, Object> row) {
    if (row == null || row.isEmpty()) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(row));
  }
}

