package com.autoproduction.mvp.module.orderexecution;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderExecutionCommandService {
  private final MvpStoreService store;

  public OrderExecutionCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> patchOrder(String orderNo, Map<String, Object> payload, String requestId, String operator) {
    return store.patchOrder(orderNo, payload, requestId, operator);
  }

  public Map<String, Object> deleteOrder(String orderNo, String requestId, String operator) {
    return store.deleteOrder(orderNo, requestId, operator);
  }

  public Map<String, Object> upsertOrder(Map<String, Object> payload, String requestId, String operator) {
    return store.upsertOrder(payload, requestId, operator);
  }

  public Map<String, Object> recordReporting(Map<String, Object> payload, String requestId, String operator) {
    return store.recordReporting(payload, requestId, operator);
  }

  public Map<String, Object> deleteReporting(String reportingId, String requestId, String operator) {
    return store.deleteReporting(reportingId, requestId, operator);
  }

  public Map<String, Object> importProductionOrdersFromErp(Map<String, Object> payload, String requestId, String operator) {
    return store.importProductionOrdersFromErp(payload, requestId, operator);
  }
}
