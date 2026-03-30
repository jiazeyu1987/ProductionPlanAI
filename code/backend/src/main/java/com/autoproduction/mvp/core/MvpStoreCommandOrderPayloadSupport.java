package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class MvpStoreCommandOrderPayloadSupport {
  private MvpStoreCommandOrderPayloadSupport() {}

  static MvpDomain.Order fromOrderPayload(MvpStoreCommandOrderPatchSupport domain, Map<String, Object> payload) {
    String orderNo = domain.string(payload, "orderNo", domain.string(payload, "order_no", null));
    String orderType = domain.string(payload, "orderType", domain.string(payload, "order_type", "production"));
    LocalDate dueDate = LocalDate.parse(
      domain.string(payload, "dueDate", domain.string(payload, "due_date", domain.state.startDate.toString()))
    );
    boolean urgent = domain.bool(payload, "urgent", domain.number(payload, "urgent_flag", 0d) == 1d);
    boolean frozen = domain.bool(payload, "frozen", domain.number(payload, "frozen_flag", 0d) == 1d);
    boolean lockFlag = domain.bool(payload, "lockFlag", domain.number(payload, "lock_flag", 0d) == 1d);
    String status = domain.string(payload, "status", domain.string(payload, "order_status", "OPEN"));
    List<Map<String, Object>> items = domain.maps(payload.get("items"));
    List<MvpDomain.OrderItem> orderItems = new ArrayList<>();
    for (Map<String, Object> item : items) {
      orderItems.add(new MvpDomain.OrderItem(
        domain.string(item, "productCode", domain.string(item, "product_code", "UNKNOWN")),
        domain.number(item, "qty", 0d),
        domain.number(item, "completedQty", domain.number(item, "completed_qty", 0d))
      ));
    }
    MvpDomain.OrderBusinessData businessData = domain.parseBusinessData(payload, orderNo, orderItems, dueDate);
    LocalDate expectedStartDate = domain.parseLocalDateFlexible(
      domain.string(
        payload,
        "expected_start_date",
        domain.string(
          payload,
          "expectedStartDate",
          domain.string(payload, "expected_start_time", domain.string(payload, "expectedStartTime", null))
        )
      ),
      domain.state.startDate
    );
    return new MvpDomain.Order(
      orderNo,
      orderType,
      dueDate,
      expectedStartDate,
      urgent,
      frozen,
      lockFlag,
      status,
      orderItems,
      businessData
    );
  }

  static MvpDomain.OrderBusinessData parseBusinessData(
    MvpStoreCommandOrderPatchSupport domain,
    Map<String, Object> payload,
    String orderNo,
    List<MvpDomain.OrderItem> orderItems,
    LocalDate dueDate
  ) {
    String productCode = orderItems.isEmpty() ? "PROD_UNKNOWN" : orderItems.get(0).productCode;
    double orderQty = orderItems.stream().mapToDouble(item -> item.qty).sum();
    MvpDomain.OrderBusinessData data = new MvpDomain.OrderBusinessData();
    data.orderDate = domain.parseLocalDateFlexible(
      domain.string(payload, "order_date", domain.string(payload, "orderDate", dueDate.minusDays(2).toString())),
      dueDate.minusDays(2)
    );
    data.customerRemark = domain.string(payload, "customer_remark", domain.string(payload, "customerRemark", ""));
    data.productName = domain.string(
      payload,
      "product_name",
      domain.string(payload, "product_name_cn", domain.productNameCn(productCode))
    );
    data.specModel = domain.string(payload, "spec_model", domain.string(payload, "specModel", ""));
    data.productionBatchNo = domain.string(
      payload,
      "production_batch_no",
      domain.string(payload, "productionBatchNo", "BATCH-" + orderNo)
    );
    data.packagingForm = domain.string(payload, "packaging_form", domain.string(payload, "packagingForm", "缁剧顢栫悮"));
    data.salesOrderNo = domain.string(payload, "sales_order_no", domain.string(payload, "salesOrderNo", "SO-" + orderNo));
    data.productionDateForeignTrade = domain.string(
      payload,
      "production_date_foreign_trade",
      domain.string(payload, "productionDateForeignTrade", "")
    );
    data.purchaseDueDate = domain.string(payload, "purchase_due_date", domain.string(payload, "purchaseDueDate", ""));
    data.injectionDueDate = domain.string(payload, "injection_due_date", domain.string(payload, "injectionDueDate", ""));
    data.marketRemarkInfo = domain.string(payload, "market_remark_info", domain.string(payload, "marketRemarkInfo", ""));
    data.marketDemand = domain.number(payload, "market_demand", domain.number(payload, "marketDemand", orderQty));
    data.plannedFinishDate1 = domain.string(
      payload,
      "planned_finish_date_1",
      domain.string(payload, "plannedFinishDate1", dueDate.toString())
    );
    data.plannedFinishDate2 = domain.string(
      payload,
      "planned_finish_date_2",
      domain.string(payload, "plannedFinishDate2", dueDate.toString())
    );
    data.semiFinishedCode = domain.string(payload, "semi_finished_code", domain.string(payload, "semiFinishedCode", "SF-" + productCode));
    data.semiFinishedInventory = domain.number(payload, "semi_finished_inventory", domain.number(payload, "semiFinishedInventory", 0d));
    data.semiFinishedDemand = domain.number(payload, "semi_finished_demand", domain.number(payload, "semiFinishedDemand", orderQty));
    data.semiFinishedWip = domain.number(payload, "semi_finished_wip", domain.number(payload, "semiFinishedWip", 0d));
    data.needOrderQty = domain.number(
      payload,
      "need_order_qty",
      domain.number(
        payload,
        "needOrderQty",
        Math.max(0d, data.semiFinishedDemand - data.semiFinishedInventory - data.semiFinishedWip)
      )
    );
    data.pendingInboundQty = domain.number(payload, "pending_inbound_qty", domain.number(payload, "pendingInboundQty", 0d));
    data.weeklyMonthlyPlanRemark = domain.string(
      payload,
      "weekly_monthly_process_plan",
      domain.string(
        payload,
        "process_schedule_remark",
        domain.string(payload, "weeklyMonthlyPlanRemark", "閸涖劏顓搁崚鎺炵礄3.16-3.22閿")
      )
    );
    data.workshopOuterPackagingDate = domain.string(
      payload,
      "workshop_outer_packaging_date",
      domain.string(payload, "workshopOuterPackagingDate", domain.formatShortDate(dueDate))
    );
    data.note = domain.string(payload, "note", domain.string(payload, "remark", ""));
    data.workshopCompletedQty = domain.number(payload, "workshop_completed_qty", domain.number(payload, "workshopCompletedQty", 0d));
    data.workshopCompletedTime = domain.string(payload, "workshop_completed_time", domain.string(payload, "workshopCompletedTime", ""));
    data.outerCompletedQty = domain.number(payload, "outer_completed_qty", domain.number(payload, "outerCompletedQty", 0d));
    data.outerCompletedTime = domain.string(payload, "outer_completed_time", domain.string(payload, "outerCompletedTime", ""));
    data.matchStatus = domain.string(payload, "match_status", domain.string(payload, "matchStatus", "瀵板懎灏柊"));
    return data;
  }

  static MvpDomain.OrderBusinessData buildSimulationBusinessData(
    MvpStoreCommandOrderPatchSupport domain,
    LocalDate businessDate,
    LocalDate dueDate,
    String salesOrderNo,
    String productCode,
    double qty
  ) {
    MvpDomain.OrderBusinessData data = new MvpDomain.OrderBusinessData();
    data.orderDate = businessDate;
    data.customerRemark = "濡剝瀚欑拋銏犲礋";
    data.productName = domain.productNameCn(productCode);
    data.specModel = "SIM-" + productCode + "-" + (int) qty;
    data.productionBatchNo = "SIM-" + businessDate.toString().replace("-", "");
    data.packagingForm = "缁剧顢栫悮";
    data.salesOrderNo = salesOrderNo;
    data.productionDateForeignTrade = "";
    data.purchaseDueDate = "";
    data.injectionDueDate = "";
    data.marketRemarkInfo = "娴犺法婀￠懛顏勫З閻㈢喐鍨";
    data.marketDemand = qty;
    data.plannedFinishDate1 = dueDate.toString();
    data.plannedFinishDate2 = dueDate.toString();
    data.semiFinishedCode = "SF-" + productCode;
    data.semiFinishedInventory = domain.round2(qty * 0.1d);
    data.semiFinishedDemand = qty;
    data.semiFinishedWip = domain.round2(qty * 0.2d);
    data.needOrderQty = domain.round2(Math.max(0d, qty - data.semiFinishedInventory - data.semiFinishedWip));
    data.pendingInboundQty = domain.round2(data.needOrderQty * 0.5d);
    data.weeklyMonthlyPlanRemark = "娴犺法婀＄拋鈥冲灊";
    data.workshopOuterPackagingDate = domain.formatShortDate(dueDate);
    data.note = "SIM";
    data.workshopCompletedQty = 0d;
    data.workshopCompletedTime = "";
    data.outerCompletedQty = 0d;
    data.outerCompletedTime = "";
    data.matchStatus = "瀵板懎灏柊";
    return data;
  }
}

