package com.autoproduction.mvp.core;

import com.autoproduction.mvp.core.MvpStoreRuntimeBase.MaterialDemandRow;
import com.autoproduction.mvp.core.MvpStoreRuntimeBase.OrderMaterialConstraint;
import com.autoproduction.mvp.core.MvpStoreRuntimeBase.OrderMaterialConstraintSnapshot;
import com.autoproduction.mvp.core.MvpStoreRuntimeBase.OrderMaterialWorkRow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.number;
import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.string;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.elapsedMillis;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.normalizeCode;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.normalizeMaterialCode;
import static com.autoproduction.mvp.core.MvpStoreCoreNormalizationSupport.round3;

final class MvpStoreOrderMaterialConstraintSupport {
  private MvpStoreOrderMaterialConstraintSupport() {}

  static List<Map<String, Object>> buildOrderMaterialAvailabilityRowsFromOrderPool(
    MvpStoreOrderDomain domain,
    boolean refreshFromErp
  ) {
    List<Map<String, Object>> orderPoolRows = domain.listOrderPool(Map.of());
    List<Map<String, Object>> forcedRows = new ArrayList<>();
    List<Map<String, Object>> prioritizedRows = new ArrayList<>();
    List<Map<String, Object>> fallbackRows = new ArrayList<>();
    for (Map<String, Object> row : orderPoolRows) {
      String orderNo = string(row, "order_no", null);
      if (orderNo == null || orderNo.isBlank() || !MvpStoreOrderDomain.looksLikeErpProductionOrderNo(orderNo)) {
        continue;
      }
      String normalizedOrderNo = normalizeCode(orderNo);
      if (normalizedOrderNo != null && MvpStoreRuntimeBase.ORDER_MATERIAL_PRIORITY_ORDER_NOS.contains(normalizedOrderNo)) {
        forcedRows.add(row);
        continue;
      }
      String materialListNo = string(row, "material_list_no", "");
      if (materialListNo != null && materialListNo.toUpperCase(Locale.ROOT).startsWith("BOM-")) {
        fallbackRows.add(row);
      } else {
        prioritizedRows.add(row);
      }
    }
    List<Map<String, Object>> orderedRows = new ArrayList<>(forcedRows);
    orderedRows.addAll(prioritizedRows);
    orderedRows.addAll(fallbackRows);

    List<OrderMaterialWorkRow> workRows = new ArrayList<>();
    Set<String> allMaterialCodes = new HashSet<>();
    Set<String> seenOrderNos = new HashSet<>();
    long startedNanos = System.nanoTime();
    long budgetMs = refreshFromErp
      ? MvpStoreRuntimeBase.ORDER_MATERIAL_AVAILABILITY_REFRESH_BUDGET_MS
      : MvpStoreRuntimeBase.ORDER_MATERIAL_AVAILABILITY_BUDGET_MS;

    for (Map<String, Object> orderRow : orderedRows) {
      if (elapsedMillis(startedNanos) >= budgetMs) {
        break;
      }
      String orderNo = string(orderRow, "order_no", null);
      if (orderNo == null || orderNo.isBlank() || !seenOrderNos.add(orderNo)) {
        continue;
      }
      if (!MvpStoreOrderDomain.looksLikeErpProductionOrderNo(orderNo)) {
        continue;
      }

      double totalOrderQty = Math.max(0d, number(orderRow, "order_qty", number(orderRow, "plan_qty", 0d)));
      double completedOrderQty = Math.max(0d, number(orderRow, "completed_qty", 0d));
      double remainingOrderQty = Math.max(
        0d,
        number(orderRow, "remaining_qty", Math.max(0d, totalOrderQty - completedOrderQty))
      );
      if (remainingOrderQty <= 1e-9d) {
        continue;
      }

      String productCode = normalizeCode(string(orderRow, "product_code", null));
      String firstProcessCode = domain.resolveFirstProcessCode(productCode);
      OrderMaterialWorkRow work = new OrderMaterialWorkRow();
      work.orderNo = orderNo;
      work.orderStatus = string(orderRow, "status", string(orderRow, "order_status", "OPEN"));
      work.productCode = productCode;
      work.productNameCn = string(orderRow, "product_name_cn", domain.productNameCn(productCode));
      work.firstProcessCode = firstProcessCode;
      work.firstProcessNameCn = MvpStoreRuntimeBase.processNameCn(firstProcessCode);
      work.totalOrderQty = totalOrderQty > 1e-9d ? totalOrderQty : remainingOrderQty;
      work.remainingOrderQty = remainingOrderQty;

      List<Map<String, Object>> rawRows = domain.listOrderPoolMaterials(orderNo, refreshFromErp);
      if (rawRows.isEmpty() && productCode != null && !productCode.isBlank()) {
        rawRows = domain.listMaterialChildrenByParentCode(productCode, refreshFromErp);
      }
      Map<String, MaterialDemandRow> demandByCode = new LinkedHashMap<>();
      for (Map<String, Object> rawRow : rawRows) {
        String materialCode = normalizeMaterialCode(string(rawRow, "child_material_code", null));
        if (materialCode == null || materialCode.isBlank()) {
          continue;
        }
        double requiredQty = Math.max(0d, number(rawRow, "required_qty", 0d));
        MaterialDemandRow demand = demandByCode.computeIfAbsent(materialCode, ignored -> {
          MaterialDemandRow row = new MaterialDemandRow();
          row.materialCode = materialCode;
          row.materialNameCn = string(rawRow, "child_material_name_cn", "");
          return row;
        });
        demand.requiredQty += requiredQty;
        if (demand.materialNameCn == null || demand.materialNameCn.isBlank()) {
          demand.materialNameCn = string(rawRow, "child_material_name_cn", "");
        }
      }

      double demandScale = work.totalOrderQty > 1e-9d
        ? Math.max(0d, Math.min(1d, work.remainingOrderQty / work.totalOrderQty))
        : 1d;
      for (MaterialDemandRow demand : demandByCode.values()) {
        demand.demandQty = round3(Math.max(0d, demand.requiredQty * demandScale));
        work.materialRows.add(demand);
        allMaterialCodes.add(demand.materialCode);
      }

      workRows.add(work);
      if (workRows.size() >= MvpStoreRuntimeBase.ORDER_MATERIAL_AVAILABILITY_MAX_ORDERS) {
        break;
      }
    }

    return buildOrderMaterialConstraintSnapshotRows(domain, workRows, allMaterialCodes, refreshFromErp).rows;
  }

