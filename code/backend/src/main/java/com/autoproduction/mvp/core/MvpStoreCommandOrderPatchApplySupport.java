package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.Map;

final class MvpStoreCommandOrderPatchApplySupport {
  private MvpStoreCommandOrderPatchApplySupport() {}

  static void applyOrderPatch(MvpStoreCommandOrderPatchSupport domain, MvpDomain.Order order, Map<String, Object> patch) {
    if (patch.containsKey("frozen")) {
      order.frozen = domain.bool(patch, "frozen", false);
    }
    if (patch.containsKey("frozen_flag")) {
      order.frozen = domain.number(patch, "frozen_flag", 0d) == 1d;
    }
    if (patch.containsKey("urgent")) {
      order.urgent = domain.bool(patch, "urgent", false);
    }
    if (patch.containsKey("urgent_flag")) {
      order.urgent = domain.number(patch, "urgent_flag", 0d) == 1d;
    }
    if (patch.containsKey("lockFlag")) {
      order.lockFlag = domain.bool(patch, "lockFlag", false);
    }
    if (patch.containsKey("lock_flag")) {
      order.lockFlag = domain.number(patch, "lock_flag", 0d) == 1d;
    }
    if (patch.containsKey("dueDate")) {
      order.dueDate = LocalDate.parse(domain.string(patch, "dueDate", order.dueDate.toString()));
    }
    if (patch.containsKey("due_date")) {
      order.dueDate = LocalDate.parse(domain.string(patch, "due_date", order.dueDate.toString()));
    }
    if (
      patch.containsKey("expected_start_date")
        || patch.containsKey("expectedStartDate")
        || patch.containsKey("expected_start_time")
        || patch.containsKey("expectedStartTime")
    ) {
      LocalDate fallback = order.expectedStartDate == null ? domain.state.startDate : order.expectedStartDate;
      order.expectedStartDate = domain.parseLocalDateFlexible(
        domain.string(
          patch,
          "expected_start_date",
          domain.string(
            patch,
            "expectedStartDate",
            domain.string(patch, "expected_start_time", domain.string(patch, "expectedStartTime", null))
          )
        ),
        fallback
      );
    }
    if (patch.containsKey("status")) {
      order.status = domain.string(patch, "status", order.status);
    }
    if (patch.containsKey("order_status")) {
      order.status = domain.string(patch, "order_status", order.status);
    }
    domain.applyBusinessPatch(order, patch);
    for (Map<String, Object> itemPatch : domain.maps(patch.get("items"))) {
      String productCode = domain.string(itemPatch, "productCode", domain.string(itemPatch, "product_code", null));
      MvpDomain.OrderItem item = order.items.stream().filter(it -> it.productCode.equals(productCode)).findFirst().orElse(null);
      if (item != null && itemPatch.containsKey("qty")) {
        item.qty = domain.number(itemPatch, "qty", item.qty);
        item.completedQty = Math.min(item.completedQty, item.qty);
      }
    }
    domain.updateOrderProgressFacts(order);
  }

