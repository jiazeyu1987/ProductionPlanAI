package com.autoproduction.mvp.core;

import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.firstText;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toInt;
import static com.autoproduction.mvp.core.erp.loader.ErpLoaderValueUtils.toNumber;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

final class ErpSqliteSalesLineRowMapper extends ErpSqliteOrderRowMapperSupport {
  Map<String, Object> toSalesLineRow(ErpSqliteOrderRecord record, Map<String, Object> line, int lineNo) {
    String salesOrderNo = firstText(record.header().get("BillNo"), record.base().get("bill_no"));
    String orderDate = firstText(record.header().get("Date"), record.base().get("bill_date"));
    String expectedDueDate = firstText(line.get("DeliveryDate"), firstPlanDeliveryDate(line), orderDate);
    String requestedShipDate = firstText(line.get("DeliveryDate"), expectedDueDate);
    String productCode = firstText(materialNumber(line), line.get("MaterialId_Id"), "UNKNOWN");
    Number qty = toNumber(line.get("Qty"));
    Integer urgentFlag = toInt(line.get("Priority")) > 0 ? 1 : 0;
    String now = OffsetDateTime.now(ZoneOffset.UTC).toString();

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("sales_order_no", salesOrderNo);
    row.put("line_no", firstText(line.get("Seq"), String.valueOf(lineNo)));
    row.put("product_code", productCode);
    row.put("order_qty", qty == null ? 0d : qty);
    row.put("order_date", orderDate);
    row.put("expected_due_date", expectedDueDate);
    row.put("requested_ship_date", requestedShipDate);
    row.put("urgent_flag", urgentFlag);
    row.put("order_status", firstText(record.header().get("DocumentStatus"), record.base().get("document_status")));
    row.put("last_update_time", firstText(record.base().get("fetched_at"), now));
    appendErpRefColumns(row, record, lineNo);
    return row;
  }
}

