package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ErpKingdeeProductionMaterialIssueQueryDelegate {
  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteOrderRowMapper rowMapper;
  private final ErpKingdeeProductionOrderQuery productionOrderQuery;
  private final ErpKingdeeProductionMaterialIssueApiSupport apiSupport;
  private final ErpKingdeeProductionMaterialIssueBomSupport bomSupport;

  ErpKingdeeProductionMaterialIssueQueryDelegate(
    ErpKingdeeBillQueryClient billQueryClient,
    ErpOrderPayloadMapper payloadMapper,
    ErpOrderNormalizer normalizer,
    ErpSqliteOrderValidator validator,
    ErpSqliteOrderRowMapper rowMapper
  ) {
    this.validator = validator;
    this.rowMapper = rowMapper;
    this.productionOrderQuery = new ErpKingdeeProductionOrderQuery(billQueryClient, validator, rowMapper);
    ErpKingdeePpBomViewSupport ppBomViewSupport = new ErpKingdeePpBomViewSupport(
      billQueryClient,
      payloadMapper,
      rowMapper
    );
    this.apiSupport = new ErpKingdeeProductionMaterialIssueApiSupport(billQueryClient, validator);
    this.bomSupport = new ErpKingdeeProductionMaterialIssueBomSupport(billQueryClient, normalizer, validator, ppBomViewSupport);
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrderContains(String productionOrderNoKeyword) {
    if (!validator.hasApiConfig()) {
      return List.of();
    }
    String keyword = productionOrderNoKeyword == null ? "" : productionOrderNoKeyword.trim();
    if (keyword.isBlank()) {
      return List.of();
    }

    List<Map<String, Object>> normalized = new ArrayList<>();
    String orderFields = "FID,FBillNo,FBomId.FNumber,FBOMID.FNumber,FQty";
    List<Map<String, Object>> orders = productionOrderQuery.queryProductionRowsByBillNoContains(keyword, orderFields, 20);
    int lineNo = 1;
    for (Map<String, Object> order : orders) {
      String productionOrderNo = text(order.get("FBillNo"));
      String materialListNo = text(
        order.get("FBomId.FNumber"),
        order.get("FBOMID.FNumber"),
        order.get("FBomId_Number")
      );
      Number productionOrderQty = toNumber(order.get("FQty"));
      if (productionOrderNo == null || materialListNo == null) {
        continue;
      }
      List<Map<String, Object>> rows = bomSupport.queryBomChildMaterialsFromEngBomView(
        productionOrderNo,
        materialListNo,
        productionOrderQty
      );
      for (Map<String, Object> row : rows) {
        normalized.add(rowMapper.toProductionMaterialIssueRowFromApi(row, lineNo));
        lineNo += 1;
      }
    }
    return normalized;
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    if (forceRefresh) {
      bomSupport.clearPpBomViewCache();
    }
    if (!validator.hasApiConfig()) {
      return List.of();
    }

    List<Map<String, Object>> normalized = new ArrayList<>();
    if (productionOrderNo != null) {
      String orderFields = "FID,FBillNo,FBomId.FNumber,FBOMID.FNumber,FQty";
      List<Map<String, Object>> orders = productionOrderQuery.queryProductionRowsByBillNoContains(productionOrderNo, orderFields, 20);
      int lineNo = 1;
      for (Map<String, Object> order : orders) {
        String resolvedProductionOrderNo = text(order.get("FBillNo"));
        if (resolvedProductionOrderNo == null || !resolvedProductionOrderNo.equalsIgnoreCase(productionOrderNo)) {
          continue;
        }
        String resolvedMaterialListNo = materialListNo == null
          ? text(order.get("FBomId.FNumber"), order.get("FBOMID.FNumber"), order.get("FBomId_Number"))
          : materialListNo;
        Number productionOrderQty = toNumber(order.get("FQty"));
        if (resolvedMaterialListNo == null) {
          continue;
        }
        List<Map<String, Object>> rows = bomSupport.queryBomChildMaterialsFromEngBomView(
          resolvedProductionOrderNo,
          resolvedMaterialListNo,
          productionOrderQty
        );
        for (Map<String, Object> row : rows) {
          normalized.add(rowMapper.toProductionMaterialIssueRowFromApi(row, lineNo));
          lineNo += 1;
        }
      }
      if (!normalized.isEmpty()) {
        return normalized;
      }
    }

    String baseFields =
      "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FActualQty,"
        + "FPrdOrgId.FNumber,FStockOrgId.FNumber,FSrcBillNo,FSrcBillType";
    List<Map<String, Object>> rows = new ArrayList<>();
    if (materialListNo != null) {
      String filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
        + " and FSrcBillNo = '" + validator.escapeFilterValue(materialListNo) + "'";
      rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields, filter);
    }
    if (rows.isEmpty() && productionOrderNo != null) {
      String filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
        + " and FMoBillNo = '" + validator.escapeFilterValue(productionOrderNo) + "'";
      rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields + ",FMoBillNo", filter);
    }
    for (int i = 0; i < rows.size(); i += 1) {
      normalized.add(rowMapper.toProductionMaterialIssueRowFromApi(rows.get(i), i + 1));
    }
    return normalized;
  }

  private String text(Object... values) {
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
}
