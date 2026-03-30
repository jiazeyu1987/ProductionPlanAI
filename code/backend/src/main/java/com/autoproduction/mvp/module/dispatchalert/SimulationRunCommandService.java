package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulationRunCommandService {
  private final MvpStoreService store;

  public SimulationRunCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> resetSimulation(Map<String, Object> payload, String requestId, String operator) {
    return store.resetSimulation(payload, requestId, operator);
  }

  public Map<String, Object> runSimulation(Map<String, Object> payload, String requestId, String operator) {
    return store.runSimulation(payload, requestId, operator);
  }
}
