package com.autoproduction.mvp.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MvpStoreOrderQuerySupport {
  private MvpStoreOrderQuerySupport() {}

  static List<Map<String, Object>> listOrders(MvpStoreOrderDomain domain) {
    domain.syncCompletedQtyFromFinalProcessReports();
    domain.refreshOrderStatuses(domain.simulationState.currentDate == null ? domain.state.startDate : domain.simulationState.currentDate);
    return domain.state.orders.stream().map(domain::toOrderMap).toList();
  }

  static List<Map<String, Object>> listOrderPool(MvpStoreOrderDomain domain, Map<String, String> filters) {
    domain.syncCompletedQtyFromFinalProcessReports();
    domain.refreshOrderStatuses(domain.simulationState.currentDate == null ? domain.state.startDate : domain.simulationState.currentDate);
    Map<String, Map<String, Object>> rowByOrderNo = new LinkedHashMap<>();
    for (MvpDomain.Order order : domain.state.orders) {
      Map<String, Object> row = domain.toOrderPoolItem(order);
      String orderNo = MvpStoreCoreExceptionSupport.string(row, "order_no", null);
      if (orderNo != null && !orderNo.isBlank()) {
        rowByOrderNo.put(orderNo, row);
      }
    }

    if (domain.erpDataManager.shouldIncludeErpOrdersInOrderPool()) {
      List<Map<String, Object>> erpRows = domain.erpDataManager.getProductionOrders();
      for (Map<String, Object> erpRow : erpRows) {
        Map<String, Object> row = domain.toOrderPoolItemFromErp(erpRow);
        String orderNo = MvpStoreCoreExceptionSupport.string(row, "order_no", null);
        if (orderNo == null || orderNo.isBlank()) {
          continue;
        }
        rowByOrderNo.putIfAbsent(orderNo, row);
      }
    }

    return rowByOrderNo.values().stream()
      .filter(row -> domain.filterOrderPoolRow(row, filters))
      .toList();
  }
}
