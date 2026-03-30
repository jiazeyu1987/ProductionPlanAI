package com.autoproduction.mvp.module.integration.erp.manager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ErpInventoryUtils {
  private ErpInventoryUtils() {}

  public static Map<String, Object> normalizeInventoryRow(String materialCode, Map<String, Object> row, boolean missing) {
    double stockQty = numberValue(
      row,
      "stock_qty",
      numberValue(row, "available_qty", numberValue(row, "qty", 0d))
    );
    String sourceTable = ErpMaterialCodeUtils.stringValue(row, "erp_source_table");
    String lastUpdateTime = ErpMaterialCodeUtils.stringValue(row, "last_update_time");
    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("material_code", materialCode);
    normalized.put("stock_qty", Math.max(0d, stockQty));
    normalized.put("erp_source_table", sourceTable == null ? "STK_Inventory" : sourceTable);
    normalized.put("last_update_time", lastUpdateTime == null ? OffsetDateTime.now(ZoneOffset.UTC).toString() : lastUpdateTime);
    normalized.put("inventory_missing_flag", missing ? 1 : 0);
    return normalized;
  }

  public static double numberValue(Map<String, Object> row, String key, double fallback) {
    if (row == null || key == null || key.isBlank()) {
      return fallback;
    }
    Object value = row.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (RuntimeException ex) {
      return fallback;
    }
  }
}
