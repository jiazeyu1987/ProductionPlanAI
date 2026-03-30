package com.autoproduction.mvp.core.erp.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ErpLoaderBillQueryUtils {
  private ErpLoaderBillQueryUtils() {}

  public static List<Map<String, Object>> normalizeBillQueryRows(List<?> rows, String fieldKeys) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    Object first = rows.get(0);
    if (first instanceof Map<?, ?> firstMap) {
      RuntimeException error = toExecuteBillQueryError(firstMap);
      if (error != null) {
        throw error;
      }
    }
    if (first instanceof List<?> firstList && !firstList.isEmpty() && firstList.get(0) instanceof Map<?, ?> firstInnerMap) {
      RuntimeException error = toExecuteBillQueryError(firstInnerMap);
      if (error != null) {
        throw error;
      }
    }
    return mapQueryRows(rows, fieldKeys);
  }

  public static RuntimeException toExecuteBillQueryError(Map<?, ?> responseMap) {
    Object result = responseMap.get("Result");
    if (!(result instanceof Map<?, ?> resultMap)) {
      return null;
    }
    Object status = resultMap.get("ResponseStatus");
    if (!(status instanceof Map<?, ?> statusMap)) {
      return null;
    }
    if (Boolean.TRUE.equals(statusMap.get("IsSuccess"))) {
      return null;
    }
    Object errors = statusMap.get("Errors");
    String message = errors == null ? String.valueOf(statusMap) : String.valueOf(errors);
    return new RuntimeException(message);
  }

  public static List<Map<String, Object>> mapQueryRows(List<?> rows, String fieldKeys) {
    List<String> fields = splitFieldKeys(fieldKeys);
    List<Map<String, Object>> mappedRows = new ArrayList<>();
    for (Object rowObj : rows) {
      if (rowObj instanceof Map<?, ?> mapRow) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : mapRow.entrySet()) {
          out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        mappedRows.add(out);
        continue;
      }
      if (rowObj instanceof List<?> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < fields.size() && i < values.size(); i += 1) {
          out.put(fields.get(i), values.get(i));
        }
        mappedRows.add(out);
      }
    }
    return mappedRows;
  }

  public static List<String> splitFieldKeys(String fieldKeys) {
    if (fieldKeys == null || fieldKeys.isBlank()) {
      return List.of();
    }
    String[] pieces = fieldKeys.split(",");
    List<String> fields = new ArrayList<>();
    for (String piece : pieces) {
      if (piece != null && !piece.isBlank()) {
        fields.add(piece.trim());
      }
    }
    return fields;
  }
}
