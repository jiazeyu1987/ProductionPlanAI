package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.trimToEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErpSqliteOrderLoader {
  private static final String DEFAULT_ERP_ORG_NO = "881";

  private final ErpOrderNormalizer normalizer;
  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteOrderAssembler assembler;
  private final ErpSqliteOrderRowMapper rowMapper;
  private final ErpSqliteOrderQueryExecutor queryExecutor;

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
    @Value("${mvp.erp.verify-ssl:${ERP_VERIFY_SSL:true}}") boolean verifySsl
  ) {
    String normalizedBaseUrl = trimToEmpty(baseUrl);
    String normalizedAcctId = trimToEmpty(acctId);
    String normalizedUsername = trimToEmpty(username);
    String normalizedPassword = password == null ? "" : password;
    int normalizedTimeoutSeconds = Math.max(1, timeoutSeconds);

    ErpOrderPayloadMapper payloadMapper = new ErpOrderPayloadMapper(objectMapper);
    this.normalizer = new ErpOrderNormalizer();
    this.validator = new ErpSqliteOrderValidator(
      normalizedBaseUrl,
      normalizedAcctId,
      normalizedUsername,
      normalizedPassword,
      DEFAULT_ERP_ORG_NO
    );
    this.assembler = new ErpSqliteOrderAssembler(objectMapper);
    this.rowMapper = new ErpSqliteOrderRowMapper();
    this.queryExecutor = new ErpSqliteOrderQueryExecutor(
      objectMapper,
      useRealOrders,
      sqlitePath,
      normalizedBaseUrl,
      normalizedAcctId,
      normalizedUsername,
      normalizedPassword,
      lcid,
      normalizedTimeoutSeconds,
      verifySsl,
      payloadMapper,
      normalizer,
      validator,
      assembler,
      rowMapper
    );
  }

  ErpSqliteOrderLoader(ObjectMapper objectMapper, boolean useRealOrders, String sqlitePath) {
    this(objectMapper, useRealOrders, sqlitePath, "", "", "", "", 2052, 30, true);
  }

  public List<Map<String, Object>> loadSalesOrderLines() {
    List<ErpSqliteOrderRecord> records = queryExecutor.loadRecords("sales_orders_raw", "SaleOrderEntry");
    return mapRecordLines(records, rowMapper::toSalesLineRow);
  }

  public List<Map<String, Object>> loadProductionOrders() {
    List<ErpSqliteOrderRecord> records = queryExecutor.loadRecords("production_orders_raw", "TreeEntity");
    List<Map<String, Object>> rows = mapRecordLines(records, rowMapper::toProductionOrderRow);
    if (!rows.isEmpty()) {
      return rows;
    }
    if (queryExecutor.hasLocalSqliteTable("production_orders_raw")) {
      return rows;
    }
    return queryExecutor.loadProductionOrdersFromApi();
  }

  public List<Map<String, Object>> loadProductionMaterialIssuesByOrder(String productionOrderNo, String materialListNo) {
    return loadProductionMaterialIssuesByOrder(productionOrderNo, materialListNo, false);
  }

  public List<Map<String, Object>> loadProductionMaterialIssuesByOrderContains(String productionOrderNoKeyword) {
    String normalizedKeyword = firstText(productionOrderNoKeyword);
    if (normalizedKeyword == null) {
      return List.of();
    }
    return queryExecutor.loadProductionMaterialIssuesFromApiByOrderContains(normalizedKeyword);
  }

  public List<Map<String, Object>> loadProductionMaterialIssuesByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    String normalizedOrderNo = firstText(productionOrderNo);
    String normalizedMaterialListNo = firstText(materialListNo);
    if (normalizedOrderNo == null && normalizedMaterialListNo == null) {
      return List.of();
    }
    return queryExecutor.loadProductionMaterialIssuesFromApiByOrder(
      normalizedOrderNo,
      normalizedMaterialListNo,
      forceRefresh
    );
  }

  public List<Map<String, Object>> loadPurchaseOrders() {
    List<ErpSqliteOrderRecord> records = queryExecutor.loadRecords(
      "purchase_orders_raw",
      List.of("POOrderEntry", "PurchaseOrderEntry", "PUR_POOrderEntry", "TreeEntity", "FEntity")
    );
    List<Map<String, Object>> rows = mapRecordLines(records, rowMapper::toPurchaseOrderRow);
    if (!rows.isEmpty()) {
      return rows;
    }
    if (queryExecutor.hasLocalSqliteTable("purchase_orders_raw")) {
      return rows;
    }
    return queryExecutor.loadPurchaseOrdersFromApi();
  }

  public Map<String, Object> loadMaterialSupplyInfo(String materialCode) {
    String normalizedMaterialCode = firstText(materialCode);
    if (normalizedMaterialCode == null) {
      return Map.of();
    }
    if (!queryExecutor.hasApiConfig()) {
      throw new IllegalStateException(
        "ERP material supply query requires API configuration. materialCode=" + normalizedMaterialCode
      );
    }
    Map<String, Object> apiRow = queryExecutor.queryMaterialSupplyInfoFromApi(normalizedMaterialCode);
    if (apiRow.isEmpty()) {
      throw new IllegalStateException(
        "ERP material supply query returned no rows. materialCode=" + normalizedMaterialCode
      );
    }
    return apiRow;
  }

  public List<Map<String, Object>> loadMaterialInventoryByCodes(List<String> materialCodes, boolean forceRefresh) {
    Set<String> normalizedCodes = normalizer.normalizeMaterialCodeSet(materialCodes);
    if (normalizedCodes.isEmpty()) {
      return List.of();
    }
    if (forceRefresh) {
      // Inventory is queried live; retained for API compatibility.
    }
    if (!validator.hasApiConfig()) {
      throw new IllegalStateException(
        "ERP material inventory query requires API configuration. materialCodes=" + normalizedCodes
      );
    }
    Map<String, Double> stockByCode = queryExecutor.queryMaterialInventoryStock(normalizedCodes);
    if (stockByCode.isEmpty()) {
      throw new IllegalStateException("ERP material inventory query returned no rows. materialCodes=" + normalizedCodes);
    }
    return assembler.buildInventoryRowsFromAggregate(stockByCode, stockByCode.keySet());
  }

  public List<Map<String, Object>> loadSalesOrderHeadersRaw() {
    return assembler.toHeaderRawRows(queryExecutor.loadRecords("sales_orders_raw", "SaleOrderEntry"));
  }

  public List<Map<String, Object>> loadSalesOrderLinesRaw() {
    return assembler.toLineRawRows(queryExecutor.loadRecords("sales_orders_raw", "SaleOrderEntry"));
  }

  public List<Map<String, Object>> loadProductionOrderHeadersRaw() {
    return assembler.toHeaderRawRows(queryExecutor.loadRecords("production_orders_raw", "TreeEntity"));
  }

  public List<Map<String, Object>> loadProductionOrderLinesRaw() {
    return assembler.toLineRawRows(queryExecutor.loadRecords("production_orders_raw", "TreeEntity"));
  }

  private List<Map<String, Object>> mapRecordLines(List<ErpSqliteOrderRecord> records, ErpRecordLineMapper mapper) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ErpSqliteOrderRecord record : records) {
      for (int i = 0; i < record.lines().size(); i += 1) {
        Map<String, Object> line = record.lines().get(i);
        rows.add(mapper.map(record, line, i + 1));
      }
    }
    return rows;
  }

  @FunctionalInterface
  private interface ErpRecordLineMapper {
    Map<String, Object> map(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo);
  }
}
