package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.Map;

final class MvpMaterialRowAssembler {

  private MvpMaterialRowAssembler() {}

  static Map<String, Object> buildOrderMaterialRow(
      String orderNo,
      String materialListNo,
      String childMaterialCode,
      Map<String, Object> rawRow,
      Map<String, Object> supplyInfo) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("order_no", orderNo);
    row.put("material_list_no", materialListNo);
    row.put("child_material_code", childMaterialCode);
    row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
    row.put("required_qty", number(rawRow, "required_qty", 0d));
    row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
    row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
    row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
    row.put("issue_date", string(rawRow, "issue_date", ""));
    row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
    row.put(
        "child_material_supply_type_name_cn",
        string(supplyInfo, "supply_type_name_cn", "\u672A\u77E5"));
    return row;
  }

  static Map<String, Object> buildParentMaterialChildRow(
      String parentMaterialCode,
      String childMaterialCode,
      Map<String, Object> rawRow,
      Map<String, Object> supplyInfo) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("parent_material_code", parentMaterialCode);
    row.put("child_material_code", childMaterialCode);
    row.put("child_material_name_cn", string(rawRow, "child_material_name_cn", ""));
    row.put("required_qty", number(rawRow, "required_qty", 0d));
    row.put("source_bill_no", string(rawRow, "source_bill_no", ""));
    row.put("source_bill_type", string(rawRow, "source_bill_type", ""));
    row.put("pick_material_bill_no", string(rawRow, "pick_material_bill_no", ""));
    row.put("issue_date", string(rawRow, "issue_date", ""));
    row.put("child_material_supply_type", string(supplyInfo, "supply_type", "UNKNOWN"));
    row.put(
        "child_material_supply_type_name_cn",
        string(supplyInfo, "supply_type_name_cn", "\u672A\u77E5"));
    return row;
  }

  private static String string(Map<String, Object> row, String key, String defaultValue) {
    if (row == null || key == null) {
      return defaultValue;
    }
    Object value = row.get(key);
    if (value == null) {
      return defaultValue;
    }
    String text = value.toString().trim();
    return text.isEmpty() ? defaultValue : text;
  }

  private static double number(Map<String, Object> row, String key, double defaultValue) {
    if (row == null || key == null) {
      return defaultValue;
    }
    Object value = row.get(key);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString().trim());
    } catch (Exception ignored) {
      return defaultValue;
    }
  }
}
