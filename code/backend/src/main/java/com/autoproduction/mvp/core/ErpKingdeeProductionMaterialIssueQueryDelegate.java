package com.autoproduction.mvp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpKingdeeProductionMaterialIssueQueryDelegate {
  private static final Logger log = LoggerFactory.getLogger(ErpKingdeeProductionMaterialIssueQuery.class);

  private final ErpSqliteOrderValidator validator;
  private final ErpSqliteOrderRowMapper rowMapper;
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
    ErpKingdeePpBomViewSupport ppBomViewSupport = new ErpKingdeePpBomViewSupport(
      billQueryClient,
      payloadMapper,
      rowMapper
    );
    this.apiSupport = new ErpKingdeeProductionMaterialIssueApiSupport(billQueryClient, validator);
    this.bomSupport = new ErpKingdeeProductionMaterialIssueBomSupport(billQueryClient, normalizer, validator, ppBomViewSupport);
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

    String baseFields =
      "FID,FBillNo,FDate,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FActualQty,"
        + "FPrdOrgId.FNumber,FStockOrgId.FNumber,FSrcBillNo,FSrcBillType";
    List<Map<String, Object>> rows = new ArrayList<>();

    if (materialListNo != null) {
      String filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
        + " and FSrcBillNo = '" + validator.escapeFilterValue(materialListNo) + "'";
      rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields, filter);
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
            filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
              + " and " + field + " = '" + validator.escapeFilterValue(materialListNo) + "'";
            rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields + "," + field, filter);
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
            filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
              + " and " + field + " = '" + validator.escapeFilterValue(materialListNo) + "'";
            rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields + "," + field, filter);
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
          String filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
            + " and " + field + " = '" + validator.escapeFilterValue(productionOrderNo) + "'";
          rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields + "," + field, filter);
          if (!rows.isEmpty()) {
            break;
          }
        } catch (RuntimeException ex) {
          log.debug("ERP pick material query candidate failed. field={}", field, ex);
        }
      }
      if (rows.isEmpty()) {
        String filter = validator.buildOrgFilter("FPrdOrgId.FNumber")
          + " and FSrcBillNo = '" + validator.escapeFilterValue(productionOrderNo) + "'";
        rows = apiSupport.queryProductionMaterialRowsFromApi(baseFields, filter);
      }
    }

    if (rows.isEmpty() && productionOrderNo != null) {
      rows = apiSupport.scanProductionMaterialRowsByOrderNo(productionOrderNo);
    }

    if (rows.isEmpty() && materialListNo != null) {
      rows = bomSupport.queryBomChildMaterialsFromApi(materialListNo);
    }

    List<Map<String, Object>> normalized = new ArrayList<>();
    for (int i = 0; i < rows.size(); i += 1) {
      normalized.add(rowMapper.toProductionMaterialIssueRowFromApi(rows.get(i), i + 1));
    }
    return normalized;
  }
}

