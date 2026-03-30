package com.autoproduction.mvp.module.integration.erp.manager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ErpMaterialDataUtils {
  private ErpMaterialDataUtils() {}

  public static boolean existsMaterialCode(List<Map<String, Object>> rows, List<String> keys, String materialCode) {
    if (rows == null || rows.isEmpty() || keys == null || keys.isEmpty()) {
      return false;
    }
    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      for (String key : keys) {
        if (equalsCode(materialCode, stringValue(row, key))) {
          return true;
        }
      }
    }
    return false;
  }

  public static String stringValue(Map<String, Object> row, String key) {
    if (row == null || key == null || key.isBlank()) {
      return null;
    }
    Object value = row.get(key);
    return value == null ? null : String.valueOf(value);
  }

  public static boolean isKnownSupplyType(String supplyType) {
    if (supplyType == null) {
      return false;
    }
    return "PURCHASED".equalsIgnoreCase(supplyType) || "SELF_MADE".equalsIgnoreCase(supplyType);
  }

  public static String inferMaterialSupplyTypeByCode(String materialCode) {
    String code = normalizeText(materialCode);
    if (code == null) {
      return null;
    }
    String normalized = code.toUpperCase();
    if (normalized.startsWith("A001.") || normalized.startsWith("A002.")) {
      return "PURCHASED";
    }
    return null;
  }

  public static String toSupplyTypeNameCn(String supplyType) {
    if ("PURCHASED".equalsIgnoreCase(supplyType)) {
      return "\u5916\u8D2D";
    }
    if ("SELF_MADE".equalsIgnoreCase(supplyType)) {
      return "\u81EA\u5236";
    }
    return "\u672A\u77E5";
  }

  public static Set<String> normalizeMaterialCodeSet(List<String> materialCodes) {
    if (materialCodes == null || materialCodes.isEmpty()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    for (String code : materialCodes) {
      String normalized = normalizeText(code);
      if (normalized != null) {
        out.add(normalized);
      }
    }
    return out;
  }

  public static Map<String, Object> normalizeInventoryRow(String materialCode, Map<String, Object> row, boolean missing) {
    double stockQty = numberValue(
      row,
      "stock_qty",
      numberValue(row, "available_qty", numberValue(row, "qty", 0d))
    );
    String sourceTable = stringValue(row, "erp_source_table");
    String lastUpdateTime = stringValue(row, "last_update_time");
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

  private static boolean equalsCode(String left, String right) {
    String normalizedLeft = normalizeText(left);
    String normalizedRight = normalizeText(right);
    if (normalizedLeft == null || normalizedRight == null) {
      return false;
    }
    return normalizedLeft.equalsIgnoreCase(normalizedRight);
  }

  private static String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String text = value.trim();
    return text.isEmpty() ? null : text;
  }
}
