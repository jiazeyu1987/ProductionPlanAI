package com.autoproduction.mvp.core;

import java.util.Map;

import static com.autoproduction.mvp.core.MvpStoreCoreExceptionSupport.string;

final class MvpStoreOrderWriteSupport {
  private MvpStoreOrderWriteSupport() {}

  static Map<String, Object> upsertOrder(
    MvpStoreOrderDomain domain,
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    String orderNo = string(payload, "orderNo", string(payload, "order_no", null));
    if (orderNo == null || orderNo.isBlank()) {
      throw domain.badRequest("orderNo is required.");
    }
    MvpDomain.Order found = domain.state.orders.stream().filter(o -> o.orderNo.equals(orderNo)).findFirst().orElse(null);
    if (found == null) {
      MvpDomain.Order created = domain.fromOrderPayload(payload);
      domain.state.orders.add(created);
      domain.appendAudit("ORDER", created.orderNo, "CREATE_ORDER", operator, requestId, null);
      return domain.toOrderMap(created);
    }
    domain.applyOrderPatch(found, payload);
    domain.appendAudit("ORDER", found.orderNo, "UPDATE_ORDER", operator, requestId, null);
    return domain.toOrderMap(found);
  }

  static Map<String, Object> patchOrder(
    MvpStoreOrderDomain domain,
    String orderNo,
    Map<String, Object> patch,
    String requestId,
    String operator
  ) {
    MvpDomain.Order found = domain.findOrder(orderNo);
    domain.applyOrderPatch(found, patch);
    domain.appendAudit("ORDER", found.orderNo, "PATCH_ORDER", operator, requestId, null);
    return domain.toOrderMap(found);
  }

  static Map<String, Object> deleteOrder(
    MvpStoreOrderDomain domain,
    String orderNo,
    String requestId,
    String operator
  ) {
    MvpDomain.Order found = domain.findOrder(orderNo);
    domain.state.orders.removeIf(order -> orderNo.equals(order.orderNo));
    domain.state.reportings.removeIf(reporting -> orderNo.equals(reporting.orderNo));
    domain.state.schedules.forEach(schedule -> {
      schedule.tasks.removeIf(task -> orderNo.equals(task.orderNo));
      schedule.allocations.removeIf(allocation -> orderNo.equals(allocation.orderNo));
      schedule.unscheduled.removeIf(row -> orderNo.equals(domain.string(row, "order_no", domain.string(row, "orderNo", null))));
    });
    domain.orderPoolMaterialsCache.remove(orderNo);
    domain.materialChildrenByParentCache.clear();
    domain.finalCompletedByOrderProductCache.clear();
    domain.appendAudit("ORDER", found.orderNo, "DELETE_ORDER", operator, requestId, null);
    return Map.of(
      "request_id", requestId,
      "order_no", orderNo,
      "deleted", true
    );
  }

  static void syncOrderCompletionProgress(MvpStoreOrderDomain domain) {
    domain.syncCompletedQtyFromFinalProcessReports();
  }
}
