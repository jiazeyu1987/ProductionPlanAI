package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreIntegrationProductionOrderSupport {
  private MvpStoreIntegrationProductionOrderSupport() {}

  static List<Map<String, Object>> listPlanOrders(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        MvpDomain.OrderBusinessData business = store.businessData(order);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("plan_order_no", "PO-" + order.orderNo);
        row.put(
          "source_sales_order_no",
          business.salesOrderNo == null || business.salesOrderNo.isBlank() ? "SO-" + order.orderNo : business.salesOrderNo
        );
        row.put("source_line_no", "1");
        row.put("release_type", "PRODUCTION");
        row.put("release_status", "RELEASED");
        row.put("release_time", now);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listPurchaseOrders(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      return store.erpDataManager.getPurchaseOrders();
    }
  }

  static List<Map<String, Object>> listProductionOrders(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      List<Map<String, Object>> erpRows = store.erpDataManager.getProductionOrders();
      if (!erpRows.isEmpty()) {
        return erpRows;
      }
      String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
      List<Map<String, Object>> rows = new ArrayList<>();
      for (MvpDomain.Order order : store.state.orders) {
        MvpDomain.OrderBusinessData business = store.businessData(order);
        double totalQty = order.items.stream().mapToDouble(item -> item.qty).sum();
        LocalDate expectedStartDate = store.resolveExpectedStartDate(order);
        String expectedStartTime = expectedStartDate == null ? null : MvpStoreRuntimeBase.toDateTime(expectedStartDate.toString(), "D", true);
        String expectedFinishTime = store.estimateExpectedFinishTime(order);
        LocalDate expectedFinishDate = store.parseLocalDateFlexible(expectedFinishTime, null);
        String productCode = order.items.isEmpty() ? "UNKNOWN" : store.normalizeCode(order.items.get(0).productCode);
        List<Map<String, Object>> processContexts = store.buildProcessContextsForProduct(productCode);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("production_order_no", order.orderNo);
        row.put(
          "source_sales_order_no",
          business.salesOrderNo == null || business.salesOrderNo.isBlank() ? "SO-" + order.orderNo : business.salesOrderNo
        );
        row.put("source_line_no", "1");
        row.put("source_plan_order_no", "PO-" + order.orderNo);
        row.put("material_list_no", "ML-" + order.orderNo);
        row.put("product_code", order.items.isEmpty() ? "UNKNOWN" : order.items.get(0).productCode);
        row.put("product_name_cn", business.productName);
        row.put("company_codes", store.joinContextValues(processContexts, "company_codes"));
        row.put("workshop_codes", store.joinContextValues(processContexts, "workshop_codes"));
        row.put("line_codes", store.joinContextValues(processContexts, "line_codes"));
        row.put("process_codes", store.joinContextValues(processContexts, "process_code"));
        row.put("process_route_summary", store.summarizeProcessContexts(processContexts));
        row.put("process_contexts", processContexts);
        row.put("plan_qty", totalQty);
        row.put("production_status", order.status);
        row.put("expected_start_date", expectedStartDate == null ? null : expectedStartDate.toString());
        row.put("expected_start_time", expectedStartTime);
        row.put("expected_finish_date", expectedFinishDate == null ? null : expectedFinishDate.toString());
        row.put("expected_finish_time", expectedFinishTime);
        row.put("order_date", business.orderDate == null ? null : business.orderDate.toString());
        row.put("customer_remark", business.customerRemark);
        row.put("spec_model", business.specModel);
        row.put("production_batch_no", business.productionBatchNo);
        row.put("planned_finish_date_1", business.plannedFinishDate1);
        row.put("planned_finish_date_2", business.plannedFinishDate2);
        row.put("production_date_foreign_trade", business.productionDateForeignTrade);
        row.put("packaging_form", business.packagingForm);
        row.put("sales_order_no", business.salesOrderNo);
        row.put("purchase_due_date", business.purchaseDueDate);
        row.put("injection_due_date", business.injectionDueDate);
        row.put("market_remark_info", business.marketRemarkInfo);
        row.put("market_demand", business.marketDemand);
        row.put("semi_finished_code", business.semiFinishedCode);
        row.put("semi_finished_inventory", business.semiFinishedInventory);
        row.put("semi_finished_demand", business.semiFinishedDemand);
        row.put("semi_finished_wip", business.semiFinishedWip);
        row.put("need_order_qty", business.needOrderQty);
        row.put("pending_inbound_qty", business.pendingInboundQty);
        row.put("weekly_monthly_process_plan", business.weeklyMonthlyPlanRemark);
        row.put("workshop_outer_packaging_date", business.workshopOuterPackagingDate);
        row.put("note", business.note);
        row.put("workshop_completed_qty", business.workshopCompletedQty);
        row.put("workshop_completed_time", business.workshopCompletedTime);
        row.put("outer_completed_qty", business.outerCompletedQty);
        row.put("outer_completed_time", business.outerCompletedTime);
        row.put("match_status", business.matchStatus);
        row.put("last_update_time", now);
        rows.add(row);
      }
      return rows;
    }
  }

  static List<Map<String, Object>> listErpProductionOrderHeadersRaw(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      return store.erpDataManager.getProductionOrderHeadersRaw();
    }
  }

  static List<Map<String, Object>> listErpProductionOrderLinesRaw(MvpStoreIntegrationDomain store) {
    synchronized (store.lock) {
      return store.erpDataManager.getProductionOrderLinesRaw();
    }
  }
}

