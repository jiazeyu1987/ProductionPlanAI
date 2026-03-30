package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderTextUtils.fixPotentialUtf8Mojibake;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstNonNullNumber;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

final class ErpSqlitePurchaseOrderRowMapper extends ErpSqliteOrderRowMapperSupport {
  Map<String, Object> toPurchaseOrderRow(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo) {
    String purchaseOrderNo = firstText(record.header().get("BillNo"), record.base().get("bill_no"));
    String orderDate = firstText(record.header().get("Date"), record.base().get("bill_date"));
    String expectedArrivalDate = firstText(
      line.get("DeliveryDate"),
      line.get("PlanArriveDate"),
      line.get("ArriveDate"),
      orderDate
    );
    String materialCode = firstText(materialNumber(line), line.get("MaterialId_Id"), line.get("MaterialNumber"), "UNKNOWN");
    String materialName = fixPotentialUtf8Mojibake(firstText(materialNameCn(line), line.get("MaterialName"), line.get("MaterialName_Text")));
    String specModel = firstText(materialSpecification(line), line.get("Model"), line.get("Specification"));
    Number orderQty = firstNonNullNumber(
      toNumber(line.get("Qty")),
      toNumber(line.get("FQty")),
      toNumber(line.get("BaseQty"))
    );
    Number receivedQty = firstNonNullNumber(
      toNumber(line.get("ReceiveQty")),
      toNumber(line.get("InStockQty")),
      toNumber(line.get("StockInQty"))
    );
    String supplierCode = firstText(
      mapValue(line.get("SupplierId"), "Number"),
      mapValue(record.header().get("SupplierId"), "Number"),
      line.get("SupplierId_Id")
    );
    String supplierNameCn = fixPotentialUtf8Mojibake(firstText(
      masterDataNameCn(line.get("SupplierId")),
      masterDataNameCn(record.header().get("SupplierId")),
      mapValue(line.get("SupplierId"), "Name"),
      mapValue(record.header().get("SupplierId"), "Name")
    ));
    String sourceProductionOrderNo = firstText(
      line.get("SrcBillNo"),
      line.get("MtoNo"),
      line.get("SaleOrderNo"),
      line.get("SourceBillNo")
    );
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("purchase_order_no", purchaseOrderNo);
    row.put("line_no", firstText(line.get("Seq"), String.valueOf(lineNo)));
    row.put("source_production_order_no", sourceProductionOrderNo);
    row.put("material_code", materialCode);
    row.put("material_name_cn", materialName);
    row.put("spec_model", specModel);
    row.put("order_qty", orderQty == null ? 0d : orderQty);
    row.put("received_qty", receivedQty == null ? 0d : receivedQty);
    row.put("order_date", orderDate);
    row.put("expected_arrival_date", expectedArrivalDate);
    row.put("supplier_code", supplierCode);
    row.put("supplier_name_cn", supplierNameCn);
    row.put("purchase_status", firstText(record.header().get("DocumentStatus"), record.base().get("document_status")));
    row.put("last_update_time", firstText(record.base().get("fetched_at"), now));
    appendErpRefColumns(row, record, lineNo);
    return row;
  }

  Map<String, Object> toPurchaseOrderRowFromApi(Map<String, Object> row, int fallbackLineNo) {
    String purchaseOrderNo = firstText(
      row.get("FBillNo"),
      row.get("BillNo"),
      row.get("purchase_order_no")
    );
    String lineNo = firstText(
      row.get("FPOOrderEntry_FSeq"),
      row.get("FSeq"),
      String.valueOf(fallbackLineNo)
    );
    String materialCode = firstText(
      row.get("FPOOrderEntry_FMaterialId.FNumber"),
      row.get("FMaterialId.FNumber"),
      row.get("FMaterialId_Id"),
      "UNKNOWN"
    );
    String materialNameCn = fixPotentialUtf8Mojibake(firstText(
      row.get("FPOOrderEntry_FMaterialId.FName"),
      row.get("FMaterialId.FName"),
      row.get("FMaterialName")
    ));
    String specModel = firstText(
      row.get("FPOOrderEntry_FMaterialId.FSpecification"),
      row.get("FMaterialId.FSpecification"),
      row.get("FSpecification")
    );
    Number orderQty = firstNonNullNumber(
      toNumber(row.get("FPOOrderEntry_FQty")),
      toNumber(row.get("FQty"))
    );
    Number receivedQty = firstNonNullNumber(
      toNumber(row.get("FPOOrderEntry_FReceiveQty")),
      toNumber(row.get("FReceiveQty"))
    );
    String orderDate = firstText(row.get("FDate"));
    String expectedArrivalDate = firstText(
      row.get("FPOOrderEntry_FDeliveryDate"),
      row.get("FDeliveryDate")
    );
    String supplierCode = firstText(row.get("FSupplierId.FNumber"), row.get("FSupplierId_Id"));
    String supplierName = fixPotentialUtf8Mojibake(firstText(row.get("FSupplierId.FName"), row.get("FSupplierName")));
    String sourceProductionOrderNo = firstText(row.get("FPOOrderEntry_FSrcBillNo"), row.get("FSrcBillNo"));
    String status = firstText(row.get("FDocumentStatus"), row.get("DocumentStatus"));
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("purchase_order_no", purchaseOrderNo);
    out.put("line_no", lineNo);
    out.put("source_production_order_no", sourceProductionOrderNo);
    out.put("material_code", materialCode);
    out.put("material_name_cn", materialNameCn);
    out.put("spec_model", specModel);
    out.put("order_qty", orderQty == null ? 0d : orderQty);
    out.put("received_qty", receivedQty == null ? 0d : receivedQty);
    out.put("order_date", orderDate);
    out.put("expected_arrival_date", expectedArrivalDate);
    out.put("supplier_code", supplierCode);
    out.put("supplier_name_cn", supplierName);
    out.put("purchase_status", status);
    out.put("last_update_time", now);
    out.put("erp_source_table", "ERP_API_PURCHASE_ORDER");
    out.put("erp_record_id", firstText(row.get("FID"), purchaseOrderNo));
    out.put("erp_line_no", lineNo);
    out.put("erp_line_id", firstText(row.get("FEntryID"), purchaseOrderNo + ":" + lineNo));
    out.put("erp_form_id", "PUR_PurchaseOrder");
    out.put("erp_document_status", status);
    out.put("erp_fetched_at", now);
    return out;
  }
}

