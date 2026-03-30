package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstNonNullNumber;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

final class ErpSqliteProductionOrderRowMapper extends ErpSqliteOrderRowMapperSupport {
  Map<String, Object> toProductionOrderRow(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo) {
    String productionOrderNo = firstText(record.header().get("BillNo"), record.base().get("bill_no"));
    String sourceSalesOrderNo = firstText(line.get("SaleOrderNo"), line.get("SrcBillNo"), "UNLINKED-" + productionOrderNo);
    String sourceLineNo = firstText(line.get("SaleOrderEntrySeq"), line.get("Seq"), String.valueOf(lineNo));
    String bomNumber = firstText(mapValue(line.get("BomId"), "Number"));
    String productCode = firstText(materialNumber(line), line.get("MaterialId_Id"), "UNKNOWN");
    String productNameCn = firstText(materialNameCn(line));
    Number planQty = toNumber(line.get("Qty"));
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("production_order_no", productionOrderNo);
    row.put("source_sales_order_no", sourceSalesOrderNo);
    row.put("source_line_no", sourceLineNo);
    row.put("source_plan_order_no", "PLAN-" + productionOrderNo);
    row.put("material_list_no", bomNumber == null ? "BOM-" + productionOrderNo : bomNumber);
    row.put("product_code", productCode);
    row.put("product_name_cn", productNameCn);
    row.put("plan_qty", planQty == null ? 0d : planQty);
    row.put("production_status", firstText(record.header().get("DocumentStatus"), record.base().get("document_status")));
    row.put("order_date", firstText(record.header().get("Date"), record.base().get("bill_date")));
    row.put("customer_remark", firstText(record.header().get("Description"), line.get("FRemarks")));
    row.put("spec_model", firstText(materialSpecification(line)));
    row.put("production_batch_no", firstText(line.get("Lot_Text"), line.get("Lot")));
    row.put("planned_finish_date_1", firstText(line.get("PlanStartDate")));
    row.put("planned_finish_date_2", firstText(line.get("PlanFinishDate")));
    row.put("production_date_foreign_trade", firstText(line.get("ProduceDate")));
    row.put("packaging_form", firstText(line.get("F_PAEZ_Text5"), line.get("F_PAEZ_Text1")));
    row.put("sales_order_no", firstText(line.get("SaleOrderNo"), line.get("SrcBillNo")));
    row.put("purchase_due_date", null);
    row.put("injection_due_date", null);
    row.put("market_remark_info", firstText(record.header().get("Note"), record.header().get("Description")));
    row.put("market_demand", null);
    row.put("semi_finished_code", null);
    row.put("semi_finished_inventory", null);
    row.put("semi_finished_demand", null);
    row.put("semi_finished_wip", null);
    row.put("need_order_qty", null);
    row.put("pending_inbound_qty", null);
    row.put("weekly_monthly_process_plan", null);
    row.put("workshop_outer_packaging_date", null);
    row.put("note", firstText(line.get("FRemarks"), record.header().get("Description")));
    row.put("workshop_completed_qty", toNumber(line.get("RepQuaQty")));
    row.put("workshop_completed_time", firstText(line.get("FinishDate")));
    row.put("outer_completed_qty", null);
    row.put("outer_completed_time", null);
    row.put("match_status", null);
    row.put("last_update_time", firstText(record.base().get("fetched_at"), now));
    appendErpRefColumns(row, record, lineNo);
    return row;
  }

