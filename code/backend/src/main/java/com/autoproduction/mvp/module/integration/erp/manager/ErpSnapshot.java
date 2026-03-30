package com.autoproduction.mvp.module.integration.erp.manager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ErpSnapshot(
  List<Map<String, Object>> salesOrderLines,
  List<Map<String, Object>> productionOrders,
  List<Map<String, Object>> purchaseOrders,
  List<Map<String, Object>> salesOrderHeadersRaw,
  List<Map<String, Object>> salesOrderLinesRaw,
  List<Map<String, Object>> productionOrderHeadersRaw,
  List<Map<String, Object>> productionOrderLinesRaw
) {
  public static ErpSnapshot empty() {
    return new ErpSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  public Map<String, Object> toCountMap() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sales_order_lines", salesOrderLines.size());
    body.put("production_orders", productionOrders.size());
    body.put("purchase_orders", purchaseOrders.size());
    body.put("sales_order_headers_raw", salesOrderHeadersRaw.size());
    body.put("sales_order_lines_raw", salesOrderLinesRaw.size());
    body.put("production_order_headers_raw", productionOrderHeadersRaw.size());
    body.put("production_order_lines_raw", productionOrderLinesRaw.size());
    return body;
  }
}