  static OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshot(MvpStoreOrderDomain domain, boolean refreshFromErp) {
    List<OrderMaterialWorkRow> workRows = new ArrayList<>();
    Set<String> allMaterialCodes = new HashSet<>();
    for (MvpDomain.Order order : domain.state.orders) {
      if (!domain.hasRemainingQty(order)) {
        continue;
      }
      String orderNo = order.orderNo == null ? "" : order.orderNo.trim();
      if (orderNo.isBlank()) {
        continue;
      }
      String productCode = normalizeCode(domain.orderPrimaryProductCode(order));
      double totalOrderQty = domain.orderTotalQty(order);
      double remainingOrderQty = domain.orderRemainingQty(order);
      String firstProcessCode = domain.resolveFirstProcessCode(productCode);
      OrderMaterialWorkRow work = new OrderMaterialWorkRow();
      work.orderNo = orderNo;
      work.orderStatus = string(domain.toOrderPoolItem(order), "status", order.status);
      work.productCode = productCode;
      work.productNameCn = domain.productNameCn(productCode);
      work.firstProcessCode = firstProcessCode;
      work.firstProcessNameCn = MvpStoreRuntimeBase.processNameCn(firstProcessCode);
      work.totalOrderQty = totalOrderQty;
      work.remainingOrderQty = remainingOrderQty;
      List<Map<String, Object>> rawRows = List.of();
      if (MvpStoreOrderDomain.looksLikeErpProductionOrderNo(orderNo)) {
        rawRows = domain.listOrderPoolMaterials(orderNo, refreshFromErp);
      }
      if (rawRows.isEmpty() && productCode != null && !productCode.isBlank()) {
        rawRows = domain.listMaterialChildrenByParentCode(productCode, refreshFromErp);
      }
      Map<String, MaterialDemandRow> demandByCode = new LinkedHashMap<>();
      for (Map<String, Object> rawRow : rawRows) {
        String materialCode = normalizeMaterialCode(string(rawRow, "child_material_code", null));
        if (materialCode == null || materialCode.isBlank()) {
          continue;
        }
        double requiredQty = Math.max(0d, number(rawRow, "required_qty", 0d));
        MaterialDemandRow demand = demandByCode.computeIfAbsent(materialCode, ignored -> {
          MaterialDemandRow row = new MaterialDemandRow();
          row.materialCode = materialCode;
          row.materialNameCn = string(rawRow, "child_material_name_cn", "");
          return row;
        });
        demand.requiredQty += requiredQty;
        if (demand.materialNameCn == null || demand.materialNameCn.isBlank()) {
          demand.materialNameCn = string(rawRow, "child_material_name_cn", "");
        }
      }

      double demandScale = totalOrderQty > 1e-9d
        ? Math.max(0d, Math.min(1d, remainingOrderQty / totalOrderQty))
        : 1d;
      for (MaterialDemandRow demand : demandByCode.values()) {
        demand.demandQty = round3(Math.max(0d, demand.requiredQty * demandScale));
        work.materialRows.add(demand);
        allMaterialCodes.add(demand.materialCode);
      }
      workRows.add(work);
    }

    return buildOrderMaterialConstraintSnapshotRows(domain, workRows, allMaterialCodes, refreshFromErp);
  }

