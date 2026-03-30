package com.autoproduction.mvp.module.masterdata;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MasterdataRouteCommandService {
  private final MvpStoreService store;

  public MasterdataRouteCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> createMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return store.createMasterdataRoute(payload, requestId, operator);
  }

  public Map<String, Object> updateMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return store.updateMasterdataRoute(payload, requestId, operator);
  }

  public Map<String, Object> copyMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return store.copyMasterdataRoute(payload, requestId, operator);
  }

  public Map<String, Object> deleteMasterdataRoute(Map<String, Object> payload, String requestId, String operator) {
    return store.deleteMasterdataRoute(payload, requestId, operator);
  }
}
