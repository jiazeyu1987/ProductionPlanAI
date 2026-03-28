package com.autoproduction.mvp.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErpSqliteOrderLoader {
  private static final Logger log = LoggerFactory.getLogger(ErpSqliteOrderLoader.class);
  private static final String DEFAULT_ERP_ORG_NO = "881";

  private final ObjectMapper objectMapper;
  private final boolean useRealOrders;
  private final String sqlitePath;
  private final String baseUrl;
  private final String acctId;
  private final String username;
  private final String password;
  private final int lcid;
  private final int timeoutSeconds;
  private final boolean verifySsl;
  private final String productionOrderCsvPath;
  private final String erpDemoOutDir;
  private final HttpClient httpClient;
  private final Map<String, List<Map<String, Object>>> ppBomViewEntryCache = new ConcurrentHashMap<>();
  private volatile boolean apiLoggedIn;

  @Autowired
  public ErpSqliteOrderLoader(
    ObjectMapper objectMapper,
    @Value("${mvp.erp.use-real-orders:true}") boolean useRealOrders,
    @Value("${mvp.erp.sqlite-path:}") String sqlitePath,
    @Value("${mvp.erp.base-url:${ERP_BASE_URL:}}") String baseUrl,
    @Value("${mvp.erp.acct-id:${ERP_ACCT_ID:}}") String acctId,
    @Value("${mvp.erp.username:${ERP_USERNAME:}}") String username,
    @Value("${mvp.erp.password:${ERP_PASSWORD:}}") String password,
    @Value("${mvp.erp.lcid:${ERP_LCID:2052}}") int lcid,
    @Value("${mvp.erp.timeout:${ERP_TIMEOUT:30}}") int timeoutSeconds,
    @Value("${mvp.erp.verify-ssl:${ERP_VERIFY_SSL:true}}") boolean verifySsl,
    @Value("${mvp.erp.production-order-csv-path:}") String productionOrderCsvPath,
    @Value("${mvp.erp.demo-out-dir:D:/ProjectPackage/demo/other}") String erpDemoOutDir
  ) {
    this.objectMapper = objectMapper;
    this.useRealOrders = useRealOrders;
    this.sqlitePath = sqlitePath;
    this.baseUrl = trimToEmpty(baseUrl);
    this.acctId = trimToEmpty(acctId);
    this.username = trimToEmpty(username);
    this.password = password == null ? "" : password;
    this.lcid = lcid;
    this.timeoutSeconds = Math.max(1, timeoutSeconds);
    this.verifySsl = verifySsl;
    this.productionOrderCsvPath = trimToEmpty(productionOrderCsvPath);
    this.erpDemoOutDir = trimToEmpty(erpDemoOutDir);
    this.httpClient = buildHttpClient(this.verifySsl, this.timeoutSeconds);
    this.apiLoggedIn = false;
  }

  ErpSqliteOrderLoader(ObjectMapper objectMapper, boolean useRealOrders, String sqlitePath) {
    this(objectMapper, useRealOrders, sqlitePath, "", "", "", "", 2052, 30, true, "", "D:/ProjectPackage/demo/other");
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
    if (!rows.isEmpty()) {
      return rows;
    }
    if (hasLocalSqliteSnapshot()) {
      return rows;
    }
    rows = loadProductionOrdersFromApi();
    if (!rows.isEmpty()) {
      return rows;
    }
    return loadProductionOrdersFromCsv();
  }

  public List<Map<String, Object>> loadProductionMaterialIssuesByOrder(String productionOrderNo, String materialListNo) {
    return loadProductionMaterialIssuesByOrder(productionOrderNo, materialListNo, false);
  }

  public List<Map<String, Object>> loadProductionMaterialIssuesByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    if (forceRefresh) {
      ppBomViewEntryCache.clear();
    }
    String normalizedOrderNo = firstText(productionOrderNo);
    String normalizedMaterialListNo = firstText(materialListNo);
    if (normalizedOrderNo == null && normalizedMaterialListNo == null) {
      return List.of();
    }
    List<Map<String, Object>> rows = loadProductionMaterialIssuesFromApiByOrder(normalizedOrderNo, normalizedMaterialListNo);
    if (!rows.isEmpty()) {
      return rows;
    }
    return loadProductionMaterialIssuesFromCsvByOrder(normalizedOrderNo, normalizedMaterialListNo);
  }

  private Object viewBillFromApi(String formId, String number, String id) {
    String normalizedFormId = firstText(formId);
    if (normalizedFormId == null) {
      return Map.of();
    }
    String service = "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc";

    List<Map<String, Object>> payloads = new ArrayList<>();
    if (number != null && id != null) {
      payloads.add(Map.of("FormId", normalizedFormId, "Number", number, "Id", id));
    }
    if (number != null) {
      payloads.add(Map.of("FormId", normalizedFormId, "Number", number));
    }
    if (id != null) {
      payloads.add(Map.of("FormId", normalizedFormId, "Id", id));
    }
    payloads.add(Map.of("FormId", normalizedFormId));

    RuntimeException lastEx = null;
    for (Map<String, Object> payload : payloads) {
      Map<String, Object> payloadWithoutFormId = new LinkedHashMap<>(payload);
      payloadWithoutFormId.remove("FormId");
      String payloadJson;
      String payloadNoFormIdJson;
      try {
        payloadJson = objectMapper.writeValueAsString(payload);
        payloadNoFormIdJson = objectMapper.writeValueAsString(payloadWithoutFormId);
      } catch (Exception ex) {
        continue;
      }
      List<Map<String, String>> wrappers = List.of(
        Map.of("data", payloadJson),
        Map.of("formid", normalizedFormId, "data", payloadNoFormIdJson),
        Map.of("FormId", normalizedFormId, "data", payloadNoFormIdJson),
        Map.of("formId", normalizedFormId, "data", payloadNoFormIdJson)
      );
      for (String url : serviceUrls(service)) {
        for (Map<String, String> wrapper : wrappers) {
          try {
            Object parsed = postFormForJson(url, wrapper);
            if (!(parsed instanceof Map<?, ?> map)) {
              return parsed;
            }
            Object statusObj = mapValue(mapValue(map, "Result"), "ResponseStatus");
            if (!(statusObj instanceof Map<?, ?> statusMap)) {
              return parsed;
            }
            if (Boolean.TRUE.equals(statusMap.get("IsSuccess"))) {
              return parsed;
            }
            lastEx = new RuntimeException(String.valueOf(statusMap.get("Errors")));
          } catch (RuntimeException ex) {
            lastEx = ex;
          }
        }
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    return Map.of();
  }

  public List<Map<String, Object>> loadPurchaseOrders() {
    List<ErpRecord> records = loadRecords(
      "purchase_orders_raw",
      List.of("POOrderEntry", "PurchaseOrderEntry", "PUR_POOrderEntry", "TreeEntity", "FEntity")
    );
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpRecord record : records) {
      for (int i = 0; i < record.lines.size(); i += 1) {
        Map<String, Object> line = record.lines.get(i);
        rows.add(toPurchaseOrderRow(record, line, i + 1));
      }
    }
    if (!rows.isEmpty()) {
      return rows;
    }
    if (hasLocalSqliteSnapshot()) {
      return rows;
    }
    return loadPurchaseOrdersFromApi();
  }

  public Map<String, Object> loadMaterialSupplyInfo(String materialCode) {
    String normalizedMaterialCode = firstText(materialCode);
    if (normalizedMaterialCode == null) {
      return Map.of();
    }

    if (hasApiConfig()) {
      try {
        Map<String, Object> apiRow = queryMaterialSupplyInfoFromApi(normalizedMaterialCode);
        if (!apiRow.isEmpty()) {
          return apiRow;
        }
      } catch (RuntimeException ex) {
        log.warn("ERP material supply query failed. materialCode={}, message={}", normalizedMaterialCode, ex.getMessage());
      }
    }

    return Map.of(
      "material_code", normalizedMaterialCode,
      "supply_type", "UNKNOWN",
      "supply_type_name_cn", "\u672A\u77E5"
    );
  }

  public List<Map<String, Object>> loadMaterialInventoryByCodes(List<String> materialCodes, boolean forceRefresh) {
    Set<String> normalizedCodes = normalizeMaterialCodeSet(materialCodes);
    if (normalizedCodes.isEmpty()) {
      return List.of();
    }
    if (!hasApiConfig()) {
      return buildInventoryRowsFromAggregate(Map.of(), normalizedCodes);
    }

    List<String> fieldVariants = List.of(
      "FID,FMaterialId.FNumber,FMaterialId.FName,FStockQty,FBaseQty,FAVBQty,FCanUseQty,FQty,FStockOrgId.FNumber",
      "FID,FMaterialId.FNumber,FMaterialId.FName,FStockQty,FBaseQty,FQty,FStockOrgId.FNumber",
      "FID,FMaterialId.FNumber,FMaterialId.FName,FBaseQty,FQty,FStockOrgId.FNumber",
      "FID,FMaterialId.FNumber,FMaterialId.FName,FQty",
      "FID,FMaterialId.FNumber,FQty"
    );
    int chunkSize = 20;
    List<String> codeList = new ArrayList<>(normalizedCodes);
    Map<String, Double> stockByCode = new HashMap<>();

    for (int start = 0; start < codeList.size(); start += chunkSize) {
      int end = Math.min(codeList.size(), start + chunkSize);
      List<String> chunk = codeList.subList(start, end);
      String inClause = buildInClause(chunk);
      if (inClause.isBlank()) {
        continue;
      }
      List<String> filters = List.of(
        "FMaterialId.FNumber in (" + inClause + ") and FStockOrgId.FNumber = '" + escapeFilterValue(DEFAULT_ERP_ORG_NO) + "'",
        "FMaterialId.FNumber in (" + inClause + ") and FUseOrgId.FNumber = '" + escapeFilterValue(DEFAULT_ERP_ORG_NO) + "'",
        "FMaterialId.FNumber in (" + inClause + ")"
      );

      List<Map<String, Object>> rows = List.of();
      RuntimeException lastEx = null;
      for (String fieldKeys : fieldVariants) {
        for (String filter : filters) {
          try {
            rows = queryBillRowsFromApi("STK_Inventory", fieldKeys, filter, "FID DESC", 0, 2000);
            if (!rows.isEmpty()) {
              break;
            }
          } catch (RuntimeException ex) {
            lastEx = ex;
          }
        }
        if (!rows.isEmpty()) {
          break;
        }
      }
      if (rows.isEmpty()) {
        if (lastEx != null) {
          log.debug("ERP inventory query chunk failed. materialCodes={}, message={}", chunk, lastEx.getMessage());
        }
        continue;
      }
      mergeInventoryRows(stockByCode, rows);
    }
    return buildInventoryRowsFromAggregate(stockByCode, normalizedCodes);
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

  private Set<String> normalizeMaterialCodeSet(List<String> materialCodes) {
    Set<String> normalized = new HashSet<>();
    if (materialCodes == null || materialCodes.isEmpty()) {
      return normalized;
    }
    for (String code : materialCodes) {
      String text = firstText(code);
      if (text == null) {
        continue;
      }
      normalized.add(text.trim().toUpperCase());
    }
    return normalized;
  }

  private String buildInClause(List<String> materialCodes) {
    if (materialCodes == null || materialCodes.isEmpty()) {
      return "";
    }
    List<String> quoted = new ArrayList<>();
    for (String code : materialCodes) {
      String normalized = firstText(code);
      if (normalized == null) {
        continue;
      }
      quoted.add("'" + escapeFilterValue(normalized.toUpperCase()) + "'");
    }
    return String.join(",", quoted);
  }

  private void mergeInventoryRows(Map<String, Double> stockByCode, List<Map<String, Object>> rows) {
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

  private List<Map<String, Object>> buildInventoryRowsFromAggregate(
    Map<String, Double> stockByCode,
    Set<String> requestedCodes
  ) {
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

  private Map<String, Object> toPurchaseOrderRow(ErpRecord record, Map<String, Object> line, int lineNo) {
    String purchaseOrderNo = firstText(record.header.get("BillNo"), record.base.get("bill_no"));
    String orderDate = firstText(record.header.get("Date"), record.base.get("bill_date"));
    String expectedArrivalDate = firstText(
      line.get("DeliveryDate"),
      line.get("PlanArriveDate"),
      line.get("ArriveDate"),
      orderDate
    );
    String materialCode = firstText(materialNumber(line), line.get("MaterialId_Id"), line.get("MaterialNumber"), "UNKNOWN");
    String materialName = fixPotentialUtf8Mojibake(firstText(materialNameCn(line), line.get("MaterialName"), line.get("MaterialName_Text")));
    String specModel = firstText(materialSpecification(line), line.get("Model"), line.get("Specification"));
    Number orderQty = firstNonNullNumber(
      toNumber(line.get("Qty")),
      toNumber(line.get("FQty")),
      toNumber(line.get("BaseQty"))
    );
    Number receivedQty = firstNonNullNumber(
      toNumber(line.get("ReceiveQty")),
      toNumber(line.get("InStockQty")),
      toNumber(line.get("StockInQty"))
    );
    String supplierCode = firstText(
      mapValue(line.get("SupplierId"), "Number"),
      mapValue(record.header.get("SupplierId"), "Number"),
      line.get("SupplierId_Id")
    );
    String supplierNameCn = fixPotentialUtf8Mojibake(firstText(
      masterDataNameCn(line.get("SupplierId")),
      masterDataNameCn(record.header.get("SupplierId")),
      mapValue(line.get("SupplierId"), "Name"),
      mapValue(record.header.get("SupplierId"), "Name")
    ));
    String sourceProductionOrderNo = firstText(
      line.get("SrcBillNo"),
      line.get("MtoNo"),
      line.get("SaleOrderNo"),
      line.get("SourceBillNo")
    );
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("purchase_order_no", purchaseOrderNo);
    row.put("line_no", firstText(line.get("Seq"), String.valueOf(lineNo)));
    row.put("source_production_order_no", sourceProductionOrderNo);
    row.put("material_code", materialCode);
    row.put("material_name_cn", materialName);
    row.put("spec_model", specModel);
    row.put("order_qty", orderQty == null ? 0d : orderQty);
    row.put("received_qty", receivedQty == null ? 0d : receivedQty);
    row.put("order_date", orderDate);
    row.put("expected_arrival_date", expectedArrivalDate);
    row.put("supplier_code", supplierCode);
    row.put("supplier_name_cn", supplierNameCn);
    row.put("purchase_status", firstText(record.header.get("DocumentStatus"), record.base.get("document_status")));
    row.put("last_update_time", firstText(record.base.get("fetched_at"), now));
    appendErpRefColumns(row, record, lineNo);
    return row;
  }

  private List<Map<String, Object>> loadPurchaseOrdersFromApi() {
    if (!hasApiConfig()) {
      return List.of();
    }
    try {
      return queryPurchaseRowsFromApi(
        "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,"
          + "FQty,FReceiveQty,FPurchaseOrgId.FNumber,FSupplierId.FNumber,FSupplierId.FName,FSrcBillNo",
        "FDate DESC,FID DESC"
      );
    } catch (RuntimeException ex) {
      log.warn("ERP purchase query failed by api. {}", ex.getMessage());
      return List.of();
    }
  }

  private Map<String, Object> queryMaterialSupplyInfoFromApi(String materialCode) {
    String normalizedMaterialCode = firstText(materialCode);
    if (normalizedMaterialCode == null) {
      return Map.of();
    }

    List<String> fieldVariants = List.of(
      "FID,FNumber,FName,FIsPurchase,FIsProduce,FCategoryID.FName,FCategoryID.FNumber,FUseOrgId.FNumber",
      "FID,FNumber,FName,FIsPurchase,FIsProduce,FMaterialGroup.FName,FUseOrgId.FNumber",
      "FID,FNumber,FName,FErpClsID,FUseOrgId.FNumber",
      "FID,FNumber,FName,FCategoryID.FName,FCategoryID.FNumber",
      "FID,FNumber,FName,FIsPurchase,FIsProduce",
      "FID,FNumber,FName"
    );

    List<String> filters = List.of(
      "FNumber = '" + escapeFilterValue(normalizedMaterialCode) + "' and FUseOrgId.FNumber = '" + escapeFilterValue(DEFAULT_ERP_ORG_NO) + "'",
      "FNumber = '" + escapeFilterValue(normalizedMaterialCode) + "' and FCreateOrgId.FNumber = '" + escapeFilterValue(DEFAULT_ERP_ORG_NO) + "'",
      "FNumber = '" + escapeFilterValue(normalizedMaterialCode) + "'"
    );

    for (String fieldKeys : fieldVariants) {
      for (String filter : filters) {
        try {
          List<Map<String, Object>> rows = queryBillRowsFromApi(
            "BD_MATERIAL",
            fieldKeys,
            filter,
            "FID DESC",
            0,
            5
          );
          if (rows.isEmpty()) {
            continue;
          }
          Map<String, Object> row = rows.get(0);
          return normalizeMaterialSupplyInfo(row, normalizedMaterialCode);
        } catch (RuntimeException ex) {
          log.debug("ERP material supply query variant failed. materialCode={}, fields={}, filter={}", normalizedMaterialCode, fieldKeys, filter, ex);
        }
      }
    }
    return Map.of();
  }

  private Map<String, Object> normalizeMaterialSupplyInfo(Map<String, Object> row, String materialCode) {
    String normalizedMaterialCode = firstText(
      row.get("FNumber"),
      row.get("material_code"),
      materialCode
    );
    String materialName = fixPotentialUtf8Mojibake(firstText(row.get("FName"), row.get("material_name_cn")));
    boolean isPurchase = toBoolean(row.get("FIsPurchase"));
    boolean isProduce = toBoolean(row.get("FIsProduce"));
    String categoryName = fixPotentialUtf8Mojibake(firstText(
      row.get("FCategoryID.FName"),
      row.get("FMaterialGroup.FName"),
      row.get("FErpClsID")
    ));
    String typeText = firstText(categoryName, row.get("supply_type_name_cn"), row.get("supply_type"));
    String supplyType = resolveMaterialSupplyType(isPurchase, isProduce, typeText, normalizedMaterialCode);
    String supplyTypeNameCn = switch (supplyType) {
      case "PURCHASED" -> "\u5916\u8D2D";
      case "SELF_MADE" -> "\u81EA\u5236";
      default -> "\u672A\u77E5";
    };

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("material_code", normalizedMaterialCode);
    out.put("material_name_cn", materialName == null ? "" : materialName);
    out.put("supply_type", supplyType);
    out.put("supply_type_name_cn", supplyTypeNameCn);
    out.put("supply_type_raw", typeText == null ? "" : typeText);
    return out;
  }

  private String resolveMaterialSupplyType(boolean isPurchase, boolean isProduce, String typeText, String materialCode) {
    if (isProduce && !isPurchase) {
      return "SELF_MADE";
    }
    if (isPurchase && !isProduce) {
      return "PURCHASED";
    }
    String normalizedTypeText = firstText(typeText);
    if (normalizedTypeText != null) {
      String upper = normalizedTypeText.toUpperCase();
      if (normalizedTypeText.contains("\u81EA\u5236") || upper.contains("MAKE") || upper.contains("PRODUCE")) {
        return "SELF_MADE";
      }
      if (
        normalizedTypeText.contains("\u5916\u8D2D")
          || normalizedTypeText.contains("\u91C7\u8D2D")
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

  private List<Map<String, Object>> queryPurchaseRowsFromApi(String fieldKeys, String orderString) {
    List<Map<String, Object>> result = new ArrayList<>();
    int startRow = 0;
    int limit = 200;
    while (true) {
      List<Map<String, Object>> page = queryBillRowsFromApi(
        "PUR_PurchaseOrder",
        fieldKeys,
        "",
        orderString,
        startRow,
        limit
      );
      if (page.isEmpty()) {
        break;
      }
      int baseIndex = result.size();
      for (int i = 0; i < page.size(); i += 1) {
        result.add(toPurchaseOrderRowFromApi(page.get(i), baseIndex + i + 1));
      }
      if (page.size() < limit) {
        break;
      }
      startRow += limit;
      if (startRow >= 20_000) {
        break;
      }
    }
    return result;
  }

  private List<Map<String, Object>> loadProductionOrdersFromApi() {
    if (!hasApiConfig()) {
      return List.of();
    }
    String baseFields =
      "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FQty,"
        + "FPlanStartDate,FPlanFinishDate,FPrdOrgId.FNumber,FSrcBillNo,FSrcBillType";
    List<String> fieldVariants = List.of(
      baseFields + ",FBomId.FNumber",
      baseFields + ",FBOMID.FNumber",
      baseFields
    );
    for (String fieldKeys : fieldVariants) {
      try {
        List<Map<String, Object>> rows = queryProductionRowsFromApi(fieldKeys, "FDate DESC,FID DESC");
        if (!rows.isEmpty()) {
          return rows;
        }
      } catch (RuntimeException ex) {
        log.debug("ERP production query variant failed. fields={}", fieldKeys, ex);
      }
    }
    return List.of();
  }

  private List<Map<String, Object>> queryProductionRowsFromApi(String fieldKeys, String orderString) {
    String filter = buildOrgDateFilter("FPrdOrgId.FNumber", "FDate", 180);
    List<Map<String, Object>> result = new ArrayList<>();
    int startRow = 0;
    int limit = 200;
    while (true) {
      List<Map<String, Object>> page = queryBillRowsFromApi(
        "PRD_MO",
        fieldKeys,
        filter,
        orderString,
        startRow,
        limit
      );
      if (page.isEmpty()) {
        break;
      }
      int baseIndex = result.size();
      for (int i = 0; i < page.size(); i += 1) {
        result.add(toProductionOrderRowFromApi(page.get(i), baseIndex + i + 1));
      }
      if (page.size() < limit) {
        break;
      }
      startRow += limit;
      if (startRow >= 20_000) {
        break;
      }
    }
    return result;
  }

  private List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrder(String productionOrderNo, String materialListNo) {
    if (!hasApiConfig()) {
      return List.of();
    }
    String baseFields =
      "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FActualQty,"
        + "FPrdOrgId.FNumber,FStockOrgId.FNumber,FSrcBillNo,FSrcBillType";
    List<Map<String, Object>> rows = new ArrayList<>();

    if (materialListNo != null) {
      String filter = buildOrgFilter("FPrdOrgId.FNumber") + " and FSrcBillNo = '" + escapeFilterValue(materialListNo) + "'";
      rows = queryProductionMaterialRowsFromApi(baseFields, filter);
      if (rows.isEmpty()) {
        List<String> bomFieldCandidates = List.of(
          "FPPBomNo",
          "FPrdBomNo",
          "FPPBomBillNo",
          "FBomNo",
          "FBomId.FNumber",
          "FBomId"
        );
        for (String field : bomFieldCandidates) {
          try {
            filter = buildOrgFilter("FPrdOrgId.FNumber") + " and " + field + " = '" + escapeFilterValue(materialListNo) + "'";
            rows = queryProductionMaterialRowsFromApi(baseFields + "," + field, filter);
            if (!rows.isEmpty()) {
              break;
            }
          } catch (RuntimeException ex) {
            log.debug("ERP pick material bom-field query failed. field={}", field, ex);
          }
        }
      }
      if (rows.isEmpty()) {
        List<String> parentMaterialFieldCandidates = List.of(
          "FParentMaterialId.FNumber",
          "FParentMaterialId",
          "FEntity_FParentMaterialId.FNumber",
          "FTreeEntity_FParentMaterialId.FNumber",
          "FPPBomEntry_FParentMaterialId.FNumber"
        );
        for (String field : parentMaterialFieldCandidates) {
          try {
            filter = buildOrgFilter("FPrdOrgId.FNumber") + " and " + field + " = '" + escapeFilterValue(materialListNo) + "'";
            rows = queryProductionMaterialRowsFromApi(baseFields + "," + field, filter);
            if (!rows.isEmpty()) {
              break;
            }
          } catch (RuntimeException ex) {
            log.debug("ERP pick material parent-field query failed. field={}", field, ex);
          }
        }
      }
    }

    if (rows.isEmpty() && productionOrderNo != null) {
      List<String> orderFieldCandidates = List.of(
        "FMoBillNo",
        "FMOBillNO",
        "FPrdMoNo",
        "FPrdOrderNo",
        "FSrcMoBillNo",
        "FMoNo",
        "FMoNumber",
        "FWorkOrderNo",
        "FWorkNo",
        "FMoBillNo.FNumber",
        "FMOBillNO.FNumber",
        "FEntity_FMoBillNo",
        "FEntity_FMOBillNO",
        "FTreeEntity_FMoBillNo",
        "FTreeEntity_FMOBillNO",
        "FEntity_FMoBillNo.FNumber",
        "FTreeEntity_FMoBillNo.FNumber"
      );
      for (String field : orderFieldCandidates) {
        try {
          String filter = buildOrgFilter("FPrdOrgId.FNumber") + " and " + field + " = '" + escapeFilterValue(productionOrderNo) + "'";
          rows = queryProductionMaterialRowsFromApi(baseFields + "," + field, filter);
          if (!rows.isEmpty()) {
            break;
          }
        } catch (RuntimeException ex) {
          log.debug("ERP pick material query candidate failed. field={}", field, ex);
        }
      }
      if (rows.isEmpty()) {
        String filter = buildOrgFilter("FPrdOrgId.FNumber") + " and FSrcBillNo = '" + escapeFilterValue(productionOrderNo) + "'";
        rows = queryProductionMaterialRowsFromApi(baseFields, filter);
      }
    }

    if (rows.isEmpty() && productionOrderNo != null) {
      rows = scanProductionMaterialRowsByOrderNo(productionOrderNo);
    }

    if (rows.isEmpty() && materialListNo != null) {
      rows = queryBomChildMaterialsFromApi(materialListNo);
    }

    List<Map<String, Object>> normalized = new ArrayList<>();
    for (int i = 0; i < rows.size(); i += 1) {
      normalized.add(toProductionMaterialIssueRowFromApi(rows.get(i), i + 1));
    }
    return normalized;
  }

  private List<Map<String, Object>> queryProductionMaterialRowsFromApi(String fieldKeys, String filterString) {
    List<Map<String, Object>> result = new ArrayList<>();
    int startRow = 0;
    int limit = 500;
    while (true) {
      List<Map<String, Object>> page = queryBillRowsFromApi(
        "PRD_PickMtrl",
        fieldKeys,
        filterString,
        "FDate DESC,FID DESC",
        startRow,
        limit
      );
      if (page.isEmpty()) {
        break;
      }
      result.addAll(page);
      if (page.size() < limit) {
        break;
      }
      startRow += limit;
      if (startRow >= 5_000) {
        break;
      }
    }
    return result;
  }

  private List<Map<String, Object>> scanProductionMaterialRowsByOrderNo(String productionOrderNo) {
    String normalizedOrderNo = firstText(productionOrderNo);
    if (normalizedOrderNo == null) {
      return List.of();
    }
    String baseFields =
      "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FActualQty,"
        + "FPrdOrgId.FNumber,FStockOrgId.FNumber,FSrcBillNo,FSrcBillType";
    List<String> orderFieldCandidates = List.of(
      "FMoBillNo",
      "FMOBillNO",
      "FPrdMoNo",
      "FPrdOrderNo",
      "FSrcMoBillNo",
      "FMoNo",
      "FMoNumber",
      "FWorkOrderNo",
      "FWorkNo",
      "FMoBillNo.FNumber",
      "FMOBillNO.FNumber",
      "FEntity_FMoBillNo",
      "FEntity_FMOBillNO",
      "FTreeEntity_FMoBillNo",
      "FTreeEntity_FMOBillNO",
      "FEntity_FMoBillNo.FNumber",
      "FTreeEntity_FMoBillNo.FNumber"
    );
    List<String> childFieldBundles = List.of(
      "",
      ",FSubMaterialId.FNumber,FSubMaterialId.FName,FSubActualQty",
      ",FEntity_FSubMaterialId.FNumber,FEntity_FSubMaterialId.FName,FEntity_FActualQty",
      ",FTreeEntity_FSubMaterialId.FNumber,FTreeEntity_FSubMaterialId.FName,FTreeEntity_FActualQty",
      ",FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,FEntity_FBaseQty",
      ",FTreeEntity_FMaterialId.FNumber,FTreeEntity_FMaterialId.FName,FTreeEntity_FBaseQty"
    );
    String filter = buildOrgDateFilter("FPrdOrgId.FNumber", "FDate", 365);

    for (String orderField : orderFieldCandidates) {
      for (String childBundle : childFieldBundles) {
        try {
          List<Map<String, Object>> rows = queryProductionMaterialRowsFromApi(
            baseFields + "," + orderField + childBundle,
            filter
          );
          if (rows.isEmpty()) {
            continue;
          }
          List<Map<String, Object>> matched = new ArrayList<>();
          for (Map<String, Object> row : rows) {
            String rowOrderNo = firstText(row.get(orderField));
            if (equalsIgnoreCaseSafe(rowOrderNo, normalizedOrderNo)) {
              matched.add(row);
            }
          }
          if (!matched.isEmpty()) {
            return matched;
          }
        } catch (RuntimeException ex) {
          log.debug("ERP pick material scan query failed. orderField={}", orderField, ex);
        }
      }
    }
    return List.of();
  }

  private List<Map<String, Object>> queryBomChildMaterialsFromApi(String materialListNo) {
    String normalizedMaterialListNo = firstText(materialListNo);
    if (normalizedMaterialListNo == null) {
      return List.of();
    }
    String normalizedMaterialBaseNo = normalizeMaterialListBaseNo(normalizedMaterialListNo);
    List<String> forms = List.of("PRD_PPBOM", "ENG_BOM");
    List<String> fieldVariants = List.of(
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMATERIALIDCHILD.FNumber,FTreeEntity_FMATERIALIDCHILD.FName,FTreeEntity_FDENOMINATOR,FTreeEntity_FNUMERATOR",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMaterialIdChild.FNumber,FTreeEntity_FMaterialIdChild.FName,FTreeEntity_FDenominator,FTreeEntity_FNumerator",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FMATERIALIDCHILD.FNumber,FEntity_FMATERIALIDCHILD.FName,FEntity_FDENOMINATOR,FEntity_FNUMERATOR",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FMaterialIdChild.FNumber,FEntity_FMaterialIdChild.FName,FEntity_FDenominator,FEntity_FNumerator",
      "FID,FBillNo,FNumber,FDocumentStatus,FPPBomEntry_FMATERIALIDCHILD.FNumber,FPPBomEntry_FMATERIALIDCHILD.FName,FPPBomEntry_FDENOMINATOR,FPPBomEntry_FNUMERATOR",
      "FID,FBillNo,FNumber,FDocumentStatus,FPPBomEntry_FMaterialIdChild.FNumber,FPPBomEntry_FMaterialIdChild.FName,FPPBomEntry_FDenominator,FPPBomEntry_FNumerator",
      "FID,FBillNo,FNumber,FDocumentStatus,FPPBomEntry_FMaterialId.FNumber,FPPBomEntry_FMaterialId.FName,FPPBomEntry_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FPPBOMENTRY_FMaterialId.FNumber,FPPBOMENTRY_FMaterialId.FName,FPPBOMENTRY_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMaterialId.FNumber,FTreeEntity_FMaterialId.FName,FTreeEntity_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMATERIALID.FNumber,FTreeEntity_FMATERIALID.FName,FTreeEntity_FBASEQTY",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,FEntity_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FMATERIALID.FNumber,FEntity_FMATERIALID.FName,FEntity_FBASEQTY",
      "FID,FBillNo,FNumber,FDocumentStatus,FSubMaterialId.FNumber,FSubMaterialId.FName,FSubActualQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FSubMaterialId.FNumber,FTreeEntity_FSubMaterialId.FName,FTreeEntity_FSubActualQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FSubMaterialId.FNumber,FEntity_FSubMaterialId.FName,FEntity_FSubActualQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FQty,FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FBaseQty",
      "FID,FBillNo,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FQty",
      "FID,FBillNo,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName"
    );
    List<String> filters = new ArrayList<>();
    filters.add("FBillNo = '" + escapeFilterValue(normalizedMaterialListNo) + "'");
    filters.add("FNumber = '" + escapeFilterValue(normalizedMaterialListNo) + "'");
    filters.add("FMaterialId.FNumber = '" + escapeFilterValue(normalizedMaterialListNo) + "'");
    if (normalizedMaterialBaseNo != null && !normalizedMaterialBaseNo.equalsIgnoreCase(normalizedMaterialListNo)) {
      filters.add("FNumber = '" + escapeFilterValue(normalizedMaterialBaseNo) + "'");
      filters.add("FMaterialId.FNumber = '" + escapeFilterValue(normalizedMaterialBaseNo) + "'");
    }
    for (String formId : forms) {
      for (String fieldKeys : fieldVariants) {
        for (String filter : filters) {
          try {
            List<Map<String, Object>> rows = queryBillRowsFromApi(
              formId,
              fieldKeys,
              filter,
              "FID DESC",
              0,
              500
            );
            if (!rows.isEmpty()) {
              for (Map<String, Object> row : rows) {
                row.putIfAbsent("source_bill_no", normalizedMaterialListNo);
                row.putIfAbsent("source_bill_type", formId);
                row.putIfAbsent("erp_source_table", "ERP_API_BOM_CHILD_MATERIAL");
                row.putIfAbsent("erp_form_id", formId);
              }
              if ("PRD_PPBOM".equalsIgnoreCase(formId)) {
                List<Map<String, Object>> expandedRows = expandPpBomRowsByView(rows, normalizedMaterialListNo);
                if (!expandedRows.isEmpty()) {
                  return expandedRows;
                }
              }
              return rows;
            }
          } catch (RuntimeException ex) {
            log.debug("ERP BOM child query failed. form={}, fields={}", formId, fieldKeys, ex);
          }
        }
      }
    }
    return List.of();
  }

  private List<Map<String, Object>> expandPpBomRowsByView(
    List<Map<String, Object>> headerRows,
    String fallbackSourceBillNo
  ) {
    if (headerRows == null || headerRows.isEmpty()) {
      return List.of();
    }
    for (Map<String, Object> headerRow : headerRows) {
      List<Map<String, Object>> expanded = new ArrayList<>();
      String billNo = firstText(headerRow.get("FBillNo"), headerRow.get("BillNo"));
      if (billNo == null) {
        continue;
      }
      List<Map<String, Object>> cached = ppBomViewEntryCache.get(billNo);
      if (cached != null && !cached.isEmpty()) {
        List<Map<String, Object>> clone = new ArrayList<>();
        for (Map<String, Object> row : cached) {
          clone.add(new LinkedHashMap<>(row));
        }
        return clone;
      }
      String sourceBillNo = firstText(
        headerRow.get("FNumber"),
        headerRow.get("source_bill_no"),
        fallbackSourceBillNo,
        billNo
      );
      String fid = firstText(headerRow.get("FID"));
      Object viewed = viewBillFromApi("PRD_PPBOM", billNo, fid);
      if (!(viewed instanceof Map<?, ?> viewedMap)) {
        continue;
      }
      Map<String, Object> model = extractViewBillModel(viewedMap);
      if (model == null || model.isEmpty()) {
        continue;
      }
      String documentStatus = firstText(model.get("DocumentStatus"), headerRow.get("FDocumentStatus"));
      String issueDate = firstText(
        model.get("Date"),
        model.get("CreateDate"),
        model.get("ApproveDate"),
        model.get("ModifyDate"),
        headerRow.get("FDate")
      );

      List<Map<String, Object>> treeRows = parseLineListByFields(model, List.of("TreeEntity", "FTreeEntity"));
      if (treeRows.isEmpty()) {
        treeRows = List.of(model);
      }
      for (Map<String, Object> treeRow : treeRows) {
        List<Map<String, Object>> entryRows = parseLineListByFields(
          treeRow,
          List.of("PPBomEntry", "FPPBomEntry", "Entity", "FEntity")
        );
        if (entryRows.isEmpty() && treeRow == model) {
          entryRows = parseLineListByFields(model, List.of("PPBomEntry", "FPPBomEntry", "Entity", "FEntity"));
        }
        for (Map<String, Object> entry : entryRows) {
          String childCode = firstText(
            mapValue(entry.get("MaterialID"), "Number"),
            mapValue(entry.get("MATERIALID"), "Number"),
            mapValue(entry.get("MaterialId"), "Number"),
            entry.get("MaterialID_Number"),
            entry.get("MaterialID.FNumber"),
            entry.get("FMaterialId.FNumber")
          );
          if (childCode == null) {
            continue;
          }
          String childName = firstText(
            masterDataNameCn(entry.get("MaterialID")),
            masterDataNameCn(entry.get("MATERIALID")),
            masterDataNameCn(entry.get("MaterialId")),
            mapValue(entry.get("MaterialID"), "Name"),
            mapValue(entry.get("MATERIALID"), "Name"),
            mapValue(entry.get("MaterialId"), "Name"),
            entry.get("MaterialID_Name"),
            entry.get("MaterialID.FName"),
            entry.get("FMaterialId.FName")
          );
          childName = fixPotentialUtf8Mojibake(childName);
          Number requiredQty = firstNonNullNumber(
            toNumber(entry.get("MustQty")),
            toNumber(entry.get("NeedQty")),
            toNumber(entry.get("StdQty")),
            toNumber(entry.get("BaseMustQty")),
            toNumber(entry.get("BaseNeedQty")),
            toNumber(entry.get("BaseStdQty")),
            toNumber(entry.get("Numerator")),
            toNumber(entry.get("BaseNumerator"))
          );

          Map<String, Object> row = new LinkedHashMap<>();
          row.put("FBillNo", billNo);
          row.put("FSeq", firstText(entry.get("Seq"), entry.get("BOMEntryID"), entry.get("Id")));
          row.put("FDate", issueDate);
          row.put("FDocumentStatus", documentStatus);
          row.put("FSrcBillNo", sourceBillNo);
          row.put("FSrcBillType", "PRD_PPBOM");
          row.put("FMaterialId.FNumber", childCode);
          row.put("FMaterialId.FName", childName);
          row.put("FActualQty", requiredQty == null ? 0d : requiredQty);
          row.put("FQty", requiredQty == null ? 0d : requiredQty);
          row.put("source_bill_no", sourceBillNo);
          row.put("source_bill_type", "PRD_PPBOM");
          row.put("erp_source_table", "ERP_API_BOM_VIEW_ENTRY");
          row.put("erp_form_id", "PRD_PPBOM");
          expanded.add(row);
        }
      }
      if (!expanded.isEmpty()) {
        List<Map<String, Object>> cachedRows = new ArrayList<>();
        for (Map<String, Object> row : expanded) {
          cachedRows.add(new LinkedHashMap<>(row));
        }
        ppBomViewEntryCache.put(billNo, cachedRows);
        return expanded;
      }
    }
    return List.of();
  }

  private Map<String, Object> extractViewBillModel(Map<?, ?> viewResponse) {
    Map<String, Object> root = toStringObjectMap(viewResponse);
    if (root == null) {
      return null;
    }
    Object resultObj = root.get("Result");
    Map<String, Object> resultMap = toStringObjectMap(resultObj);
    if (resultMap == null) {
      return null;
    }
    Object modelObj = resultMap.get("Result");
    Map<String, Object> modelMap = toStringObjectMap(modelObj);
    if (modelMap != null && !modelMap.isEmpty()) {
      return modelMap;
    }
    Object dataObj = resultMap.get("NeedReturnData");
    Map<String, Object> dataMap = toStringObjectMap(dataObj);
    if (dataMap != null && !dataMap.isEmpty()) {
      return dataMap;
    }
    if (dataObj instanceof List<?> list && !list.isEmpty()) {
      Map<String, Object> first = toStringObjectMap(list.get(0));
      if (first != null && !first.isEmpty()) {
        return first;
      }
    }
    return null;
  }

  private Map<String, Object> toStringObjectMap(Object source) {
    if (!(source instanceof Map<?, ?> sourceMap)) {
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
      out.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return out;
  }

  private List<Map<String, Object>> loadProductionOrdersFromCsv() {
    Path csvPath = resolveProductionOrderCsvPath();
    if (csvPath == null) {
      return List.of();
    }
    List<Map<String, Object>> csvRows = readCsvRows(csvPath);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < csvRows.size(); i += 1) {
      rows.add(toProductionOrderRowFromApi(csvRows.get(i), i + 1));
    }
    return rows;
  }

  private List<Map<String, Object>> loadProductionMaterialIssuesFromCsvByOrder(String productionOrderNo, String materialListNo) {
    Path csvPath = resolvePickMaterialCsvPath();
    if (csvPath == null) {
      return List.of();
    }
    List<Map<String, Object>> csvRows = readCsvRows(csvPath);
    List<Map<String, Object>> rows = new ArrayList<>();

    if (materialListNo != null) {
      for (int i = 0; i < csvRows.size(); i += 1) {
        Map<String, Object> csvRow = csvRows.get(i);
        String sourceBillNo = firstText(csvRow.get("FSrcBillNo"), csvRow.get("source_bill_no"));
        if (!equalsIgnoreCaseSafe(sourceBillNo, materialListNo)) {
          continue;
        }
        csvRow.putIfAbsent("erp_source_table", "ERP_CSV_PRODUCTION_MATERIAL_ISSUE");
        csvRow.putIfAbsent("erp_form_id", "PRD_PickMtrl");
        rows.add(toProductionMaterialIssueRowFromApi(csvRow, i + 1));
      }
      if (!rows.isEmpty()) {
        return rows;
      }
    }

    if (productionOrderNo != null) {
      for (int i = 0; i < csvRows.size(); i += 1) {
        Map<String, Object> csvRow = csvRows.get(i);
        String sourceProductionOrderNo = firstText(
          csvRow.get("FMoBillNo"),
          csvRow.get("FMOBillNO"),
          csvRow.get("FPrdMoNo"),
          csvRow.get("source_production_order_no")
        );
        if (equalsIgnoreCaseSafe(sourceProductionOrderNo, productionOrderNo)) {
          csvRow.putIfAbsent("erp_source_table", "ERP_CSV_PRODUCTION_MATERIAL_ISSUE");
          csvRow.putIfAbsent("erp_form_id", "PRD_PickMtrl");
          rows.add(toProductionMaterialIssueRowFromApi(csvRow, i + 1));
        }
      }
      if (!rows.isEmpty()) {
        return rows;
      }
      for (int i = 0; i < csvRows.size(); i += 1) {
        Map<String, Object> csvRow = csvRows.get(i);
        String sourceBillNo = firstText(csvRow.get("FSrcBillNo"), csvRow.get("source_bill_no"));
        if (equalsIgnoreCaseSafe(sourceBillNo, productionOrderNo)) {
          csvRow.putIfAbsent("erp_source_table", "ERP_CSV_PRODUCTION_MATERIAL_ISSUE");
          csvRow.putIfAbsent("erp_form_id", "PRD_PickMtrl");
          rows.add(toProductionMaterialIssueRowFromApi(csvRow, i + 1));
        }
      }
    }
    return rows;
  }

  private Path resolveProductionOrderCsvPath() {
    Path explicit = resolveExplicitCsvPath(productionOrderCsvPath);
    if (explicit != null) {
      return explicit;
    }
    return resolveLatestDemoCsv("production_order.csv");
  }

  private Path resolvePickMaterialCsvPath() {
    return resolveLatestDemoCsv("pick_material.csv");
  }

  private Path resolveExplicitCsvPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return null;
    }
    try {
      Path path = Path.of(rawPath);
      return Files.exists(path) ? path : null;
    } catch (Exception ex) {
      return null;
    }
  }

  private Path resolveLatestDemoCsv(String fileName) {
    if (erpDemoOutDir == null || erpDemoOutDir.isBlank()) {
      return null;
    }
    try {
      Path baseDir = Path.of(erpDemoOutDir);
      if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
        return null;
      }
      List<Path> candidates = new ArrayList<>();
      Path direct = baseDir.resolve(fileName);
      if (Files.exists(direct)) {
        candidates.add(direct);
      }
      try (var stream = Files.list(baseDir)) {
        stream
          .filter(Files::isDirectory)
          .forEach(dir -> {
            Path candidate = dir.resolve(fileName);
            if (Files.exists(candidate)) {
              candidates.add(candidate);
            }
          });
      }
      if (candidates.isEmpty()) {
        return null;
      }
      candidates.sort(Comparator.comparingLong(this::safeLastModifiedMillis).reversed());
      return candidates.get(0);
    } catch (Exception ex) {
      return null;
    }
  }

  private long safeLastModifiedMillis(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (Exception ex) {
      return 0L;
    }
  }

  private List<Map<String, Object>> readCsvRows(Path csvPath) {
    if (csvPath == null || !Files.exists(csvPath)) {
      return List.of();
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return List.of();
      }
      List<String> headers = parseCsvLine(headerLine);
      if (!headers.isEmpty()) {
        headers.set(0, headers.get(0).replace("\uFEFF", ""));
      }
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        List<String> values = parseCsvLine(line);
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i += 1) {
          String key = headers.get(i);
          if (key == null || key.isBlank()) {
            continue;
          }
          row.put(key, i < values.size() ? values.get(i) : "");
        }
        rows.add(row);
      }
    } catch (Exception ex) {
      log.warn("Failed to read ERP csv rows from {}.", csvPath, ex);
      return List.of();
    }
    return rows;
  }

  private List<String> parseCsvLine(String line) {
    List<String> values = new ArrayList<>();
    if (line == null) {
      return values;
    }
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i += 1) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i += 1;
          continue;
        }
        inQuotes = !inQuotes;
        continue;
      }
      if (ch == ',' && !inQuotes) {
        values.add(current.toString());
        current.setLength(0);
        continue;
      }
      current.append(ch);
    }
    values.add(current.toString());
    return values;
  }

  private String buildOrgDateFilter(String orgField, String dateField, int lookbackDays) {
    List<String> pieces = new ArrayList<>();
    pieces.add(buildOrgFilter(orgField));
    LocalDate startDate = LocalDate.now(ZoneOffset.UTC).minusDays(Math.max(30, lookbackDays));
    pieces.add(dateField + " >= '" + startDate + "'");
    return String.join(" and ", pieces);
  }

  private String buildOrgFilter(String orgField) {
    return orgField + " = '" + escapeFilterValue(DEFAULT_ERP_ORG_NO) + "'";
  }

  private String escapeFilterValue(String text) {
    return text == null ? "" : text.replace("'", "''");
  }

  private boolean equalsIgnoreCaseSafe(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return left.equalsIgnoreCase(right);
  }

  private List<Map<String, Object>> queryBillRowsFromApi(
    String formId,
    String fieldKeys,
    String filterString,
    String orderString,
    int startRow,
    int limit
  ) {
    ensureApiLogin(false);
    String service = "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc";
    Map<String, Object> queryObj = new LinkedHashMap<>();
    queryObj.put("FormId", formId);
    queryObj.put("FieldKeys", fieldKeys);
    queryObj.put("FilterString", filterString == null ? "" : filterString);
    queryObj.put("OrderString", orderString == null ? "FID DESC" : orderString);
    queryObj.put("StartRow", Math.max(0, startRow));
    queryObj.put("Limit", Math.max(1, limit));

    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(queryObj);
    } catch (Exception ex) {
      throw new RuntimeException("Failed to serialize ERP query payload.", ex);
    }

    RuntimeException lastEx = null;
    for (String url : serviceUrls(service)) {
      try {
        Object parsed = postFormForJson(url, Map.of("data", payloadJson));
        if (parsed instanceof List<?> rows) {
          List<Map<String, Object>> normalizedRows = normalizeBillQueryRows(rows, fieldKeys);
          return normalizedRows;
        }
        if (parsed instanceof Map<?, ?> parsedMap) {
          String message = "ERP query unexpected response.";
          Object statusObj = mapValue(mapValue(parsedMap, "Result"), "ResponseStatus");
          if (statusObj instanceof Map<?, ?> statusMap) {
            if (Boolean.TRUE.equals(statusMap.get("IsSuccess"))) {
              return List.of();
            }
            Object errors = statusMap.get("Errors");
            message = errors == null ? String.valueOf(statusMap) : String.valueOf(errors);
          } else {
            message = String.valueOf(parsedMap);
          }
          if (containsAuthError(message)) {
            apiLoggedIn = false;
            ensureApiLogin(true);
            parsed = postFormForJson(url, Map.of("data", payloadJson));
            if (parsed instanceof List<?> retryRows) {
              return mapQueryRows(retryRows, fieldKeys);
            }
          }
          throw new RuntimeException(message);
        }
        throw new RuntimeException("ERP query response is not JSON.");
      } catch (RuntimeException ex) {
        lastEx = ex;
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    return List.of();
  }

  private List<Map<String, Object>> normalizeBillQueryRows(List<?> rows, String fieldKeys) {
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

  private RuntimeException toExecuteBillQueryError(Map<?, ?> responseMap) {
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

  private List<Map<String, Object>> mapQueryRows(List<?> rows, String fieldKeys) {
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

  private List<String> splitFieldKeys(String fieldKeys) {
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

  private Map<String, Object> toProductionOrderRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    String productionOrderNo = firstText(
      row.get("FBillNo"),
      row.get("BillNo"),
      row.get("production_order_no")
    );
    String sourceBillNo = firstText(
      row.get("FSrcBillNo"),
      row.get("source_sales_order_no")
    );
    String sourceLineNo = firstText(
      row.get("FSeq"),
      row.get("FTreeEntity_FSeq"),
      String.valueOf(fallbackLineNo)
    );
    String bomNumber = firstText(
      row.get("FBomId.FNumber"),
      row.get("FBOMID.FNumber"),
      row.get("FBomId_Number"),
      row.get("material_list_no")
    );
    String productCode = firstText(
      row.get("FMaterialId.FNumber"),
      row.get("FMaterialId_Id"),
      row.get("product_code"),
      "UNKNOWN"
    );
    String productNameCn = firstText(
      row.get("FMaterialId.FName"),
      row.get("FMaterialName"),
      row.get("product_name_cn")
    );
    Number planQty = firstNonNullNumber(
      toNumber(row.get("FQty")),
      toNumber(row.get("plan_qty"))
    );
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
    String status = firstText(row.get("FDocumentStatus"), row.get("DocumentStatus"), row.get("production_status"));
    String orderDate = firstText(row.get("FDate"), row.get("order_date"));
    String planStartDate = firstText(row.get("FPlanStartDate"), row.get("planned_finish_date_1"));
    String planFinishDate = firstText(row.get("FPlanFinishDate"), row.get("planned_finish_date_2"));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("production_order_no", productionOrderNo);
    out.put("source_sales_order_no", sourceBillNo == null ? "UNLINKED-" + productionOrderNo : sourceBillNo);
    out.put("source_line_no", sourceLineNo);
    out.put("source_plan_order_no", sourceBillNo == null ? null : sourceBillNo);
    out.put("material_list_no", bomNumber == null ? "BOM-" + productionOrderNo : bomNumber);
    out.put("product_code", productCode);
    out.put("product_name_cn", productNameCn);
    out.put("plan_qty", planQty == null ? 0d : planQty);
    out.put("production_status", status);
    out.put("order_date", orderDate);
    out.put("customer_remark", firstText(row.get("FDescription"), row.get("FRemarks"), row.get("customer_remark")));
    out.put("spec_model", firstText(row.get("FMaterialId.FSpecification"), row.get("spec_model")));
    out.put("production_batch_no", firstText(row.get("FLot.FNumber"), row.get("production_batch_no")));
    out.put("planned_finish_date_1", planStartDate);
    out.put("planned_finish_date_2", planFinishDate);
    out.put("production_date_foreign_trade", firstText(row.get("FProduceDate"), row.get("production_date_foreign_trade")));
    out.put("packaging_form", firstText(row.get("packaging_form")));
    out.put("sales_order_no", sourceBillNo);
    out.put("purchase_due_date", firstText(row.get("purchase_due_date")));
    out.put("injection_due_date", firstText(row.get("injection_due_date")));
    out.put("market_remark_info", firstText(row.get("market_remark_info")));
    out.put("market_demand", toNumber(row.get("market_demand")));
    out.put("semi_finished_code", firstText(row.get("semi_finished_code")));
    out.put("semi_finished_inventory", toNumber(row.get("semi_finished_inventory")));
    out.put("semi_finished_demand", toNumber(row.get("semi_finished_demand")));
    out.put("semi_finished_wip", toNumber(row.get("semi_finished_wip")));
    out.put("need_order_qty", toNumber(row.get("need_order_qty")));
    out.put("pending_inbound_qty", toNumber(row.get("pending_inbound_qty")));
    out.put("weekly_monthly_process_plan", firstText(row.get("weekly_monthly_process_plan")));
    out.put("workshop_outer_packaging_date", firstText(row.get("workshop_outer_packaging_date")));
    out.put("note", firstText(row.get("FNote"), row.get("note")));
    out.put("workshop_completed_qty", firstNonNullNumber(toNumber(row.get("FRepQuaQty")), toNumber(row.get("workshop_completed_qty"))));
    out.put("workshop_completed_time", firstText(row.get("FFinishDate"), row.get("workshop_completed_time")));
    out.put("outer_completed_qty", firstNonNullNumber(toNumber(row.get("outer_completed_qty"))));
    out.put("outer_completed_time", firstText(row.get("outer_completed_time")));
    out.put("match_status", firstText(row.get("match_status")));
    out.put("last_update_time", firstText(row.get("erp_fetched_at"), now));
    out.put("erp_source_table", firstText(row.get("erp_source_table"), "ERP_API_PRODUCTION_ORDER"));
    out.put("erp_record_id", firstText(row.get("FID"), row.get("erp_record_id"), productionOrderNo));
    out.put("erp_line_no", sourceLineNo);
    out.put("erp_line_id", firstText(row.get("FEntryID"), row.get("erp_line_id"), productionOrderNo + ":" + sourceLineNo));
    out.put("erp_form_id", firstText(row.get("erp_form_id"), "PRD_MO"));
    out.put("erp_document_status", status);
    out.put("erp_fetched_at", firstText(row.get("erp_fetched_at"), now));
    return out;
  }

  private Map<String, Object> toProductionMaterialIssueRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    String issueBillNo = firstText(row.get("FBillNo"), row.get("pick_material_bill_no"), row.get("BillNo"));
    String lineNo = firstText(row.get("FSeq"), row.get("line_no"), String.valueOf(fallbackLineNo));
    String childMaterialCode = firstText(
      row.get("FSubMaterialId.FNumber"),
      row.get("FTreeEntity_FSubMaterialId.FNumber"),
      row.get("FEntity_FSubMaterialId.FNumber"),
      row.get("FTreeEntity_FMATERIALIDCHILD.FNumber"),
      row.get("FTreeEntity_FMaterialIdChild.FNumber"),
      row.get("FEntity_FMATERIALIDCHILD.FNumber"),
      row.get("FEntity_FMaterialIdChild.FNumber"),
      row.get("FPPBomEntry_FMATERIALIDCHILD.FNumber"),
      row.get("FPPBomEntry_FMaterialIdChild.FNumber"),
      row.get("FPPBomEntry_FMaterialId.FNumber"),
      row.get("FPPBOMENTRY_FMaterialId.FNumber"),
      row.get("FTreeEntity_FMaterialId.FNumber"),
      row.get("FTreeEntity_FMATERIALID.FNumber"),
      row.get("FEntity_FMaterialId.FNumber"),
      row.get("FEntity_FMATERIALID.FNumber"),
      row.get("FMaterialId.FNumber"),
      row.get("child_material_code"),
      "UNKNOWN"
    );
    String childMaterialName = fixPotentialUtf8Mojibake(firstText(
      row.get("FSubMaterialId.FName"),
      row.get("FTreeEntity_FSubMaterialId.FName"),
      row.get("FEntity_FSubMaterialId.FName"),
      row.get("FTreeEntity_FMATERIALIDCHILD.FName"),
      row.get("FTreeEntity_FMaterialIdChild.FName"),
      row.get("FEntity_FMATERIALIDCHILD.FName"),
      row.get("FEntity_FMaterialIdChild.FName"),
      row.get("FPPBomEntry_FMATERIALIDCHILD.FName"),
      row.get("FPPBomEntry_FMaterialIdChild.FName"),
      row.get("FPPBomEntry_FMaterialId.FName"),
      row.get("FPPBOMENTRY_FMaterialId.FName"),
      row.get("FTreeEntity_FMaterialId.FName"),
      row.get("FTreeEntity_FMATERIALID.FName"),
      row.get("FEntity_FMaterialId.FName"),
      row.get("FEntity_FMATERIALID.FName"),
      row.get("FMaterialId.FName"),
      row.get("child_material_name_cn")
    ));
    Number requiredQty = firstNonNullNumber(
      toNumber(row.get("FActualQty")),
      toNumber(row.get("required_qty")),
      toNumber(row.get("FSubActualQty")),
      toNumber(row.get("FTreeEntity_FSubActualQty")),
      toNumber(row.get("FEntity_FSubActualQty")),
      toNumber(row.get("FTreeEntity_FDENOMINATOR")),
      toNumber(row.get("FTreeEntity_FDenominator")),
      toNumber(row.get("FEntity_FDENOMINATOR")),
      toNumber(row.get("FEntity_FDenominator")),
      toNumber(row.get("FPPBomEntry_FDENOMINATOR")),
      toNumber(row.get("FPPBomEntry_FDenominator")),
      toNumber(row.get("FTreeEntity_FNUMERATOR")),
      toNumber(row.get("FTreeEntity_FNumerator")),
      toNumber(row.get("FEntity_FNUMERATOR")),
      toNumber(row.get("FEntity_FNumerator")),
      toNumber(row.get("FPPBomEntry_FNUMERATOR")),
      toNumber(row.get("FPPBomEntry_FNumerator")),
      toNumber(row.get("FPPBomEntry_FBaseQty")),
      toNumber(row.get("FPPBOMENTRY_FBaseQty")),
      toNumber(row.get("FBaseQty")),
      toNumber(row.get("FTreeEntity_FBaseQty")),
      toNumber(row.get("FTreeEntity_FBASEQTY")),
      toNumber(row.get("FEntity_FBaseQty")),
      toNumber(row.get("FEntity_FBASEQTY")),
      toNumber(row.get("FQty"))
    );
    String sourceBillNo = firstText(
      row.get("FSrcBillNo"),
      row.get("source_bill_no"),
      row.get("FNumber"),
      row.get("FBillNo")
    );
    String sourceBillType = firstText(row.get("FSrcBillType"), row.get("source_bill_type"));
    String sourceProductionOrderNo = firstText(
      row.get("FMoBillNo"),
      row.get("FMOBillNO"),
      row.get("FPrdMoNo"),
      row.get("FPrdOrderNo"),
      row.get("FSrcMoBillNo"),
      row.get("FMoBillNo.FNumber"),
      row.get("FMOBillNO.FNumber"),
      row.get("FEntity_FMoBillNo"),
      row.get("FTreeEntity_FMoBillNo"),
      row.get("FEntity_FMoBillNo.FNumber"),
      row.get("FTreeEntity_FMoBillNo.FNumber"),
      row.get("source_production_order_no")
    );
    String issueDate = firstText(row.get("FDate"), row.get("issue_date"));
    String status = firstText(row.get("FDocumentStatus"), row.get("DocumentStatus"), row.get("pick_status"));
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("pick_material_bill_no", issueBillNo);
    out.put("line_no", lineNo);
    out.put("issue_date", issueDate);
    out.put("pick_status", status);
    out.put("source_bill_no", sourceBillNo);
    out.put("source_bill_type", sourceBillType);
    out.put("source_production_order_no", sourceProductionOrderNo);
    out.put("child_material_code", childMaterialCode);
    out.put("child_material_name_cn", childMaterialName);
    out.put("required_qty", requiredQty == null ? 0d : requiredQty);
    out.put("last_update_time", firstText(row.get("erp_fetched_at"), now));
    out.put("erp_source_table", firstText(row.get("erp_source_table"), "ERP_API_PRODUCTION_MATERIAL_ISSUE"));
    out.put("erp_record_id", firstText(row.get("FID"), row.get("erp_record_id"), issueBillNo));
    out.put("erp_line_no", lineNo);
    out.put("erp_line_id", firstText(row.get("FEntryID"), row.get("erp_line_id"), issueBillNo + ":" + lineNo));
    out.put("erp_form_id", firstText(row.get("erp_form_id"), "PRD_PickMtrl"));
    out.put("erp_document_status", status);
    out.put("erp_fetched_at", firstText(row.get("erp_fetched_at"), now));
    return out;
  }

  private Map<String, Object> toPurchaseOrderRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    String purchaseOrderNo = firstText(
      row.get("FBillNo"),
      row.get("BillNo"),
      row.get("purchase_order_no")
    );
    String lineNo = firstText(
      row.get("FPOOrderEntry_FSeq"),
      row.get("FSeq"),
      String.valueOf(fallbackLineNo)
    );
    String materialCode = firstText(
      row.get("FPOOrderEntry_FMaterialId.FNumber"),
      row.get("FMaterialId.FNumber"),
      row.get("FMaterialId_Id"),
      "UNKNOWN"
    );
    String materialNameCn = fixPotentialUtf8Mojibake(firstText(
      row.get("FPOOrderEntry_FMaterialId.FName"),
      row.get("FMaterialId.FName"),
      row.get("FMaterialName")
    ));
    String specModel = firstText(
      row.get("FPOOrderEntry_FMaterialId.FSpecification"),
      row.get("FMaterialId.FSpecification"),
      row.get("FSpecification")
    );
    Number orderQty = firstNonNullNumber(
      toNumber(row.get("FPOOrderEntry_FQty")),
      toNumber(row.get("FQty"))
    );
    Number receivedQty = firstNonNullNumber(
      toNumber(row.get("FPOOrderEntry_FReceiveQty")),
      toNumber(row.get("FReceiveQty"))
    );
    String orderDate = firstText(row.get("FDate"));
    String expectedArrivalDate = firstText(
      row.get("FPOOrderEntry_FDeliveryDate"),
      row.get("FDeliveryDate")
    );
    String supplierCode = firstText(row.get("FSupplierId.FNumber"), row.get("FSupplierId_Id"));
    String supplierName = fixPotentialUtf8Mojibake(firstText(row.get("FSupplierId.FName"), row.get("FSupplierName")));
    String sourceProductionOrderNo = firstText(row.get("FPOOrderEntry_FSrcBillNo"), row.get("FSrcBillNo"));
    String status = firstText(row.get("FDocumentStatus"), row.get("DocumentStatus"));
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("purchase_order_no", purchaseOrderNo);
    out.put("line_no", lineNo);
    out.put("source_production_order_no", sourceProductionOrderNo);
    out.put("material_code", materialCode);
    out.put("material_name_cn", materialNameCn);
    out.put("spec_model", specModel);
    out.put("order_qty", orderQty == null ? 0d : orderQty);
    out.put("received_qty", receivedQty == null ? 0d : receivedQty);
    out.put("order_date", orderDate);
    out.put("expected_arrival_date", expectedArrivalDate);
    out.put("supplier_code", supplierCode);
    out.put("supplier_name_cn", supplierName);
    out.put("purchase_status", status);
    out.put("last_update_time", now);
    out.put("erp_source_table", "ERP_API_PURCHASE_ORDER");
    out.put("erp_record_id", firstText(row.get("FID"), purchaseOrderNo));
    out.put("erp_line_no", lineNo);
    out.put("erp_line_id", firstText(row.get("FEntryID"), purchaseOrderNo + ":" + lineNo));
    out.put("erp_form_id", "PUR_PurchaseOrder");
    out.put("erp_document_status", status);
    out.put("erp_fetched_at", now);
    return out;
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
    return loadRecords(tableName, List.of(lineListField));
  }

  private List<ErpRecord> loadRecords(String tableName, List<String> lineListFields) {
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
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath())) {
      if (!tableExists(connection, tableName)) {
        return List.of();
      }
      try (
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
          List<Map<String, Object>> lines = parseLineListByFields(header, lineListFields);
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
      }
      return rows;
    } catch (Exception ex) {
      log.warn("Failed to load ERP real orders from sqlite file: {}", path, ex);
      return List.of();
    }
  }

  private boolean hasLocalSqliteSnapshot() {
    if (!useRealOrders || sqlitePath == null || sqlitePath.isBlank()) {
      return false;
    }
    try {
      Path path = Path.of(sqlitePath);
      return Files.exists(path) && !Files.isDirectory(path);
    } catch (RuntimeException ex) {
      return false;
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

  private List<Map<String, Object>> parseLineListByFields(Map<String, Object> header, List<String> lineListFields) {
    if (header == null || lineListFields == null || lineListFields.isEmpty()) {
      return List.of();
    }
    for (String field : lineListFields) {
      if (field == null || field.isBlank()) {
        continue;
      }
      List<Map<String, Object>> lines = parseLineList(header.get(field));
      if (!lines.isEmpty()) {
        return lines;
      }
    }
    return List.of();
  }

  private boolean tableExists(Connection connection, String tableName) {
    String sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1";
    try (
      PreparedStatement statement = connection.prepareStatement(sql)
    ) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    } catch (Exception ex) {
      return false;
    }
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

  private String masterDataNameCn(Object source) {
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

  private String fixPotentialUtf8Mojibake(String text) {
    String normalized = firstText(text);
    if (normalized == null) {
      return null;
    }
    if (containsCjk(normalized) || !containsLatin1Supplement(normalized)) {
      return normalized;
    }
    try {
      String repaired = new String(normalized.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
      if (repaired.contains("\uFFFD")) {
        return normalized;
      }
      if (containsCjk(repaired)) {
        return repaired;
      }
    } catch (RuntimeException ignored) {
      return normalized;
    }
    return normalized;
  }

  private boolean containsLatin1Supplement(String text) {
    for (int i = 0; i < text.length(); i += 1) {
      char ch = text.charAt(i);
      if (ch >= '\u00C0' && ch <= '\u00FF') {
        return true;
      }
    }
    return false;
  }

  private boolean containsCjk(String text) {
    for (int i = 0; i < text.length();) {
      int codePoint = text.codePointAt(i);
      Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
      if (
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
          || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
          || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
      ) {
        return true;
      }
      i += Character.charCount(codePoint);
    }
    return false;
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

  private boolean toBoolean(Object value) {
    if (value instanceof Boolean boolValue) {
      return boolValue;
    }
    if (value instanceof Number number) {
      return number.intValue() != 0;
    }
    String text = firstText(value);
    if (text == null) {
      return false;
    }
    String normalized = text.trim().toLowerCase();
    return "true".equals(normalized)
      || "1".equals(normalized)
      || "yes".equals(normalized)
      || "y".equals(normalized);
  }

  private boolean hasApiConfig() {
    return !baseUrl.isBlank() && !acctId.isBlank() && !username.isBlank() && !password.isBlank();
  }

  private synchronized void ensureApiLogin(boolean force) {
    if (!hasApiConfig()) {
      throw new RuntimeException("ERP API config is missing.");
    }
    if (apiLoggedIn && !force) {
      return;
    }
    String service = "Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc";
    List<Map<String, String>> payloads = List.of(
      Map.of(
        "acctID", acctId,
        "username", username,
        "password", password,
        "lcid", String.valueOf(lcid)
      ),
      Map.of(
        "AcctID", acctId,
        "UserName", username,
        "Password", password,
        "Lcid", String.valueOf(lcid)
      )
    );

    RuntimeException lastEx = null;
    for (String url : serviceUrls(service)) {
      for (Map<String, String> payload : payloads) {
        try {
          Object parsed = postFormForJson(url, payload);
          if (parsed instanceof Map<?, ?> map) {
            Object loginResultType = map.get("LoginResultType");
            if (loginResultType instanceof Number number && number.intValue() == 1) {
              apiLoggedIn = true;
              return;
            }
            Object successByApi = map.get("IsSuccessByAPI");
            if (Boolean.TRUE.equals(successByApi)) {
              apiLoggedIn = true;
              return;
            }
            lastEx = new RuntimeException("ERP login failed: " + map);
            continue;
          }
          lastEx = new RuntimeException("ERP login response is not json object.");
        } catch (RuntimeException ex) {
          lastEx = ex;
        }
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    throw new RuntimeException("ERP login failed.");
  }

  private List<String> serviceUrls(String serviceName) {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return List.of(
      base + "/K3Cloud/" + serviceName,
      base + "/k3cloud/" + serviceName,
      base + "/" + serviceName
    );
  }

  private Object postFormForJson(String url, Map<String, String> payload) {
    String body = encodeForm(payload);
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(timeoutSeconds))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return parseJson(response.body());
    } catch (Exception ex) {
      throw new RuntimeException("ERP http request failed: " + url, ex);
    }
  }

  private String encodeForm(Map<String, String> payload) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : payload.entrySet()) {
      if (!first) {
        builder.append("&");
      }
      first = false;
      builder.append(URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8));
      builder.append("=");
      builder.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));
    }
    return builder.toString();
  }

  private Object parseJson(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    try {
      return objectMapper.readValue(text, Object.class);
    } catch (Exception ex) {
      return text;
    }
  }

  private boolean containsAuthError(String message) {
    String text = message == null ? "" : message.toLowerCase();
    return text.contains("登录")
      || text.contains("请先登录")
      || text.contains("session")
      || text.contains("context")
      || text.contains("401")
      || text.contains("403");
  }

  private static HttpClient buildHttpClient(boolean verifySsl, int timeoutSeconds) {
    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    HttpClient.Builder builder = HttpClient.newBuilder()
      .cookieHandler(cookieManager)
      .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)));
    if (!verifySsl) {
      try {
        TrustManager[] trustAll = new TrustManager[] {
          new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return new java.security.cert.X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
          }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new java.security.SecureRandom());
        builder.sslContext(sslContext);
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("");
        builder.sslParameters(sslParameters);
      } catch (Exception ex) {
        log.warn("Failed to initialize insecure SSL for ERP client.", ex);
      }
    }
    return builder.build();
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeMaterialListBaseNo(String materialListNo) {
    String normalized = firstText(materialListNo);
    if (normalized == null) {
      return null;
    }
    int splitIndex = normalized.indexOf('_');
    if (splitIndex <= 0) {
      splitIndex = normalized.toUpperCase().indexOf("-V");
    }
    if (splitIndex <= 0) {
      return normalized;
    }
    return normalized.substring(0, splitIndex);
  }

  private Number firstNonNullNumber(Number... values) {
    if (values == null) {
      return null;
    }
    for (Number value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
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