  Map<String, Object> toProductionOrderRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    String productionOrderNo = firstText(
      row.get("FBillNo"),
      row.get("BillNo"),
      row.get("production_order_no")
    );
    String sourceBillNo = firstText(
      row.get("FSrcBillNo"),
      row.get("source_sales_order_no")
    );
    String sourceLineNo = firstText(
      row.get("FSeq"),
      row.get("FTreeEntity_FSeq"),
      String.valueOf(fallbackLineNo)
    );
    String bomNumber = firstText(
      row.get("FBomId.FNumber"),
      row.get("FBOMID.FNumber"),
      row.get("FBomId_Number"),
      row.get("material_list_no")
    );
    String productCode = firstText(
      row.get("FMaterialId.FNumber"),
      row.get("FMaterialId_Id"),
      row.get("product_code"),
      "UNKNOWN"
    );
    String productNameCn = firstText(
      row.get("FMaterialId.FName"),
      row.get("FMaterialName"),
      row.get("product_name_cn")
    );
    Number planQty = firstNonNullNumber(
      toNumber(row.get("FQty")),
      toNumber(row.get("plan_qty"))
    );
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
    String status = firstText(row.get("FDocumentStatus"), row.get("DocumentStatus"), row.get("production_status"));
    String orderDate = firstText(row.get("FDate"), row.get("order_date"));
    String planStartDate = firstText(row.get("FPlanStartDate"), row.get("planned_finish_date_1"));
    String planFinishDate = firstText(row.get("FPlanFinishDate"), row.get("planned_finish_date_2"));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("production_order_no", productionOrderNo);
    out.put("source_sales_order_no", sourceBillNo == null ? "UNLINKED-" + productionOrderNo : sourceBillNo);
    out.put("source_line_no", sourceLineNo);
    out.put("source_plan_order_no", sourceBillNo == null ? null : sourceBillNo);
    out.put("material_list_no", bomNumber == null ? "BOM-" + productionOrderNo : bomNumber);
    out.put("product_code", productCode);
    out.put("product_name_cn", productNameCn);
    out.put("plan_qty", planQty == null ? 0d : planQty);
    out.put("production_status", status);
    out.put("order_date", orderDate);
    out.put("customer_remark", firstText(row.get("FDescription"), row.get("FRemarks"), row.get("customer_remark")));
    out.put("spec_model", firstText(row.get("FMaterialId.FSpecification"), row.get("spec_model")));
    out.put("production_batch_no", firstText(row.get("FLot.FNumber"), row.get("production_batch_no")));
    out.put("planned_finish_date_1", planStartDate);
    out.put("planned_finish_date_2", planFinishDate);
    out.put("production_date_foreign_trade", firstText(row.get("FProduceDate"), row.get("production_date_foreign_trade")));
    out.put("packaging_form", firstText(row.get("packaging_form")));
    out.put("sales_order_no", sourceBillNo);
    out.put("purchase_due_date", firstText(row.get("purchase_due_date")));
    out.put("injection_due_date", firstText(row.get("injection_due_date")));
    out.put("market_remark_info", firstText(row.get("market_remark_info")));
    out.put("market_demand", toNumber(row.get("market_demand")));
    out.put("semi_finished_code", firstText(row.get("semi_finished_code")));
    out.put("semi_finished_inventory", toNumber(row.get("semi_finished_inventory")));
    out.put("semi_finished_demand", toNumber(row.get("semi_finished_demand")));
    out.put("semi_finished_wip", toNumber(row.get("semi_finished_wip")));
    out.put("need_order_qty", toNumber(row.get("need_order_qty")));
    out.put("pending_inbound_qty", toNumber(row.get("pending_inbound_qty")));
    out.put("weekly_monthly_process_plan", firstText(row.get("weekly_monthly_process_plan")));
    out.put("workshop_outer_packaging_date", firstText(row.get("workshop_outer_packaging_date")));
    out.put("note", firstText(row.get("FNote"), row.get("note")));
    out.put("workshop_completed_qty", firstNonNullNumber(toNumber(row.get("FRepQuaQty")), toNumber(row.get("workshop_completed_qty"))));
    out.put("workshop_completed_time", firstText(row.get("FFinishDate"), row.get("workshop_completed_time")));
    out.put("outer_completed_qty", firstNonNullNumber(toNumber(row.get("outer_completed_qty"))));
    out.put("outer_completed_time", firstText(row.get("outer_completed_time")));
    out.put("match_status", firstText(row.get("match_status")));
    out.put("last_update_time", firstText(row.get("erp_fetched_at"), now));
    out.put("erp_source_table", firstText(row.get("erp_source_table"), "ERP_API_PRODUCTION_ORDER"));
    out.put("erp_record_id", firstText(row.get("FID"), row.get("erp_record_id"), productionOrderNo));
    out.put("erp_line_no", sourceLineNo);
    out.put("erp_line_id", firstText(row.get("FEntryID"), row.get("erp_line_id"), productionOrderNo + ":" + sourceLineNo));
    out.put("erp_form_id", firstText(row.get("erp_form_id"), "PRD_MO"));
    out.put("erp_document_status", status);
    out.put("erp_fetched_at", firstText(row.get("erp_fetched_at"), now));
    return out;
  }
}

