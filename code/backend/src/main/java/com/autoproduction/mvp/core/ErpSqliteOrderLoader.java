package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErpSqliteOrderLoader {
  private static final Logger log = LoggerFactory.getLogger(ErpSqliteOrderLoader.class);

  private final ObjectMapper objectMapper;
  private final boolean useRealOrders;
  private final String sqlitePath;

  public ErpSqliteOrderLoader(
    ObjectMapper objectMapper,
    @Value("${mvp.erp.use-real-orders:true}") boolean useRealOrders,
    @Value("${mvp.erp.sqlite-path:}") String sqlitePath
  ) {
    this.objectMapper = objectMapper;
    this.useRealOrders = useRealOrders;
    this.sqlitePath = sqlitePath;
  }

  public List<Map<String, Object>> loadSalesOrderLines() {
    List<ErpRecord> records = loadRecords("sales_orders_raw", "SaleOrderEntry");
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpRecord record : records) {
      for (int i = 0; i < record.lines.size(); i += 1) {
        Map<String, Object> line = record.lines.get(i);
        rows.add(toSalesLineRow(record, line, i + 1));
      }
    }
    return rows;
  }

  public List<Map<String, Object>> loadProductionOrders() {
    List<ErpRecord> records = loadRecords("production_orders_raw", "TreeEntity");
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpRecord record : records) {
      for (int i = 0; i < record.lines.size(); i += 1) {
        Map<String, Object> line = record.lines.get(i);
        rows.add(toProductionOrderRow(record, line, i + 1));
      }
    }
    return rows;
  }

  public List<Map<String, Object>> loadSalesOrderHeadersRaw() {
    return toHeaderRawRows(loadRecords("sales_orders_raw", "SaleOrderEntry"));
  }

  public List<Map<String, Object>> loadSalesOrderLinesRaw() {
    return toLineRawRows(loadRecords("sales_orders_raw", "SaleOrderEntry"));
  }

  public List<Map<String, Object>> loadProductionOrderHeadersRaw() {
    return toHeaderRawRows(loadRecords("production_orders_raw", "TreeEntity"));
  }

  public List<Map<String, Object>> loadProductionOrderLinesRaw() {
    return toLineRawRows(loadRecords("production_orders_raw", "TreeEntity"));
  }

  private List<Map<String, Object>> toHeaderRawRows(List<ErpRecord> records) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpRecord record : records) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("erp_source_table", record.tableName);
      row.put("erp_record_id", record.recordId);
      row.put("erp_run_id", record.base.get("run_id"));
      row.put("erp_fid", record.base.get("fid"));
      row.put("erp_bill_no", record.base.get("bill_no"));
      row.put("erp_bill_date", record.base.get("bill_date"));
      row.put("erp_modify_date", record.base.get("modify_date"));
      row.put("erp_document_status", record.base.get("document_status"));
      row.put("erp_org_no", record.base.get("org_no"));
      row.put("erp_fetched_at", record.base.get("fetched_at"));
      row.put("erp_form_id", firstText(record.header.get("FFormId")));
      row.put("erp_header_json", record.payloadJson);
      rows.add(row);
    }
    return rows;
  }

  private List<Map<String, Object>> toLineRawRows(List<ErpRecord> records) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpRecord record : records) {
      for (int i = 0; i < record.lines.size(); i += 1) {
        Map<String, Object> line = record.lines.get(i);
        int lineNo = i + 1;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("erp_source_table", record.tableName);
        row.put("erp_record_id", record.recordId);
        row.put("erp_line_no", String.valueOf(lineNo));
        row.put("erp_line_id", buildLineId(record.recordId, lineNo));
        row.put("erp_bill_no", record.base.get("bill_no"));
        row.put("erp_fid", record.base.get("fid"));
        row.put("erp_form_id", firstText(record.header.get("FFormId")));
        row.put("erp_line_json", toJsonString(line));
        rows.add(row);
      }
    }
    return rows;
  }

  private Map<String, Object> toSalesLineRow(ErpRecord record, Map<String, Object> line, int lineNo) {
    String salesOrderNo = firstText(record.header.get("BillNo"), record.base.get("bill_no"));
    String orderDate = firstText(record.header.get("Date"), record.base.get("bill_date"));
    String expectedDueDate = firstText(line.get("DeliveryDate"), firstPlanDeliveryDate(line), orderDate);
    String requestedShipDate = firstText(line.get("DeliveryDate"), expectedDueDate);
    String productCode = firstText(materialNumber(line), line.get("MaterialId_Id"), "UNKNOWN");
    Number qty = toNumber(line.get("Qty"));
    Integer urgentFlag = toInt(line.get("Priority")) > 0 ? 1 : 0;
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("sales_order_no", salesOrderNo);
    row.put("line_no", firstText(line.get("Seq"), String.valueOf(lineNo)));
    row.put("product_code", productCode);
    row.put("order_qty", qty == null ? 0d : qty);
    row.put("order_date", orderDate);
    row.put("expected_due_date", expectedDueDate);
    row.put("requested_ship_date", requestedShipDate);
    row.put("urgent_flag", urgentFlag);
    row.put("order_status", firstText(record.header.get("DocumentStatus"), record.base.get("document_status")));
    row.put("last_update_time", firstText(record.base.get("fetched_at"), now));
    appendErpRefColumns(row, record, lineNo);
    return row;
  }

  private Map<String, Object> toProductionOrderRow(ErpRecord record, Map<String, Object> line, int lineNo) {
    String productionOrderNo = firstText(record.header.get("BillNo"), record.base.get("bill_no"));
    String sourceSalesOrderNo = firstText(line.get("SaleOrderNo"), line.get("SrcBillNo"), "UNLINKED-" + productionOrderNo);
    String sourceLineNo = firstText(line.get("SaleOrderEntrySeq"), line.get("Seq"), String.valueOf(lineNo));
    String bomNumber = firstText(mapValue(line.get("BomId"), "Number"));
    String productCode = firstText(materialNumber(line), line.get("MaterialId_Id"), "UNKNOWN");
    String productNameCn = firstText(materialNameCn(line));
    Number planQty = toNumber(line.get("Qty"));
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("production_order_no", productionOrderNo);
    row.put("source_sales_order_no", sourceSalesOrderNo);
    row.put("source_line_no", sourceLineNo);
    row.put("source_plan_order_no", "PLAN-" + productionOrderNo);
    row.put("material_list_no", bomNumber == null ? "BOM-" + productionOrderNo : bomNumber);
    row.put("product_code", productCode);
    row.put("product_name_cn", productNameCn);
    row.put("plan_qty", planQty == null ? 0d : planQty);
    row.put("production_status", firstText(record.header.get("DocumentStatus"), record.base.get("document_status")));
    row.put("order_date", firstText(record.header.get("Date"), record.base.get("bill_date")));
    row.put("customer_remark", firstText(record.header.get("Description"), line.get("FRemarks")));
    row.put("spec_model", firstText(materialSpecification(line)));
    row.put("production_batch_no", firstText(line.get("Lot_Text"), line.get("Lot")));
    row.put("planned_finish_date_1", firstText(line.get("PlanStartDate")));
    row.put("planned_finish_date_2", firstText(line.get("PlanFinishDate")));
    row.put("production_date_foreign_trade", firstText(line.get("ProduceDate")));
    row.put("packaging_form", firstText(line.get("F_PAEZ_Text5"), line.get("F_PAEZ_Text1")));
    row.put("sales_order_no", firstText(line.get("SaleOrderNo"), line.get("SrcBillNo")));
    row.put("purchase_due_date", null);
    row.put("injection_due_date", null);
    row.put("market_remark_info", firstText(record.header.get("Note"), record.header.get("Description")));
    row.put("market_demand", null);
    row.put("semi_finished_code", null);
    row.put("semi_finished_inventory", null);
    row.put("semi_finished_demand", null);
    row.put("semi_finished_wip", null);
    row.put("need_order_qty", null);
    row.put("pending_inbound_qty", null);
    row.put("weekly_monthly_process_plan", null);
    row.put("workshop_outer_packaging_date", null);
    row.put("note", firstText(line.get("FRemarks"), record.header.get("Description")));
    row.put("workshop_completed_qty", toNumber(line.get("RepQuaQty")));
    row.put("workshop_completed_time", firstText(line.get("FinishDate")));
    row.put("outer_completed_qty", null);
    row.put("outer_completed_time", null);
    row.put("match_status", null);
    row.put("last_update_time", firstText(record.base.get("fetched_at"), now));
    appendErpRefColumns(row, record, lineNo);
    return row;
  }

  private void appendErpRefColumns(Map<String, Object> row, ErpRecord record, int lineNo) {
    row.put("erp_source_table", record.tableName);
    row.put("erp_record_id", record.recordId);
    row.put("erp_line_no", String.valueOf(lineNo));
    row.put("erp_line_id", buildLineId(record.recordId, lineNo));
    row.put("erp_run_id", record.base.get("run_id"));
    row.put("erp_fid", record.base.get("fid"));
    row.put("erp_form_id", firstText(record.header.get("FFormId")));
    row.put("erp_document_status", record.base.get("document_status"));
    row.put("erp_fetched_at", record.base.get("fetched_at"));
  }

  private List<ErpRecord> loadRecords(String tableName, String lineListField) {
    if (!useRealOrders || sqlitePath == null || sqlitePath.isBlank()) {
      return List.of();
    }
    Path path = Path.of(sqlitePath);
    if (!Files.exists(path)) {
      return List.of();
    }
    String sql = "SELECT run_id, fid, bill_no, bill_date, modify_date, document_status, org_no, payload_json, fetched_at"
      + " FROM " + tableName + " ORDER BY fid DESC";
    List<ErpRecord> rows = new ArrayList<>();
    try (
      Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
      PreparedStatement statement = connection.prepareStatement(sql);
      ResultSet resultSet = statement.executeQuery()
    ) {
      while (resultSet.next()) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("run_id", resultSet.getLong("run_id"));
        base.put("fid", resultSet.getLong("fid"));
        base.put("bill_no", resultSet.getString("bill_no"));
        base.put("bill_date", resultSet.getString("bill_date"));
        base.put("modify_date", resultSet.getString("modify_date"));
        base.put("document_status", resultSet.getString("document_status"));
        base.put("org_no", resultSet.getString("org_no"));
        base.put("fetched_at", resultSet.getString("fetched_at"));
        String payloadJson = resultSet.getString("payload_json");
        Map<String, Object> header = parseMap(payloadJson);
        List<Map<String, Object>> lines = parseLineList(header.get(lineListField));
        if (lines.isEmpty()) {
          lines = List.of(new LinkedHashMap<>());
        }
        rows.add(new ErpRecord(
          tableName,
          buildRecordId(tableName, resultSet.getLong("fid")),
          base,
          header,
          lines,
          payloadJson
        ));
      }
      return rows;
    } catch (Exception ex) {
      log.warn("Failed to load ERP real orders from sqlite file: {}", path, ex);
      return List.of();
    }
  }

  private static String buildRecordId(String tableName, long fid) {
    return tableName + ":" + fid;
  }

  private static String buildLineId(String recordId, int lineNo) {
    return recordId + ":" + lineNo;
  }

  private Map<String, Object> parseMap(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(payloadJson, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception ex) {
      return new LinkedHashMap<>();
    }
  }

  private List<Map<String, Object>> parseLineList(Object value) {
    if (!(value instanceof List<?> listValue)) {
      return List.of();
    }
    List<Map<String, Object>> lines = new ArrayList<>();
    for (Object item : listValue) {
      if (item instanceof Map<?, ?> source) {
        Map<String, Object> line = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
          line.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        lines.add(line);
      }
    }
    return lines;
  }

  private String firstPlanDeliveryDate(Map<String, Object> line) {
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

  private Object mapValue(Object source, String key) {
    if (!(source instanceof Map<?, ?> map)) {
      return null;
    }
    return map.get(key);
  }

  private String materialNumber(Map<String, Object> line) {
    Object material = line.get("MaterialId");
    if (material instanceof Map<?, ?> materialMap) {
      return firstText(materialMap.get("Number"));
    }
    return null;
  }

  private String materialNameCn(Map<String, Object> line) {
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

  private String materialSpecification(Map<String, Object> line) {
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

  private String firstText(Object... values) {
    for (Object value : values) {
      if (value == null) {
        continue;
      }
      String text = String.valueOf(value).trim();
      if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
        return text;
      }
    }
    return null;
  }

  private Number toNumber(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    String text = firstText(value);
    if (text == null) {
      return null;
    }
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private int toInt(Object value) {
    Number number = toNumber(value);
    return number == null ? 0 : number.intValue();
  }

  private String toJsonString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return value == null ? null : String.valueOf(value);
    }
  }

  private record ErpRecord(
    String tableName,
    String recordId,
    Map<String, Object> base,
    Map<String, Object> header,
    List<Map<String, Object>> lines,
    String payloadJson
  ) {}
}
