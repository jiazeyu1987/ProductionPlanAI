package com.autoproduction.mvp.module.orderexecution;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderExecutionFacade {
  private final OrderPoolQueryService orderPoolQueryService;
  private final OrderMaterialQueryService orderMaterialQueryService;
  private final OrderExecutionCommandService orderExecutionCommandService;
  private final OrderExecutionReadService orderExecutionReadService;
  private final OrderProgressSyncPort orderProgressSyncPort;

  public OrderExecutionFacade(
    OrderPoolQueryService orderPoolQueryService,
    OrderMaterialQueryService orderMaterialQueryService,
    OrderExecutionCommandService orderExecutionCommandService,
    OrderExecutionReadService orderExecutionReadService,
    OrderProgressSyncPort orderProgressSyncPort
  ) {
    this.orderPoolQueryService = orderPoolQueryService;
    this.orderMaterialQueryService = orderMaterialQueryService;
    this.orderExecutionCommandService = orderExecutionCommandService;
    this.orderExecutionReadService = orderExecutionReadService;
    this.orderProgressSyncPort = orderProgressSyncPort;
  }

  public List<Map<String, Object>> listOrderPool(Map<String, String> filters) {
    orderProgressSyncPort.syncOrderCompletionProgress();
    return orderPoolQueryService.listOrderPool(filters);
  }

  public List<Map<String, Object>> listOrderPoolMaterials(String orderNo, boolean refresh) {
    return orderMaterialQueryService.listOrderPoolMaterials(orderNo, refresh);
  }

  public List<Map<String, Object>> listMaterialChildrenByParentCode(String parentMaterialCode, boolean refresh) {
    return orderMaterialQueryService.listMaterialChildrenByParentCode(parentMaterialCode, refresh);
  }

  public List<Map<String, Object>> listOrderMaterialAvailability(boolean refresh) {
    orderProgressSyncPort.syncOrderCompletionProgress();
    return orderMaterialQueryService.listOrderMaterialAvailability(refresh);
  }

  public Map<String, Object> patchOrder(String orderNo, Map<String, Object> payload, String requestId, String operator) {
    return orderExecutionCommandService.patchOrder(orderNo, payload, requestId, operator);
  }

  public Map<String, Object> deleteOrder(String orderNo, String requestId, String operator) {
    return orderExecutionCommandService.deleteOrder(orderNo, requestId, operator);
  }

  public List<Map<String, Object>> listOrders() {
    orderProgressSyncPort.syncOrderCompletionProgress();
    return orderExecutionReadService.listOrders();
  }

  public Map<String, Object> upsertOrder(Map<String, Object> payload, String requestId, String operator) {
    return orderExecutionCommandService.upsertOrder(payload, requestId, operator);
  }

  public Map<String, Object> recordReporting(Map<String, Object> payload, String requestId, String operator) {
    return orderExecutionCommandService.recordReporting(payload, requestId, operator);
  }

  public List<Map<String, Object>> listReportings() {
    return orderExecutionReadService.listReportings();
  }

  public Map<String, Object> deleteReporting(String reportingId, String requestId, String operator) {
    return orderExecutionCommandService.deleteReporting(reportingId, requestId, operator);
  }

  public Map<String, Object> importProductionOrdersFromErp(
    Map<String, Object> payload,
    String requestId,
    String operator
  ) {
    return orderExecutionCommandService.importProductionOrdersFromErp(payload, requestId, operator);
  }
}