  static OrderMaterialConstraintSnapshot buildOrderMaterialConstraintSnapshotRows(
    MvpStoreOrderDomain domain,
    List<OrderMaterialWorkRow> workRows,
    Set<String> allMaterialCodes,
    boolean refreshFromErp
  ) {
    Map<String, Map<String, Object>> inventoryByCode = domain.queryInventoryByMaterialCodesInParallel(
      new ArrayList<>(allMaterialCodes),
      refreshFromErp
    );

    List<Map<String, Object>> rows = new ArrayList<>();
    Map<String, OrderMaterialConstraint> constraintByOrderNo = new LinkedHashMap<>();
    for (OrderMaterialWorkRow work : workRows) {
      if (work.materialRows.isEmpty()) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", work.orderNo);
        row.put("order_status", work.orderStatus);
        row.put("product_code", work.productCode);
        row.put("product_name_cn", work.productNameCn);
        row.put("first_process_code", work.firstProcessCode);
        row.put("first_process_name_cn", work.firstProcessNameCn);
        row.put("child_material_code", "");
        row.put("child_material_name_cn", "");
        row.put("demand_qty", 0d);
        row.put("stock_qty", 0d);
        row.put("shortage_qty", 0d);
        row.put("max_schedulable_qty", 0d);
        row.put("order_max_schedulable_qty", 0d);
        row.put("schedulable_flag", 0);
        row.put("data_status", "MISSING_BOM");
        rows.add(domain.localizeRow(row));

        OrderMaterialConstraint constraint = new OrderMaterialConstraint();
        constraint.maxSchedulableQty = 0d;
        constraint.dataStatus = "MISSING_BOM";
        constraint.firstProcessCode = work.firstProcessCode;
        constraintByOrderNo.put(work.orderNo, constraint);
        continue;
      }

      boolean missingStock = false;
      double orderMaxSchedulable = Double.POSITIVE_INFINITY;
      for (MaterialDemandRow demand : work.materialRows) {
        Map<String, Object> inventoryRow = inventoryByCode.get(normalizeCode(demand.materialCode));
        double stockQty = inventoryRow == null ? 0d : Math.max(0d, number(inventoryRow, "stock_qty", 0d));
        int missingFlag = inventoryRow == null ? 1 : (int) Math.round(number(inventoryRow, "inventory_missing_flag", 0d));
        if (missingFlag == 1) {
          missingStock = true;
        }
        double demandQty = Math.max(0d, demand.demandQty);
        double shortageQty = Math.max(0d, demandQty - stockQty);
        double demandRate = work.remainingOrderQty > 1e-9d ? demandQty / work.remainingOrderQty : 0d;
        double maxSchedulableQty = demandRate <= 1e-9d ? Double.POSITIVE_INFINITY : stockQty / demandRate;
        if (maxSchedulableQty < orderMaxSchedulable) {
          orderMaxSchedulable = maxSchedulableQty;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", work.orderNo);
        row.put("order_status", work.orderStatus);
        row.put("product_code", work.productCode);
        row.put("product_name_cn", work.productNameCn);
        row.put("first_process_code", work.firstProcessCode);
        row.put("first_process_name_cn", work.firstProcessNameCn);
        row.put("child_material_code", demand.materialCode);
        row.put("child_material_name_cn", demand.materialNameCn);
        row.put("demand_qty", round3(demandQty));
        row.put("stock_qty", round3(stockQty));
        row.put("shortage_qty", round3(shortageQty));
        row.put(
          "max_schedulable_qty",
          Double.isFinite(maxSchedulableQty) ? round3(Math.max(0d, maxSchedulableQty)) : null
        );
        row.put("inventory_missing_flag", missingFlag);
        rows.add(domain.localizeRow(row));
      }

      double boundedMaxQty = Double.isFinite(orderMaxSchedulable) ? Math.max(0d, orderMaxSchedulable) : work.remainingOrderQty;
      boundedMaxQty = Math.min(work.remainingOrderQty, boundedMaxQty);
      String dataStatus = missingStock ? "MISSING_STOCK" : "OK";
      if (missingStock) {
        boundedMaxQty = 0d;
      }
      int schedulableFlag = boundedMaxQty > 1e-9d ? 1 : 0;

      for (Map<String, Object> row : rows) {
        if (!Objects.equals(work.orderNo, row.get("order_no"))) {
          continue;
        }
        row.put("order_max_schedulable_qty", round3(boundedMaxQty));
        row.put("schedulable_flag", schedulableFlag);
        row.put("data_status", dataStatus);
      }

      OrderMaterialConstraint constraint = new OrderMaterialConstraint();
      constraint.maxSchedulableQty = round3(Math.max(0d, boundedMaxQty));
      constraint.dataStatus = dataStatus;
      constraint.firstProcessCode = work.firstProcessCode;
      constraintByOrderNo.put(work.orderNo, constraint);
    }

    rows.sort((a, b) -> {
      String aOrderNo = String.valueOf(a.get("order_no"));
      String bOrderNo = String.valueOf(b.get("order_no"));
      int byOrder = aOrderNo.compareTo(bOrderNo);
      if (byOrder != 0) {
        return byOrder;
      }
      return String.valueOf(a.get("child_material_code")).compareTo(String.valueOf(b.get("child_material_code")));
    });
    return new OrderMaterialConstraintSnapshot(rows, constraintByOrderNo);
  }

