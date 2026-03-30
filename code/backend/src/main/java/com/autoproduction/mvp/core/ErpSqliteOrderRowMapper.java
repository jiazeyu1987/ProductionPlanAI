package com.autoproduction.mvp.core;

import java.util.Map;

final class ErpSqliteOrderRowMapper extends ErpSqliteOrderRowMapperSupport {
  private final ErpSqliteSalesLineRowMapper salesLineRowMapper = new ErpSqliteSalesLineRowMapper();
  private final ErpSqliteProductionOrderRowMapper productionOrderRowMapper = new ErpSqliteProductionOrderRowMapper();
  private final ErpSqlitePurchaseOrderRowMapper purchaseOrderRowMapper = new ErpSqlitePurchaseOrderRowMapper();
  private final ErpSqliteMaterialSupplyInfoRowMapper materialSupplyInfoRowMapper = new ErpSqliteMaterialSupplyInfoRowMapper();
  private final ErpSqliteProductionMaterialIssueRowMapper productionMaterialIssueRowMapper = new ErpSqliteProductionMaterialIssueRowMapper();

  Map<String, Object> toSalesLineRow(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo) {
    return salesLineRowMapper.toSalesLineRow(record, line, lineNo);
  }

  Map<String, Object> toProductionOrderRow(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo) {
    return productionOrderRowMapper.toProductionOrderRow(record, line, lineNo);
  }

  Map<String, Object> toPurchaseOrderRow(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo) {
    return purchaseOrderRowMapper.toPurchaseOrderRow(record, line, lineNo);
  }

  Map<String, Object> normalizeMaterialSupplyInfo(Map<String, Object> row, String materialCode) {
    return materialSupplyInfoRowMapper.normalizeMaterialSupplyInfo(row, materialCode);
  }

  Map<String, Object> toProductionOrderRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    return productionOrderRowMapper.toProductionOrderRowFromApi(row, fallbackLineNo);
  }

  Map<String, Object> toProductionMaterialIssueRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    return productionMaterialIssueRowMapper.toProductionMaterialIssueRowFromApi(row, fallbackLineNo);
  }

  Map<String, Object> toPurchaseOrderRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    return purchaseOrderRowMapper.toPurchaseOrderRowFromApi(row, fallbackLineNo);
  }
}

