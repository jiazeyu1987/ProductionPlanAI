package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;

import com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils;
import java.util.List;
import java.util.Map;

abstract class ErpSqliteOrderRowMapperSupport {
  Object mapValue(Object source, String key) {
    if (!(source instanceof Map<?, ?> map)) {
      return null;
    }
    return map.get(key);
  }

  String masterDataNameCn(Object source) {
    if (!(source instanceof Map<?, ?> sourceMap)) {
      return null;
    }
    Object name = sourceMap.get("Name");
    if (name instanceof List<?> nameList && !nameList.isEmpty()) {
      Object first = nameList.get(0);
      if (first instanceof Map<?, ?> firstMap) {
        String value = firstText(firstMap.get("Value"), firstMap.get("Name"));
        if (value != null) {
          return value;
        }
      }
    }
    Object multi = sourceMap.get("MultiLanguageText");
    if (multi instanceof List<?> multiList && !multiList.isEmpty()) {
      Object first = multiList.get(0);
      if (first instanceof Map<?, ?> firstMap) {
        String value = firstText(firstMap.get("Name"), firstMap.get("Value"));
        if (value != null) {
          return value;
        }
      }
    }
    return firstText(sourceMap.get("Name"));
  }

  protected void appendErpRefColumns(Map<String, Object> row, ErpSqliteOrderRecord record, int lineNo) {
    row.put("erp_source_table", record.tableName());
    row.put("erp_record_id", record.recordId());
    row.put("erp_line_no", String.valueOf(lineNo));
    row.put("erp_line_id", ErpSqliteOrderRecord.buildLineId(record.recordId(), lineNo));
    row.put("erp_run_id", record.base().get("run_id"));
    row.put("erp_fid", record.base().get("fid"));
    row.put("erp_form_id", firstText(record.header().get("FFormId")));
    row.put("erp_document_status", record.base().get("document_status"));
    row.put("erp_fetched_at", record.base().get("fetched_at"));
  }

  protected String resolveMaterialSupplyType(boolean isPurchase, boolean isProduce, String typeText, String materialCode) {
    if (isProduce && !isPurchase) {
      return "SELF_MADE";
    }
    if (isPurchase && !isProduce) {
      return "PURCHASED";
    }
    String normalizedTypeText = firstText(typeText);
    if (normalizedTypeText != null) {
      String upper = normalizedTypeText.toUpperCase();
      if (normalizedTypeText.contains("自制") || upper.contains("MAKE") || upper.contains("PRODUCE")) {
        return "SELF_MADE";
      }
      if (
        normalizedTypeText.contains("外购")
          || normalizedTypeText.contains("采购")
          || upper.contains("PURCHASE")
          || upper.contains("BUY")
      ) {
        return "PURCHASED";
      }
    }
    String normalizedCode = firstText(materialCode);
    if (normalizedCode != null && normalizedCode.startsWith("YXN.")) {
      return "SELF_MADE";
    }
    return "UNKNOWN";
  }

  protected String firstPlanDeliveryDate(Map<String, Object> line) {
    Object plans = line.get("OrderEntryPlan");
    if (!(plans instanceof List<?> listValue) || listValue.isEmpty()) {
      return null;
    }
    Object first = listValue.get(0);
    if (!(first instanceof Map<?, ?> mapValue)) {
      return null;
    }
    return firstText(mapValue.get("PlanDeliveryDate"), mapValue.get("PlanDate"));
  }

  protected String materialNumber(Map<String, Object> line) {
    Object material = line.get("MaterialId");
    if (material instanceof Map<?, ?> materialMap) {
      return firstText(materialMap.get("Number"));
    }
    return null;
  }

  protected String materialNameCn(Map<String, Object> line) {
    Object material = line.get("MaterialId");
    if (!(material instanceof Map<?, ?> materialMap)) {
      return null;
    }
    Object name = materialMap.get("Name");
    if (name instanceof List<?> nameList && !nameList.isEmpty()) {
      Object first = nameList.get(0);
      if (first instanceof Map<?, ?> firstMap) {
        String value = firstText(firstMap.get("Value"));
        if (value != null) {
          return value;
        }
      }
    }
    Object multi = materialMap.get("MultiLanguageText");
    if (multi instanceof List<?> multiList && !multiList.isEmpty()) {
      Object first = multiList.get(0);
      if (first instanceof Map<?, ?> firstMap) {
        return firstText(firstMap.get("Name"));
      }
    }
    return null;
  }

  protected String materialSpecification(Map<String, Object> line) {
    Object material = line.get("MaterialId");
    if (!(material instanceof Map<?, ?> materialMap)) {
      return null;
    }
    Object specification = materialMap.get("Specification");
    if (specification instanceof List<?> specs && !specs.isEmpty()) {
      Object first = specs.get(0);
      if (first instanceof Map<?, ?> firstMap) {
        return firstText(firstMap.get("Value"));
      }
    }
    return firstText(specification);
  }

  protected Number firstNonNullNumber(Number... values) {
    return ErpLoaderValueUtils.firstNonNullNumber(values);
  }
}
