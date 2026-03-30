package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ErpKingdeeProductionMaterialIssueBomSupport {
  private static final Logger log = LoggerFactory.getLogger(ErpKingdeeProductionMaterialIssueQuery.class);

  private final ErpKingdeeBillQueryClient billQueryClient;
  private final ErpOrderNormalizer normalizer;
  private final ErpSqliteOrderValidator validator;
  private final ErpKingdeePpBomViewSupport ppBomViewSupport;

  ErpKingdeeProductionMaterialIssueBomSupport(
    ErpKingdeeBillQueryClient billQueryClient,
    ErpOrderNormalizer normalizer,
    ErpSqliteOrderValidator validator,
    ErpKingdeePpBomViewSupport ppBomViewSupport
  ) {
    this.billQueryClient = billQueryClient;
    this.normalizer = normalizer;
    this.validator = validator;
    this.ppBomViewSupport = ppBomViewSupport;
  }

  void clearPpBomViewCache() {
    ppBomViewSupport.clearCache();
  }

  List<Map<String, Object>> queryBomChildMaterialsFromApi(String materialListNo) {
    String normalizedMaterialListNo = firstText(materialListNo);
    if (normalizedMaterialListNo == null) {
      return List.of();
    }
    String normalizedMaterialBaseNo = normalizer.normalizeMaterialListBaseNo(normalizedMaterialListNo);
    List<String> forms = List.of("PRD_PPBOM", "ENG_BOM");
    List<String> fieldVariants = List.of(
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMATERIALIDCHILD.FNumber,FTreeEntity_FMATERIALIDCHILD.FName,FTreeEntity_FDENOMINATOR,FTreeEntity_FNUMERATOR",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMaterialIdChild.FNumber,FTreeEntity_FMaterialIdChild.FName,FTreeEntity_FDenominator,FTreeEntity_FNumerator",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMATERIALIDCHILD.FNumber,FTreeEntity_FMATERIALIDCHILD.FName,FTreeEntity_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMaterialIdChild.FNumber,FTreeEntity_FMaterialIdChild.FName,FTreeEntity_FBaseQty",
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
    filters.add("FBillNo = '" + validator.escapeFilterValue(normalizedMaterialListNo) + "'");
    filters.add("FNumber = '" + validator.escapeFilterValue(normalizedMaterialListNo) + "'");
    filters.add("FMaterialId.FNumber = '" + validator.escapeFilterValue(normalizedMaterialListNo) + "'");
    if (normalizedMaterialBaseNo != null && !normalizedMaterialBaseNo.equalsIgnoreCase(normalizedMaterialListNo)) {
      filters.add("FNumber = '" + validator.escapeFilterValue(normalizedMaterialBaseNo) + "'");
      filters.add("FMaterialId.FNumber = '" + validator.escapeFilterValue(normalizedMaterialBaseNo) + "'");
    }
    for (String formId : forms) {
      for (String fieldKeys : fieldVariants) {
        for (String filter : filters) {
          try {
            List<Map<String, Object>> rows = billQueryClient.queryBillRowsFromApi(
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
                List<Map<String, Object>> expandedRows = ppBomViewSupport.expandPpBomRowsByView(rows, normalizedMaterialListNo);
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
}

