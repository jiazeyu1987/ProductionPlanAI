package com.autoproduction.mvp.module.dispatchalert;

import com.autoproduction.mvp.core.MvpStoreService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlertCommandService {
  private final MvpStoreService store;

  public AlertCommandService(MvpStoreService store) {
    this.store = store;
  }

  public Map<String, Object> ackAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    return store.ackAlert(alertId, payload, requestId, operator);
  }

  public Map<String, Object> closeAlert(String alertId, Map<String, Object> payload, String requestId, String operator) {
    return store.closeAlert(alertId, payload, requestId, operator);
  }
}
