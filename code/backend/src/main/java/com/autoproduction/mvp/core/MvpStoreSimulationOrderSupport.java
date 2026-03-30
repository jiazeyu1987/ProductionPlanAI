package com.autoproduction.mvp.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class MvpStoreSimulationOrderSupport {
  private MvpStoreSimulationOrderSupport() {}

  static int generateDailySalesAndProductionOrders(
    MvpStoreSimulationEngineSupport domain,
    LocalDate businessDate,
    int dailySales,
    Random random,
    String requestId
  ) {
    int created = 0;
    for (int i = 0; i < dailySales; i += 1) {
      String productCode = domain.pickProductCode(random);
      boolean urgent = random.nextDouble() < 0.2d;
      double qty = (2 + random.nextInt(10)) * 100d;
      LocalDate dueDate = businessDate.plusDays(urgent ? 1 + random.nextInt(2) : 2 + random.nextInt(5));

      String salesOrderNo = "SO-SIM-%05d".formatted(domain.salesSeq.incrementAndGet());
      String productionOrderNo = "MO-SIM-%05d".formatted(domain.productionSeq.incrementAndGet());

      Map<String, Object> salesOrder = new LinkedHashMap<>();
      salesOrder.put("sales_order_no", salesOrderNo);
      salesOrder.put("line_no", "1");
      salesOrder.put("product_code", productCode);
      salesOrder.put("product_name_cn", MvpStoreRuntimeBase.productNameCn(productCode));
      salesOrder.put("order_qty", qty);
      salesOrder.put("order_date", MvpStoreRuntimeBase.toDateTime(businessDate.toString(), "D", true));
      salesOrder.put("expected_due_date", MvpStoreRuntimeBase.toDateTime(dueDate.toString(), "D", true));
      salesOrder.put("requested_ship_date", MvpStoreRuntimeBase.toDateTime(dueDate.toString(), "N", true));
      salesOrder.put("urgent_flag", urgent ? 1 : 0);
      salesOrder.put("order_status", "OPEN");
      salesOrder.put("source", "SIMULATION");
      domain.simulationState.salesOrders.add(salesOrder);

      MvpDomain.Order order = new MvpDomain.Order(
        productionOrderNo,
        "production",
        dueDate,
        businessDate,
        urgent,
        false,
        false,
        "OPEN",
        List.of(new MvpDomain.OrderItem(productCode, qty, 0d)),
        domain.buildSimulationBusinessData(businessDate, dueDate, salesOrderNo, productCode, qty)
      );
      domain.state.orders.add(order);

      domain.appendSimulationEvent(
        businessDate,
        "SALES_RECEIVED",
        "接收到随机销售订单。",
        requestId,
        Map.of("sales_order_no", salesOrderNo, "product_code", productCode, "order_qty", qty)
      );
      domain.appendSimulationEvent(
        businessDate,
        "ORDER_CONVERTED",
        "销售订单已转换为生产订单。",
        requestId,
        Map.of("sales_order_no", salesOrderNo, "production_order_no", productionOrderNo)
      );
      created += 1;
    }
    return created;
  }

  static void refreshOrderStatuses(MvpStoreSimulationEngineSupport domain, LocalDate businessDate) {
    for (MvpDomain.Order order : domain.state.orders) {
      if (isOrderDone(order)) {
        order.status = "DONE";
      } else if (order.dueDate.isBefore(businessDate)) {
        order.status = "DELAY";
      } else if (order.items.stream().anyMatch(item -> item.completedQty > 0d)) {
        order.status = "IN_PROGRESS";
      } else if ("DELAY".equals(order.status)) {
        order.status = "OPEN";
      }
    }
  }

  static int countDelayedOrders(MvpStoreSimulationEngineSupport domain, LocalDate businessDate) {
    int count = 0;
    for (MvpDomain.Order order : domain.state.orders) {
      if (order.dueDate.isBefore(businessDate) && !isOrderDone(order)) {
        count += 1;
      }
    }
    return count;
  }

  static boolean hasRemainingQty(MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return false;
    }
    for (MvpDomain.OrderItem item : order.items) {
      if (item == null) {
        continue;
      }
      if (item.qty - item.completedQty > 1e-9) {
        return true;
      }
    }
    return false;
  }

  static double orderTotalQty(MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return 0d;
    }
    double total = 0d;
    for (MvpDomain.OrderItem item : order.items) {
      if (item == null) {
        continue;
      }
      total += Math.max(0d, item.qty);
    }
    return total;
  }

  static double orderRemainingQty(MvpDomain.Order order) {
    if (order == null || order.items == null || order.items.isEmpty()) {
      return 0d;
    }
    double total = 0d;
    for (MvpDomain.OrderItem item : order.items) {
      if (item == null) {
        continue;
      }
      total += Math.max(0d, item.qty - item.completedQty);
    }
    return total;
  }

  static boolean isOrderDone(MvpDomain.Order order) {
    for (MvpDomain.OrderItem item : order.items) {
      if (item.completedQty + 1e-9 < item.qty) {
        return false;
      }
    }
    return true;
  }
}