  static void applyBusinessPatch(MvpStoreCommandOrderPatchSupport domain, MvpDomain.Order order, Map<String, Object> patch) {
    MvpDomain.OrderBusinessData data = domain.businessData(order);
    if (patch.containsKey("order_date") || patch.containsKey("orderDate")) {
      data.orderDate = domain.parseLocalDateFlexible(
        domain.string(
          patch,
          "order_date",
          domain.string(patch, "orderDate", data.orderDate == null ? null : data.orderDate.toString())
        ),
        data.orderDate == null ? order.dueDate.minusDays(2) : data.orderDate
      );
    }
    if (patch.containsKey("customer_remark") || patch.containsKey("customerRemark")) {
      data.customerRemark = domain.string(patch, "customer_remark", domain.string(patch, "customerRemark", data.customerRemark));
    }
    if (patch.containsKey("product_name") || patch.containsKey("product_name_cn")) {
      data.productName = domain.string(patch, "product_name", domain.string(patch, "product_name_cn", data.productName));
    }
    if (patch.containsKey("spec_model") || patch.containsKey("specModel")) {
      data.specModel = domain.string(patch, "spec_model", domain.string(patch, "specModel", data.specModel));
    }
    if (patch.containsKey("production_batch_no") || patch.containsKey("productionBatchNo")) {
      data.productionBatchNo = domain.string(
        patch,
        "production_batch_no",
        domain.string(patch, "productionBatchNo", data.productionBatchNo)
      );
    }
    if (patch.containsKey("packaging_form") || patch.containsKey("packagingForm")) {
      data.packagingForm = domain.string(patch, "packaging_form", domain.string(patch, "packagingForm", data.packagingForm));
    }
    if (patch.containsKey("sales_order_no") || patch.containsKey("salesOrderNo")) {
      data.salesOrderNo = domain.string(patch, "sales_order_no", domain.string(patch, "salesOrderNo", data.salesOrderNo));
    }
    if (patch.containsKey("production_date_foreign_trade")) {
      data.productionDateForeignTrade = domain.string(patch, "production_date_foreign_trade", data.productionDateForeignTrade);
    }
    if (patch.containsKey("purchase_due_date")) {
      data.purchaseDueDate = domain.string(patch, "purchase_due_date", data.purchaseDueDate);
    }
    if (patch.containsKey("injection_due_date")) {
      data.injectionDueDate = domain.string(patch, "injection_due_date", data.injectionDueDate);
    }
    if (patch.containsKey("market_remark_info")) {
      data.marketRemarkInfo = domain.string(patch, "market_remark_info", data.marketRemarkInfo);
    }
    if (patch.containsKey("market_demand")) {
      data.marketDemand = domain.number(patch, "market_demand", data.marketDemand);
    }
    if (patch.containsKey("planned_finish_date_1")) {
      data.plannedFinishDate1 = domain.string(patch, "planned_finish_date_1", data.plannedFinishDate1);
    }
    if (patch.containsKey("planned_finish_date_2")) {
      data.plannedFinishDate2 = domain.string(patch, "planned_finish_date_2", data.plannedFinishDate2);
    }
    if (patch.containsKey("semi_finished_code")) {
      data.semiFinishedCode = domain.string(patch, "semi_finished_code", data.semiFinishedCode);
    }
    if (patch.containsKey("semi_finished_inventory")) {
      data.semiFinishedInventory = domain.number(patch, "semi_finished_inventory", data.semiFinishedInventory);
    }
    if (patch.containsKey("semi_finished_demand")) {
      data.semiFinishedDemand = domain.number(patch, "semi_finished_demand", data.semiFinishedDemand);
    }
    if (patch.containsKey("semi_finished_wip")) {
      data.semiFinishedWip = domain.number(patch, "semi_finished_wip", data.semiFinishedWip);
    }
    if (patch.containsKey("need_order_qty")) {
      data.needOrderQty = domain.number(patch, "need_order_qty", data.needOrderQty);
    }
    if (patch.containsKey("pending_inbound_qty")) {
      data.pendingInboundQty = domain.number(patch, "pending_inbound_qty", data.pendingInboundQty);
    }
    if (patch.containsKey("weekly_monthly_process_plan") || patch.containsKey("process_schedule_remark")) {
      data.weeklyMonthlyPlanRemark = domain.string(
        patch,
        "weekly_monthly_process_plan",
        domain.string(patch, "process_schedule_remark", data.weeklyMonthlyPlanRemark)
      );
    }
    if (patch.containsKey("workshop_outer_packaging_date")) {
      data.workshopOuterPackagingDate = domain.string(patch, "workshop_outer_packaging_date", data.workshopOuterPackagingDate);
    }
    if (patch.containsKey("note") || patch.containsKey("remark")) {
      data.note = domain.string(patch, "note", domain.string(patch, "remark", data.note));
    }
    if (patch.containsKey("workshop_completed_qty")) {
      data.workshopCompletedQty = domain.number(patch, "workshop_completed_qty", data.workshopCompletedQty);
    }
    if (patch.containsKey("workshop_completed_time")) {
      data.workshopCompletedTime = domain.string(patch, "workshop_completed_time", data.workshopCompletedTime);
    }
    if (patch.containsKey("outer_completed_qty")) {
      data.outerCompletedQty = domain.number(patch, "outer_completed_qty", data.outerCompletedQty);
    }
    if (patch.containsKey("outer_completed_time")) {
      data.outerCompletedTime = domain.string(patch, "outer_completed_time", data.outerCompletedTime);
    }
    if (patch.containsKey("match_status")) {
      data.matchStatus = domain.string(patch, "match_status", data.matchStatus);
    }
  }
}

