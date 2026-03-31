package com.autoproduction.mvp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ErpSqliteOrderQueryExecutor {
  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteSnapshotReader snapshotReader;
  private final ErpKingdeeErpQueries apiQueries;

  ErpSqliteOrderQueryExecutor(
    ObjectMapper objectMapper,
    boolean useRealOrders,
    String sqlitePath,
    String baseUrl,
    String acctId,
    String username,
    String password,
    int lcid,
    int timeoutSeconds,
    boolean verifySsl,
    ErpOrderPayloadMapper payloadMapper,
    ErpOrderNormalizer normalizer,
    ErpSqliteOrderValidator validator,
    ErpSqliteOrderAssembler assembler,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.validator = validator;
    this.snapshotReader = new ErpSqliteSnapshotReader(useRealOrders, sqlitePath, payloadMapper);
    this.apiQueries = new ErpKingdeeErpQueries(
      objectMapper,
      baseUrl,
      acctId,
      username,
      password,
      lcid,
      timeoutSeconds,
      verifySsl,
      payloadMapper,
      normalizer,
      validator,
      assembler,
      rowMapper
    );
  }

  boolean hasApiConfig() {
    return validator.hasApiConfig();
  }

  List<ErpSqliteOrderRecord> loadRecords(String tableName, String lineListField) {
    return loadRecords(tableName, List.of(lineListField));
  }

  List<ErpSqliteOrderRecord> loadRecords(String tableName, List<String> lineListFields) {
    return snapshotReader.loadRecords(tableName, lineListFields);
  }

  boolean hasLocalSqliteSnapshot() {
    return snapshotReader.hasLocalSqliteSnapshot();
  }

  boolean hasLocalSqliteTable(String tableName) {
    return snapshotReader.hasLocalSqliteTable(tableName);
  }

  List<Map<String, Object>> loadPurchaseOrdersFromApi() {
    return apiQueries.loadPurchaseOrdersFromApi();
  }

  Map<String, Object> queryMaterialSupplyInfoFromApi(String materialCode) {
    return apiQueries.queryMaterialSupplyInfoFromApi(materialCode);
  }

  Map<String, Double> queryMaterialInventoryStock(Set<String> normalizedCodes) {
    return apiQueries.queryMaterialInventoryStock(normalizedCodes);
  }

  List<Map<String, Object>> loadProductionOrdersFromApi() {
    return apiQueries.loadProductionOrdersFromApi();
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    return apiQueries.loadProductionMaterialIssuesFromApiByOrder(productionOrderNo, materialListNo, forceRefresh);
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrderContains(String productionOrderNoKeyword) {
    return apiQueries.loadProductionMaterialIssuesFromApiByOrderContains(productionOrderNoKeyword);
  }
}
