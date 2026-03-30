package com.autoproduction.mvp.module.integration.erp.manager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ErpMaterialCodeUtils {
  private ErpMaterialCodeUtils() {}

  public static String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String text = value.trim();
    return text.isEmpty() ? null : text;
  }

  public static String stringValue(Map<String, Object> row, String key) {
    if (row == null || key == null || key.isBlank()) {
      return null;
    }
    Object value = row.get(key);
    return value == null ? null : String.valueOf(value);
  }

  public static boolean equalsCode(String left, String right) {
    String normalizedLeft = normalizeText(left);
    String normalizedRight = normalizeText(right);
    if (normalizedLeft == null || normalizedRight == null) {
      return false;
    }
    return normalizedLeft.equalsIgnoreCase(normalizedRight);
  }

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
}
