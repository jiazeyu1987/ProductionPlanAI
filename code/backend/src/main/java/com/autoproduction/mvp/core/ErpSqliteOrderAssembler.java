package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstNonNullNumber;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.round3;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ErpSqliteOrderAssembler {
  private final ObjectMapper objectMapper;

  ErpSqliteOrderAssembler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  List<Map<String, Object>> toHeaderRawRows(List<ErpSqliteOrderRecord> records) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpSqliteOrderRecord record : records) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("erp_source_table", record.tableName());
      row.put("erp_record_id", record.recordId());
      row.put("erp_run_id", record.base().get("run_id"));
      row.put("erp_fid", record.base().get("fid"));
      row.put("erp_bill_no", record.base().get("bill_no"));
      row.put("erp_bill_date", record.base().get("bill_date"));
      row.put("erp_modify_date", record.base().get("modify_date"));
      row.put("erp_document_status", record.base().get("document_status"));
      row.put("erp_org_no", record.base().get("org_no"));
      row.put("erp_fetched_at", record.base().get("fetched_at"));
      row.put("erp_form_id", firstText(record.header().get("FFormId")));
      row.put("erp_header_json", record.payloadJson());
      rows.add(row);
    }
    return rows;
  }

  List<Map<String, Object>> toLineRawRows(List<ErpSqliteOrderRecord> records) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpSqliteOrderRecord record : records) {
      for (int i = 0; i < record.lines().size(); i += 1) {
        Map<String, Object> line = record.lines().get(i);
        int lineNo = i + 1;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("erp_source_table", record.tableName());
        row.put("erp_record_id", record.recordId());
        row.put("erp_line_no", String.valueOf(lineNo));
        row.put("erp_line_id", ErpSqliteOrderRecord.buildLineId(record.recordId(), lineNo));
        row.put("erp_bill_no", record.base().get("bill_no"));
        row.put("erp_fid", record.base().get("fid"));
        row.put("erp_form_id", firstText(record.header().get("FFormId")));
        row.put("erp_line_json", toJsonString(line));
        rows.add(row);
      }
    }
    return rows;
  }

  void mergeInventoryRows(Map<String, Double> stockByCode, List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    for (Map<String, Object> row : rows) {
      String materialCode = firstText(
        row.get("FMaterialId.FNumber"),
        row.get("FMaterialId_Number"),
        row.get("FMATERIALID.FNUMBER"),
        row.get("material_code")
      );
      if (materialCode == null) {
        continue;
      }
      String normalizedMaterialCode = materialCode.trim().toUpperCase();
      Number stockQty = firstNonNullNumber(
        toNumber(row.get("FAVBQty")),
        toNumber(row.get("FCanUseQty")),
        toNumber(row.get("FStockQty")),
        toNumber(row.get("FBaseQty")),
        toNumber(row.get("FQty")),
        toNumber(row.get("stock_qty"))
      );
      double value = stockQty == null ? 0d : Math.max(0d, stockQty.doubleValue());
      stockByCode.merge(normalizedMaterialCode, value, Double::sum);
    }
  }

  List<Map<String, Object>> buildInventoryRowsFromAggregate(Map<String, Double> stockByCode, Set<String> requestedCodes) {
    List<String> sortedCodes = new ArrayList<>(requestedCodes == null ? Set.of() : requestedCodes);
    sortedCodes.sort(String::compareTo);
    List<Map<String, Object>> rows = new ArrayList<>();
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
    for (String code : sortedCodes) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("material_code", code);
      row.put("stock_qty", round3(Math.max(0d, stockByCode.getOrDefault(code, 0d))));
      row.put("last_update_time", now);
      row.put("erp_source_table", "STK_Inventory");
      rows.add(row);
    }
    return rows;
  }

  private String toJsonString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return value == null ? null : String.valueOf(value);
    }
  }
}
