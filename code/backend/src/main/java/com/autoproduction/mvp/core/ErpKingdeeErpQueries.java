package com.autoproduction.mvp.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpKingdeeErpQueries {
  private static final Logger log = LoggerFactory.getLogger(ErpKingdeeErpQueries.class);

  private final ErpSqliteOrderValidator validator;
  private final ErpKingdeePurchaseOrderQuery purchaseOrderQuery;
  private final ErpKingdeeProductionOrderQuery productionOrderQuery;
  private final ErpKingdeeProductionMaterialIssueQuery productionMaterialIssueQuery;
  private final ErpKingdeeMaterialQuery materialQuery;

  ErpKingdeeErpQueries(
    ObjectMapper objectMapper,
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
    ErpKingdeeApiSession session = new ErpKingdeeApiSession(
      objectMapper,
      verifySsl,
      timeoutSeconds,
      baseUrl,
      acctId,
      username,
      password,
      lcid,
      validator
    );
    ErpKingdeeBillQueryClient billQueryClient = new ErpKingdeeBillQueryClient(session, objectMapper, validator);
    this.purchaseOrderQuery = new ErpKingdeePurchaseOrderQuery(billQueryClient, rowMapper);
    this.productionOrderQuery = new ErpKingdeeProductionOrderQuery(billQueryClient, validator, rowMapper);
    this.productionMaterialIssueQuery = new ErpKingdeeProductionMaterialIssueQuery(
      billQueryClient,
      payloadMapper,
      normalizer,
      validator,
      rowMapper
    );
    this.materialQuery = new ErpKingdeeMaterialQuery(billQueryClient, normalizer, validator, assembler, rowMapper);
  }

  List<Map<String, Object>> loadPurchaseOrdersFromApi() {
    if (!validator.hasApiConfig()) {
      return List.of();
    }
    try {
      return purchaseOrderQuery.queryPurchaseRowsFromApi(
        "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,"
          + "FQty,FReceiveQty,FPurchaseOrgId.FNumber,FSupplierId.FNumber,FSupplierId.FName,FSrcBillNo",
        "FDate DESC,FID DESC"
      );
    } catch (RuntimeException ex) {
      log.warn("ERP purchase query failed by api. {}", ex.getMessage());
      return List.of();
    }
  }

  List<Map<String, Object>> loadProductionOrdersFromApi() {
    if (!validator.hasApiConfig()) {
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
        List<Map<String, Object>> rows = productionOrderQuery.queryProductionRowsFromApi(fieldKeys, "FDate DESC,FID DESC");
        if (!rows.isEmpty()) {
          return rows;
        }
      } catch (RuntimeException ex) {
        log.debug("ERP production query variant failed. fields={}", fieldKeys, ex);
      }
    }
    return List.of();
  }

  List<Map<String, Object>> loadProductionMaterialIssuesFromApiByOrder(
    String productionOrderNo,
    String materialListNo,
    boolean forceRefresh
  ) {
    return productionMaterialIssueQuery.loadProductionMaterialIssuesFromApiByOrder(productionOrderNo, materialListNo, forceRefresh);
  }

  Map<String, Object> queryMaterialSupplyInfoFromApi(String materialCode) {
    return materialQuery.queryMaterialSupplyInfoFromApi(materialCode);
  }

  Map<String, Double> queryMaterialInventoryStock(Set<String> normalizedCodes) {
    return materialQuery.queryMaterialInventoryStock(normalizedCodes);
  }
}

