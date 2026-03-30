package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulationQueryService {
  private final MvpStoreService store;

  public SimulationQueryService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> getSimulationState(String requestId) {
    return store.getSimulationState(requestId);
  }

  public List<Map<String, Object>> listSimulationEvents(Map<String, String> filters) {
    return store.listSimulationEvents(filters);
  }
}
