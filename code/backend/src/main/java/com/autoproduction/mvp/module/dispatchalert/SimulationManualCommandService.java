package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulationManualCommandService {
  private final MvpStoreService store;

  public SimulationManualCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> addManualProductionOrder(Map<String, Object> payload, String requestId, String operator) {
    return store.addManualProductionOrder(payload, requestId, operator);
  }

  public Map<String, Object> advanceManualOneDay(Map<String, Object> payload, String requestId, String operator) {
    return store.advanceManualOneDay(payload, requestId, operator);
  }

  public Map<String, Object> resetManualSimulation(Map<String, Object> payload, String requestId, String operator) {
    return store.resetManualSimulation(payload, requestId, operator);
  }
}
