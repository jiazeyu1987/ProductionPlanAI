package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstNonNullNumber;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.round3;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

  List<Map<String, Object>> queryBomChildMaterialsFromEngBomView(
    String productionOrderNo,
    String materialListNo,
    Number productionOrderQty
  ) {
    String normalizedProductionOrderNo = firstText(productionOrderNo);
    String normalizedMaterialListNo = firstText(materialListNo);
    if (normalizedProductionOrderNo == null || normalizedMaterialListNo == null) {
      return List.of();
    }

    Object viewed = billQueryClient.viewBillFromApi("ENG_BOM", normalizedMaterialListNo, null);
    if (!(viewed instanceof Map<?, ?> viewedMap)) {
      return List.of();
    }
    Map<String, Object> model = extractViewBillModel(viewedMap);
    if (model == null || model.isEmpty()) {
      return List.of();
    }

    Object treeEntityObj = model.get("TreeEntity");
    if (!(treeEntityObj instanceof List<?> treeEntityList) || treeEntityList.isEmpty()) {
      return List.of();
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object item : treeEntityList) {
      Map<String, Object> entry = toStringObjectMap(item);
      if (entry == null || entry.isEmpty()) {
        continue;
      }
      Map<String, Object> child = toStringObjectMap(entry.get("MATERIALIDCHILD"));
      if (child == null || child.isEmpty()) {
        child = toStringObjectMap(entry.get("MaterialIdChild"));
      }
      String childCode = child == null ? null : firstText(child.get("Number"));
      if (childCode == null) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("source_production_order_no", normalizedProductionOrderNo);
      row.put("source_bill_no", normalizedMaterialListNo);
      row.put("source_bill_type", "ENG_BOM");
      row.put("child_material_code", childCode);
      row.put("child_material_name_cn", extractLocalizedText(child, "Name"));
      row.put("spec_model", extractLocalizedText(child, "Specification"));
      double issueQty = resolveOrderIssueQty(entry, productionOrderQty);
      row.put("issue_qty", issueQty);
      row.put("required_qty", issueQty);
      row.put("erp_source_table", "ERP_API_ENG_BOM_VIEW");
      row.put("erp_form_id", "ENG_BOM");
      row.put("erp_record_id", firstText(model.get("Id"), model.get("FID"), normalizedMaterialListNo));
      row.put("erp_line_no", firstText(entry.get("Seq"), entry.get("Id")));
      row.put("erp_line_id", firstText(entry.get("RowId"), entry.get("Id"), childCode));
      rows.add(row);
    }
    return rows;
  }

  List<Map<String, Object>> queryBomChildMaterialsFromApi(String materialListNo) {
    String normalizedMaterialListNo = firstText(materialListNo);
    if (normalizedMaterialListNo == null) {
      return List.of();
    }
    String normalizedMaterialBaseNo = normalizer.normalizeMaterialListBaseNo(normalizedMaterialListNo);
    List<String> forms = List.of("PRD_PPBOM", "ENG_BOM");
    List<String> fieldVariants = List.of(
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMATERIALIDCHILD.FNumber,FTreeEntity_FMATERIALIDCHILD.FName,FTreeEntity_FMATERIALIDCHILD.FSpecification,FTreeEntity_FDENOMINATOR,FTreeEntity_FNUMERATOR",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMaterialIdChild.FNumber,FTreeEntity_FMaterialIdChild.FName,FTreeEntity_FMaterialIdChild.FSpecification,FTreeEntity_FDenominator,FTreeEntity_FNumerator",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMATERIALIDCHILD.FNumber,FTreeEntity_FMATERIALIDCHILD.FName,FTreeEntity_FMATERIALIDCHILD.FSpecification,FTreeEntity_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FMaterialIdChild.FNumber,FTreeEntity_FMaterialIdChild.FName,FTreeEntity_FMaterialIdChild.FSpecification,FTreeEntity_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,FEntity_FMaterialId.FSpecification,FEntity_FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FMATERIALID.FNumber,FEntity_FMATERIALID.FName,FEntity_FMATERIALID.FSpecification,FEntity_FBASEQTY",
      "FID,FBillNo,FNumber,FDocumentStatus,FSubMaterialId.FNumber,FSubMaterialId.FName,FSubMaterialId.FSpecification,FSubActualQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FTreeEntity_FSubMaterialId.FNumber,FTreeEntity_FSubMaterialId.FName,FTreeEntity_FSubMaterialId.FSpecification,FTreeEntity_FSubActualQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FEntity_FSubMaterialId.FNumber,FEntity_FSubMaterialId.FName,FEntity_FSubMaterialId.FSpecification,FEntity_FSubActualQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification,FQty,FBaseQty",
      "FID,FBillNo,FNumber,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification,FBaseQty",
      "FID,FBillNo,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification,FQty",
      "FID,FBillNo,FDocumentStatus,FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification"
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

  private Map<String, Object> extractViewBillModel(Map<?, ?> viewResponse) {
    Map<String, Object> root = toStringObjectMap(viewResponse);
    if (root == null) {
      return null;
    }
    Map<String, Object> resultMap = toStringObjectMap(root.get("Result"));
    if (resultMap == null) {
      return null;
    }
    Map<String, Object> modelMap = toStringObjectMap(resultMap.get("Result"));
    if (modelMap != null && !modelMap.isEmpty()) {
      return modelMap;
    }
    return toStringObjectMap(resultMap.get("NeedReturnData"));
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

  private String extractLocalizedText(Map<String, Object> source, String fieldName) {
    if (source == null || fieldName == null) {
      return null;
    }
    Object raw = source.get(fieldName);
    if (raw instanceof List<?> rawList && !rawList.isEmpty()) {
      Object first = rawList.get(0);
      if (first instanceof Map<?, ?> rawMap) {
        return firstText(rawMap.get("Value"), rawMap.get("Name"));
      }
    }
    return firstText(raw);
  }

  private double resolveOrderIssueQty(Map<String, Object> entry, Number productionOrderQty) {
    Number explicitQty = firstNonNullNumber(
      positiveNumber(entry.get("MustQty")),
      positiveNumber(entry.get("NeedQty")),
      positiveNumber(entry.get("BaseQty")),
      positiveNumber(entry.get("Qty")),
      positiveNumber(entry.get("ActualQty"))
    );
    if (explicitQty != null) {
      return round3(explicitQty.doubleValue());
    }

    Number numerator = firstNonNullNumber(
      positiveNumber(entry.get("BaseNumerator")),
      positiveNumber(entry.get("NUMERATOR")),
      positiveNumber(entry.get("Numerator")),
      positiveNumber(entry.get("BaseQty")),
      positiveNumber(entry.get("NeedQty")),
      positiveNumber(entry.get("MustQty"))
    );
    Number denominator = firstNonNullNumber(
      positiveNumber(entry.get("BaseDenominator")),
      positiveNumber(entry.get("DENOMINATOR")),
      positiveNumber(entry.get("Denominator"))
    );
    if (numerator == null || denominator == null || denominator.doubleValue() <= 0d || productionOrderQty == null) {
      return 0d;
    }
    double rawIssueQty = productionOrderQty.doubleValue() * numerator.doubleValue() / denominator.doubleValue();
    if (rawIssueQty <= 0d) {
      return 0d;
    }
    return round3(Math.ceil(rawIssueQty));
  }

  private Number positiveNumber(Object value) {
    Number number = toNumber(value);
    if (number == null || number.doubleValue() <= 0d) {
      return null;
    }
    return number;
  }
}