  static void applyOrderMaterialConstraintsForSchedule(
    MvpStoreOrderDomain domain,
    MvpDomain.State scheduleState,
    Map<String, OrderMaterialConstraint> constraintByOrderNo
  ) {
    if (scheduleState == null || scheduleState.orders == null || scheduleState.orders.isEmpty() || constraintByOrderNo == null) {
      return;
    }
    String defaultArrivalDate = scheduleState.startDate == null ? null : scheduleState.startDate.toString();
    for (MvpDomain.Order order : scheduleState.orders) {
      if (order == null || !domain.hasRemainingQty(order)) {
        continue;
      }
      OrderMaterialConstraint constraint = constraintByOrderNo.get(order.orderNo);
      if (constraint == null) {
        continue;
      }
      if ("MISSING_BOM".equalsIgnoreCase(constraint.dataStatus)) {
        continue;
      }
      if (order.businessData == null) {
        order.businessData = new MvpDomain.OrderBusinessData();
      }
      order.businessData.semiFinishedCode = "ORDER_MATERIAL_" + normalizeCode(order.orderNo);
      order.businessData.semiFinishedInventory = "OK".equalsIgnoreCase(constraint.dataStatus)
        ? Math.max(0d, constraint.maxSchedulableQty)
        : 0d;
      order.businessData.semiFinishedWip = 0d;
      order.businessData.pendingInboundQty = 0d;
      if (defaultArrivalDate != null && !defaultArrivalDate.isBlank()) {
        order.businessData.purchaseDueDate = defaultArrivalDate;
        order.businessData.injectionDueDate = defaultArrivalDate;
      }
    }
  }
}

