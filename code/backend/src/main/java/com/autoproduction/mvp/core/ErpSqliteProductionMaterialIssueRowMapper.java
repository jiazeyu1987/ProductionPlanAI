package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderTextUtils.fixPotentialUtf8Mojibake;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstNonNullNumber;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

final class ErpSqliteProductionMaterialIssueRowMapper extends ErpSqliteOrderRowMapperSupport {
  Map<String, Object> toProductionMaterialIssueRowFromApi(Map<String, Object> row, int fallbackLineNo) {
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
}

