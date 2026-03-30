package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpKingdeeProductionMaterialIssueApiSupport {
  private static final Logger log = LoggerFactory.getLogger(ErpKingdeeProductionMaterialIssueQuery.class);

  private final ErpKingdeeBillQueryClient billQueryClient;
  private final ErpSqliteOrderValidator validator;

  ErpKingdeeProductionMaterialIssueApiSupport(ErpKingdeeBillQueryClient billQueryClient, ErpSqliteOrderValidator validator) {
    this.billQueryClient = billQueryClient;
    this.validator = validator;
  }

  List<Map<String, Object>> queryProductionMaterialRowsFromApi(String fieldKeys, String filterString) {
    List<Map<String, Object>> result = new ArrayList<>();
    int startRow = 0;
    int limit = 500;
    while (true) {
      List<Map<String, Object>> page = billQueryClient.queryBillRowsFromApi(
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

  List<Map<String, Object>> scanProductionMaterialRowsByOrderNo(String productionOrderNo) {
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
    String filter = validator.buildOrgDateFilter("FPrdOrgId.FNumber", "FDate", 365);

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
            if (validator.equalsIgnoreCaseSafe(rowOrderNo, normalizedOrderNo)) {
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
}

