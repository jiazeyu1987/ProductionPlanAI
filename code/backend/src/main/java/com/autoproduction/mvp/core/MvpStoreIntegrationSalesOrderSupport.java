package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class MvpStoreIntegrationSalesOrderSupport {
  private MvpStoreIntegrationSalesOrderSupport() {}

  static List<Map<String, Object>> listSalesOrderLines(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      List<Map<String, Object>> erpRows = store.erpDataManager.getSalesOrderLines();
      if (!erpRows.isEmpty()) {
        return erpRows;
      }
      List<Map<String, Object>> rows = new ArrayList<>();
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      for (MvpDomain.Order order : store.state.orders) {
        MvpDomain.OrderBusinessData business = store.businessData(order);
        for (int i = 0; i < order.items.size(); i += 1) {
          MvpDomain.OrderItem item = order.items.get(i);
          rows.add(Map.of(
            "sales_order_no",
            (business.salesOrderNo == null || business.salesOrderNo.isBlank()) ? "SO-" + order.orderNo : business.salesOrderNo,
            "line_no", String.valueOf(i + 1),
            "product_code", item.productCode,
            "order_qty", item.qty,
            "order_date",
            MvpStoreRuntimeBase.toDateTime(
              (business.orderDate == null ? order.dueDate.minusDays(2) : business.orderDate).toString(),
              "D",
              true
            ),
            "expected_due_date", MvpStoreRuntimeBase.toDateTime(order.dueDate.toString(), "D", true),
            "requested_ship_date", MvpStoreRuntimeBase.toDateTime(order.dueDate.toString(), "N", true),
            "urgent_flag", order.urgent ? 1 : 0,
            "order_status", order.status,
            "last_update_time", now
          ));
        }
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listErpSalesOrderHeadersRaw(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      return store.erpDataManager.getSalesOrderHeadersRaw();
    }
  }

  static List<Map<String, Object>> listErpSalesOrderLinesRaw(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      return store.erpDataManager.getSalesOrderLinesRaw();
    }
  }
}

