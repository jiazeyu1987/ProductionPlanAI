package com.autoproduction.mvp.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreIntegrationScheduleOpsSupport {
  private MvpStoreIntegrationScheduleOpsSupport() {}

  static List<Map<String, Object>> listScheduleControls(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("order_type", order.orderType);
        row.put("review_passed_flag", 1);
        row.put("frozen_flag", order.frozen ? 1 : 0);
        row.put("schedulable_flag", "CLOSED".equals(order.status) ? 0 : 1);
        row.put("close_flag", "CLOSED".equals(order.status) ? 1 : 0);
        row.put("promised_due_date", MvpStoreRuntimeBase.toDateTime(order.dueDate.toString(), "D", true));
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listMrpLinks(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("order_type", order.orderType);
        row.put("mrp_run_id", "MRP-" + order.orderNo);
        row.put("run_time", now);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listDeliveryProgress(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", order.orderNo);
        row.put("order_type", order.orderType);
        row.put("warehoused_qty", order.items.stream().mapToDouble(item -> item.completedQty).sum());
        row.put("shipped_qty", 0d);
        row.put("delivery_status", "IN_PROGRESS");
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }
}

